package com.health.jinryohanjang.domain.hospital.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.hospital.entity.Region;

public interface RegionRepository extends JpaRepository<Region, String> {

	List<Region> findAllByOrderByNameAsc();
}
