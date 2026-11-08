package com.health.jinryohanjang.domain.guardian.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.exception.GeneralExceptionAdvice.FieldErrorDetail;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.service.ConsentService;
import com.health.jinryohanjang.domain.guardian.dto.GuardianRequest;
import com.health.jinryohanjang.domain.guardian.dto.GuardianResponse;
import com.health.jinryohanjang.domain.guardian.entity.Guardian;
import com.health.jinryohanjang.domain.guardian.repository.GuardianRepository;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.service.MemberService;

/**
 * 보호자 등록·변경·삭제를 처리한다. 보호자와 보호자 연락처 저장 동의(GUARDIAN)는 함께 저장하고 함께 철회한다.
 */
@Service
public class GuardianService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final GuardianRepository guardianRepository;
	private final MemberService memberService;
	private final ConsentService consentService;
	private final String guardianConsentVersion;

	public GuardianService(
		GuardianRepository guardianRepository,
		MemberService memberService,
		ConsentService consentService,
		@Value("${app.consent.version.guardian}") String guardianConsentVersion) {
		this.guardianRepository = guardianRepository;
		this.memberService = memberService;
		this.consentService = consentService;
		this.guardianConsentVersion = guardianConsentVersion;
	}

	/**
	 * 회원당 한 행을 upsert한다. 지워진 행이 있으면 되살려 새 값으로 덮어쓴다.
	 */
	@Transactional
	public GuardianResponse save(Long memberId, GuardianRequest request) {
		if (!guardianConsentVersion.equals(request.policyVersion())) {
			throw new GeneralException(ErrorStatus.GUARDIAN_INVALID_INPUT,
				Map.of("errors", List.of(new FieldErrorDetail("policyVersion", "현재 동의 문구 버전이 아니에요"))));
		}
		Member member = memberService.getActiveMember(memberId);
		Guardian guardian = guardianRepository.findByMemberId(memberId)
			.map(found -> {
				found.change(request.relation(), request.phoneNumber());
				return found;
			})
			.orElseGet(() -> guardianRepository.save(
				Guardian.register(member, request.relation(), request.phoneNumber())));
		consentService.agree(member, ConsentType.GUARDIAN, request.policyVersion());
		return new GuardianResponse(guardian.getId(), guardian.getRelation(), guardian.maskedPhoneNumber());
	}

	@Transactional
	public void delete(Long memberId) {
		memberService.getActiveMember(memberId);
		Guardian guardian = guardianRepository.findByMemberIdAndDeletedAtIsNull(memberId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.GUARDIAN_NOT_FOUND));
		guardian.delete(LocalDateTime.now(SEOUL).withNano(0));
		consentService.withdraw(memberId, ConsentType.GUARDIAN);
	}

	/**
	 * 응급 카드에 쓰는 보호자다. 로그인하지 않았거나 등록된 보호자가 없으면 비어 있다.
	 */
	@Transactional(readOnly = true)
	public Optional<Guardian> findActive(Long memberId) {
		if (memberId == null) {
			return Optional.empty();
		}
		return guardianRepository.findByMemberIdAndDeletedAtIsNull(memberId);
	}
}
