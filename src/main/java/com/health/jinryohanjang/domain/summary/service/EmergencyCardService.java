package com.health.jinryohanjang.domain.summary.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.guardian.service.GuardianService;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.enums.Stage;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.intake.service.BasicInfoQuestions;
import com.health.jinryohanjang.domain.summary.dto.EmergencyCardResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryContent;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

import lombok.RequiredArgsConstructor;

/**
 * 응급 전환된 문진의 응급 카드(E02)를 만든다. 문진에서 모은 정보만 쓰고, 못 물은 항목은 null("확인 못 했어요")이다.
 * 응급 상황이라 AI를 부르지 않는다. 복용약·앓는 병·알레르기는 사용자가 한 말을 그대로 한 항목으로 담는다.
 * 보호자는 로그인했고 등록된 경우에만 전체 번호로 담고, 아니면 null이다.
 */
@Service
@RequiredArgsConstructor
public class EmergencyCardService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int NONE_ANSWER_MAX_LENGTH = 10;

	private final IntakeStore intakeStore;
	private final GuardianService guardianService;

	public EmergencyCardResponse get(String intakeId, String guestKey, Long memberId) {
		if (!StringUtils.hasText(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		IntakeSession session = intakeStore.find(intakeId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.SUMMARY_NOT_FOUND));
		if (!session.isOwnedBy(guestKey)) {
			throw new GeneralException(ErrorStatus.SUMMARY_FORBIDDEN);
		}
		if (session.getStatus() != IntakeStatus.EMERGENCY) {
			throw new GeneralException(ErrorStatus.SUMMARY_NOT_EMERGENCY);
		}
		intakeStore.touch(session.getIntakeId(), session.getGuestKey());

		OffsetDateTime emergencyAt = session.getEmergencyAt() != null ? session.getEmergencyAt() : session.getCreatedAt();
		return new EmergencyCardResponse(
			session.getIntakeId(),
			emergencyAt.atZoneSameInstant(SEOUL).toOffsetDateTime(),
			session.getEmergencyUtterance(),
			session.getRedFlagCategory(),
			session.getMainSymptom(),
			session.getOnsetDetail(),
			basicAnswer(session, BasicInfoQuestions.AGE_GROUP).map(AgeGroup::valueOf).orElse(null),
			basicAnswer(session, BasicInfoQuestions.GENDER).map(Gender::valueOf).orElse(null),
			healthInfo(basicAnswer(session, BasicInfoQuestions.MEDICATIONS)),
			healthInfo(basicAnswer(session, BasicInfoQuestions.CONDITIONS)),
			healthInfo(basicAnswer(session, BasicInfoQuestions.ALLERGIES)),
			guardianService.findActive(memberId)
				.map(guardian -> new EmergencyCardResponse.Guardian(guardian.getRelation(),
					guardian.formattedPhoneNumber()))
				.orElse(null));
	}

	private Optional<String> basicAnswer(IntakeSession session, String questionId) {
		return session.getAnswered().stream()
			.filter(answer -> answer.stage() == Stage.BASIC_INFO && answer.question().questionId().equals(questionId))
			.map(IntakeSession.Answered::normalizedAnswer)
			.filter(StringUtils::hasText)
			.reduce((first, second) -> second);
	}

	/**
	 * 묻지 못했거나 모른다고 하면 null, "없어요"처럼 짧게 없다고 하면 [], 그 외에는 한 말을 그대로 한 항목으로 담는다.
	 */
	private List<SummaryContent.HealthInfo> healthInfo(Optional<String> answer) {
		if (answer.isEmpty()) {
			return null;
		}
		String text = answer.get().trim();
		if (text.contains("모르") || text.contains("몰라")) {
			return null;
		}
		if (text.length() <= NONE_ANSWER_MAX_LENGTH && text.contains("없")) {
			return List.of();
		}
		return List.of(new SummaryContent.HealthInfo(text, null));
	}
}
