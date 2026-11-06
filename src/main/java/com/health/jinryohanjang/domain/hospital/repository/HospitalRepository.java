package com.health.jinryohanjang.domain.hospital.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.hospital.entity.Hospital;

/**
 * 거리는 하버사인 공식으로 km 단위로 계산한다.
 * departmentCode가 null이면 진료과 조건 없이, emergencyOnly가 false면 응급실 조건 없이 찾는다.
 */
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

	String DISTANCE = """
		6371 * acos(least(1.0,
			cos(radians(:latitude)) * cos(radians(h.latitude)) * cos(radians(h.longitude) - radians(:longitude))
			+ sin(radians(:latitude)) * sin(radians(h.latitude))))
		""";

	String NEARBY_CONDITION = """
		FROM hospital h
		WHERE (CAST(:departmentCode AS varchar) IS NULL OR EXISTS (
				SELECT 1 FROM hospital_department hd
				WHERE hd.hospital_id = h.hospital_id AND hd.department_code = CAST(:departmentCode AS varchar)))
			AND (:emergencyOnly = false OR h.has_emergency_room = true)
			AND h.latitude BETWEEN :latitude - :radius / 111.0 AND :latitude + :radius / 111.0
		""";

	@Query(value = "SELECT id, distance FROM (SELECT h.hospital_id AS id, " + DISTANCE + " AS distance "
		+ NEARBY_CONDITION + ") t WHERE distance <= :radius ORDER BY distance LIMIT :limit", nativeQuery = true)
	List<HospitalDistance> findNearby(
		@Param("latitude") double latitude,
		@Param("longitude") double longitude,
		@Param("radius") double radius,
		@Param("departmentCode") String departmentCode,
		@Param("emergencyOnly") boolean emergencyOnly,
		@Param("limit") int limit);

	@Query(value = "SELECT count(*) FROM (SELECT " + DISTANCE + " AS distance "
		+ NEARBY_CONDITION + ") t WHERE distance <= :radius", nativeQuery = true)
	long countNearby(
		@Param("latitude") double latitude,
		@Param("longitude") double longitude,
		@Param("radius") double radius,
		@Param("departmentCode") String departmentCode,
		@Param("emergencyOnly") boolean emergencyOnly);

	@Query(value = "SELECT h.hospital_id AS id, " + DISTANCE + " AS distance FROM hospital h "
		+ "WHERE h.hospital_type IN (:hospitalTypes) ORDER BY distance LIMIT 1", nativeQuery = true)
	List<HospitalDistance> findNearestByTypes(
		@Param("latitude") double latitude,
		@Param("longitude") double longitude,
		@Param("hospitalTypes") Collection<String> hospitalTypes);

	@EntityGraph(attributePaths = "departments")
	List<Hospital> findAllByIdIn(Collection<Long> ids);

	Optional<Hospital> findByYkiho(String ykiho);

	List<Hospital> findAllBySyncedAtBefore(LocalDateTime syncedAt);

	@Query("SELECT max(h.syncedAt) FROM Hospital h")
	LocalDateTime findLastSyncedAt();

	interface HospitalDistance {

		Long getId();

		Double getDistance();
	}
}
