package com.health.jinryohanjang.domain.intake.dto;

import java.util.List;

import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;
import com.health.jinryohanjang.domain.intake.enums.Stage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문진 시작·상태 조회·답변·이전 질문이 함께 쓰는 응답이다.
 */
public record IntakeResponse(
	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "문진 상태", example = "IN_PROGRESS")
	IntakeStatus status,

	@Schema(description = "문진 단계", example = "SYMPTOM")
	Stage stage,

	@Schema(description = "단계 안 진행 상황. stage마다 1부터 다시 센다.")
	Progress progress,

	@Schema(description = "지금까지 모은 정보 (화면 상단 표시)", example = "[\"무릎 통증\", \"사흘째\"]")
	List<String> collected,

	@Schema(description = "현재 질문. EMERGENCY·COMPLETED면 null", nullable = true)
	Question question,

	@Schema(description = "못 알아들어 같은 질문을 다시 묻는지 (\"다시 말씀해 주세요\")", example = "false")
	boolean reask,

	@Schema(description = "응급 정보. EMERGENCY일 때만 채운다 (E01).", nullable = true)
	Emergency emergency,

	@Schema(description = "추천 진료과. COMPLETED일 때만 채운다.", nullable = true)
	Department department,

	@Schema(description = "요약지 ID (UUID). COMPLETED일 때만 채우고, 서버가 요약지 생성을 바로 시작한다.",
		example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f", nullable = true)
	String summaryId
) {

	public record Progress(
		@Schema(description = "현재 질문 번호", example = "1")
		int current,

		@Schema(description = "전체 질문 수. SYMPTOM은 최대 7, BASIC_INFO는 5", example = "7")
		int total
	) {
	}

	public record Question(
		@Schema(description = "질문 ID. 답변할 때 그대로 보낸다.", example = "q_swelling")
		String questionId,

		@Schema(description = "질문 문장", example = "무릎이 부었나요?")
		String text,

		@Schema(description = "답 형식", example = "YES_NO_UNKNOWN")
		AnswerType answerType,

		@Schema(description = "선택지. SINGLE_CHOICE일 때만 있고 그 외에는 null", nullable = true)
		List<Option> options
	) {
	}

	public record Option(
		@Schema(description = "선택지 code. 버튼으로 답할 때 answer에 넣는다.", example = "SIXTIES")
		String code,

		@Schema(description = "화면에 보일 문구", example = "60대")
		String label
	) {
	}

	public record Emergency(
		@Schema(description = "위험 신호로 판정된 사용자 발화 (E01 \"말씀하신 내용\")", example = "가슴이 조이고 식은땀이 나요")
		String userUtterance,

		@Schema(description = "위험 신호 종류", example = "CHEST_PAIN")
		RedFlagCategory redFlagCategory
	) {
	}

	public record Department(
		@Schema(description = "심평원 진료과목코드", example = "05")
		String departmentCode,

		@Schema(description = "진료과 이름", example = "정형외과")
		String departmentName,

		@Schema(description = "추천 이유", example = "무릎 통증이 사흘째이고 붓기가 있어 정형외과 진료를 권해요.")
		String recommendReason
	) {
	}
}
