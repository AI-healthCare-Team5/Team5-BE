package com.health.jinryohanjang.domain.summary.entity;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.common.util.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
 * 보관한 기록의 증상별 문답 한 줄이다. 답은 암호화해 저장한다.
 */
@Getter
@Entity
@Table(name = "record_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordItem extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "record_item_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medical_record_id", nullable = false)
	private MedicalRecord medicalRecord;

	@Column(nullable = false)
	private String question;

	@Column(nullable = false, length = 50)
	private String itemLabel;

	/**
	 * null = 확인 못 함 (요약지에서 사용자가 지운 답)
	 */
	@Convert(converter = EncryptedStringConverter.class)
	@Column(columnDefinition = "TEXT")
	private String answer;

	@Column(nullable = false)
	private int displayOrder;

	RecordItem(MedicalRecord medicalRecord, int displayOrder, String itemLabel, String question, String answer) {
		this.medicalRecord = medicalRecord;
		this.displayOrder = displayOrder;
		this.itemLabel = itemLabel;
		this.question = question;
		this.answer = answer;
	}
}
