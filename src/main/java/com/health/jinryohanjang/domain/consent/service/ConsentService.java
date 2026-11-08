package com.health.jinryohanjang.domain.consent.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.health.jinryohanjang.domain.consent.dto.ConsentRequest;
import com.health.jinryohanjang.domain.consent.dto.ConsentResponse;
import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.service.MemberService;

/**
 * 동의를 저장하고 철회한다. 회원·종류별로 withdrawn_at이 없는 행 하나가 현재 유효한 동의다.
 * 기록 저장 동의(RECORD)는 이 도메인의 API로, 보호자 연락처 저장 동의(GUARDIAN)는 보호자 API에서 함께 저장한다.
 */
@Service
public class ConsentService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ConsentRepository consentRepository;
	private final MemberService memberService;
	private final TransactionTemplate transactionTemplate;
	private final String recordConsentVersion;

	public ConsentService(
		ConsentRepository consentRepository,
		MemberService memberService,
		TransactionTemplate transactionTemplate,
		@Value("${app.consent.version.record}") String recordConsentVersion) {
		this.consentRepository = consentRepository;
		this.memberService = memberService;
		this.transactionTemplate = transactionTemplate;
		this.recordConsentVersion = recordConsentVersion;
	}

	/**
	 * 기록 저장 동의(C02). consentType은 RECORD만, policyVersion은 서버 설정의 현재 버전만 받는다.
	 * 같은 회원이 동시에 두 번 보내면 늦은 쪽이 부분 UNIQUE에 걸리므로, 한 번 더 시도해 먼저 저장된 동의를 돌려준다(CONSENT_200).
	 */
	public AgreeResult agreeRecord(Long memberId, ConsentRequest request) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (request.consentType() != ConsentType.RECORD) {
			errors.add(new FieldErrorDetail("consentType", "기록 저장 동의만 할 수 있어요"));
		}
		if (!recordConsentVersion.equals(request.policyVersion())) {
			errors.add(new FieldErrorDetail("policyVersion", "현재 동의 문구 버전이 아니에요"));
		}
		if (!errors.isEmpty()) {
			throw new GeneralException(ErrorStatus.CONSENT_INVALID_INPUT, Map.of("errors", errors));
		}
		Member member = memberService.getActiveMember(memberId);
		try {
			return transactionTemplate.execute(status -> agree(member, ConsentType.RECORD, request.policyVersion()));
		} catch (DataIntegrityViolationException e) {
			return transactionTemplate.execute(status -> agree(member, ConsentType.RECORD, request.policyVersion()));
		}
	}

	/**
	 * 같은 버전에 이미 동의해 있으면 그 동의를 그대로 돌려준다. 예전 버전 동의는 철회하고 새로 저장한다.
	 * 버전 확인은 부르는 쪽에서 한다 (실패 code의 도메인이 API마다 달라서).
	 */
	@Transactional
	public AgreeResult agree(Member member, ConsentType consentType, String policyVersion) {
		Optional<Consent> current = consentRepository.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(
			member.getId(), consentType);
		if (current.isPresent() && current.get().getPolicyVersion().equals(policyVersion)) {
			return new AgreeResult(current.get(), false);
		}
		LocalDateTime now = now();
		if (current.isPresent()) {
			current.get().withdraw(now);
			// 부분 UNIQUE 인덱스에 걸리지 않도록 철회를 새 행 INSERT보다 먼저 반영한다.
			consentRepository.flush();
		}
		Consent consent = consentRepository.save(Consent.agree(member, consentType, policyVersion, now));
		return new AgreeResult(consent, true);
	}

	@Transactional
	public void withdraw(Long memberId, ConsentType consentType) {
		consentRepository.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, consentType)
			.ifPresent(consent -> consent.withdraw(now()));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(SEOUL).withNano(0);
	}

	public record AgreeResult(Consent consent, boolean created) {

		public ConsentResponse toResponse() {
			return new ConsentResponse(consent.getId(), consent.getConsentType(), consent.getPolicyVersion(),
				consent.getAgreedAt().atZone(SEOUL).toOffsetDateTime());
		}
	}
}
