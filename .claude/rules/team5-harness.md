# Team5-BE 하네스 지침

`AGENTS.md`를 보완하는 Claude용 지침입니다. 도메인·API 규칙은 `AGENTS.md`를 따르고, 이 파일은 거기에 없는 코드 관용구와 함정만 다룹니다. 둘이 충돌하면 `AGENTS.md`를 우선하고 사용자에게 알립니다.

## 작업 전 확인

- 노션의 API 명세와 ERD는 저장소에 없습니다. 명세가 필요한 값을 추측해서 API, 엔티티, 컬럼, code를 만들지 말고 사용자에게 요청합니다.
- 커밋, 푸시, 브랜치 삭제, 공유 설정 변경은 사용자 확인 후에 합니다.
- 공통 코드(`common`)를 바꾸면 모든 API에 영향을 줍니다. 기능 작업에 섞지 말고 변경 범위를 먼저 알립니다.

## Git 흐름

- 새 브랜치는 `git fetch origin` 후 `origin/develop`에서 땁니다. PR 대상도 `develop`입니다. `CONTRIBUTING.md`의 "기본 브랜치"는 `main`이라 실제 흐름과 다릅니다.
- 브랜치 이름은 `타입/이슈번호-깃허브아이디`입니다. 예: `feat/12-mybookg`
- 커밋 형식은 문서끼리 충돌합니다. `README.md`와 PR 템플릿은 `[FEAT] #12: 요약`, `AGENTS.md`와 `CONTRIBUTING.md`는 `Feat: 요약`입니다. 팀이 정하기 전에는 커밋 전에 어느 쪽을 쓸지 사용자에게 확인합니다.
- `develop`에 PR이 머지되면 `close-issues-on-develop.yml`이 본문의 `Closes #번호`로 이슈를 닫고 브랜치를 삭제합니다. 그 브랜치를 쓰는 다른 열린 PR이 있으면 삭제하지 않습니다. 할 일이 남은 이슈에는 `Closes`를 쓰지 않습니다.
- `deploy-dev.yml`의 자동 실행은 AWS 준비 전이라 꺼져 있습니다. 임의로 켜지 않습니다.

## 코드 스타일

- `.java`, `.gradle`은 탭, 그 외는 스페이스 2칸입니다. 줄바꿈 연속 들여쓰기는 탭 1개입니다. 파일 끝에 개행을 넣습니다.
- import는 `java`, `javax`, `org`, `com` 순서로 그룹 사이에 빈 줄을 두고, `jakarta`와 `lombok`을 마지막 그룹에 둡니다. 와일드카드 import는 쓰지 않습니다.
- 클래스 어노테이션 순서는 코드에 하나로 고정돼 있지 않습니다(`@Slf4j @RestControllerAdvice`, `@Component @RequiredArgsConstructor`). 같은 종류의 기존 클래스를 따릅니다.
- 의존성은 `@RequiredArgsConstructor`와 `private final`로 주입합니다. `@Autowired`를 쓰지 않습니다.
- 클래스 Javadoc은 한국어로 역할을 짧게 적고 "~한다"로 끝냅니다. 코드를 그대로 설명하는 주석은 달지 않습니다.
- 실패 메시지는 "~요" 체입니다. 예: `"로그인이 필요해요."` 성공 메시지는 `"답변 처리 성공"`과 `"이미 동의했어요."`가 섞여 있으니 기존 값을 확인합니다.
- `ErrorStatus` 상수 이름은 도메인이나 대상을 앞에 씁니다. 예: `INTAKE_NOT_FOUND`, `TOKEN_INVALID`
- `ErrorStatus` 상수는 `(code, HttpStatus, message)` 순서이고, 도메인 주석으로 묶습니다. 메시지가 달라도 code는 같을 수 있습니다.
- 로그는 `log.error("메시지", e)` 형식으로 쓰고 문자열을 연결하지 않습니다. 건강정보와 토큰을 로그에 남기지 않습니다.

### Greaming과의 차이

공통 구조와 응답·예외 방식은 Greaming과 비슷하지만, 아래는 Greaming 코드를 따라 쓰면 규칙 위반입니다.

- 스페이스 들여쓰기, 와일드카드 import
- `@JsonInclude(NON_NULL)`: 이 프로젝트는 null 필드를 유지합니다.
- `COMM_400` 같은 고정 code: 이 프로젝트의 400, 500은 Advice가 도메인으로 만듭니다.
- CORS 설정: 프론트가 `/api`를 프록시하므로 만들지 않습니다.

## 응답과 예외 관용구

```java
return ApiResponse.success(SuccessStatus.SOME_STATUS, result);
throw new GeneralException(ErrorStatus.INTAKE_NOT_FOUND);
throw new GeneralException(ErrorStatus.INTAKE_ALREADY_FINISHED, Map.of("status", status));
throw GeneralException.tooManyRequests(ErrorStatus.INTAKE_TOO_MANY_REQUESTS, seconds);
```

타입이 막아 주지 않으므로 아래를 직접 지킵니다.

