package com.health.jinryohanjang.domain.guardian.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
	private final TransactionTemplate transactionTemplate;
	private final String guardianConsentVersion;

	public GuardianService(
		GuardianRepository guardianRepository,
		MemberService memberService,
		ConsentService consentService,
		TransactionTemplate transactionTemplate,
		@Value("${app.consent.version.guardian}") String guardianConsentVersion) {
		this.guardianRepository = guardianRepository;
		this.memberService = memberService;
		this.consentService = consentService;
		this.transactionTemplate = transactionTemplate;
		this.guardianConsentVersion = guardianConsentVersion;
	}

	/**
	 * 회원당 한 행을 upsert한다. 지워진 행이 있으면 되살려 새 값으로 덮어쓴다.
	 * 같은 회원이 동시에 두 번 보내면 늦은 쪽이 UNIQUE에 걸리므로, 한 번 더 시도해 먼저 저장된 행을 덮어쓴다.
	 * 다시 시도하려면 트랜잭션이 새로 시작돼야 해서 메서드가 아니라 TransactionTemplate으로 묶는다.
	 */
	public GuardianResponse save(Long memberId, GuardianRequest request) {
		if (!guardianConsentVersion.equals(request.policyVersion())) {
			throw new GeneralException(ErrorStatus.GUARDIAN_INVALID_INPUT,
				Map.of("errors", List.of(new FieldErrorDetail("policyVersion", "현재 동의 문구 버전이 아니에요"))));
		}
		try {
			return transactionTemplate.execute(status -> upsert(memberId, request));
		} catch (DataIntegrityViolationException e) {
			return transactionTemplate.execute(status -> upsert(memberId, request));
		}
	}

	private GuardianResponse upsert(Long memberId, GuardianRequest request) {
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
