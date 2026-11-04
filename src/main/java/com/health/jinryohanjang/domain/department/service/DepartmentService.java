package com.health.jinryohanjang.domain.department.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.department.dto.RecommendedDepartmentResponse;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;

import lombok.RequiredArgsConstructor;

/**
 * 문진이 끝날 때 계산해 둔 추천 진료과를 돌려준다. 여기서는 AI를 다시 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

	private final IntakeStore intakeStore;

	public RecommendedDepartmentResponse getRecommended(String intakeId, String guestKey) {
		if (!StringUtils.hasText(guestKey)) {
			throw new GeneralException(ErrorStatus.DEPARTMENT_FORBIDDEN);
		}
		IntakeSession session = intakeStore.find(intakeId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.DEPARTMENT_NOT_FOUND));
		if (!session.isOwnedBy(guestKey)) {
			throw new GeneralException(ErrorStatus.DEPARTMENT_FORBIDDEN);
		}
		if (session.getStatus() != IntakeStatus.COMPLETED) {
			throw new GeneralException(ErrorStatus.DEPARTMENT_NOT_COMPLETED);
		}
		intakeStore.save(session);
		return new RecommendedDepartmentResponse(session.getIntakeId(), session.getDepartmentCode(),
			session.getDepartmentName(), session.getRecommendReason());
	}
}
