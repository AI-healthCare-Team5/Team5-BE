package com.health.jinryohanjang.domain.summary.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.common.util.EncryptedStringConverter;
import com.health.jinryohanjang.domain.department.entity.Department;
import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;
import com.health.jinryohanjang.domain.summary.enums.HealthInfoStatus;
import com.health.jinryohanjang.domain.summary.enums.HealthInfoType;
import com.health.jinryohanjang.domain.summary.model.Summary;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 기록에 보관한 요약지다. Redis의 요약지를 복사해 만든다.
 * 건강정보 컬럼은 암호화해 저장하고, source_summary_id(UNIQUE)로 같은 요약지를 두 번 보관하지 않는다.
 * 복용약·앓는 병·알레르기는 상태 컬럼으로 null(NOT_ASKED)과 [](NONE)을 구분한다.
 */
@Getter
@Entity
@Table(name = "medical_record", uniqueConstraints = @UniqueConstraint(
	name = "uk_medical_record_source_summary_id", columnNames = "source_summary_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicalRecord extends BaseEntity {

	private static final int RECOMMEND_REASON_MAX_LENGTH = 255;
	private static final int QUESTION_MAX_LENGTH = 255;
	private static final int ITEM_LABEL_MAX_LENGTH = 50;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "medical_record_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_code", nullable = false)
	private Department department;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AgeGroup ageGroup;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Gender gender;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(nullable = false, columnDefinition = "TEXT")
	private String mainSymptom;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private BodyPart bodyPart;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Onset onset;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(columnDefinition = "TEXT")
	private String onsetDetail;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(columnDefinition = "TEXT")
	private String extraNote;

	@Column(nullable = false)
	private String recommendReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private InputMode inputMode;

	@Column(nullable = false)
	private boolean isConfirmed;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HealthInfoStatus medicationStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HealthInfoStatus conditionStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HealthInfoStatus allergyStatus;

	@Column(name = "source_summary_id", nullable = false, length = 36)
	private String sourceSummaryId;

	private LocalDateTime deletedAt;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL)
	@OrderBy("displayOrder")
	private List<RecordItem> items = new ArrayList<>();

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<RecordHealthInfo> healthInfos = new ArrayList<>();

	/**
	 * 생성이 끝난(DONE) 요약지를 그대로 복사한다.
	 */
	public static MedicalRecord copyOf(Member member, Department department, Summary summary) {
		MedicalRecord record = new MedicalRecord();
		record.member = member;
		record.department = department;
		record.ageGroup = summary.getAgeGroup();
		record.gender = summary.getGender();
		record.mainSymptom = summary.getMainSymptom();
		record.bodyPart = summary.getBodyPart();
		record.onset = summary.getOnset();
		record.onsetDetail = summary.getOnsetDetail();
		record.extraNote = summary.getExtraNote();
		record.recommendReason = cut(summary.getDepartment().recommendReason(), RECOMMEND_REASON_MAX_LENGTH);
		record.inputMode = summary.getInputMode();
		record.isConfirmed = summary.isConfirmed();
		record.medicationStatus = HealthInfoStatus.of(summary.getMedications());
		record.conditionStatus = HealthInfoStatus.of(summary.getConditions());
		record.allergyStatus = HealthInfoStatus.of(summary.getAllergies());
		record.sourceSummaryId = summary.getSummaryId();
		if (summary.getItems() != null) {
			for (Summary.Item item : summary.getItems()) {
				record.items.add(new RecordItem(record, item.order(), cut(item.label(), ITEM_LABEL_MAX_LENGTH),
					cut(item.question(), QUESTION_MAX_LENGTH), item.value()));
			}
		}
		record.addHealthInfos(HealthInfoType.MEDICATION, summary.getMedications());
		record.addHealthInfos(HealthInfoType.CONDITION, summary.getConditions());
		record.addHealthInfos(HealthInfoType.ALLERGY, summary.getAllergies());
		return record;
	}

	public boolean isOwnedBy(Long memberId) {
		return member.getId().equals(memberId);
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void delete(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	/**
	 * 상태가 NOT_ASKED면 null, NONE이면 [], HAS면 그 종류의 항목 목록이다.
	 */
	public List<RecordHealthInfo> healthInfosOf(HealthInfoType type) {
		HealthInfoStatus status = switch (type) {
			case MEDICATION -> medicationStatus;
			case CONDITION -> conditionStatus;
			case ALLERGY -> allergyStatus;
		};
		if (status == HealthInfoStatus.NOT_ASKED) {
			return null;
		}
		return healthInfos.stream()
			.filter(info -> info.getInfoType() == type)
			.toList();
	}

	private void addHealthInfos(HealthInfoType type, List<Summary.HealthInfo> values) {
		if (values == null) {
			return;
		}
		for (Summary.HealthInfo value : values) {
			healthInfos.add(new RecordHealthInfo(this, type, value.name(), value.detail()));
		}
	}

	private static String cut(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
