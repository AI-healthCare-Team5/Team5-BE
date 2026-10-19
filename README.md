# 진료한장

Spring Boot, JPA, PostgreSQL을 사용하는 백엔드 프로젝트입니다.

## 개발 환경

- JDK 17
- Docker Desktop (로컬 PostgreSQL 실행용)

## 시작하기

1. DB를 실행합니다: `docker compose up -d`
2. 서버를 실행합니다: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`)
3. `http://localhost:8080`에서 서버가 실행됩니다.

DB 접속 정보는 `application.yml`의 기본값(docker-compose.yml과 동일)을 사용합니다. 다른 값을 쓰려면 `.env.example`을 참고해 환경 변수로 지정합니다.

DB를 종료할 때는 `docker compose down`을 실행합니다. 데이터까지 지우려면 `docker compose down -v`를 실행합니다.

## 주요 명령

- `./gradlew bootRun`: 로컬 서버 실행
- `./gradlew test`: 테스트 (DB가 실행 중이어야 합니다)
- `./gradlew build`: 빌드 및 테스트

## 폴더 구성

- `src/main/java/com/health/jinryohanjang/global`: 여러 도메인에서 공유하는 코드
  - `common`: 공통 엔티티(BaseEntity)와 상태값(Status)
  - `config`: 설정 클래스
- `src/main/java/com/health/jinryohanjang/domain`: 도메인별 코드 (API 명세 확정 후 추가)
- `.github`: 이슈 및 PR 템플릿과 CI 워크플로
- `docker-compose.yml`: 로컬 PostgreSQL
- `Dockerfile`: 서버 배포용 이미지

프로젝트의 개발 규칙은 루트 AGENTS.md에 있습니다. Claude Code는 CLAUDE.md를 통해 같은 지침을 읽습니다.

## GitHub Actions

GitHub Actions는 main 브랜치 push와 PR에서 PostgreSQL을 띄운 뒤 `./gradlew build`(컴파일과 테스트)를 실행합니다.
