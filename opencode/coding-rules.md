# 코딩 규칙

## 목적

- 프로젝트의 전반적인 코드 품질 강화
- 높은 가독성과 효율적인 유지보수를 위한 코드 개발

---

## 코드 가이드

### 1. Immutable 객체를 위해 Setter 사용 금지 (DO, DTO 제외)

- 객체의 상태 변경으로 인한 버그 방지를 위함 (어디서 바뀌었는지 추적이 무척 어렵기 때문)
- 참조 공유 시 안전하고, 동시성에서도 불변은 안전하며 따라서 함수형 프로그래밍에서도 필수로 여기는 부분
- 객체 상태 변경 시 **빌더 패턴**을 통해 새로운 객체 생성
- 코틀린 `data class`의 경우 setter가 생기므로 domain 패키지의 domain class들은 immutable을 지키기 위해 `class`로 선언

**예시 1: BillMng**

```kotlin
class BillMng private constructor(
    val ownerId: String?,
    val phase: Int?,
    val startDate: String?,
    val endDate: String?,
    var applyYn: String?
) {
    companion object {
        fun create(
            ownerId: String?,
            phase: Int?,
            startDate: String?,
            endDate: String?,
            applyYn: String?
        ): BillMng {
            val billMng = BillMng(
                ownerId,
                phase,
                startDate,
                endDate,
                applyYn
            )
            return billMng
        }
    }

    fun copy(
        ownerId: String? = this.ownerId,
        phase: Int? = this.phase,
        startDate: String? = this.startDate,
        endDate: String? = this.endDate,
        applyYn: String? = this.applyYn
    ): BillMng = create(
        ownerId,
        phase,
        startDate,
        endDate,
        applyYn
    )
}
```

**예시 2: Role (update/create 포함)**

```kotlin
class Role private constructor(
    val roleId: String?,
    val roleName: String?,
    val isActive: IsActive?
) {
    companion object {
        fun create(
            roleId: String?,
            roleName: String?,
            isActive: IsActive?
        ): Role = Role(
            roleId,
            roleName,
            isActive
        )
    }

    private fun copy(
        roleId: String? = this.roleId,
        roleName: String? = this.roleName,
        isActive: IsActive? = this.isActive
    ): Role = create(
        roleId,
        roleName,
        isActive
    )

    fun updateRole(
        update: Role
    ): Role = copy(
        roleId = update.roleId,
        roleName = update.roleName,
        isActive = update.isActive
    )

    fun createRole(
        create: Role,
        newRoleId: String
    ): Role = copy(
        roleId = newRoleId,
        roleName = create.roleName
    )
}
```

---

### 2. 일급 컬렉션 사용

- **Map 사용 금지**
- 일급 컬렉션 사용 권장
- 예: `List<UserCode>` 대신 `UserCodeList(val values: List<UserCode>)` 형태로 감싸 비즈니스 로직 포함

**❌ 안좋은 예시**

```kotlin
data class UserCode(val code: String)

class UserService {
    fun processCodes(codes: List<UserCode>) {
        // 비즈니스 로직이 여기저기 흩어질 가능성
        if (codes.isEmpty()) {
            throw IllegalArgumentException("사용자 코드가 비어 있습니다.")
        }
        // ...
    }
}
```

**✅ 일급 컬렉션 적용**

```kotlin
class UserCodeList(val values: List<UserCode>) {
    init {
        require(values.isNotEmpty()) { "사용자 코드 리스트는 비어 있을 수 없습니다." }
    }

    fun contains(code: String): Boolean {
        return values.any { it.code == code }
    }

    fun size(): Int = values.size

    // 필요한 경우 toString/equals/hashCode override 가능
}

class UserService {
    fun processCodes(userCodes: UserCodeList) {
        if (userCodes.contains("ADMIN")) {
            // 관리자 처리 로직
        }
        println("총 사용자 코드 수: ${userCodes.size()}")
    }
}
```

---

### 3. Data Transfer Object, Domain Object, Data Object 분리

- 계층간 결합도 최소화, 도메인 일관성 및 무결성 확보
- 각 레이어에서 역할이 분리되어 있기 때문에 로직 수정이나 새로운 기능 추가 시 영향 범위가 좁아짐

**변환 흐름:** `UserRequest → User → UserDO`

| 객체 | 역할 |
|---|---|
| **UserRequest** | 외부 API 요청을 담는 입력 전용 객체로, 검증이나 구조 변경이 잦음 |
| **User** | 도메인 로직을 담는 비즈니스 객체, 핵심 규칙과 정책을 표현 |
| **UserDO** | DB와 직접 매핑되는 Persistence 객체, ORM을 위한 구조 |

**Request 클래스 예시**

```kotlin
package com.cjl.fizz.api.auth.controller.request

import com.cjl.fizz.api.auth.domain.Signup
import io.swagger.v3.oas.annotations.media.Schema

data class SignupRequest(
    @Schema(description = "로그인할 사용자의 이메일입니다.", example = "example@cj.net", required = true)
    val userId: String,
    ...
) {
    fun toSignup(): Signup {
        return Signup.create(
            userId = userId,
            password = password,
            koreanUserName = koreanUserName,
            englishUserName = englishUserName,
            companyName = companyName,
            phoneNumber = phoneNumber,
            mobileNumber = mobileNumber,
            email = email,
            timeZone = timeZone,
            confirmationCode = null
        )
    }
}
```

