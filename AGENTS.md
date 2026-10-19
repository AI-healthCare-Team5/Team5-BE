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
- 모든 엔티티는 global.common.BaseEntity를 상속합니다. 생성/수정 시각과 상태값(status)이 자동으로 포함됩니다.
- 삭제는 실제 삭제 대신 BaseEntity의 delete()로 상태를 DELETED로 바꿉니다.

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
