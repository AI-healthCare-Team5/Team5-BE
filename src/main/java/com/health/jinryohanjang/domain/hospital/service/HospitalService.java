package com.health.jinryohanjang.domain.hospital.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.exception.GeneralExceptionAdvice.FieldErrorDetail;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.department.repository.DepartmentRepository;
import com.health.jinryohanjang.domain.hospital.dto.HealthCenterResponse;
import com.health.jinryohanjang.domain.hospital.dto.HospitalResponse;
import com.health.jinryohanjang.domain.hospital.dto.HospitalSearchResponse;
import com.health.jinryohanjang.domain.hospital.dto.RegionListResponse;
import com.health.jinryohanjang.domain.hospital.entity.Hospital;
import com.health.jinryohanjang.domain.hospital.entity.HospitalSchedule;
import com.health.jinryohanjang.domain.hospital.entity.Region;
import com.health.jinryohanjang.domain.hospital.enums.HospitalType;
import com.health.jinryohanjang.domain.hospital.repository.HospitalRepository;
import com.health.jinryohanjang.domain.hospital.repository.HospitalRepository.HospitalDistance;
import com.health.jinryohanjang.domain.hospital.repository.HospitalScheduleRepository;
import com.health.jinryohanjang.domain.hospital.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 동기화해 둔 병원 데이터에서 병원·응급실·보건소를 찾는다. 요청 중에 공공 API를 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

	private final HospitalRepository hospitalRepository;
	private final HospitalScheduleRepository hospitalScheduleRepository;
	private final RegionRepository regionRepository;
	private final DepartmentRepository departmentRepository;

	public HospitalSearchResponse search(String departmentCode, Double latitude, Double longitude, String regionCode,
		int radius, int limit) {
		if (StringUtils.hasText(departmentCode) && !departmentRepository.existsById(departmentCode)) {
			throw invalid("departmentCode", "없는 진료과 코드예요");
		}
		String department = StringUtils.hasText(departmentCode) ? departmentCode : null;
		return searchNearby(resolveCenter(latitude, longitude, regionCode), radius, limit, department, false);
	}

	public HospitalSearchResponse searchEmergencyRooms(Double latitude, Double longitude, String regionCode,
		int radius, int limit) {
		return searchNearby(resolveCenter(latitude, longitude, regionCode), radius, limit, null, true);
	}

	public HealthCenterResponse findNearestHealthCenter(Double latitude, Double longitude, String regionCode) {
		Center center = resolveCenter(latitude, longitude, regionCode);
		List<HospitalDistance> nearest = hospitalRepository.findNearestByTypes(
			center.latitude(), center.longitude(), HospitalType.HEALTH_CENTER_CODES);
		HospitalResponse hospital = toResponses(nearest, false).stream().findFirst().orElse(null);
		return new HealthCenterResponse(lastSyncedAt(), hospital);
	}

	public RegionListResponse getRegions() {
		List<RegionListResponse.Region> regions = regionRepository.findAllByOrderByNameAsc().stream()
			.map(region -> new RegionListResponse.Region(region.getCode(), region.getName(),
				region.getCenterLatitude().doubleValue(), region.getCenterLongitude().doubleValue()))
			.toList();
		return new RegionListResponse(regions);
	}

	private HospitalSearchResponse searchNearby(Center center, int radius, int limit, String departmentCode,
		boolean emergencyOnly) {
		List<HospitalDistance> nearby = hospitalRepository.findNearby(
			center.latitude(), center.longitude(), radius, departmentCode, emergencyOnly, limit);
		long totalCount = hospitalRepository.countNearby(
			center.latitude(), center.longitude(), radius, departmentCode, emergencyOnly);
		return new HospitalSearchResponse(radius, lastSyncedAt(), totalCount, toResponses(nearby, emergencyOnly));
	}

	/**
	 * 위치(위도·경도)가 있으면 그 위치를, 없으면 지역 중심 좌표를 쓴다. 둘 다 없으면 field "location"으로 400을 준다.
	 */
	private Center resolveCenter(Double latitude, Double longitude, String regionCode) {
		if (latitude != null && longitude != null) {
			return new Center(latitude, longitude);
		}
		if (StringUtils.hasText(regionCode)) {
			Region region = regionRepository.findById(regionCode)
				.orElseThrow(() -> invalid("regionCode", "없는 지역 코드예요"));
			return new Center(region.getCenterLatitude().doubleValue(), region.getCenterLongitude().doubleValue());
		}
		throw invalid("location", "위치나 지역을 선택해 주세요");
	}

	private List<HospitalResponse> toResponses(List<HospitalDistance> distances, boolean emergencyRoom) {
		if (distances.isEmpty()) {
			return List.of();
		}
		List<Long> ids = distances.stream().map(HospitalDistance::getId).toList();
		Map<Long, Hospital> hospitals = hospitalRepository.findAllByIdIn(ids).stream()
			.collect(Collectors.toMap(Hospital::getId, Function.identity()));
		Map<Long, List<HospitalSchedule>> schedules = hospitalScheduleRepository.findAllByHospitalIdIn(ids).stream()
			.collect(Collectors.groupingBy(schedule -> schedule.getHospital().getId()));
		ZonedDateTime now = ZonedDateTime.now(SEOUL);
		return distances.stream()
			.map(distance -> toResponse(hospitals.get(distance.getId()), distance.getDistance(),
				schedules.getOrDefault(distance.getId(), List.of()), emergencyRoom, now))
			.toList();
	}

	private HospitalResponse toResponse(Hospital hospital, double distanceKm, List<HospitalSchedule> schedules,
		boolean emergencyRoom, ZonedDateTime now) {
		List<HospitalResponse.Department> departments = hospital.getDepartments().stream()
			.map(department -> new HospitalResponse.Department(department.getCode(), department.getName()))
			.toList();
		HospitalResponse.Today today = emergencyRoom ? allDay() : today(schedules, now);
		return new HospitalResponse(
			hospital.getId(),
			hospital.getName(),
			hospital.getHospitalType(),
			HospitalType.labelOf(hospital.getHospitalType()),
			departments,
			BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP).doubleValue(),
			hospital.getLatitude().doubleValue(),
			hospital.getLongitude().doubleValue(),
			hospital.getPhoneNumber(),
			hospital.getAddress(),
			hospital.isHasEmergencyRoom(),
			today);
	}

	/**
	 * 오늘 요일 진료 시간이 없으면 휴진이고, 진료 시간 정보가 아예 없으면 null이다.
	 * 공휴일(8)은 공휴일 달력이 생기면 반영한다.
	 */
	private HospitalResponse.Today today(List<HospitalSchedule> schedules, ZonedDateTime now) {
		if (schedules.isEmpty()) {
			return null;
		}
		HospitalSchedule todaySchedule = scheduleOf(schedules, now.getDayOfWeek().getValue());
		if (todaySchedule != null && todaySchedule.is24Hours()) {
			return allDay();
		}
		LocalTime time = now.toLocalTime();
		boolean isOpen = todaySchedule != null
			&& !time.isBefore(todaySchedule.getOpenTime()) && time.isBefore(todaySchedule.getCloseTime());
		OffsetDateTime nextOpenAt = isOpen ? null : nextOpenAt(schedules, todaySchedule, now);
		return new HospitalResponse.Today(
			isOpen,
			false,
			todaySchedule == null ? null : todaySchedule.getOpenTime().format(TIME_FORMAT),
			todaySchedule == null ? null : todaySchedule.getCloseTime().format(TIME_FORMAT),
			nextOpenAt);
	}

	private OffsetDateTime nextOpenAt(List<HospitalSchedule> schedules, HospitalSchedule todaySchedule,
		ZonedDateTime now) {
		if (todaySchedule != null && now.toLocalTime().isBefore(todaySchedule.getOpenTime())) {
			return now.with(todaySchedule.getOpenTime()).withSecond(0).withNano(0).toOffsetDateTime();
		}
		for (int plusDays = 1; plusDays <= 7; plusDays++) {
			ZonedDateTime day = now.plusDays(plusDays);
			HospitalSchedule schedule = scheduleOf(schedules, day.getDayOfWeek().getValue());
			if (schedule != null) {
				return day.with(schedule.getOpenTime()).withSecond(0).withNano(0).toOffsetDateTime();
			}
		}
		return null;
	}

	private HospitalSchedule scheduleOf(List<HospitalSchedule> schedules, int dayOfWeek) {
		return schedules.stream()
			.filter(schedule -> schedule.getDayOfWeek() == dayOfWeek)
			.min(Comparator.comparing(HospitalSchedule::getOpenTime))
			.orElse(null);
	}

	private HospitalResponse.Today allDay() {
		return new HospitalResponse.Today(true, true, LocalTime.MIDNIGHT.format(TIME_FORMAT),
			END_OF_DAY.format(TIME_FORMAT), null);
	}

	private OffsetDateTime lastSyncedAt() {
		LocalDateTime syncedAt = hospitalRepository.findLastSyncedAt();
		return syncedAt == null ? null : syncedAt.atZone(SEOUL).toOffsetDateTime();
	}

	private GeneralException invalid(String field, String reason) {
		return new GeneralException(ErrorStatus.HOSPITAL_INVALID_INPUT,
			Map.of("errors", List.of(new FieldErrorDetail(field, reason))));
	}

	private record Center(double latitude, double longitude) {
	}
}
