package com.health.jinryohanjang.domain.summary.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.department.entity.Department;
import com.health.jinryohanjang.domain.department.repository.DepartmentRepository;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.service.MemberService;
import com.health.jinryohanjang.domain.summary.dto.RecordDeleteResponse;
import com.health.jinryohanjang.domain.summary.dto.RecordDetailResponse;
import com.health.jinryohanjang.domain.summary.dto.RecordListResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryContent;
import com.health.jinryohanjang.domain.summary.dto.SummarySaveResponse;
import com.health.jinryohanjang.domain.summary.entity.MedicalRecord;
import com.health.jinryohanjang.domain.summary.entity.RecordHealthInfo;
import com.health.jinryohanjang.domain.summary.enums.HealthInfoType;
import com.health.jinryohanjang.domain.summary.model.Summary;
import com.health.jinryohanjang.domain.summary.repository.MedicalRecordRepository;
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;

/**
 * 내 기록(보관한 요약지)의 보관·목록·상세·삭제를 처리한다.
 * 보관은 Redis의 요약지를 DB로 복사하고, 복사가 끝나면 Redis의 문진·요약지를 바로 지운다.
 */
@Service
public class RecordService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String SUBTITLE_SEPARATOR = " · ";

	private final MedicalRecordRepository medicalRecordRepository;
	private final DepartmentRepository departmentRepository;
	private final ConsentRepository consentRepository;
	private final MemberService memberService;
	private final SummaryService summaryService;
	private final SummaryStore summaryStore;
	private final IntakeStore intakeStore;
	private final String recordConsentVersion;

	public RecordService(
		MedicalRecordRepository medicalRecordRepository,
		DepartmentRepository departmentRepository,
		ConsentRepository consentRepository,
		MemberService memberService,
		SummaryService summaryService,
		SummaryStore summaryStore,
		IntakeStore intakeStore,
		@Value("${app.consent.version.record}") String recordConsentVersion) {
		this.medicalRecordRepository = medicalRecordRepository;
		this.departmentRepository = departmentRepository;
		this.consentRepository = consentRepository;
		this.memberService = memberService;
		this.summaryService = summaryService;
		this.summaryStore = summaryStore;
		this.intakeStore = intakeStore;
		this.recordConsentVersion = recordConsentVersion;
	}

	/**
	 * 이미 보관한 요약지인지는 Redis보다 DB를 먼저 봐서, Redis가 지워졌어도 SUMMARY_409와 medicalRecordId를 준다.
	 * 동시에 두 번 보내면 source_summary_id UNIQUE에 걸린 쪽도 같은 응답을 준다.
	 * UNIQUE 위반 뒤에 다시 조회할 수 있도록 메서드 전체를 한 트랜잭션으로 묶지 않는다.
	 */
	public SummarySaveResponse save(Long memberId, String summaryId, String guestKey) {
		Member member = memberService.getActiveMember(memberId);
		throwIfAlreadySaved(summaryId, memberId);
		if (!consentRepository.existsByMemberIdAndConsentTypeAndPolicyVersionAndWithdrawnAtIsNull(
			memberId, ConsentType.RECORD, recordConsentVersion)) {
			throw new GeneralException(ErrorStatus.CONSENT_RECORD_REQUIRED);
		}
		Summary summary = summaryService.getDone(summaryId, guestKey);
		String departmentCode = summary.getDepartment().departmentCode();
		Department department = departmentRepository.findById(departmentCode)
			.orElseThrow(() -> new IllegalStateException("없는 진료과 코드입니다: " + departmentCode));

		MedicalRecord record;
		try {
			record = medicalRecordRepository.saveAndFlush(MedicalRecord.copyOf(member, department, summary));
		} catch (DataIntegrityViolationException e) {
			throwIfAlreadySaved(summaryId, memberId);
			throw e;
		}
		summaryStore.delete(summaryId, summary.getGuestKey());
		intakeStore.find(summary.getIntakeId()).ifPresent(intakeStore::delete);
		return new SummarySaveResponse(record.getId(), toOffset(record.getCreatedAt()));
	}

	@Transactional(readOnly = true)
	public RecordListResponse getRecords(Long memberId, int page, int size) {
		memberService.getActiveMember(memberId);
		Page<MedicalRecord> records = medicalRecordRepository.findAllByMemberIdAndDeletedAtIsNull(memberId,
			PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		List<RecordListResponse.RecordItem> items = records.stream()
			.map(record -> new RecordListResponse.RecordItem(record.getId(), toOffset(record.getCreatedAt()),
				record.getDepartment().getName(), record.getMainSymptom(), subtitle(record)))
			.toList();
		return new RecordListResponse(page, size, records.getTotalElements(), records.hasNext(), items);
	}

	@Transactional(readOnly = true)
	public RecordDetailResponse getRecord(Long memberId, Long medicalRecordId) {
		MedicalRecord record = load(memberId, medicalRecordId);
		return new RecordDetailResponse(record.getId(), toOffset(record.getCreatedAt()), toContent(record));
	}

	@Transactional
	public RecordDeleteResponse delete(Long memberId, Long medicalRecordId) {
		MedicalRecord record = load(memberId, medicalRecordId);
		record.delete(LocalDateTime.now(SEOUL).withNano(0));
		return new RecordDeleteResponse(record.getId());
	}

	@Transactional(readOnly = true)
	public long countRecords(Long memberId) {
		return medicalRecordRepository.countByMemberIdAndDeletedAtIsNull(memberId);
	}

	/**
	 * 내 기록이면 SUMMARY_409 + medicalRecordId, 다른 회원이 보관한 요약지면 SUMMARY_403이다.
	 */
	private void throwIfAlreadySaved(String summaryId, Long memberId) {
		medicalRecordRepository.findBySourceSummaryId(summaryId).ifPresent(record -> {
			if (!record.isOwnedBy(memberId)) {
				throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
			}
			throw new GeneralException(ErrorStatus.SUMMARY_ALREADY_SAVED, Map.of("medicalRecordId", record.getId()));
		});
	}

	/**
	 * 없거나 지운 기록이면 404, 다른 회원의 기록이면 403이다.
	 */
	private MedicalRecord load(Long memberId, Long medicalRecordId) {
		memberService.getActiveMember(memberId);
		MedicalRecord record = medicalRecordRepository.findById(medicalRecordId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(() -> new GeneralException(ErrorStatus.RECORD_NOT_FOUND));
		if (!record.isOwnedBy(memberId)) {
			throw new GeneralException(ErrorStatus.RECORD_FORBIDDEN);
		}
		return record;
	}

	/**
	 * H01 두 번째 줄이다. onsetDetail과 첫 번째 증상 항목("붓기 있음")을 잇고, 항목이 없으면 onsetDetail만 쓴다.
	 */
	private String subtitle(MedicalRecord record) {
		String firstItem = record.getItems().stream()
			.findFirst()
			.map(item -> item.getAnswer() == null ? item.getItemLabel() : item.getItemLabel() + " " + item.getAnswer())
			.orElse(null);
		String subtitle = Stream.of(record.getOnsetDetail(), firstItem)
			.filter(value -> value != null && !value.isBlank())
			.collect(Collectors.joining(SUBTITLE_SEPARATOR));
		return subtitle.isEmpty() ? null : subtitle;
	}

	private SummaryContent toContent(MedicalRecord record) {
		Department department = record.getDepartment();
		return new SummaryContent(record.getAgeGroup(), record.getGender(), record.getInputMode(),
			record.getMainSymptom(), record.getBodyPart(), record.getOnset(), record.getOnsetDetail(),
			record.getExtraNote(), toContent(record.healthInfosOf(HealthInfoType.MEDICATION)),
			toContent(record.healthInfosOf(HealthInfoType.CONDITION)),
			toContent(record.healthInfosOf(HealthInfoType.ALLERGY)),
			record.getItems().stream()
				.map(item -> new SummaryContent.Item(item.getDisplayOrder(), item.getItemLabel(), item.getQuestion(),
					item.getAnswer()))
				.toList(),
			new SummaryContent.Department(department.getCode(), department.getName(), record.getRecommendReason()),
			record.isConfirmed());
	}

	private List<SummaryContent.HealthInfo> toContent(List<RecordHealthInfo> values) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.map(value -> new SummaryContent.HealthInfo(value.getInfoName(), value.getDetail()))
			.toList();
	}

	/**
	 * 다른 API와 같이 초 단위까지만 내린다.
	 */
	private OffsetDateTime toOffset(LocalDateTime dateTime) {
		return dateTime.withNano(0).atZone(SEOUL).toOffsetDateTime();
	}
}
