package com.health.jinryohanjang.domain.hospital.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.hospital.entity.HospitalSchedule;

public interface HospitalScheduleRepository extends JpaRepository<HospitalSchedule, Long> {

	List<HospitalSchedule> findAllByHospitalIdIn(Collection<Long> hospitalIds);

	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("DELETE FROM HospitalSchedule s WHERE s.hospital.id = :hospitalId")
	void deleteAllByHospitalId(@Param("hospitalId") Long hospitalId);
}
