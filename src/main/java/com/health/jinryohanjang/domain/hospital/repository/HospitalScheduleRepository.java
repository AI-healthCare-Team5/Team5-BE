package com.health.jinryohanjang.domain.hospital.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.hospital.entity.HospitalSchedule;

public interface HospitalScheduleRepository extends JpaRepository<HospitalSchedule, Long> {

	List<HospitalSchedule> findAllByHospitalIdIn(Collection<Long> hospitalIds);
}
