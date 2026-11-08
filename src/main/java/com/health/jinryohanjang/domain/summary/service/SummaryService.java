package com.health.jinryohanjang.domain.summary.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.exception.GeneralExceptionAdvice.FieldErrorDetail;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.summary.dto.SummaryConfirmResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryContent;
import com.health.jinryohanjang.domain.summary.dto.SummaryResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryRetryResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryUpdateRequest;
import com.health.jinryohanjang.domain.summary.enums.SummaryStatus;
import com.health.jinryohanjang.domain.summary.model.Summary;
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;

import lombok.RequiredArgsConstructor;

/**
 * 보관 전 요약지의 조회·수정·확인·다시 만들기를 처리한다. 주인은 guestKey로 판정한다.
 * 요청이 올 때마다 요약지와 문진의 보관 시간을 1시간으로 늘린다.
 */
@Service
@RequiredArgsConstructor
public class SummaryService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SummaryStore summaryStore;
	private final IntakeStore intakeStore;
	private final SummaryGenerator summaryGenerator;

	public SummaryResponse get(String summaryId, String guestKey) {
		return toResponse(load(summaryId, guestKey));
	}

	public SummaryResponse update(String summaryId, String guestKey, SummaryUpdateRequest request) {
		Summary summary = loadDone(summaryId, guestKey);
		validate(summary, request);

		if (request.has(SummaryUpdateRequest.AGE_GROUP)) {
			summary.changeAgeGroup(request.getAgeGroup());
		}
		if (request.has(SummaryUpdateRequest.GENDER)) {
			summary.changeGender(request.getGender());
		}
		if (request.has(SummaryUpdateRequest.MAIN_SYMPTOM)) {
			summary.changeMainSymptom(request.getMainSymptom().trim());
		}
		if (request.has(SummaryUpdateRequest.BODY_PART)) {
			summary.changeBodyPart(request.getBodyPart());
		}
		if (request.has(SummaryUpdateRequest.ONSET)) {
			String onsetDetail = request.has(SummaryUpdateRequest.ONSET_DETAIL) ? trim(request.getOnsetDetail())
				: request.getOnset() == null ? null : request.getOnset().getLabel();
			summary.changeOnset(request.getOnset(), onsetDetail);
		} else if (request.has(SummaryUpdateRequest.ONSET_DETAIL)) {
			summary.changeOnsetDetail(trim(request.getOnsetDetail()));
		}
		if (request.has(SummaryUpdateRequest.EXTRA_NOTE)) {
			summary.changeExtraNote(trim(request.getExtraNote()));
		}
		if (request.has(SummaryUpdateRequest.MEDICATIONS)) {
			summary.changeMedications(toModel(request.getMedications()));
		}
		if (request.has(SummaryUpdateRequest.CONDITIONS)) {
			summary.changeConditions(toModel(request.getConditions()));
		}
		if (request.has(SummaryUpdateRequest.ALLERGIES)) {
			summary.changeAllergies(toModel(request.getAllergies()));
		}
		if (request.getItems() != null) {
			for (SummaryUpdateRequest.ItemUpdate item : request.getItems()) {
				summary.changeItemValue(item.order(), trim(item.value()));
			}
		}
		summaryStore.save(summary);
		return toResponse(summary);
	}

	/**
	 * 보관할 요약지를 찾는다. guestKey가 없거나 다르면 403, 없으면 404, 생성 중·실패면 409(result null)다.
	 */
	public Summary getDone(String summaryId, String guestKey) {
		return loadDone(summaryId, guestKey);
	}

	public SummaryConfirmResponse confirm(String summaryId, String guestKey) {
		Summary summary = loadDone(summaryId, guestKey);
		summary.confirm();
		summaryStore.save(summary);
		return new SummaryConfirmResponse(summary.getSummaryId(), summary.isConfirmed());
	}

	/**
	 * 실패한 요약지를 같은 summaryId로 다시 만든다. 문진은 남아 있는데 요약지만 없으면 새로 만든다.
	 */
	public SummaryRetryResponse retry(String intakeId, String guestKey) {
		if (!StringUtils.hasText(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		IntakeSession session = intakeStore.find(intakeId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.SUMMARY_NOT_FOUND));
		if (!session.isOwnedBy(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		if (session.getStatus() != IntakeStatus.COMPLETED) {
			throw new GeneralException(ErrorStatus.SUMMARY_INTAKE_NOT_COMPLETED);
		}
		Optional<Summary> found = summaryStore.find(session.getSummaryId());
		if (found.isPresent()) {
			Summary summary = failIfOverdue(found.get());
			if (!summary.isFailed()) {
				throw new GeneralException(ErrorStatus.SUMMARY_NOT_FAILED);
			}
			summaryGenerator.restart(summary, session);
		} else {
			summaryGenerator.start(session);
		}
		intakeStore.touch(session.getIntakeId(), session.getGuestKey());
		return new SummaryRetryResponse(session.getIntakeId(), session.getSummaryId(), SummaryStatus.GENERATING);
	}

	/**
	 * 요약지를 찾아 주인(guestKey)을 확인한다. guestKey가 없거나 다르면 403, 없으면 404다.
	 * 마감이 지났는데 GENERATING이면 (서버 재시작 등) 여기서 FAILED로 바꾼다.
	 */
	private Summary load(String summaryId, String guestKey) {
		if (!StringUtils.hasText(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		Summary summary = summaryStore.find(summaryId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.SUMMARY_NOT_FOUND));
		if (!summary.isOwnedBy(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		summary = failIfOverdue(summary);
		summaryStore.touch(summary);
		intakeStore.touch(summary.getIntakeId(), summary.getGuestKey());
		return summary;
	}

	private Summary loadDone(String summaryId, String guestKey) {
		Summary summary = load(summaryId, guestKey);
		if (!summary.isDone()) {
			throw new GeneralException(ErrorStatus.SUMMARY_NOT_READY);
		}
		return summary;
	}

	private Summary failIfOverdue(Summary summary) {
		if (summary.isExpired(OffsetDateTime.now(SEOUL))) {
			summary.fail();
			summaryStore.save(summary);
		}
		return summary;
	}

	/**
	 * 길이, null 불가 필드의 null, 없는 order, 이름 없는 건강정보를 확인한다.
	 */
	private void validate(Summary summary, SummaryUpdateRequest request) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (request.has(SummaryUpdateRequest.AGE_GROUP) && request.getAgeGroup() == null) {
			errors.add(new FieldErrorDetail(SummaryUpdateRequest.AGE_GROUP, "나이대는 비울 수 없어요"));
		}
		if (request.has(SummaryUpdateRequest.GENDER) && request.getGender() == null) {
			errors.add(new FieldErrorDetail(SummaryUpdateRequest.GENDER, "성별은 비울 수 없어요"));
		}
		if (request.has(SummaryUpdateRequest.MAIN_SYMPTOM)) {
			if (!StringUtils.hasText(request.getMainSymptom())) {
				errors.add(new FieldErrorDetail(SummaryUpdateRequest.MAIN_SYMPTOM, "주된 증상을 입력해 주세요"));
			} else if (request.getMainSymptom().trim().length() > SummaryUpdateRequest.MAIN_SYMPTOM_MAX_LENGTH) {
				errors.add(new FieldErrorDetail(SummaryUpdateRequest.MAIN_SYMPTOM,
					"%d자까지 입력할 수 있어요".formatted(SummaryUpdateRequest.MAIN_SYMPTOM_MAX_LENGTH)));
			}
		}
		String onsetDetail = trim(request.getOnsetDetail());
		if (onsetDetail != null && onsetDetail.length() > SummaryUpdateRequest.ONSET_DETAIL_MAX_LENGTH) {
			errors.add(new FieldErrorDetail(SummaryUpdateRequest.ONSET_DETAIL,
				"%d자까지 입력할 수 있어요".formatted(SummaryUpdateRequest.ONSET_DETAIL_MAX_LENGTH)));
		}
		validateHealthInfo(SummaryUpdateRequest.MEDICATIONS, request.getMedications(), errors);
		validateHealthInfo(SummaryUpdateRequest.CONDITIONS, request.getConditions(), errors);
		validateHealthInfo(SummaryUpdateRequest.ALLERGIES, request.getAllergies(), errors);
		List<SummaryUpdateRequest.ItemUpdate> items = request.getItems();
		if (items != null) {
			for (int i = 0; i < items.size(); i++) {
				SummaryUpdateRequest.ItemUpdate item = items.get(i);
				if (item == null || item.order() == null || !summary.hasItem(item.order())) {
					errors.add(new FieldErrorDetail("items[%d].order".formatted(i), "없는 항목이에요"));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw new GeneralException(ErrorStatus.SUMMARY_INVALID_INPUT, Map.of("errors", errors));
		}
	}

	private void validateHealthInfo(String field, List<SummaryContent.HealthInfo> values,
		List<FieldErrorDetail> errors) {
		if (values == null) {
			return;
		}
		for (int i = 0; i < values.size(); i++) {
			SummaryContent.HealthInfo value = values.get(i);
			if (value == null || !StringUtils.hasText(value.name())) {
				errors.add(new FieldErrorDetail("%s[%d].name".formatted(field, i), "이름을 입력해 주세요"));
			}
		}
	}

	private List<Summary.HealthInfo> toModel(List<SummaryContent.HealthInfo> values) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.map(value -> new Summary.HealthInfo(value.name().trim(), trim(value.detail())))
			.toList();
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private SummaryResponse toResponse(Summary summary) {
		List<SummaryResponse.Step> steps = summary.getSteps().stream()
			.map(step -> new SummaryResponse.Step(step.step(), step.status()))
			.toList();
		SummaryContent content = summary.isDone() ? toContent(summary) : null;
		return new SummaryResponse(summary.getSummaryId(), summary.getIntakeId(), summary.getStatus(), steps, content,
			summary.getCreatedAt().atZoneSameInstant(SEOUL).toOffsetDateTime());
	}

	private SummaryContent toContent(Summary summary) {
		Summary.Department department = summary.getDepartment();
		return new SummaryContent(summary.getAgeGroup(), summary.getGender(), summary.getInputMode(),
			summary.getMainSymptom(), summary.getBodyPart(), summary.getOnset(), summary.getOnsetDetail(),
			summary.getExtraNote(), toContent(summary.getMedications()), toContent(summary.getConditions()),
			toContent(summary.getAllergies()),
			summary.getItems() == null ? List.of() : summary.getItems().stream()
				.map(item -> new SummaryContent.Item(item.order(), item.label(), item.question(), item.value()))
				.toList(),
			department == null ? null : new SummaryContent.Department(department.departmentCode(),
				department.departmentName(), department.recommendReason()),
			summary.isConfirmed());
	}

	private List<SummaryContent.HealthInfo> toContent(List<Summary.HealthInfo> values) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.map(value -> new SummaryContent.HealthInfo(value.name(), value.detail()))
			.toList();
	}
}
