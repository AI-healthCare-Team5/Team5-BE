# 진료한장

Spring Boot, JPA, PostgreSQL을 사용하는 백엔드 프로젝트입니다.

## 개발 환경

- JDK 17
- Docker Desktop (로컬 PostgreSQL, Redis 실행용)

## 시작하기

1. DB와 Redis를 실행합니다: `docker compose up -d`
2. 건강정보 암호화 키 `DATA_ENCRYPTION_KEY`를 환경 변수로 지정합니다. 32바이트 키를 Base64로 인코딩한 값이며 `openssl rand -base64 32`로 만들 수 있습니다. 키가 없으면 서버가 시작하지 않습니다.
3. 서버를 실행합니다: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`)
4. `http://localhost:8080`에서 서버가 실행됩니다.

DB·Redis 접속 정보는 `application.yml`의 기본값(docker-compose.yml과 동일)을 사용합니다. 다른 값과 외부 서비스 키는 `.env.example`을 참고해 환경 변수로 지정합니다.

종료할 때는 `docker compose down`을 실행합니다. 데이터까지 지우려면 `docker compose down -v`를 실행합니다.

## 주요 명령

- `./gradlew bootRun`: 로컬 서버 실행
- `./gradlew test`: 테스트 (DB가 실행 중이어야 합니다. 암호화 키는 테스트 전용 키를 씁니다)
- `./gradlew build`: 빌드 및 테스트

## 폴더 구성

- `src/main/java/com/health/jinryohanjang/global`: 여러 도메인에서 공유하는 코드
  - `common`: 공통 엔티티(BaseEntity, 생성/수정 시각)
  - `common.response`: 공통 응답(ApiResponse), code 도메인(Domain), 컨트롤러 도메인 지정(@ApiDomain)
  - `error`: 실패 예외(BusinessException)와 전역 예외 처리
  - `crypto`: 건강정보 AES 암호화 컨버터
  - `ratelimit`: Redis 호출 제한
  - `config`: 설정 클래스
- `src/main/java/com/health/jinryohanjang/domain`: 도메인별 코드 (API 명세 확정 후 추가)
- `.github`: 이슈 및 PR 템플릿과 CI 워크플로
- `docker-compose.yml`: 로컬 PostgreSQL, Redis
- `Dockerfile`: 서버 배포용 이미지

프로젝트의 개발 규칙은 루트 AGENTS.md에 있습니다. Claude Code는 CLAUDE.md를 통해 같은 지침을 읽습니다.

## GitHub Actions

GitHub Actions는 main 브랜치 push와 PR에서 PostgreSQL을 띄운 뒤 `./gradlew build`(컴파일과 테스트)를 실행합니다.
