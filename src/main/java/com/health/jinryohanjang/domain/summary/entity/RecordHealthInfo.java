package com.health.jinryohanjang.domain.summary.entity;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.common.util.EncryptedStringConverter;
import com.health.jinryohanjang.domain.summary.enums.HealthInfoType;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보관한 기록의 복용약·앓는 병·알레르기 한 항목이다. 이름과 자세한 내용은 암호화해 저장한다.
 */
@Getter
@Entity
@Table(name = "record_health_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordHealthInfo extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "record_health_info_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medical_record_id", nullable = false)
	private MedicalRecord medicalRecord;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HealthInfoType infoType;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(nullable = false, columnDefinition = "TEXT")
	private String infoName;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(columnDefinition = "TEXT")
	private String detail;

	RecordHealthInfo(MedicalRecord medicalRecord, HealthInfoType infoType, String infoName, String detail) {
		this.medicalRecord = medicalRecord;
		this.infoType = infoType;
		this.infoName = infoName;
		this.detail = detail;
	}
}
