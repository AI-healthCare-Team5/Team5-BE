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

# Git 협업 규칙

## 1. 커밋 유형 (Commit Type)

커밋은 다음과 같이 분류하여 메시지를 작성한다.

- **`feat`** : 기능 개발
- **`fix`** : 버그 수정
- **`style`** : UI 스타일 수정 (CSS, Tailwind class, 디자인 토큰 값 변경 등 **기능 변경 없음**)
- **`docs`** : 문서 작업 (주로 `main` 에서 `README.md` 작성)
- **`refactor`** : 리팩토링 (기능 변경 없이 **구조/설계 개선**, 함수 추출, 클래스 구조 변경 등)
- **`chore`** : 설정 변경, 파일 이동/이름 변경, 주석 추가 등 빌드/기능에 영향 없는 작업

---

## 2. 커밋 메시지 작성 규칙

커밋 메시지는 다음과 같은 형태로 작성한다. (띄어쓰기 및 형식 준수)

### 📌 규칙

- 커밋 유형은 **반드시 영문 소문자**로 작성한다.
- 커밋 메시지는 **기본적으로 한글**로 작성하되, 라이브러리명/기술 용어/에러 메시지는 **영어 사용을 허용**한다.
- 필요 시 커밋 메시지 가장 앞에 **이슈 번호**를 추가한다.
- 커밋은 **“논리적으로 독립적인 작업”** 단위로 작성한다.
  - 독립적으로 **빌드 및 테스트**가 가능한 단위
  - 롤백 시 **최소한의 영향**을 주는 단위

> **`[태그] #이슈번호: 설명`**

- `[FEAT] #12: 구글 소셜 로그인 Service 구현`
- `[FIX] #15: 회원가입 시 중복 이메일 에러 수정`
- `[DOCS] #20: README.md 컨벤션 정리`
- `[CHORE] #0: 빌드 설정 변경 (build.gradle)`

---

## 3. 브랜치 전략 (Branch Strategy)

### `main` : 배포용 브랜치
- 실제 서비스에 배포되는 코드만 포함
- 직접 개발하지 않고 `dev` 또는 `fix` 등의 브랜치를 이용하여 병합하여 사용

### `develop` : 개발 통합 브랜치
- `main` 브랜치에서 분기
- 여러 기능이 개발되고 통합되는 브랜치
- `feature` 브랜치에서 작업한 기능들을 이 곳으로 병합
- 충분히 테스트한 후 `main`으로 배포

### `feat/#이슈번호-아이디` : 신규 기능 개발 브랜치
- `dev` 브랜치에서 분기되어 기능 단위로 개발
- 개발 완료 후 `dev`로 병합
- 브랜치명은 `kebab-case` 사용

### `fix/#이슈번호-아이디` : 버그 수정용 브랜치
- `dev` 또는 `main`에서 분기하여 버그 수정
- **dev에서 분기** : 개발 중 발견된 버그 수정
  - 기능 구현에 대한 버그 수정 시, 반드시 해당 기능 브랜치가 `dev`와 병합되어 있는지 확인 후 수정 진행
  - 수정 완료 후 `dev`로 병합

### `hotfix/#이슈번호-아이디` : 긴급 수정 브랜치
- 운영 환경(`main`)에서 발생한 긴급 버그 수정
- `main`에서 분기하여 수정 후 `main`과 `dev`에 모두 병합

---

## 4. 브랜치 규칙 (Branching Rule)

1. 기능 개발은 반드시 `feature` 브랜치에서 개발한다.
2. 개발 완료 시 `dev` 브랜치로 합병한다.
3. 브랜치명은 `kebab-case`를 사용한다.
4. 모든 QA 및 버그 수정 완료 시 `main`으로 병합한다.
5. 긴급 수정은 `hotfix` 브랜치에서 진행 후 `main`과 `dev`에 병합한다.


> **`타입/이슈번호-깃허브아이디`**

- **기능 개발**: `feat/12-greaming`
- **버그 수정**: `fix/15-greaming`
- **문서 작업**: `docs/20-greaming`