**Domain 클래스 예시**

```kotlin
package com.cjl.fizz.api.auth.domain

import com.cjl.fizz.api.shared.enums.IsActive
import com.cjl.fizz.api.shared.enums.IsTemporaryPassword
import com.cjl.fizz.api.shared.enums.IsUsed
import com.cjl.fizz.api.shared.enums.RoleId
import com.cjl.fizz.api.shared.utils.PasswordUtils
import java.time.OffsetDateTime

class Signup private constructor(
    val userId: String,
    val password: String?,
    val koreanUserName: String?,
    val englishUserName: String?,
    val companyName: String?,
    val phoneNumber: String?,
    val mobileNumber: String?,
    val email: String?,
    val timeZone: String?,
    val confirmationCode: String?
) {

    companion object {
        fun create(
            userId: String,
            password: String?,
            koreanUserName: String?,
            englishUserName: String?,
            companyName: String?,
            phoneNumber: String?,
            mobileNumber: String?,
            email: String?,
            timeZone: String?,
            confirmationCode: String?
        ): Signup {
            return Signup(
                userId = userId,
                password = password,
                koreanUserName = koreanUserName,
                englishUserName = englishUserName,
                companyName = companyName,
                phoneNumber = phoneNumber,
                mobileNumber = mobileNumber,
                email = email,
                timeZone = timeZone,
                confirmationCode = confirmationCode
            )
        }
    }

    fun toUser(): User {
        val now = OffsetDateTime.now()
        val passwordSalt = password?.let { PasswordUtils.generateSalt() }
        val password = password?.let { PasswordUtils.encrypt(password, passwordSalt!!) }
        return User.create(
            userId = userId,
            password = password,
            passwordSalt = passwordSalt,
            passwordTemporaryYn = IsTemporaryPassword.PERMANENT,
            passwordLastUpdateDateTime = now,
            ...
            loginRetryCount = 0,
            koreanUserName = koreanUserName,
            englishUserName = englishUserName,
            companyName = companyName,
            roleId = RoleId.DEFAULT.value,
            phoneNumber = phoneNumber,
            mobileNumber = mobileNumber,
            email = email,
            timeZone = timeZone,
            isUsed = IsUsed.USED,
            isActive = IsActive.ACTIVE
        )
    }
}
```

---

### 4. Package Dependency 관리

- `controller` 안에 `request`, `response`가 있고, `repository` 안에 DO를 담는 `dataobject` 패키지는 **해당 패키지 안에서만 사용**, 외부에 디펜던시 금지
- 각 최상위 domain 객체끼리는 **dependency 참조 불가** (최상위 domain 객체를 둘 필요가 없을 시에는 없어도 무방)
    - `util` 및 `model` 객체는 제외

#### 패키지 구조

```
domain(Optional - 최상위 도메인 패키지) → controller → service → repository
```

> 도메인을 나눌 필요가 없다면 최상위는 생략 가능

#### 디펜던시 규칙

- 패키지 디펜던시는 **단방향**으로 호출해야 함
- 예: 서비스 패키지 안에 있는 클래스는 컨트롤러 패키지에 들어갈 수 없음
- `domain(model)` 패키지는 `controller`, `service`, `repository` 디펜던시가 가능하지만 **반대로는 불가**

**❌ 잘못된 디펜던시**

```kotlin
// 도메인 모델 패키지의 클래스
package com.cjl.fizz.api.mast.domain

// 잘못된 디펜던시 - domain이 controller.response를 참조하면 안됨
import com.cjl.fizz.api.mast.controller.response.SmCodeResponse

class SmCode private constructor(
    ...
    // 디펜던시를 끊기 위해서는 response 클래스에서 domain을 주입받아
    // to가 아닌 from으로 생성해야 함
    fun toResponse(): SmCodeResponse {
        return this.transform(SmCodeResponse::class)
    }
)
```

**✅ 올바른 디펜던시 (from 패턴)**

```kotlin
// controller 패키지의 response 패키지에서 from으로 도메인을 주입받아 response 객체 생성
package com.cjl.fizz.api.mast.controller.response

data class SmCodeResponse(
    ...
    companion object {
        fun fromDomain(domain: SmCode): SmCodeResponse {
            return domain.transform(
                targetClass = SmCodeResponse::class,
                overrides = mapOf(
                    "wcsRcvYn" to domain.wcsRcvYn?.value
                )
            )
        }
    }
)
```

---

## DB 관련 규칙

### SQL Query 제한

| 항목 | 규칙 |
|---|---|
| **Join** | 사용 금지. 사유 입증 시 **최대 2개 테이블**까지 허용 |
| **Subquery** | 사용 금지. 사유 입증 시 허용 |
| **Outer Join** | 명확한 Join 조건을 식별하지 않는 경우 사용 금지 |

