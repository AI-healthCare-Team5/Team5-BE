package com.health.jinryohanjang.domain.intake.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
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
 * 입력을 쉼표·마침표와 연결 어미("~고", "~는데", "~지만") 뒤에서 마디로 나누고, 증상을 부정하거나 괜찮다고 한 마디는
 * 다음 마디가 새 주어("무릎이 ~")로 시작할 때만 판정에서 뺀다.
 * 그래서 "가슴은 안 아파요, 무릎이 아파요"는 응급이 아니고, "가슴이 아프지는 않고 조여요"와 부정이 없는 "가슴하고 팔이 아파요"는 응급이다.
 */
@Component
@RequiredArgsConstructor
public class RedFlagDetector {

	private static final Pattern NEGATED_AFTER = Pattern.compile("^[가-힣]?(지는|지도|지가|지|진)않|^[가-힣]{0,2}?(은|는|도|이|가)?없");
	private static final Pattern CLAUSE_BREAK = Pattern.compile("[,.!?;~\\n]+|(?<=(고|는데|은데|던데|지만|면서|으나))\\s+");
	private static final Pattern RELIEF = Pattern.compile("괜찮|멀쩡|문제없");
	private static final Pattern SUBJECT_START = Pattern.compile("^[가-힣]+(이|가|은|는|도|에|에서|께서)$");
	private static final String CLAUSE_JOIN = "|";

	private final RedFlagRuleRepository redFlagRuleRepository;

	@Transactional(readOnly = true)
	public Optional<RedFlagCategory> detect(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		List<String> clauses = Arrays.stream(CLAUSE_BREAK.split(text.trim()))
			.map(String::trim)
			.filter(clause -> !clause.isEmpty())
			.toList();
		List<RedFlagRule> rules = redFlagRuleRepository.findAllByIsActiveTrueOrderByPriorityAsc();
		return rules.stream()
			.filter(rule -> matches(rule, clauses))
			.map(RedFlagRule::getCategory)
			.findFirst();
	}

	/**
	 * 증상을 부정하거나 괜찮다고 한 마디를 빼고, 남은 마디를 이어서 규칙의 모든 group에 부정되지 않은 키워드가 있는지 본다.
	 */
	private boolean matches(RedFlagRule rule, List<String> clauses) {
		TreeMap<Integer, List<String>> groups = rule.getKeywords().stream()
			.collect(Collectors.groupingBy(RedFlagKeyword::getGroupNo, TreeMap::new,
				Collectors.mapping(keyword -> normalize(keyword.getKeyword()), Collectors.toList())));
		if (groups.isEmpty()) {
			return false;
		}
		List<String> kept = new ArrayList<>();
		for (int i = 0; i < clauses.size(); i++) {
			String clause = normalize(clauses.get(i));
			boolean nextIsNewSubject = i + 1 == clauses.size() || startsWithSubject(clauses.get(i + 1));
			if (!(nextIsNewSubject && denies(clause, groups))) {
				kept.add(clause);
			}
		}
		String joined = String.join(CLAUSE_JOIN, kept);
		return groups.values().stream()
			.allMatch(group -> group.stream().anyMatch(keyword -> containsAffirmed(joined, keyword)));
	}

	/**
	 * 마디의 첫 단어가 "무릎이", "숨은"처럼 주어로 시작하는지 본다. 주어 없이 "조여요"로 시작하면 앞 마디의 이야기가 이어진다.
	 */
	private boolean startsWithSubject(String clause) {
		String first = clause.split("\\s+", 2)[0];
		return SUBJECT_START.matcher(first).matches();
	}

	/**
	 * 마디가 이 규칙의 증상을 부정하는지 본다.
	 * - 어떤 group의 키워드가 부정됐고 같은 group의 다른 키워드는 그대로면 ("가슴은 안 아파요") 부정이다.
	 *   같은 group에 부정되지 않은 키워드가 있으면 ("아프지는 않고 조여요") 부정이 아니다.
	 * - "괜찮·멀쩡"이 있고 증상을 나타내는 마지막 group의 키워드가 없으면 ("가슴은 괜찮은데") 부정이다.
	 */
	private boolean denies(String clause, TreeMap<Integer, List<String>> groups) {
		for (List<String> group : groups.values()) {
			boolean negated = group.stream().anyMatch(keyword -> containsNegated(clause, keyword));
			boolean affirmed = group.stream().anyMatch(keyword -> containsAffirmed(clause, keyword));
			if (negated && !affirmed) {
				return true;
			}
		}
		if (RELIEF.matcher(clause).find()) {
			List<String> symptoms = groups.lastEntry().getValue();
			return symptoms.stream().noneMatch(keyword -> containsAffirmed(clause, keyword));
		}
		return false;
	}

	/**
	 * 키워드가 부정 없이 한 번이라도 나오는지 본다. 바로 앞에 "안"이 있거나, 바로 뒤에 "~지 않"·"~은 없"이 오면 부정이다.
	 */
	static boolean containsAffirmed(String normalized, String keyword) {
		int index = normalized.indexOf(keyword);
		while (index >= 0) {
			if (!isNegated(normalized, keyword, index)) {
				return true;
			}
			index = normalized.indexOf(keyword, index + 1);
		}
		return false;
	}

	static boolean containsNegated(String normalized, String keyword) {
		int index = normalized.indexOf(keyword);
		while (index >= 0) {
			if (isNegated(normalized, keyword, index)) {
				return true;
			}
			index = normalized.indexOf(keyword, index + 1);
		}
		return false;
	}

	private static boolean isNegated(String normalized, String keyword, int index) {
		boolean negatedBefore = index > 0 && normalized.charAt(index - 1) == '안' && !keyword.startsWith("안");
		return negatedBefore || NEGATED_AFTER.matcher(normalized.substring(index + keyword.length())).find();
	}

	static String normalize(String text) {
		return text.replaceAll("\\s+", "");
	}
}