- `success()`에는 `SuccessStatus`만 넘깁니다. `ErrorStatus`를 넘겨도 컴파일되며 404에 `isSuccess:true`가 나갑니다.
- `INTAKE_ALREADY_FINISHED`에는 `{status}`, `SUMMARY_ALREADY_SAVED`에는 `{medicalRecordId}`를 담습니다. 빠뜨려도 컴파일됩니다.
- 400은 되도록 Bean Validation으로 처리합니다. `ErrorStatus`의 400 상수를 그대로 던지면 `result`가 `{errors}`가 아니라 null입니다.
- 401, 429는 `GeneralException`으로 던집니다. `ResponseStatusException`을 쓰면 `INTAKE_401`처럼 틀린 code가 나오고 `Retry-After`도 빠집니다.

## 알려진 함정

아직 고쳐지지 않은 공통 코드 문제입니다. 고치려면 먼저 사용자에게 알립니다.

- **도메인 판별:** `\.domain\.([a-z]+)\.` 패키지에서 읽습니다. `domain.medicalRecord`는 `COMMON_400`이 되고, `domain.medicalrecord`는 `MEDICALRECORD_400`이 됩니다. 도메인 패키지는 `AGENTS.md`의 도메인 이름을 소문자로 씁니다.
- **빈 `errors`:** 깨진 JSON, 쿠키·헤더 누락(`guestKey` 포함), 클래스 수준 제약 위반은 `errors:[]`로 나갑니다. 프론트가 필드를 알아야 하면 처리를 추가해야 합니다.
- **복합 검증:** `@Valid` 본문과 `@RequestParam` 제약을 함께 쓰면 `field`가 실제 필드명이 아닌 파라미터 이름으로 나옵니다.
- **STT 업로드:** 크기 초과는 `STT_413`이 아니라 `COMMON_413`입니다. 잘못된 multipart 요청은 500입니다.
- **필터:** 필터에서 던진 예외는 Advice를 거치지 않습니다. JWT 인증을 필터로 만들면 `AUTH_401` 응답을 직접 처리해야 합니다.
- **날짜:** `BaseEntity`는 `LocalDateTime`입니다. 응답 DTO에 그대로 넣으면 `+09:00`이 빠지니 `OffsetDateTime`으로 변환합니다. 시각만 쓸 때는 `HH:mm`으로 맞춥니다.

## JPA와 암호화

- 암호화 필드는 `@Convert(converter = EncryptedStringConverter.class)`와 TEXT 컬럼을 함께 씁니다. `columnDefinition`을 빠뜨리면 `varchar(255)`가 되어 긴 값 저장이 실패합니다.
- 암호화 컬럼은 IV가 매번 달라 `=` 검색이 되지 않습니다.
- 복호화에 실패한 행이 있으면 엔티티 조회와 삭제가 실패합니다. 30일 삭제 배치는 JPQL이나 네이티브 쿼리로 작성합니다.
- JPQL 일괄 UPDATE는 `updatedAt`을 갱신하지 않습니다. 필요하면 직접 넣습니다.
- `@DataJpaTest`는 `JpaAuditingConfig`를 `@Import`해야 `created_at` NOT NULL 위반이 나지 않습니다.

## 환경과 설정

- JDK 21이 필요합니다.
- 프로필은 지정하지 않으면 `local`이고, 서버는 `SPRING_PROFILES_ACTIVE=dev`입니다.
  - 공통 설정은 `application.yml`, DB 기본값과 `ddl-auto`, `show-sql`은 프로필 파일에 둡니다.
  - `dev`에 로컬 기본값을 넣지 않습니다. `dev,local`로 지정하면 local이 우선해 로컬 DB로 연결됩니다.
- `dev`는 DB 값이 없으면 기동에 실패하지만, `REDIS_HOST`가 없으면 기동된 뒤 첫 Redis 호출에서 실패합니다.
- Redis 타임아웃 2초는 `dev`에만 있습니다. `local`은 기본값이라 장애 시 최대 60초 대기합니다.
- 새 환경 변수를 추가하면 `.env.example`을 함께 고칩니다. 실제 값은 커밋하지 않습니다.
- `DATA_ENCRYPTION_KEY`가 없으면 서버가 뜨지 않습니다. 형식은 32바이트의 Base64입니다.

## 테스트와 검증

- `./gradlew build`와 `./gradlew test`는 PostgreSQL이 필요합니다. 먼저 `docker compose up -d`를 실행합니다.
- 6379 포트를 다른 프로젝트의 Redis가 쓰고 있으면 이 프로젝트의 Redis 컨테이너가 뜨지 않고, 앱이 그쪽에 연결될 수 있습니다.
- 테스트 키는 Gradle `test` 태스크에서만 주입됩니다. IntelliJ 러너로 바로 돌리면 키가 없어 실패합니다.
- **현재 테스트는 `contextLoads` 1개뿐이고 CI에는 Redis가 없습니다.** 빌드 통과는 앱이 뜬다는 뜻일 뿐, 응답 형식, 암호화, 호출 제한의 동작을 보증하지 않습니다. 변경한 동작은 직접 확인하고, 확인하지 못한 항목은 사용자에게 알립니다.
- 커밋 `2846829`에서 단위 테스트 18개가 의도적으로 삭제됐습니다. 복원 여부는 팀이 정할 일이니 임의로 되살리지 않습니다.
