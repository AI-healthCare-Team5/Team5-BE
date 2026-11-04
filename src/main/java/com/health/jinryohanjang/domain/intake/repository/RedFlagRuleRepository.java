package com.health.jinryohanjang.domain.intake.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.intake.entity.RedFlagRule;

public interface RedFlagRuleRepository extends JpaRepository<RedFlagRule, Long> {

	@EntityGraph(attributePaths = "keywords")
	List<RedFlagRule> findAllByIsActiveTrueOrderByPriorityAsc();
}
