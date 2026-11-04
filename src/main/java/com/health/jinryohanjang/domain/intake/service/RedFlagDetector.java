package com.health.jinryohanjang.domain.intake.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.domain.intake.entity.RedFlagKeyword;
import com.health.jinryohanjang.domain.intake.entity.RedFlagRule;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;
import com.health.jinryohanjang.domain.intake.repository.RedFlagRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 입력에 위험 신호가 있는지 키워드 규칙으로만 판정한다 (AI 판단은 쓰지 않는다).
 * 띄어쓰기를 지우고 비교해서 "숨이 차요"와 "숨이차요"를 같게 본다.
 * 키워드를 바로 부정한 말("안 아파요", "아프지 않아요", "통증은 없어요")은 그 키워드가 없는 것으로 본다.
 * 문장을 나누지는 않으므로 "가슴 통증은 없고 무릎이 아파요"처럼 다른 곳에 나온 말이 걸릴 수는 있다 (놓치는 것보다 낫다고 본다).
 */
@Component
@RequiredArgsConstructor
public class RedFlagDetector {

	private static final Pattern NEGATED_AFTER = Pattern.compile("^[가-힣]?(지는|지도|지가|지|진)않|^[가-힣]{0,2}?(은|는|도|이|가)?없");

	private final RedFlagRuleRepository redFlagRuleRepository;

	@Transactional(readOnly = true)
	public Optional<RedFlagCategory> detect(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalize(text);
		List<RedFlagRule> rules = redFlagRuleRepository.findAllByIsActiveTrueOrderByPriorityAsc();
		return rules.stream()
			.filter(rule -> matches(rule, normalized))
			.map(RedFlagRule::getCategory)
			.findFirst();
	}

	private boolean matches(RedFlagRule rule, String normalized) {
		Map<Integer, List<RedFlagKeyword>> groups = rule.getKeywords().stream()
			.collect(Collectors.groupingBy(RedFlagKeyword::getGroupNo));
		if (groups.isEmpty()) {
			return false;
		}
		return groups.values().stream()
			.allMatch(group -> group.stream()
				.anyMatch(keyword -> containsAffirmed(normalized, normalize(keyword.getKeyword()))));
	}

	/**
	 * 키워드가 부정 없이 한 번이라도 나오는지 본다. 바로 앞에 "안"이 있거나, 바로 뒤에 "~지 않"·"~은 없"이 오면 부정이다.
	 */
	static boolean containsAffirmed(String normalized, String keyword) {
		int index = normalized.indexOf(keyword);
		while (index >= 0) {
			boolean negatedBefore = index > 0 && normalized.charAt(index - 1) == '안' && !keyword.startsWith("안");
			boolean negatedAfter = NEGATED_AFTER.matcher(normalized.substring(index + keyword.length())).find();
			if (!negatedBefore && !negatedAfter) {
				return true;
			}
			index = normalized.indexOf(keyword, index + 1);
		}
		return false;
	}

	static String normalize(String text) {
		return text.replaceAll("\\s+", "");
	}
}
