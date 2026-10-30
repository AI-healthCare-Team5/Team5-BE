# 진료한장 백엔드 지침

이 파일을 저장소의 기본 개발 지침으로 사용합니다. 작업을 시작하기 전에 관련 지침과 기존 코드를 확인하고, 범위를 벗어나는 기능이나 API를 임의로 추가하지 않습니다.

## 기술 기준

- Java 17, Spring Boot 4, Gradle을 사용합니다.
- 데이터 접근은 Spring Data JPA, DB는 PostgreSQL입니다.
- 로컬 DB는 docker-compose.yml로 실행합니다.
- API 명세와 ERD에 없는 API, 엔티티, 컬럼은 임의로 만들지 않습니다.

## 패키지 구조

- 공통 코드는 global 아래에, 기능 코드는 domain/{도메인명} 아래에 둡니다.
- 도메인 패키지는 controller, service, repository, entity, dto로 나눕니다.
- 모든 엔티티는 global.common.BaseEntity를 상속합니다. BaseEntity에는 생성/수정 시각(created_at, updated_at)만 둡니다.
- 삭제는 ERD 기준 soft delete입니다. ERD에 deleted_at이 있는 엔티티(member, guardian, medical_record)만 deleted_at을 가지고, 삭제할 때 실제로 지우지 않고 deleted_at을 기록하는 의미 있는 메서드(예: delete())로 처리합니다. 조회할 때는 deleted_at이 없는 행만 씁니다.
- 회원 탈퇴는 member.status를 WITHDRAWN으로 바꾸고 deleted_at을 기록합니다. 같은 카카오 계정으로 다시 로그인하면 회원 행만 복구하고 기록·보호자·동의는 복구하지 않습니다.
- 보호자는 회원당 1명입니다. 지웠다가 다시 등록하면 기존 guardian 행을 되살려 새 값으로 덮어씁니다.
- 동의 철회는 consent.withdrawn_at을 기록합니다.
- 문진·요약지(보관 전)는 DB가 아니라 Redis에 두고, 마지막 요청부터 1시간 TTL을 둡니다. 내 기록에 보관할 때 medical_record·record_item·record_health_info로 복사합니다.

## API 규칙

API 명세와 공통 규칙은 노션 "진료한장 도메인별 API 목록" 페이지의 "공통 규칙", "백엔드 결정 사항"을 기준으로 합니다. 요약하면 아래와 같습니다.

- 모든 경로는 /api로 시작합니다. 경로 변수는 {intakeId}, {summaryId}, {medicalRecordId}처럼 이름을 씁니다.
- 응답은 성공·실패 모두 isSuccess, code, message, result 네 필드로 감쌉니다.
- code는 {도메인}_{HTTP 상태} 형식입니다 (예: INTAKE_200, INTAKE_201, SUMMARY_409). 도메인은 API 명세의 도메인을 대문자로 씁니다 (AUTH, MEMBER, CONSENT, GUARDIAN, INTAKE, DEPARTMENT, HOSPITAL, SUMMARY, STT). 예외로 401은 어느 API든 AUTH_401, 보관 시 기록 저장 동의가 없으면 CONSENT_403입니다. code는 global의 enum 한 곳에서 관리합니다.
- 실패 result는 null입니다. 예외: 400은 {errors: [{field, reason}]}, 이미 끝난 문진의 INTAKE_409는 {status}, 이미 보관한 요약지의 SUMMARY_409는 {medicalRecordId}입니다. 같은 code 안에서 이유가 갈리면 프론트는 이 result로 구분합니다.
- 날짜·시간은 ISO 8601 + 시간대(+09:00), 시각만 쓸 때는 HH:mm입니다. 값이 없는 응답 필드는 빼지 않고 null로 내립니다.
- 인증은 쿠키로 합니다: accessToken(JWT, 30분), refreshToken(14일, Redis 저장, 재발급 때 교체), guestKey(비로그인 문진 본인 확인). 모두 HttpOnly, Secure, SameSite=Lax입니다. 프론트가 /api를 프록시하므로 CORS 설정은 하지 않습니다.
- LLM과 STT는 백엔드에서만 호출합니다. API 키와 모델명은 환경 변수로 받습니다.
- 병원 데이터는 공공데이터를 주기적으로 동기화한 DB만 조회하고, 요청 중에 공공 API를 호출하지 않습니다.

## 코드 규칙

- 들여쓰기는 탭을 사용합니다. 기존 코드 스타일을 따릅니다.
- 클래스는 PascalCase, 변수와 메서드는 camelCase, 상수와 enum 값은 UPPER_SNAKE_CASE로 작성합니다.
- 엔티티를 컨트롤러 응답으로 직접 반환하지 않고 DTO로 변환합니다. DTO는 record를 우선 사용합니다.
- 엔티티에는 @Setter를 사용하지 않고, 값 변경은 의미 있는 메서드로 만듭니다.
- 조회 전용 서비스 메서드에는 @Transactional(readOnly = true)를 붙입니다.

## 작업 흐름

- 저장소에서 기능 작업을 할 때는 먼저 담당자와 라벨을 지정한 GitHub 이슈를 준비하고, 이슈 단위 브랜치에서 작업합니다.
- 커밋 메시지는 이모지 없이 "작업 유형: 요약" 형식으로 작성합니다. 예: Feat: 진료 기록 조회 API 추가, Fix: 날짜 변환 오류 수정, Docs: 개발 안내 보완.
- PR에는 변경 요약, 확인한 항목, API 변경 시 요청/응답 예시를 포함합니다. 적절한 라벨과 담당자, 리뷰어를 지정합니다.
- GitHub Actions는 build(컴파일과 테스트)를 확인합니다.

## 명령

- DB 실행 / 종료: docker compose up -d / docker compose down
- 개발 서버: ./gradlew bootRun
- 테스트: ./gradlew test
- 빌드: ./gradlew build
