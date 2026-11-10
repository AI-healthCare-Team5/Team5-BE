package com.health.jinryohanjang.domain.hospital.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import com.health.jinryohanjang.domain.department.entity.Department;
import com.health.jinryohanjang.domain.department.repository.DepartmentRepository;
import com.health.jinryohanjang.domain.hospital.client.PublicDataClient;
import com.health.jinryohanjang.domain.hospital.entity.Hospital;
import com.health.jinryohanjang.domain.hospital.entity.HospitalSchedule;
import com.health.jinryohanjang.domain.hospital.entity.Region;
import com.health.jinryohanjang.domain.hospital.repository.HospitalRepository;
import com.health.jinryohanjang.domain.hospital.repository.HospitalScheduleRepository;
import com.health.jinryohanjang.domain.hospital.repository.RegionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 공공데이터로 강원 병원·진료과·진료 시간·응급실 여부를 동기화한다.
 * app.hospital-sync.regions에 적은 시군 병원만 저장하고, 비어 있으면 강원 전체를 저장한다.
 * 병원 목록을 끝까지 받지 못하면 중단하고 이전 데이터를 그대로 둔다.
 * 끝까지 성공했을 때만 목록에서 사라진 병원(대상에서 빠진 시군 포함)을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalSyncService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int PAGE_SIZE = 100;
	private static final int PAGE_MAX_ATTEMPTS = 3;
	private static final int DETAIL_MAX_CONSECUTIVE_FAILURES = 10;
	private static final double EMERGENCY_MATCH_KM = 0.5;
	private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);
	private static final String[] DAY_FIELDS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

	private final PublicDataClient publicDataClient;
	private final HospitalRepository hospitalRepository;
	private final HospitalScheduleRepository hospitalScheduleRepository;
	private final RegionRepository regionRepository;
	private final DepartmentRepository departmentRepository;
	private final TransactionTemplate transactionTemplate;
	private final AtomicBoolean running = new AtomicBoolean(false);

	@Value("${app.hospital-sync.regions:}")
	private List<String> targetRegionNames;

	public boolean hasNoHospitals() {
		return hospitalRepository.count() == 0;
	}

	public void sync() {
		if (!publicDataClient.isConfigured()) {
			log.warn("PUBLIC_DATA_API_KEY가 없어서 병원 동기화를 건너뜁니다.");
			return;
		}
		if (!running.compareAndSet(false, true)) {
			log.info("병원 동기화가 이미 실행 중입니다.");
			return;
		}
		try {
			syncAll();
		} catch (RuntimeException e) {
			log.error("병원 동기화 실패, 이전 데이터를 그대로 씁니다.", e);
		} finally {
			running.set(false);
		}
	}

	private void syncAll() {
		LocalDateTime startedAt = LocalDateTime.now(SEOUL).withNano(0);
		Map<String, Region> regions = regionRepository.findAll().stream()
			.collect(Collectors.toMap(Region::getName, Function.identity()));
		Map<String, Department> departments = departmentRepository.findAll().stream()
			.collect(Collectors.toMap(Department::getCode, Function.identity()));
		List<String> unknownRegions = unknownTargetRegions(regions);
		if (!unknownRegions.isEmpty()) {
			// 그대로 진행하면 저장 0건으로 끝나고 기존 병원이 모두 지워진다.
			throw new IllegalStateException("app.hospital-sync.regions에 없는 시군 이름: " + unknownRegions);
		}
		log.info("병원 동기화 시작, 대상 시군: {}", isAllRegions() ? "강원 전체" : targetRegionNames);

		SyncCount count = new SyncCount();
		int pageNo = 1;
		int totalCount;
		do {
			PublicDataClient.Page page = fetchHospitalPage(pageNo);
			totalCount = page.totalCount();
			for (JsonNode item : page.items()) {
				syncHospital(item, regions, departments, startedAt, count);
			}
			pageNo++;
		} while ((long)(pageNo - 1) * PAGE_SIZE < totalCount);

		syncEmergencyRooms();
		if (count.saved == 0) {
			log.warn("저장한 병원이 없어서 기존 병원을 지우지 않습니다. 대상 시군 설정과 공공 API 응답을 확인하세요.");
			return;
		}
		int deleted = deleteStaleHospitals(startedAt);
		log.info("병원 동기화 완료: 저장 {}, 건너뜀 {}, 상세정보 실패 {}, 삭제 {}",
			count.saved, count.skipped, count.detailFailed, deleted);
	}

	/**
	 * 목록 한 페이지가 실패하면 동기화 전체가 멈추므로, 그 페이지만 몇 번 다시 받는다.
	 */
	private PublicDataClient.Page fetchHospitalPage(int pageNo) {
		for (int attempt = 1; ; attempt++) {
			try {
				return publicDataClient.getHospitals(pageNo, PAGE_SIZE);
			} catch (RestClientException e) {
				if (attempt >= PAGE_MAX_ATTEMPTS) {
					throw e;
				}
				log.warn("병원 목록 {}페이지 받기 실패({}/{}), 다시 시도합니다: {}",
					pageNo, attempt, PAGE_MAX_ATTEMPTS, e.getMessage());
			}
		}
	}

	private void syncHospital(JsonNode item, Map<String, Region> regions, Map<String, Department> departments,
		LocalDateTime syncedAt, SyncCount count) {
		String regionName = text(item, "sgguCdNm");
		if (!isTargetRegion(regionName)) {
			return;
		}
		String ykiho = text(item, "ykiho");
		Region region = regions.get(regionName);
		BigDecimal longitude = decimal(item, "XPos");
		BigDecimal latitude = decimal(item, "YPos");
		String name = text(item, "yadmNm");
		if (ykiho == null || region == null || latitude == null || longitude == null || name == null) {
			count.skipped++;
			return;
		}
		Set<Department> hospitalDepartments = fetchDepartments(ykiho, departments, count);
		List<DaySchedule> schedules = fetchSchedules(ykiho, count);

		transactionTemplate.executeWithoutResult(status -> {
			Hospital hospital = hospitalRepository.findByYkiho(ykiho).orElseGet(() -> Hospital.create(ykiho));
			hospital.updateBasicInfo(region, truncate(name, 100), truncate(text(item, "telno"), 20),
				truncate(text(item, "addr"), 255), latitude, longitude, hospitalType(item), syncedAt);
			if (hospitalDepartments != null) {
				hospital.replaceDepartments(hospitalDepartments);
			}
			Hospital saved = hospitalRepository.save(hospital);
			if (schedules != null) {
				hospitalScheduleRepository.deleteAllByHospitalId(saved.getId());
				hospitalScheduleRepository.saveAll(schedules.stream()
					.map(day -> HospitalSchedule.create(saved, day.dayOfWeek(), day.openTime(), day.closeTime()))
					.toList());
			}
		});
		count.saved++;
	}

	/**
	 * 실패하면 null을 돌려줘서 이전 진료과를 그대로 둔다.
	 */
	private Set<Department> fetchDepartments(String ykiho, Map<String, Department> departments, SyncCount count) {
		try {
			Set<Department> result = new LinkedHashSet<>();
			for (JsonNode item : publicDataClient.getDepartments(ykiho)) {
				Department department = departments.get(text(item, "dgsbjtCd"));
				if (department != null) {
					result.add(department);
				}
			}
			count.detailSucceeded();
			return result;
		} catch (RuntimeException e) {
			count.detailFailed(e);
			return null;
		}
	}

	/**
	 * 요일별 진료 시간(trmtMonStart 등, "0830" 형식)을 읽는다. 실패하면 null을 돌려줘서 이전 진료 시간을 그대로 둔다.
	 */
	private List<DaySchedule> fetchSchedules(String ykiho, SyncCount count) {
		JsonNode detail;
		try {
			detail = publicDataClient.getDetail(ykiho);
			count.detailSucceeded();
		} catch (RuntimeException e) {
			count.detailFailed(e);
			return null;
		}
		List<DaySchedule> schedules = new ArrayList<>();
		if (detail == null) {
			return schedules;
		}
		for (int i = 0; i < DAY_FIELDS.length; i++) {
			LocalTime open = time(text(detail, "trmt" + DAY_FIELDS[i] + "Start"));
			LocalTime close = time(text(detail, "trmt" + DAY_FIELDS[i] + "End"));
			if (open != null && close != null && open.isBefore(close)) {
				schedules.add(new DaySchedule(i + 1, open, close));
			}
		}
		return schedules;
	}

	/**
	 * 응급의료기관 목록의 좌표와 가장 가까운 병원(0.5km 안)을 응급실 있음으로 표시한다.
	 * 두 기관의 좌표가 다른 경우가 있어서, 좌표로 못 찾으면 이름이 같은 병원을 쓴다.
	 */
	private void syncEmergencyRooms() {
		List<JsonNode> gangwonEmergencyRooms;
		try {
			gangwonEmergencyRooms = publicDataClient.getEmergencyRooms();
		} catch (RuntimeException e) {
			log.warn("응급의료기관 조회 실패, 응급실 표시는 이전 값을 씁니다.", e);
			return;
		}
		if (gangwonEmergencyRooms.isEmpty()) {
			// 강원에 응급의료기관이 0곳일 리 없으니 포털 이상으로 보고 기존 표시를 지우지 않는다.
			log.warn("응급의료기관이 0건으로 와서 응급실 표시는 이전 값을 씁니다.");
			return;
		}
		List<JsonNode> emergencyRooms = gangwonEmergencyRooms.stream()
			.filter(this::isTargetEmergencyRoom)
			.toList();
		transactionTemplate.executeWithoutResult(status -> {
			List<Hospital> hospitals = hospitalRepository.findAll();
			hospitals.stream().filter(Hospital::isHasEmergencyRoom).forEach(Hospital::clearEmergencyRoom);
			Map<String, Hospital> hospitalsByName = hospitals.stream()
				.collect(Collectors.toMap(Hospital::getName, Function.identity(), (first, second) -> first));
			int matched = 0;
			for (JsonNode emergencyRoom : emergencyRooms) {
				BigDecimal latitude = decimal(emergencyRoom, "wgs84Lat");
				BigDecimal longitude = decimal(emergencyRoom, "wgs84Lon");
				if (latitude == null || longitude == null) {
					continue;
				}
				Hospital nearest = null;
				double nearestKm = EMERGENCY_MATCH_KM;
				for (Hospital hospital : hospitals) {
					double km = distanceKm(latitude.doubleValue(), longitude.doubleValue(),
						hospital.getLatitude().doubleValue(), hospital.getLongitude().doubleValue());
					if (km <= nearestKm) {
						nearest = hospital;
						nearestKm = km;
					}
				}
				if (nearest == null) {
					nearest = hospitalsByName.get(text(emergencyRoom, "dutyName"));
				}
				if (nearest != null) {
					nearest.markEmergencyRoom(text(emergencyRoom, "hpid"));
					matched++;
				} else {
					log.warn("응급의료기관과 맞는 병원을 찾지 못했습니다: {}", text(emergencyRoom, "dutyName"));
				}
			}
			log.info("응급실 표시: {}곳 중 {}곳", emergencyRooms.size(), matched);
		});
	}

	private int deleteStaleHospitals(LocalDateTime startedAt) {
		Integer deleted = transactionTemplate.execute(status -> {
			List<Hospital> stale = hospitalRepository.findAllBySyncedAtBefore(startedAt);
			for (Hospital hospital : stale) {
				hospitalScheduleRepository.deleteAllByHospitalId(hospital.getId());
				hospitalRepository.delete(hospital);
			}
			return stale.size();
		});
		return deleted == null ? 0 : deleted;
	}

	private List<String> unknownTargetRegions(Map<String, Region> regions) {
		if (isAllRegions()) {
			return List.of();
		}
		return targetRegionNames.stream()
			.map(String::trim)
			.filter(StringUtils::hasText)
			.filter(name -> !regions.containsKey(name))
			.toList();
	}

	private boolean isAllRegions() {
		return targetRegionNames == null || targetRegionNames.stream().noneMatch(StringUtils::hasText);
	}

	private boolean isTargetRegion(String regionName) {
		return isAllRegions() || (regionName != null && targetRegionNames.stream()
			.map(String::trim)
			.anyMatch(regionName::equals));
	}

	/**
	 * 응급의료기관은 시군 필드가 없어서 주소(예: "강원특별자치도 원주시 ...")의 시군 이름으로 고른다.
	 */
	private boolean isTargetEmergencyRoom(JsonNode emergencyRoom) {
		if (isAllRegions()) {
			return true;
		}
		String address = text(emergencyRoom, "dutyAddr");
		return address != null && targetRegionNames.stream()
			.map(String::trim)
			.filter(StringUtils::hasText)
			.anyMatch(address::contains);
	}

	private String hospitalType(JsonNode item) {
		String code = text(item, "clCd");
		if (code == null) {
			return "00";
		}
		return code.length() == 1 ? "0" + code : code;
	}

	/**
	 * "0830", "1800", "2400" 같은 값을 시각으로 바꾼다. 2400은 23:59로 저장한다.
	 */
	private LocalTime time(String value) {
		if (value == null || !value.matches("\\d{3,4}")) {
			return null;
		}
		String padded = value.length() == 3 ? "0" + value : value;
		int hour = Integer.parseInt(padded.substring(0, 2));
		int minute = Integer.parseInt(padded.substring(2));
		if (hour == 24 && minute == 0) {
			return END_OF_DAY;
		}
		if (hour > 23 || minute > 59) {
			return null;
		}
		return LocalTime.of(hour, minute);
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString().trim();
		return StringUtils.hasText(text) ? text : null;
	}

	private BigDecimal decimal(JsonNode node, String field) {
		String text = text(node, field);
		if (text == null) {
			return null;
		}
		try {
			return new BigDecimal(text).setScale(7, RoundingMode.HALF_UP);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String truncate(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	private record DaySchedule(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
	}

	private static class SyncCount {
		private int saved;
		private int skipped;
		private int detailFailed;
		private int consecutiveDetailFailures;

		private void detailSucceeded() {
			consecutiveDetailFailures = 0;
		}

		/**
		 * 상세 호출이 연달아 실패하면 포털 장애로 보고 동기화를 멈춘다. 멈추면 기존 병원은 지우지 않는다.
		 */
		private void detailFailed(RuntimeException e) {
			detailFailed++;
			consecutiveDetailFailures++;
			if (consecutiveDetailFailures >= DETAIL_MAX_CONSECUTIVE_FAILURES) {
				throw new IllegalStateException("상세 API가 " + consecutiveDetailFailures + "번 연속 실패", e);
			}
		}
	}
}
