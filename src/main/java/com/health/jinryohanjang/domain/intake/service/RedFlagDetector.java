package com.health.jinryohanjang.domain.intake.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 */
@Component
@RequiredArgsConstructor
public class RedFlagDetector {

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
			.allMatch(group -> group.stream().anyMatch(keyword -> normalized.contains(normalize(keyword.getKeyword()))));
	}

	private String normalize(String text) {
		return text.replaceAll("\\s+", "");
	}
}