### DB 오브젝트 사용 금지

- `Procedure`, `Function` 등 DB 의존적인 오브젝트 사용 금지
- SQL 코드 품질 가이드 준수

---

## DDD 레이어 구조

### 패키지 구조 및 설명

#### `presentation/` — 들어오는 어댑터

> **책임:** 요청 검증, 인증/인가 연동, 예외 → HTTP 매핑 (RESTful)

- `rest/controller` — 컨트롤러
- `rest/dto/request`, `rest/dto/response` — 외부 표현 DTO

#### `application/` — 유스케이스 / 오케스트레이션

> **책임:** 도메인 모델 조합/절차, 저장/조회 호출, 외부 연동 조정

- `service` — 트랜잭션 경계, 시나리오 흐름
- `gateway` — 외부 연동 인터페이스 (관세/국세/택배사 등)
- `dto` — Command/Query/Result 등 내부용 DTO
- `mapper` — app DTO ↔ domain 변환

#### `domain/` — 순수 도메인

> **책임:** 규칙/정책/불변식, 상태 전이, 도메인 이벤트(선택)

- `model/aggregate`, `model/entity`, `model/vo` — 불변 VO
- `service` — 도메인 규칙/계산
- `repository` — 저장소 인터페이스, 기술 중립

#### `infrastructure/` — 나가는 어댑터 (구현체)

> **책임:** JPA/MyBatis/HTTP 등 구현 기술 의존 영역

- `persistence/jpa/entity`, `persistence/jpa/repo`, `persistence/jpa/mapper`
- `persistence/adapter` — 도메인 repository 인터페이스 구현체
- `http` — 외부 API 클라이언트 구현 (WebClient/Feign)
- `messaging` — Kafka/SQS 등
- `acl` — Anti-Corruption Layer
- `config`

### 핵심 원칙

1. **저장소 인터페이스**는 `domain.repository`에 위치 (도메인 언어, 기술 독립)
2. **구현체(=본체)**는 `infrastructure`에 위치 (JPA/MyBatis 등 구체 기술)
3. **외부기관 연동**은 `application.gateway`에 인터페이스, 구현은 `infrastructure.http`

### 레이어 경계에서의 데이터 모델

| 모델 | 위치 | 특성 |
|---|---|---|
| **DTO** | presentation / application | 계층 간 전달용, 가변 가능, 외부 표현과 내부 유스케이스에 각각 최적화 |
| **VO** | domain | 불변, 동등성 기반 비교, 비즈니스 개념을 표현 |

- 도메인 모델 ↔ JPA 엔티티 **분리 권장**: 순수 도메인 보존 + 테스트 용이성 향상 (매퍼로 변환)

### 트랜잭션 · 예외 · 검증 정책

| 항목 | 정책 |
|---|---|
| **트랜잭션 경계** | `application.service` |
| **도메인 검증** | 도메인 생성자/메서드에서 `require`/예외로 보장 |
| **입력 검증** | presentation 레이어 (애너테이션/바인딩) |
| **예외 매핑** | 도메인/애플리케이션 예외를 HTTP 상태로 사상 (글로벌 핸들러) |

---

## 테스트 코드 가이드

### 테스트 커버리지 목표

- 모든 핵심 비즈니스 로직에 대한 테스트 작성
- 패키지 별 설정된 테스트 커버리지 기준 준수 (**전체 70% 이상**)
- 모든 API **End-to-End Test** 작성

**E2E 테스트 예시**

```kotlin
@Test
fun `MBL 정산 수정`() {
    // Given
    val updateBody = """
        [
          {
            "mblNumber": "mbl001",
            "carrierCost": "1111",
            "lineTransportCost": "2222"
          }
        ]
    """.trimIndent()

    // When & Then
    val result = mockMvc.patch("/v1/billing-bases/master-bls") {
        contentType = MediaType.APPLICATION_JSON
        content = updateBody

        with(jwt().jwt { it.claim("username", "aaa") })
    }
        .andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
        }
        .andReturn()

    val responseBody = result.response.contentAsString
    log.info("Response Body: {}", responseBody)

    val jsonNode = objectMapper.readTree(responseBody)
    val success = jsonNode["success"].asBoolean()

    assertTrue(success)
}
```

### 테스트 계층 분리

| 계층 | 목적 |
|---|---|
| **Unit Test** | 각 메서드의 단위 동작 검증 |
| **Integration Test** | 데이터베이스, 외부 API 연동 테스트 |
| **End-to-End Test** | API 요청-응답 테스트 |

### 외부 의존성 격리 및 테스트

- **Mockito** 및 **MockMVC** 사용
- 외부 의존성(Mock Repository, Mock API 등)을 Mockito로 Mocking하여 테스트 작성

---

## 네이밍 규칙

| 대상 | 컨벤션 | 예시 |
|---|---|---|
| 클래스 이름 | `PascalCase` | `UserController` |
| 메서드/변수 이름 | `camelCase` | `getUser` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_USER_COUNT` |
