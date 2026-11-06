package com.health.jinryohanjang.domain.summary.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;
import com.health.jinryohanjang.domain.summary.enums.StepStatus;
import com.health.jinryohanjang.domain.summary.enums.SummaryStatus;
import com.health.jinryohanjang.domain.summary.enums.SummaryStep;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis에 JSON으로 저장하는 보관 전 요약지다. 주인은 문진과 같은 guestKey로 판정한다.
 * 다시 만들 때마다 generation을 올려서, 이전 생성 작업의 늦은 결과가 덮어쓰지 않게 한다.
 * 못 물은 항목은 null("확인 못 했어요"), 물었는데 없다고 하면 [] ("없음")이다.
 */
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
	getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Summary {

	private String summaryId;
	private String intakeId;
	private String guestKey;
	private SummaryStatus status;
	private int generation;
	private OffsetDateTime deadline;
	private List<StepState> steps = new ArrayList<>();
	private OffsetDateTime createdAt;

	private AgeGroup ageGroup;
	private Gender gender;
	private InputMode inputMode;
	private String mainSymptom;
	private BodyPart bodyPart;
	private Onset onset;
	private String onsetDetail;
	private String extraNote;
	private List<HealthInfo> medications;
	private List<HealthInfo> conditions;
	private List<HealthInfo> allergies;
	private List<Item> items;
	private Department department;
	private boolean confirmed;

	public static Summary start(String summaryId, String intakeId, String guestKey, OffsetDateTime now,
		OffsetDateTime deadline) {
		Summary summary = new Summary();
		summary.summaryId = summaryId;
		summary.intakeId = intakeId;
		summary.guestKey = guestKey;
		summary.createdAt = now;
		summary.restart(now, deadline);
		return summary;
	}

	/**
	 * 처음 상태(GENERATING, 모든 단계 WAITING)로 되돌리고 generation을 올린다.
	 */
	public void restart(OffsetDateTime now, OffsetDateTime deadline) {
		this.status = SummaryStatus.GENERATING;
		this.generation++;
		this.deadline = deadline;
		this.createdAt = now;
		this.steps = new ArrayList<>();
		for (SummaryStep step : SummaryStep.values()) {
			steps.add(new StepState(step, StepStatus.WAITING));
		}
		this.confirmed = false;
	}

	public boolean isOwnedBy(String guestKey) {
		return this.guestKey != null && this.guestKey.equals(guestKey);
	}

	public boolean isGenerating() {
		return status == SummaryStatus.GENERATING;
	}

	public boolean isDone() {
		return status == SummaryStatus.DONE;
	}

	public boolean isFailed() {
		return status == SummaryStatus.FAILED;
	}

	public boolean isExpired(OffsetDateTime now) {
		return isGenerating() && deadline != null && now.isAfter(deadline);
	}

	public void stepInProgress(SummaryStep step) {
		changeStep(step, StepStatus.IN_PROGRESS);
	}

	public void stepDone(SummaryStep step) {
		changeStep(step, StepStatus.DONE);
	}

	public void fillBasicInfo(AgeGroup ageGroup, Gender gender, InputMode inputMode, String mainSymptom,
		BodyPart bodyPart, Onset onset, String onsetDetail) {
		this.ageGroup = ageGroup;
		this.gender = gender;
		this.inputMode = inputMode;
		this.mainSymptom = mainSymptom;
		this.bodyPart = bodyPart;
		this.onset = onset;
		this.onsetDetail = onsetDetail;
		this.extraNote = null;
	}

	public void fillItems(List<Item> items) {
		this.items = new ArrayList<>(items);
	}

	public void fillHealthInfo(List<HealthInfo> medications, List<HealthInfo> conditions, List<HealthInfo> allergies) {
		this.medications = copy(medications);
		this.conditions = copy(conditions);
		this.allergies = copy(allergies);
	}

	public void fillDepartment(Department department) {
		this.department = department;
	}

	public void done() {
		this.status = SummaryStatus.DONE;
	}

	public void fail() {
		this.status = SummaryStatus.FAILED;
	}

	public void confirm() {
		this.confirmed = true;
	}

	public void changeAgeGroup(AgeGroup ageGroup) {
		this.ageGroup = ageGroup;
		this.confirmed = false;
	}

	public void changeGender(Gender gender) {
		this.gender = gender;
		this.confirmed = false;
	}

	public void changeMainSymptom(String mainSymptom) {
		this.mainSymptom = mainSymptom;
		this.confirmed = false;
	}

	public void changeBodyPart(BodyPart bodyPart) {
		this.bodyPart = bodyPart;
		this.confirmed = false;
	}

	public void changeOnset(Onset onset, String onsetDetail) {
		this.onset = onset;
		this.onsetDetail = onsetDetail;
		this.confirmed = false;
	}

	public void changeOnsetDetail(String onsetDetail) {
		this.onsetDetail = onsetDetail;
		this.confirmed = false;
	}

	public void changeExtraNote(String extraNote) {
		this.extraNote = extraNote;
		this.confirmed = false;
	}

	public void changeMedications(List<HealthInfo> medications) {
		this.medications = copy(medications);
		this.confirmed = false;
	}

	public void changeConditions(List<HealthInfo> conditions) {
		this.conditions = copy(conditions);
		this.confirmed = false;
	}

	public void changeAllergies(List<HealthInfo> allergies) {
		this.allergies = copy(allergies);
		this.confirmed = false;
	}

	public boolean hasItem(int order) {
		return items != null && items.stream().anyMatch(item -> item.order() == order);
	}

	public void changeItemValue(int order, String value) {
		this.items = items.stream()
			.map(item -> item.order() == order ? new Item(item.order(), item.label(), item.question(), value) : item)
			.toList();
		this.confirmed = false;
	}

	private void changeStep(SummaryStep step, StepStatus status) {
		this.steps = steps.stream()
			.map(state -> state.step() == step ? new StepState(step, status) : state)
			.toList();
	}

	private List<HealthInfo> copy(List<HealthInfo> values) {
		return values == null ? null : new ArrayList<>(values);
	}

	public record StepState(SummaryStep step, StepStatus status) {
	}

	public record HealthInfo(String name, String detail) {
	}

	public record Item(int order, String label, String question, String value) {
	}

	public record Department(String departmentCode, String departmentName, String recommendReason) {
	}
}
