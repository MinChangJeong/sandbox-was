# WMS 기능 구현 지침서 (High Priority)

> 본 문서는 WMS(Warehouse Management System) 개발을 위한 High Priority 기능 구현 가이드입니다.

---

## 목차
1. [핵심 설계 원칙](#1-핵심-설계-원칙)
2. [기술 스택](#2-기술-스택)
3. [아키텍처](#3-아키텍처)
4. [상태 관리 (Sealed Class)](#4-상태-관리-sealed-class)
5. [로그 관리 체계](#5-로그-관리-체계)
6. [예외 처리 (AOP)](#6-예외-처리-aop)
7. [HTTP 통신 규격](#7-http-통신-규격)
8. [개발 Phase 및 의존성](#8-개발-phase-및-의존성)
9. [기준정보 관리 (Master Data)](#9-기준정보-관리-master-data)
10. [입고 관리 (Inbound)](#10-입고-관리-inbound)
11. [재고 관리 (Inventory)](#11-재고-관리-inventory)
12. [출고 관리 (Outbound)](#12-출고-관리-outbound)
13. [보고서/통계](#13-보고서통계)
14. [시스템 관리](#14-시스템-관리)
15. [공통 설계 규칙](#15-공통-설계-규칙)
16. [테스트 가이드](#16-테스트-가이드)
17. [인증/인가](#17-인증인가)
18. [반품 관리 (Return)](#18-반품-관리-return)
19. [이벤트 핸들러 및 알림](#19-이벤트-핸들러-및-알림)
20. [인프라스트럭처 상세](#20-인프라스트럭처-상세)

---

## 1. 핵심 설계 원칙

### 1.1 객체지향 설계 원칙 (능동적 객체)

> ⚠️ **최우선 원칙 - 모든 객체는 자기 자신의 상태와 값을 스스로 관리하는 주체적 객체여야 함**

#### 원칙 1: Tell, Don't Ask
```kotlin
// ❌ 수동적 객체 - 외부에서 상태를 직접 변경 (절대 금지)
inventory.status = "ALLOCATED"
inventory.allocatedQty += 10
inventory.availableQty -= 10
location.status = "OCCUPIED"

// ✅ 능동적 객체 - 객체가 스스로 상태를 변경
inventory.allocate(qty = 10, orderId = orderId)  // 내부에서 상태, 수량, 이력 모두 처리
location.occupy(inventory)                        // 내부에서 상태 전이 판단
```

#### 원칙 2: 상태 변경의 단일 진입점
- 모든 상태 변경은 **반드시 해당 객체의 메서드를 통해서만** 가능
- setter 직접 노출 금지 (`private set` 또는 `val` 사용)
- 상태 변경 로직이 여러 곳에 흩어지면 안 됨

#### 원칙 3: 도메인 클래스 data class 사용 금지
> ⚠️ **coding-rules.md 참조** - Kotlin `data class`는 자동으로 setter를 생성하므로 도메인 클래스에서 사용 금지

```kotlin
// ❌ data class 사용 금지 - setter가 자동 생성됨
data class Inventory(
    var status: InventoryStatus,
    var quantity: Int
)

// ✅ 일반 class 사용 - Immutable 원칙 준수
class Inventory private constructor(
    private var _status: InventoryStatus,
    private var _quantity: Int,
    private var _allocatedQty: Int
) {
    // 읽기 전용 프로퍼티만 외부 노출
    val status: InventoryStatus get() = _status
    val quantity: Int get() = _quantity
    val allocatedQty: Int get() = _allocatedQty
    val availableQty: Int get() = _quantity - _allocatedQty
    
    companion object {
        fun create(
            status: InventoryStatus = InventoryStatus.Available,
            quantity: Int,
            allocatedQty: Int = 0
        ): Inventory {
            require(quantity >= 0) { "수량은 0 이상이어야 합니다" }
            require(allocatedQty >= 0) { "할당 수량은 0 이상이어야 합니다" }
            require(allocatedQty <= quantity) { "할당 수량은 총 수량을 초과할 수 없습니다" }
            
            return Inventory(status, quantity, allocatedQty)
        }
    }
    
    // 상태 변경은 도메인 메서드를 통해서만
    fun allocate(qty: Int, orderId: Long): AllocationResult {
        // ... 할당 로직
    }
}
```

```kotlin
@Entity
class Inventory : AggregateRoot() {
    // 상태는 외부에서 직접 변경 불가
    private var _status: InventoryStatus = InventoryStatus.Available
    private var _quantity: Int = 0
    private var _allocatedQty: Int = 0
    
    // 읽기 전용 프로퍼티만 노출
    val status: InventoryStatus get() = _status
    val quantity: Int get() = _quantity
    val allocatedQty: Int get() = _allocatedQty
    val availableQty: Int get() = _quantity - _allocatedQty
    
    // ✅ 상태 변경은 반드시 도메인 메서드를 통해서만
    fun allocate(qty: Int, orderId: Long): AllocationResult {
        // 1. 자기 스스로 검증 (다른 곳에서 검증하면 안 됨)
        require(availableQty >= qty) { "가용재고 부족: 요청=$qty, 가용=$availableQty" }
        require(_status.canAllocate()) { "할당 불가 상태: $_status" }
        
        // 2. 상태 변경
        val beforeQty = _allocatedQty
        _allocatedQty += qty
        
        // 3. 상태 전이도 내부에서 판단
        if (availableQty == 0) {
            _status = InventoryStatus.FullyAllocated
        }
        
        // 4. 이력 기록도 내부에서 (불변 규칙 보장)
        recordHistory(
            transactionType = TransactionType.ALLOCATE,
            changeQty = qty,
            reason = "출고오더: $orderId"
        )
        
        // 5. 도메인 이벤트 발행
        registerEvent(InventoryAllocatedEvent(id, qty, orderId))
        
        return AllocationResult(this.id, qty)
    }
    
    fun deallocate(qty: Int, reason: String) {
        require(_allocatedQty >= qty) { "할당 해제 수량 초과" }
        
        _allocatedQty -= qty
        
        // 상태 복원도 내부 판단
        if (_status == InventoryStatus.FullyAllocated && availableQty > 0) {
            _status = InventoryStatus.Available
        }
        
        recordHistory(TransactionType.DEALLOCATE, -qty, reason)
        registerEvent(InventoryDeallocatedEvent(id, qty, reason))
    }
    
    // 이력 기록도 내부 메서드
    private fun recordHistory(transactionType: TransactionType, changeQty: Int, reason: String) {
        _histories.add(InventoryHistory(
            inventory = this,
            transactionType = transactionType,
            changeQuantity = changeQty,
            beforeQuantity = _quantity - changeQty,
            afterQuantity = _quantity,
            reason = reason
        ))
    }
}
```

#### 원칙 3: Location도 능동적 객체
```kotlin
@Entity
class Location : AggregateRoot() {
    private var _status: LocationStatus = LocationStatus.Empty
    
    val status: LocationStatus get() = _status
    
    fun occupy(inventory: Inventory) {
        require(_status.canAcceptInventory()) { "적치 불가 상태: $_status" }
        
        _status = LocationStatus.Occupied
        registerEvent(LocationOccupiedEvent(id, inventory.id))
    }
    
    fun release() {
        require(canRelease()) { "재고가 남아있어 해제 불가" }
        
        _status = LocationStatus.Empty
        registerEvent(LocationReleasedEvent(id))
    }
    
    fun lock(reason: String) {
        require(_status.canLock()) { "잠금 불가 상태: $_status" }
        
        _status = LocationStatus.Locked
        registerEvent(LocationLockedEvent(id, reason))
    }
}
```

---

### 1.2 불변 규칙 (Invariants)

> ⚠️ **절대 위반 불가 - 시스템의 데이터 정합성을 보장하는 핵심 규칙**

#### 규칙 1: 재고 수량 = 재고 이력의 합
```
SUM(Inventory.quantity) == SUM(InventoryHistory.changeQuantity)
```
- **모든** 재고 변동은 반드시 `InventoryHistory`에 기록
- 재고 증가, 감소, 이동, 조정, 할당, 해제 등 **예외 없음**
- 정기적 검증 배치 작업으로 정합성 확인
- **이력 기록은 반드시 도메인 객체 내부에서** (외부에서 별도 기록 금지)

#### 규칙 2: 재고 이력 기록 필수 케이스
| 케이스 | TransactionType | 설명 |
|--------|-----------------|------|
| 입고 적치 | `INBOUND` | 재고 생성/증가 |
| 출고 확정 | `OUTBOUND` | 재고 차감 |
| 재고 조정 | `ADJUSTMENT_INCREASE` / `ADJUSTMENT_DECREASE` | 수량 조정 |
| 로케이션 이동 | `MOVEMENT_OUT` / `MOVEMENT_IN` | 이동 시 출발지/도착지 각각 기록 |
| 존 간 이동 | `TRANSFER_OUT` / `TRANSFER_IN` | 존 이동 시 각각 기록 |
| 재고 할당 | `ALLOCATE` | 가용→할당 (availableQty 감소) |
| 할당 해제 | `DEALLOCATE` | 할당→가용 (availableQty 증가) |
| 재고 실사 | `CYCLE_COUNT` | 실사 차이 반영 |
| 불량 전환 | `STATUS_CHANGE` | 상태 변경 시 |
| 반품 입고 | `RETURN_INBOUND` | 반품 재고 증가 |

#### 규칙 3: 동시성 제어 필수
- 재고 변경 작업은 반드시 **락(Lock)** 사용
- 단일 트랜잭션 내에서 재고 변경 + 이력 기록 **원자적 처리**

---

## 2. 기술 스택

### 2.1 필수 기술 스택 (변경 불가)
| 구분 | 기술 | 버전 |
|------|------|------|
| Language | **Kotlin** | 1.9+ |
| Framework | **Spring Boot** | 3.2+ |
| ORM | **Spring Data JPA** | - |
| Database | **H2 Database** | - |
| Build Tool | **Gradle** | 8.x (Kotlin DSL) |

### 2.2 추가 권장 스택
| 구분 | 기술 | 용도 |
|------|------|------|
| Validation | Jakarta Validation | 요청 검증 |
| API Docs | **Redoc** | API 문서화 (OpenAPI 기반) |
| Testing | JUnit 5, Mockito, MockMvc | 테스트 |
| Event | Spring Events | 도메인 이벤트 |

### 2.3 참조 문서
> ⚠️ **개발 전 반드시 아래 문서를 참조하여 규칙 준수**

| 문서 | 경로 | 내용 |
|------|------|------|
| **코딩 규칙** | `opencode/coding-rules.md` | Immutable 객체, 일급 컬렉션, 패키지 의존성, DB 규칙, DDD 레이어 구조 |
| **테스트 가이드** | `opencode/testing.md` | 테스트 피라미드, 커버리지 목표(70%), E2E 테스트 필수 |

### 2.4 build.gradle.kts 기본 구성
```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.h2database:h2")
    
    // Redoc (OpenAPI 문서화)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}
```

---

## 3. 아키텍처

### 3.1 Hexagonal Architecture (Ports & Adapters)

> **핵심**: 도메인 로직은 외부 인프라(HTTP, DB, Message Queue)에 **절대 의존하지 않음**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Adapters (Infrastructure)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │  REST API   │  │  Event     │  │  Database   │  │  External  │ │
│  │  Controller │  │  Listener  │  │  Repository │  │  System    │ │
│  └──────┬──────┘  └──────┬─────┘  └──────┬──────┘  └─────┬──────┘ │
│         │                │               │                │        │
├─────────┼────────────────┼───────────────┼────────────────┼────────┤
│         │     Ports (Interfaces)         │                │        │
│  ┌──────▼──────┐  ┌──────▼─────┐  ┌──────▼──────┐  ┌─────▼──────┐ │
│  │  Inbound    │  │  Inbound   │  │  Outbound   │  │  Outbound  │ │
│  │  (Command)  │  │  (Event)   │  │  (Persist)  │  │  (External)│ │
│  └──────┬──────┘  └──────┬─────┘  └──────┬──────┘  └─────┬──────┘ │
│         │                │               │                │        │
├─────────┴────────────────┴───────────────┴────────────────┴────────┤
│                      Application Layer                              │
│  ┌────────────────────────┐  ┌────────────────────────────────┐    │
│  │    Command Handlers    │  │       Query Handlers           │    │
│  │    (Use Cases)         │  │       (Read Models)            │    │
│  └───────────┬────────────┘  └────────────────────────────────┘    │
│              │                                                      │
├──────────────┼──────────────────────────────────────────────────────┤
│              │           Domain Layer                               │
│  ┌───────────▼────────────────────────────────────────────────┐    │
│  │  Entities │ Value Objects │ Domain Services │ Domain Events │    │
│  └────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 디렉토리 구조
```
src/main/kotlin/com/wms/
├── domain/                          # 도메인 계층 (순수 Kotlin, 프레임워크 무관)
│   ├── inventory/
│   │   ├── model/
│   │   │   ├── Inventory.kt         # Entity
│   │   │   ├── InventoryHistory.kt  # Entity
│   │   │   ├── InventoryStatus.kt   # Value Object (Sealed Class)
│   │   │   └── InventoryList.kt     # 일급 컬렉션
│   │   ├── event/
│   │   │   ├── InventoryCreated.kt
│   │   │   └── InventoryChanged.kt
│   │   ├── service/
│   │   │   └── InventoryDomainService.kt
│   │   └── repository/
│   │       └── InventoryRepository.kt  # Port (Interface)
│   ├── inbound/
│   ├── outbound/
│   └── common/
│       ├── event/
│       │   └── DomainEvent.kt
│       └── model/
│           └── BaseEntity.kt
│
├── application/                      # 애플리케이션 계층 (Use Cases)
│   ├── inventory/
│   │   ├── command/                  # CQRS - Command
│   │   │   ├── CreateInventoryCommand.kt
│   │   │   ├── AdjustInventoryCommand.kt
│   │   │   └── CreateInventoryCommandHandler.kt
│   │   └── query/                    # CQRS - Query
│   │       ├── GetInventoryQuery.kt
│   │       ├── InventorySearchCriteria.kt
│   │       └── GetInventoryQueryHandler.kt
│   └── common/
│       └── PageRequest.kt
│
├── infrastructure/                   # 인프라 계층 (Adapters)
│   ├── persistence/
│   │   ├── entity/                   # JPA Entity (DB 매핑용)
│   │   │   └── InventoryJpaEntity.kt
│   │   ├── repository/
│   │   │   └── InventoryJpaRepository.kt
│   │   ├── mapper/                   # Domain ↔ JPA Entity 변환
│   │   │   └── InventoryMapper.kt
│   │   └── adapter/
│   │       └── InventoryRepositoryAdapter.kt  # Port 구현
│   ├── web/
│   │   ├── rest/
│   │   │   ├── InventoryCommandController.kt
│   │   │   └── InventoryQueryController.kt
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   │           └── InventoryResponse.kt  # fromDomain() 패턴 사용
│   └── event/
│       └── SpringEventPublisher.kt
│
└── config/
    └── JpaConfig.kt
```

### 3.3 일급 컬렉션 (First-Class Collection)

> ⚠️ **coding-rules.md 참조** - Map 사용 금지, 일급 컬렉션으로 비즈니스 로직 캡슐화

```kotlin
// ❌ 일반 List 사용 금지 - 비즈니스 로직이 흩어짐
fun processInventories(inventories: List<Inventory>) {
    val totalQty = inventories.sumOf { it.quantity }
    val availableQty = inventories.sumOf { it.availableQty }
    // 검증 로직이 여기저기 분산됨
}

// ✅ 일급 컬렉션 사용 - 비즈니스 로직 캡슐화
class InventoryList private constructor(
    private val values: List<Inventory>
) {
    init {
        require(values.isNotEmpty()) { "재고 목록은 비어있을 수 없습니다" }
    }
    
    companion object {
        fun of(inventories: List<Inventory>): InventoryList {
            return InventoryList(inventories.toList())
        }
        
        fun empty(): InventoryList? = null  // 빈 컬렉션은 null로 표현
    }
    
    // 비즈니스 로직을 컬렉션이 직접 소유
    val totalQuantity: Int
        get() = values.sumOf { it.quantity }
    
    val totalAvailableQuantity: Int
        get() = values.sumOf { it.availableQty }
    
    val totalAllocatedQuantity: Int
        get() = values.sumOf { it.allocatedQty }
    
    fun canAllocate(requiredQty: Int): Boolean =
        totalAvailableQuantity >= requiredQty
    
    fun findByLocation(locationId: Long): Inventory? =
        values.find { it.locationId == locationId }
    
    fun filterByStatus(status: InventoryStatus): InventoryList? {
        val filtered = values.filter { it.status == status }
        return if (filtered.isNotEmpty()) InventoryList(filtered) else null
    }
    
    fun sortByCreatedAtAsc(): InventoryList =
        InventoryList(values.sortedBy { it.createdAt })
    
    fun toList(): List<Inventory> = values.toList()
    
    fun size(): Int = values.size
    
    fun forEach(action: (Inventory) -> Unit) = values.forEach(action)
}

// 입고 품목 일급 컬렉션
class InboundItemList private constructor(
    private val values: List<InboundItem>
) {
    init {
        require(values.isNotEmpty()) { "입고 품목은 최소 1개 이상이어야 합니다" }
    }
    
    companion object {
        fun of(items: List<InboundItem>): InboundItemList = InboundItemList(items)
    }
    
    val totalExpectedQuantity: Int
        get() = values.sumOf { it.expectedQty }
    
    val totalAcceptedQuantity: Int
        get() = values.sumOf { it.acceptedQty }
    
    val totalRejectedQuantity: Int
        get() = values.sumOf { it.rejectedQty }
    
    fun isFullyInspected(): Boolean =
        values.all { it.isInspected }
    
    fun getUninspectedItems(): List<InboundItem> =
        values.filter { !it.isInspected }
    
    fun toList(): List<InboundItem> = values.toList()
}
```

### 3.4 DTO 변환 패턴 (from 패턴)

> ⚠️ **coding-rules.md 참조** - 패키지 의존성 방향 유지를 위해 Response에서 `fromDomain()` 패턴 사용

```kotlin
// ❌ Domain에서 Response로 변환 (디펜던시 위반)
// domain 패키지가 presentation 패키지를 참조하게 됨
class Inventory {
    fun toResponse(): InventoryResponse {  // 금지!
        return InventoryResponse(...)
    }
}

// ✅ Response에서 Domain을 받아 변환 (from 패턴)
// presentation 패키지가 domain 패키지를 참조 (올바른 방향)
data class InventoryResponse(
    val id: Long,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val locationId: Long,
    val locationCode: String,
    val quantity: Int,
    val availableQty: Int,
    val allocatedQty: Int,
    val status: String,
    val statusDisplayName: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun fromDomain(domain: Inventory): InventoryResponse {
            return InventoryResponse(
                id = domain.id,
                itemId = domain.itemId,
                itemCode = domain.item.itemCode,
                itemName = domain.item.itemName,
                locationId = domain.locationId,
                locationCode = domain.location.locationCode,
                quantity = domain.quantity,
                availableQty = domain.availableQty,
                allocatedQty = domain.allocatedQty,
                status = domain.status.code,
                statusDisplayName = domain.status.displayName,
                createdAt = domain.createdAt
            )
        }
        
        fun fromDomainList(domains: List<Inventory>): List<InventoryResponse> {
            return domains.map { fromDomain(it) }
        }
        
        // 일급 컬렉션에서 변환
        fun fromInventoryList(inventoryList: InventoryList): List<InventoryResponse> {
            return inventoryList.toList().map { fromDomain(it) }
        }
    }
}

// Request에서 Command로 변환 (toCommand 패턴)
data class AllocateInventoryRequest(
    @field:NotNull
    val outboundOrderId: Long,
    
    @field:NotNull
    val itemId: Long,
    
    @field:Min(1)
    val quantity: Int,
    
    val allocationStrategy: String = "FIFO"
) {
    fun toCommand(): AllocateInventoryCommand {
        return AllocateInventoryCommand(
            outboundOrderId = outboundOrderId,
            itemId = itemId,
            requiredQty = quantity,
            strategy = AllocationStrategy.fromCode(allocationStrategy)
        )
    }
}
```

### 3.5 CQRS (Command Query Responsibility Segregation)

#### Command (쓰기 작업)
```kotlin
// Command 정의
data class AdjustInventoryCommand(
    val inventoryId: Long,
    val adjustmentType: String,
    val quantity: Int,
    val reason: String,
    val adjustedBy: String
)

// Command Handler
@Service
class AdjustInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun handle(command: AdjustInventoryCommand): Long {
        val inventory = inventoryRepository.findByIdWithLock(command.inventoryId)
            ?: throw InventoryNotFoundException(command.inventoryId)
        
        // 도메인 로직 실행 (이력 자동 생성)
        inventory.adjust(
            type = command.adjustmentType,
            qty = command.quantity,
            reason = command.reason,
            by = command.adjustedBy
        )
        
        inventoryRepository.save(inventory)
        
        // 도메인 이벤트 발행
        inventory.domainEvents.forEach { eventPublisher.publish(it) }
        inventory.clearDomainEvents()
        
        return inventory.id
    }
}
```

#### Query (읽기 작업) - 항상 페이징/정렬 포함
```kotlin
// Query 정의 - 페이징/정렬 필수
data class InventorySearchCriteria(
    val itemId: Long? = null,
    val warehouseId: Long? = null,
    val zoneId: Long? = null,
    val locationId: Long? = null,
    val status: String? = null,
    
    // 페이징 (필수)
    val page: Int = 0,
    val size: Int = 20,
    
    // 정렬 (필수)
    val sortBy: String = "createdAt",
    val sortDirection: SortDirection = SortDirection.DESC
)

enum class SortDirection { ASC, DESC }

// Query Response (페이징 포함)
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

// Query Handler
@Service
@Transactional(readOnly = true)
class GetInventoryQueryHandler(
    private val inventoryQueryRepository: InventoryQueryRepository
) {
    fun handle(criteria: InventorySearchCriteria): PagedResponse<InventoryDto> {
        return inventoryQueryRepository.search(criteria)
    }
}
```

#### API 엔드포인트 분리
```kotlin
// Command Controller
@RestController
@RequestMapping("/api/v1/inventory/commands")
class InventoryCommandController(
    private val adjustHandler: AdjustInventoryCommandHandler
) {
    @PostMapping("/adjust")
    fun adjust(@RequestBody request: AdjustInventoryRequest): ResponseEntity<CommandResult> {
        val id = adjustHandler.handle(request.toCommand())
        return ResponseEntity.ok(CommandResult(id))
    }
}

// Query Controller  
@RestController
@RequestMapping("/api/v1/inventory/queries")
class InventoryQueryController(
    private val getInventoryHandler: GetInventoryQueryHandler
) {
    @GetMapping
    fun search(
        @RequestParam itemId: Long?,
        @RequestParam warehouseId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortDirection: SortDirection
    ): ResponseEntity<PagedResponse<InventoryDto>> {
        val criteria = InventorySearchCriteria(
            itemId = itemId,
            warehouseId = warehouseId,
            page = page,
            size = size,
            sortBy = sortBy,
            sortDirection = sortDirection
        )
        return ResponseEntity.ok(getInventoryHandler.handle(criteria))
    }
}
```

---

### 3.6 동시성 제어 (Concurrency Control)

> **재고 시스템의 핵심**: 여러 작업자가 동시에 같은 SKU를 할당할 때 경합 해결

#### 3.4.1 락 전략

| 상황 | 락 타입 | 이유 |
|------|--------|------|
| 재고 할당 | **Pessimistic Lock** | 경합 빈번, 즉시 일관성 필요 |
| 재고 조정 | **Pessimistic Lock** | 정확한 수량 보장 필수 |
| 기준정보 수정 | **Optimistic Lock** | 경합 드묾, 성능 우선 |
| 일반 조회 | Lock 없음 | 읽기 전용 |

#### 3.4.2 Pessimistic Lock 구현 (재고 할당)
```kotlin
// Repository Interface (Port)
interface InventoryRepository {
    fun findByIdWithLock(id: Long): Inventory?
    fun findByItemIdAndLocationIdWithLock(itemId: Long, locationId: Long): Inventory?
    fun findAllocatableInventoriesWithLock(itemId: Long, requiredQty: Int): List<Inventory>
}

// JPA Repository (Adapter)
interface InventoryJpaRepository : JpaRepository<InventoryJpaEntity, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.id = :id")
    fun findByIdWithLock(@Param("id") id: Long): InventoryJpaEntity?
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i FROM InventoryJpaEntity i 
        WHERE i.itemId = :itemId 
        AND i.availableQty > 0
        ORDER BY i.createdAt ASC
    """)
    fun findAllocatableWithLock(@Param("itemId") itemId: Long): List<InventoryJpaEntity>
}
```

#### 3.4.3 재고 할당 시 경합 처리 예시
```kotlin
@Service
class AllocateInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository,
    private val allocationRepository: AllocationRepository
) {
    @Transactional
    fun handle(command: AllocateInventoryCommand): AllocationResult {
        // 1. 할당 가능한 재고를 PESSIMISTIC_WRITE 락으로 조회
        val inventories = inventoryRepository
            .findAllocatableInventoriesWithLock(command.itemId, command.requiredQty)
        
        if (inventories.sumOf { it.availableQty } < command.requiredQty) {
            throw InsufficientInventoryException(command.itemId, command.requiredQty)
        }
        
        // 2. FIFO/FEFO 전략에 따라 할당
        var remainingQty = command.requiredQty
        val allocations = mutableListOf<Allocation>()
        
        for (inventory in inventories) {
            if (remainingQty <= 0) break
            
            val allocateQty = minOf(inventory.availableQty, remainingQty)
            
            // 3. 재고 할당 (도메인 로직 - 이력 자동 기록)
            inventory.allocate(allocateQty, command.outboundOrderId)
            
            allocations.add(Allocation(
                inventoryId = inventory.id,
                outboundOrderId = command.outboundOrderId,
                quantity = allocateQty
            ))
            
            remainingQty -= allocateQty
        }
        
        // 4. 저장
        inventories.forEach { inventoryRepository.save(it) }
        allocations.forEach { allocationRepository.save(it) }
        
        return AllocationResult(allocations)
    }
}
```

#### 3.4.4 Optimistic Lock (기준정보)
```kotlin
@Entity
class Item(
    @Id @GeneratedValue
    val id: Long = 0,
    
    @Version  // Optimistic Lock
    val version: Long = 0,
    
    var itemCode: String,
    var itemName: String
    // ...
)

// 동시 수정 시 OptimisticLockException 발생 → 재시도 또는 사용자 알림
```

#### 3.4.5 데드락 방지 규칙
```
1. 락 순서 일관성: 항상 ID 오름차순으로 락 획득
2. 락 범위 최소화: 필요한 레코드만 락
3. 트랜잭션 시간 최소화: 락 구간 내 외부 호출 금지
4. 타임아웃 설정: spring.jpa.properties.jakarta.persistence.lock.timeout=3000
```

---

### 3.7 도메인 이벤트

> 외부 시스템 연계(HTTP, Event Driven)를 위해 도메인 로직과 인프라 분리

#### 3.5.1 도메인 이벤트 정의
```kotlin
// 공통 이벤트 인터페이스
interface DomainEvent {
    val eventId: String
    val occurredAt: LocalDateTime
    val aggregateId: Long
    val aggregateType: String
}

// 재고 변경 이벤트
data class InventoryChangedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val itemId: Long,
    val locationId: Long,
    val changeType: String,  // INBOUND, OUTBOUND, ADJUSTMENT, MOVEMENT
    val beforeQty: Int,
    val afterQty: Int,
    val changeQty: Int,
    val reason: String?
) : DomainEvent
```

#### 3.5.2 Aggregate에서 이벤트 발행
```kotlin
abstract class AggregateRoot {
    @Transient
    private val _domainEvents = mutableListOf<DomainEvent>()
    val domainEvents: List<DomainEvent> get() = _domainEvents.toList()
    
    protected fun registerEvent(event: DomainEvent) {
        _domainEvents.add(event)
    }
    
    fun clearDomainEvents() {
        _domainEvents.clear()
    }
}

@Entity
class Inventory : AggregateRoot() {
    // ...
    
    fun adjust(type: String, qty: Int, reason: String, by: String) {
        val beforeQty = this.quantity
        
        when (type) {
            "INCREASE" -> this.quantity += qty
            "DECREASE" -> {
                require(this.availableQty >= qty) { "가용재고 부족" }
                this.quantity -= qty
            }
        }
        
        // 이력 생성 (불변 규칙 준수)
        this.histories.add(InventoryHistory(
            inventory = this,
            transactionType = "ADJUSTMENT_$type",
            changeQuantity = if (type == "INCREASE") qty else -qty,
            beforeQuantity = beforeQty,
            afterQuantity = this.quantity,
            reason = reason,
            createdBy = by
        ))
        
        // 도메인 이벤트 등록
        registerEvent(InventoryChangedEvent(
            aggregateId = this.id,
            itemId = this.itemId,
            locationId = this.locationId,
            changeType = "ADJUSTMENT",
            beforeQty = beforeQty,
            afterQty = this.quantity,
            changeQty = qty,
            reason = reason
        ))
    }
}
```

#### 3.5.3 이벤트 발행 (인프라 계층)
```kotlin
// Port (Application 계층)
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
    fun publishAll(events: List<DomainEvent>)
}

// Adapter - Spring Events (동기)
@Component
class SpringEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
    
    override fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}

// Adapter - Kafka (비동기) - 필요시 교체 가능
@Component
@Profile("kafka")
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, DomainEvent>
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        kafkaTemplate.send("domain-events", event.aggregateId.toString(), event)
    }
}
```

#### 3.5.4 이벤트 핸들러 (연계 시스템 알림)
```kotlin
@Component
class InventoryEventHandler {
    
    @EventListener
    fun handleInventoryChanged(event: InventoryChangedEvent) {
        // 외부 시스템 연계 (ERP, TMS 등)
        // HTTP 또는 Message Queue로 전송
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleAfterCommit(event: InventoryChangedEvent) {
        // 트랜잭션 커밋 후 실행이 필요한 작업
    }
}
```

---

## 4. 상태 관리 (Sealed Class)

> **원칙**: 상태는 Sealed Class로 타입 안전하게 관리하며, 상태 전이 규칙은 상태 객체 내부에 정의

### 4.1 Sealed Class 기반 상태 설계

#### 왜 Sealed Class인가?
| 방식 | 타입 안전성 | 전이 규칙 | 컴파일 검증 | 확장성 |
|------|------------|----------|------------|--------|
| String | ❌ | ❌ 분산 | ❌ | ❌ 무한정 |
| Enum | ⭕ | ❌ 분산 | ⭕ | ⭕ |
| **Sealed Class** | ⭕ | ⭕ 내장 | ⭕ | ⭕ |
| 코드 테이블 (DB) | ❌ | ❌ 분산 | ❌ | ⭕ 위험 |

#### 4.1.1 InventoryStatus (재고 상태)
```kotlin
sealed class InventoryStatus(
    val code: String,
    val displayName: String
) {
    // 상태 전이 규칙을 상태 객체가 스스로 알고 있음
    abstract fun allowedTransitions(): Set<KClass<out InventoryStatus>>
    
    fun canTransitionTo(target: InventoryStatus): Boolean =
        target::class in allowedTransitions()
    
    // 비즈니스 규칙도 상태가 스스로 판단
    abstract fun canAllocate(): Boolean
    abstract fun canPick(): Boolean
    abstract fun canAdjust(): Boolean
    
    // === 구체적인 상태들 ===
    
    data object Available : InventoryStatus("AVAILABLE", "가용") {
        override fun allowedTransitions() = setOf(
            Allocated::class,
            OnHold::class,
            Damaged::class
        )
        override fun canAllocate() = true
        override fun canPick() = false
        override fun canAdjust() = true
    }
    
    data object Allocated : InventoryStatus("ALLOCATED", "할당됨") {
        override fun allowedTransitions() = setOf(
            Available::class,  // 할당 해제
            Picked::class      // 피킹 완료
        )
        override fun canAllocate() = false
        override fun canPick() = true
        override fun canAdjust() = false
    }
    
    data object FullyAllocated : InventoryStatus("FULLY_ALLOCATED", "전량할당") {
        override fun allowedTransitions() = setOf(
            Allocated::class,  // 부분 해제
            Available::class   // 전량 해제
        )
        override fun canAllocate() = false
        override fun canPick() = true
        override fun canAdjust() = false
    }
    
    data object Picked : InventoryStatus("PICKED", "피킹완료") {
        override fun allowedTransitions() = setOf(
            Shipped::class
        )
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canAdjust() = false
    }
    
    data object Shipped : InventoryStatus("SHIPPED", "출고완료") {
        override fun allowedTransitions() = emptySet<KClass<out InventoryStatus>>()
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canAdjust() = false
    }
    
    data object OnHold : InventoryStatus("ON_HOLD", "보류") {
        override fun allowedTransitions() = setOf(
            Available::class,
            Damaged::class
        )
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canAdjust() = true
    }
    
    data object Damaged : InventoryStatus("DAMAGED", "불량") {
        override fun allowedTransitions() = setOf(
            Available::class,  // 복원
            Disposed::class    // 폐기
        )
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canAdjust() = true
    }
    
    data object Disposed : InventoryStatus("DISPOSED", "폐기") {
        override fun allowedTransitions() = emptySet<KClass<out InventoryStatus>>()
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canAdjust() = false
    }
    
    companion object {
        fun fromCode(code: String): InventoryStatus = when (code) {
            "AVAILABLE" -> Available
            "ALLOCATED" -> Allocated
            "FULLY_ALLOCATED" -> FullyAllocated
            "PICKED" -> Picked
            "SHIPPED" -> Shipped
            "ON_HOLD" -> OnHold
            "DAMAGED" -> Damaged
            "DISPOSED" -> Disposed
            else -> throw IllegalArgumentException("Unknown InventoryStatus: $code")
        }
        
        fun all(): List<InventoryStatus> = listOf(
            Available, Allocated, FullyAllocated, Picked, 
            Shipped, OnHold, Damaged, Disposed
        )
    }
}
```

#### 4.1.2 LocationStatus (로케이션 상태)
```kotlin
sealed class LocationStatus(
    val code: String,
    val displayName: String
) {
    abstract fun allowedTransitions(): Set<KClass<out LocationStatus>>
    abstract fun canAcceptInventory(): Boolean
    abstract fun canLock(): Boolean
    
    fun canTransitionTo(target: LocationStatus): Boolean =
        target::class in allowedTransitions()
    
    data object Empty : LocationStatus("EMPTY", "빈 로케이션") {
        override fun allowedTransitions() = setOf(
            Occupied::class,
            Locked::class,
            Maintenance::class
        )
        override fun canAcceptInventory() = true
        override fun canLock() = true
    }
    
    data object Occupied : LocationStatus("OCCUPIED", "사용중") {
        override fun allowedTransitions() = setOf(
            Empty::class,      // 재고 모두 출고
            Locked::class
        )
        override fun canAcceptInventory() = true  // 추가 적치 가능
        override fun canLock() = true
    }
    
    data object Locked : LocationStatus("LOCKED", "잠금") {
        override fun allowedTransitions() = setOf(
            Empty::class,
            Occupied::class
        )
        override fun canAcceptInventory() = false
        override fun canLock() = false
    }
    
    data object Maintenance : LocationStatus("MAINTENANCE", "점검중") {
        override fun allowedTransitions() = setOf(
            Empty::class
        )
        override fun canAcceptInventory() = false
        override fun canLock() = false
    }
    
    companion object {
        fun fromCode(code: String): LocationStatus = when (code) {
            "EMPTY" -> Empty
            "OCCUPIED" -> Occupied
            "LOCKED" -> Locked
            "MAINTENANCE" -> Maintenance
            else -> throw IllegalArgumentException("Unknown LocationStatus: $code")
        }
    }
}
```

#### 4.1.3 주문/프로세스 상태들
```kotlin
// 입고 상태
sealed class InboundStatus(val code: String, val displayName: String) {
    abstract fun allowedTransitions(): Set<KClass<out InboundStatus>>
    
    fun canTransitionTo(target: InboundStatus): Boolean =
        target::class in allowedTransitions()
    
    data object Expected : InboundStatus("EXPECTED", "입고예정") {
        override fun allowedTransitions() = setOf(Inspecting::class, Cancelled::class)
    }
    data object Inspecting : InboundStatus("INSPECTING", "검수중") {
        override fun allowedTransitions() = setOf(Inspected::class, Rejected::class)
    }
    data object Inspected : InboundStatus("INSPECTED", "검수완료") {
        override fun allowedTransitions() = setOf(PutawayInProgress::class)
    }
    data object PutawayInProgress : InboundStatus("PUTAWAY_IN_PROGRESS", "적치중") {
        override fun allowedTransitions() = setOf(Completed::class)
    }
    data object Completed : InboundStatus("COMPLETED", "입고완료") {
        override fun allowedTransitions() = emptySet<KClass<out InboundStatus>>()
    }
    data object Rejected : InboundStatus("REJECTED", "반려") {
        override fun allowedTransitions() = setOf(Returned::class, Disposed::class)
    }
    data object Returned : InboundStatus("RETURNED", "반송") {
        override fun allowedTransitions() = emptySet<KClass<out InboundStatus>>()
    }
    data object Disposed : InboundStatus("DISPOSED", "폐기") {
        override fun allowedTransitions() = emptySet<KClass<out InboundStatus>>()
    }
    data object Cancelled : InboundStatus("CANCELLED", "취소") {
        override fun allowedTransitions() = emptySet<KClass<out InboundStatus>>()
    }
    
    companion object {
        fun fromCode(code: String): InboundStatus = when (code) {
            "EXPECTED" -> Expected
            "INSPECTING" -> Inspecting
            "INSPECTED" -> Inspected
            "PUTAWAY_IN_PROGRESS" -> PutawayInProgress
            "COMPLETED" -> Completed
            "REJECTED" -> Rejected
            "RETURNED" -> Returned
            "DISPOSED" -> Disposed
            "CANCELLED" -> Cancelled
            else -> throw IllegalArgumentException("Unknown InboundStatus: $code")
        }
        
        fun all(): List<InboundStatus> = listOf(
            Expected, Inspecting, Inspected, PutawayInProgress,
            Completed, Rejected, Returned, Disposed, Cancelled
        )
    }
}

// 출고 상태
sealed class OutboundStatus(val code: String, val displayName: String) {
    abstract fun allowedTransitions(): Set<KClass<out OutboundStatus>>
    
    fun canTransitionTo(target: OutboundStatus): Boolean =
        target::class in allowedTransitions()
    
    // 비즈니스 규칙
    abstract fun canAllocate(): Boolean
    abstract fun canPick(): Boolean
    abstract fun canShip(): Boolean
    abstract fun canCancel(): Boolean
    
    data object Pending : OutboundStatus("PENDING", "대기") {
        override fun allowedTransitions() = setOf(Allocated::class, Cancelled::class)
        override fun canAllocate() = true
        override fun canPick() = false
        override fun canShip() = false
        override fun canCancel() = true
    }
    data object Allocated : OutboundStatus("ALLOCATED", "할당완료") {
        override fun allowedTransitions() = setOf(Picking::class, Deallocated::class)
        override fun canAllocate() = false
        override fun canPick() = true
        override fun canShip() = false
        override fun canCancel() = false
    }
    data object Deallocated : OutboundStatus("DEALLOCATED", "할당해제") {
        override fun allowedTransitions() = setOf(Allocated::class, Cancelled::class)
        override fun canAllocate() = true
        override fun canPick() = false
        override fun canShip() = false
        override fun canCancel() = true
    }
    data object Picking : OutboundStatus("PICKING", "피킹중") {
        override fun allowedTransitions() = setOf(Picked::class, PickShortage::class)
        override fun canAllocate() = false
        override fun canPick() = true
        override fun canShip() = false
        override fun canCancel() = false
    }
    data object Picked : OutboundStatus("PICKED", "피킹완료") {
        override fun allowedTransitions() = setOf(Packed::class)
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canShip() = false
        override fun canCancel() = false
    }
    data object PickShortage : OutboundStatus("PICK_SHORTAGE", "피킹부족") {
        override fun allowedTransitions() = setOf(Picking::class, Cancelled::class)
        override fun canAllocate() = false
        override fun canPick() = true
        override fun canShip() = false
        override fun canCancel() = true
    }
    data object Packed : OutboundStatus("PACKED", "패킹완료") {
        override fun allowedTransitions() = setOf(Shipped::class)
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canShip() = true
        override fun canCancel() = false
    }
    data object Shipped : OutboundStatus("SHIPPED", "출고완료") {
        override fun allowedTransitions() = emptySet<KClass<out OutboundStatus>>()
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canShip() = false
        override fun canCancel() = false
    }
    data object Cancelled : OutboundStatus("CANCELLED", "취소") {
        override fun allowedTransitions() = emptySet<KClass<out OutboundStatus>>()
        override fun canAllocate() = false
        override fun canPick() = false
        override fun canShip() = false
        override fun canCancel() = false
    }
    
    companion object {
        fun fromCode(code: String): OutboundStatus = when (code) {
            "PENDING" -> Pending
            "ALLOCATED" -> Allocated
            "DEALLOCATED" -> Deallocated
            "PICKING" -> Picking
            "PICKED" -> Picked
            "PICK_SHORTAGE" -> PickShortage
            "PACKED" -> Packed
            "SHIPPED" -> Shipped
            "CANCELLED" -> Cancelled
            else -> throw IllegalArgumentException("Unknown OutboundStatus: $code")
        }
        
        fun all(): List<OutboundStatus> = listOf(
            Pending, Allocated, Deallocated, Picking, Picked,
            PickShortage, Packed, Shipped, Cancelled
        )
    }
}
```

### 4.2 JPA AttributeConverter

```kotlin
@Converter(autoApply = true)
class InventoryStatusConverter : AttributeConverter<InventoryStatus, String> {
    override fun convertToDatabaseColumn(attribute: InventoryStatus): String =
        attribute.code
    
    override fun convertToEntityAttribute(dbData: String): InventoryStatus =
        InventoryStatus.fromCode(dbData)
}

@Converter(autoApply = true)
class LocationStatusConverter : AttributeConverter<LocationStatus, String> {
    override fun convertToDatabaseColumn(attribute: LocationStatus): String =
        attribute.code
    
    override fun convertToEntityAttribute(dbData: String): LocationStatus =
        LocationStatus.fromCode(dbData)
}

// Entity에서 사용
@Entity
class Inventory {
    @Convert(converter = InventoryStatusConverter::class)
    @Column(name = "status", nullable = false)
    private var _status: InventoryStatus = InventoryStatus.Available
}
```

### 4.3 상태 전이 규칙 적용

```kotlin
@Entity
class Inventory : AggregateRoot() {
    @Convert(converter = InventoryStatusConverter::class)
    private var _status: InventoryStatus = InventoryStatus.Available
    
    val status: InventoryStatus get() = _status
    
    // 상태 전이는 반드시 이 메서드를 통해서만
    fun transitionTo(newStatus: InventoryStatus, reason: String) {
        require(_status.canTransitionTo(newStatus)) {
            "상태 전이 불가: ${_status.displayName} → ${newStatus.displayName}"
        }
        
        val previousStatus = _status
        _status = newStatus
        
        recordHistory(
            transactionType = TransactionType.STATUS_CHANGE,
            changeQty = 0,
            reason = "[$previousStatus → $newStatus] $reason"
        )
        
        registerEvent(InventoryStatusChangedEvent(id, previousStatus, newStatus, reason))
    }
}
```

---

## 5. 로그 관리 체계

> **원칙**: 로그는 문제 추적과 시스템 모니터링의 핵심 도구. 구조화된 로깅으로 유지보수성 극대화

### 5.1 로그 레벨 정책

| Level | 용도 | 예시 |
|-------|------|------|
| **ERROR** | 즉시 대응 필요한 오류 | 시스템 장애, 데이터 정합성 오류 |
| **WARN** | 잠재적 문제, 모니터링 필요 | 재고 부족 경고, 느린 쿼리 |
| **INFO** | 비즈니스 이벤트, 상태 변경 | 재고 할당 완료, 출고 확정 |
| **DEBUG** | 개발/디버깅용 상세 정보 | 메서드 진입/종료, 변수 값 |
| **TRACE** | 가장 상세한 추적 정보 | 루프 내부, SQL 바인딩 값 |

### 5.2 쿼리 로깅 (필수)

> ⚠️ **모든 쿼리는 언제, 어떤 의도로 실행했는지 추적 가능해야 함**

#### 5.2.1 QueryContext (쿼리 컨텍스트)
```kotlin
data class QueryContext(
    val intent: String,          // 쿼리 의도 설명
    val correlationId: String,   // 요청 추적 ID
    val timestamp: Instant = Instant.now()
)

// ThreadLocal로 현재 컨텍스트 관리
object QueryContextHolder {
    private val contextHolder = ThreadLocal<QueryContext>()
    
    fun set(context: QueryContext) = contextHolder.set(context)
    fun get(): QueryContext? = contextHolder.get()
    fun clear() = contextHolder.remove()
}
```

#### 5.2.2 쿼리 로깅 인터셉터
```kotlin
@Component
class QueryLoggingInterceptor : StatementInspector {
    private val logger = LoggerFactory.getLogger(QueryLoggingInterceptor::class.java)
    
    override fun inspect(sql: String): String {
        val context = QueryContextHolder.get()
        val startTime = System.currentTimeMillis()
        
        return sql.also {
            logger.info("""
                |[QUERY] 
                |  Intent: ${context?.intent ?: "UNKNOWN"}
                |  CorrelationId: ${context?.correlationId ?: "N/A"}
                |  SQL: ${formatSql(sql)}
            """.trimMargin())
        }
    }
    
    private fun formatSql(sql: String): String =
        sql.replace(Regex("\\s+"), " ").trim()
}
```

#### 5.2.3 Repository에서 쿼리 컨텍스트 사용
```kotlin
interface InventoryQueryRepository {
    fun findByItemId(itemId: Long, context: QueryContext): List<Inventory>
    fun searchWithCriteria(criteria: InventorySearchCriteria, context: QueryContext): Page<Inventory>
}

@Repository
class InventoryQueryRepositoryImpl(
    private val jpaRepository: InventoryJpaRepository
) : InventoryQueryRepository {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    override fun findByItemId(itemId: Long, context: QueryContext): List<Inventory> {
        QueryContextHolder.set(context.copy(intent = "품목 ID로 재고 조회 (itemId=$itemId)"))
        
        return try {
            jpaRepository.findByItemId(itemId).map { it.toDomain() }
        } finally {
            QueryContextHolder.clear()
        }
    }
    
    override fun searchWithCriteria(
        criteria: InventorySearchCriteria, 
        context: QueryContext
    ): Page<Inventory> {
        QueryContextHolder.set(context.copy(
            intent = "재고 검색 - 필터: warehouse=${criteria.warehouseId}, status=${criteria.status}"
        ))
        
        return try {
            // 쿼리 실행
            jpaRepository.searchWithCriteria(criteria).map { it.toDomain() }
        } finally {
            QueryContextHolder.clear()
        }
    }
}
```

#### 5.2.4 Command Handler에서 컨텍스트 전달
```kotlin
@Service
class AllocateInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository
) {
    @Transactional
    fun handle(command: AllocateInventoryCommand, requestContext: RequestContext): AllocationResult {
        val queryContext = QueryContext(
            intent = "출고 할당을 위한 가용 재고 조회",
            correlationId = requestContext.correlationId
        )
        
        val inventories = inventoryRepository.findAllocatableWithLock(
            command.itemId, 
            queryContext
        )
        
        // ... 할당 로직
    }
}
```

### 5.3 애플리케이션 로깅

#### 5.3.1 구조화된 로그 포맷
```kotlin
// 로그 메시지 빌더
data class LogMessage(
    val event: String,                    // 이벤트 타입
    val message: String,                  // 설명
    val data: Map<String, Any> = emptyMap(),  // 추가 데이터
    val correlationId: String? = null
) {
    fun toStructured(): String = buildString {
        append("[${event}] $message")
        if (data.isNotEmpty()) {
            append(" | ")
            append(data.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        correlationId?.let { append(" | correlationId=$it") }
    }
}

// 사용 예시
logger.info(LogMessage(
    event = "INVENTORY_ALLOCATED",
    message = "재고 할당 완료",
    data = mapOf(
        "inventoryId" to inventory.id,
        "allocatedQty" to qty,
        "remainingQty" to inventory.availableQty,
        "outboundOrderId" to orderId
    ),
    correlationId = requestContext.correlationId
).toStructured())

// 출력: [INVENTORY_ALLOCATED] 재고 할당 완료 | inventoryId=123, allocatedQty=10, remainingQty=90, outboundOrderId=456 | correlationId=req-abc-123
```

#### 5.3.2 도메인 이벤트 로깅
```kotlin
@Component
class DomainEventLogger {
    private val logger = LoggerFactory.getLogger(DomainEventLogger::class.java)
    
    @EventListener
    fun logDomainEvent(event: DomainEvent) {
        logger.info(LogMessage(
            event = event::class.simpleName ?: "UNKNOWN_EVENT",
            message = "도메인 이벤트 발생",
            data = mapOf(
                "eventId" to event.eventId,
                "aggregateType" to event.aggregateType,
                "aggregateId" to event.aggregateId,
                "occurredAt" to event.occurredAt
            )
        ).toStructured())
    }
}
```

#### 5.3.3 성능 로깅 (느린 작업 감지)
```kotlin
@Aspect
@Component
class PerformanceLoggingAspect {
    private val logger = LoggerFactory.getLogger(PerformanceLoggingAspect::class.java)
    
    @Around("@annotation(LogExecutionTime)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        
        return try {
            joinPoint.proceed()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val methodName = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}"
            
            if (duration > 1000) {
                logger.warn(LogMessage(
                    event = "SLOW_EXECUTION",
                    message = "느린 작업 감지",
                    data = mapOf(
                        "method" to methodName,
                        "durationMs" to duration,
                        "threshold" to 1000
                    )
                ).toStructured())
            } else {
                logger.debug(LogMessage(
                    event = "EXECUTION_TIME",
                    message = "메서드 실행 완료",
                    data = mapOf("method" to methodName, "durationMs" to duration)
                ).toStructured())
            }
        }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogExecutionTime
```

### 5.4 로그 설정 (application.yml)

```yaml
logging:
  level:
    root: INFO
    com.wms: DEBUG
    com.wms.infrastructure.persistence: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # 바인딩 파라미터
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n"
  file:
    name: logs/wms-application.log
    max-size: 100MB
    max-history: 30
```

---

## 6. 예외 처리 (AOP)

> **원칙**: 예외 처리는 AOP로 중앙 집중 관리. 비즈니스 로직에서는 예외를 던지기만 함

### 6.1 예외 계층 구조

```kotlin
// 기본 비즈니스 예외
sealed class WmsException(
    val errorCode: String,
    override val message: String,
    val details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : RuntimeException(message, cause)

// === 도메인별 예외 ===

// 재고 관련
sealed class InventoryException(
    errorCode: String,
    message: String,
    details: Map<String, Any> = emptyMap()
) : WmsException(errorCode, message, details) {
    
    class NotFound(inventoryId: Long) : InventoryException(
        "INV_001",
        "재고를 찾을 수 없습니다",
        mapOf("inventoryId" to inventoryId)
    )
    
    class InsufficientQuantity(
        inventoryId: Long,
        requested: Int,
        available: Int
    ) : InventoryException(
        "INV_002",
        "가용 재고가 부족합니다",
        mapOf("inventoryId" to inventoryId, "requested" to requested, "available" to available)
    )
    
    class InvalidStatusTransition(
        inventoryId: Long,
        from: String,
        to: String
    ) : InventoryException(
        "INV_003",
        "유효하지 않은 상태 전이입니다",
        mapOf("inventoryId" to inventoryId, "from" to from, "to" to to)
    )
    
    class AlreadyAllocated(inventoryId: Long) : InventoryException(
        "INV_004",
        "이미 할당된 재고입니다",
        mapOf("inventoryId" to inventoryId)
    )
}

// 로케이션 관련
sealed class LocationException(
    errorCode: String,
    message: String,
    details: Map<String, Any> = emptyMap()
) : WmsException(errorCode, message, details) {
    
    class NotFound(locationId: Long) : LocationException(
        "LOC_001",
        "로케이션을 찾을 수 없습니다",
        mapOf("locationId" to locationId)
    )
    
    class NotAvailable(locationId: Long, status: String) : LocationException(
        "LOC_002",
        "사용 불가능한 로케이션입니다",
        mapOf("locationId" to locationId, "currentStatus" to status)
    )
    
    class CapacityExceeded(locationId: Long, maxCapacity: Int, requested: Int) : LocationException(
        "LOC_003",
        "로케이션 용량을 초과합니다",
        mapOf("locationId" to locationId, "maxCapacity" to maxCapacity, "requested" to requested)
    )
}

// 출고 관련
sealed class OutboundException(
    errorCode: String,
    message: String,
    details: Map<String, Any> = emptyMap()
) : WmsException(errorCode, message, details) {
    
    class NotFound(orderId: Long) : OutboundException(
        "OB_001",
        "출고 오더를 찾을 수 없습니다",
        mapOf("orderId" to orderId)
    )
    
    class AlreadyAllocated(orderId: Long) : OutboundException(
        "OB_002",
        "이미 할당된 출고 오더입니다",
        mapOf("orderId" to orderId)
    )
    
    class CannotCancel(orderId: Long, status: String) : OutboundException(
        "OB_003",
        "취소할 수 없는 상태입니다",
        mapOf("orderId" to orderId, "status" to status)
    )
}

// 입고 관련
sealed class InboundException(
    errorCode: String,
    message: String,
    details: Map<String, Any> = emptyMap()
) : WmsException(errorCode, message, details) {
    
    class NotFound(orderId: Long) : InboundException(
        "IB_001",
        "입고 오더를 찾을 수 없습니다",
        mapOf("orderId" to orderId)
    )
    
    class AlreadyInspected(orderId: Long) : InboundException(
        "IB_002",
        "이미 검수 완료된 입고 오더입니다",
        mapOf("orderId" to orderId)
    )
}

// 검증 관련
class ValidationException(
    message: String,
    val violations: List<FieldViolation>
) : WmsException(
    "VAL_001",
    message,
    mapOf("violations" to violations)
)

data class FieldViolation(
    val field: String,
    val message: String,
    val rejectedValue: Any?
)

// 동시성 관련
class ConcurrencyException(
    message: String,
    val resourceType: String,
    val resourceId: Long
) : WmsException(
    "CONC_001",
    message,
    mapOf("resourceType" to resourceType, "resourceId" to resourceId)
)
```

### 6.2 전역 예외 핸들러 (AOP)

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    // WMS 비즈니스 예외 처리
    @ExceptionHandler(WmsException::class)
    fun handleWmsException(
        ex: WmsException,
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<Nothing, ErrorBody>> {
        
        val correlationId = request.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString()
        
        logger.warn(LogMessage(
            event = "BUSINESS_EXCEPTION",
            message = ex.message,
            data = ex.details + mapOf("errorCode" to ex.errorCode),
            correlationId = correlationId
        ).toStructured())
        
        val statusCode = when (ex) {
            is InventoryException.NotFound,
            is LocationException.NotFound,
            is OutboundException.NotFound,
            is InboundException.NotFound -> HttpStatus.NOT_FOUND
            
            is InventoryException.InsufficientQuantity,
            is InventoryException.InvalidStatusTransition,
            is LocationException.NotAvailable,
            is OutboundException.CannotCancel -> HttpStatus.CONFLICT
            
            is ValidationException -> HttpStatus.BAD_REQUEST
            is ConcurrencyException -> HttpStatus.CONFLICT
            
            else -> HttpStatus.BAD_REQUEST
        }
        
        return ResponseEntity
            .status(statusCode)
            .headers(createResponseHeaders(correlationId))
            .body(HttpResponseSpec(
                statusCode = statusCode.value(),
                message = ex.message,
                request = HttpRequestSpec(
                    method = request.method,
                    path = request.requestURI,
                    timestamp = Instant.now()
                ),
                headers = mapOf("X-Correlation-ID" to listOf(correlationId)),
                errorBody = ErrorBody(
                    code = ex.errorCode,
                    message = ex.message,
                    details = ex.details
                )
            ))
    }
    
    // Validation 예외 처리 (Jakarta Validation)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<Nothing, ErrorBody>> {
        
        val correlationId = request.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString()
        
        val violations = ex.bindingResult.fieldErrors.map { error ->
            FieldViolation(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value",
                rejectedValue = error.rejectedValue
            )
        }
        
        logger.warn(LogMessage(
            event = "VALIDATION_EXCEPTION",
            message = "요청 데이터 검증 실패",
            data = mapOf("violations" to violations),
            correlationId = correlationId
        ).toStructured())
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(createResponseHeaders(correlationId))
            .body(HttpResponseSpec(
                statusCode = HttpStatus.BAD_REQUEST.value(),
                message = "요청 데이터 검증에 실패했습니다",
                request = HttpRequestSpec(
                    method = request.method,
                    path = request.requestURI,
                    timestamp = Instant.now()
                ),
                headers = mapOf("X-Correlation-ID" to listOf(correlationId)),
                errorBody = ErrorBody(
                    code = "VAL_001",
                    message = "Validation failed",
                    details = mapOf("violations" to violations)
                )
            ))
    }
    
    // Optimistic Lock 예외
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(
        ex: OptimisticLockingFailureException,
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<Nothing, ErrorBody>> {
        
        val correlationId = request.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString()
        
        logger.warn(LogMessage(
            event = "OPTIMISTIC_LOCK_EXCEPTION",
            message = "동시 수정 충돌 발생",
            correlationId = correlationId
        ).toStructured())
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .headers(createResponseHeaders(correlationId))
            .body(HttpResponseSpec(
                statusCode = HttpStatus.CONFLICT.value(),
                message = "다른 사용자가 동시에 수정했습니다. 다시 시도해주세요.",
                request = HttpRequestSpec(
                    method = request.method,
                    path = request.requestURI,
                    timestamp = Instant.now()
                ),
                headers = mapOf("X-Correlation-ID" to listOf(correlationId)),
                errorBody = ErrorBody(
                    code = "CONC_002",
                    message = "Optimistic lock conflict",
                    details = emptyMap()
                )
            ))
    }
    
    // 예상치 못한 예외 (시스템 오류)
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<Nothing, ErrorBody>> {
        
        val correlationId = request.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString()
        
        logger.error(LogMessage(
            event = "UNEXPECTED_EXCEPTION",
            message = "예상치 못한 시스템 오류",
            data = mapOf("exceptionType" to ex::class.simpleName),
            correlationId = correlationId
        ).toStructured(), ex)
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .headers(createResponseHeaders(correlationId))
            .body(HttpResponseSpec(
                statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message = "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                request = HttpRequestSpec(
                    method = request.method,
                    path = request.requestURI,
                    timestamp = Instant.now()
                ),
                headers = mapOf("X-Correlation-ID" to listOf(correlationId)),
                errorBody = ErrorBody(
                    code = "SYS_001",
                    message = "Internal server error",
                    details = mapOf("correlationId" to correlationId)
                )
            ))
    }
    
    private fun createResponseHeaders(correlationId: String): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Correlation-ID", correlationId)
        }
    }
}
```

### 6.3 도메인에서 예외 사용

```kotlin
@Entity
class Inventory : AggregateRoot() {
    
    fun allocate(qty: Int, orderId: Long): AllocationResult {
        // 예외는 도메인에서 던지고, 핸들링은 AOP가 처리
        if (availableQty < qty) {
            throw InventoryException.InsufficientQuantity(
                inventoryId = id,
                requested = qty,
                available = availableQty
            )
        }
        
        if (!_status.canAllocate()) {
            throw InventoryException.InvalidStatusTransition(
                inventoryId = id,
                from = _status.code,
                to = "ALLOCATED"
            )
        }
        
        // ... 할당 로직
    }
}
```

---

## 7. HTTP 통신 규격

### 7.1 Request 추적 (Correlation ID)

> **원칙**: 모든 요청은 고유 ID로 추적 가능해야 함. 문제 발생 시 전체 흐름 파악 가능

#### 7.1.1 Request Context
```kotlin
data class RequestContext(
    val correlationId: String,       // 요청 추적 ID (클라이언트 제공 또는 서버 생성)
    val requestId: String,           // 서버에서 생성한 고유 요청 ID
    val userId: String,              // 인증된 사용자 ID
    val clientIp: String,            // 클라이언트 IP
    val userAgent: String?,          // User-Agent
    val requestedAt: Instant         // 요청 시각
)
```

#### 7.1.2 Request 인터셉터
```kotlin
@Component
class RequestContextInterceptor : HandlerInterceptor {
    
    companion object {
        private val contextHolder = ThreadLocal<RequestContext>()
        
        fun getContext(): RequestContext? = contextHolder.get()
        fun setContext(context: RequestContext) = contextHolder.set(context)
        fun clear() = contextHolder.remove()
    }
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val correlationId = request.getHeader("X-Correlation-ID") 
            ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        
        val context = RequestContext(
            correlationId = correlationId,
            requestId = requestId,
            userId = extractUserId(request),
            clientIp = extractClientIp(request),
            userAgent = request.getHeader("User-Agent"),
            requestedAt = Instant.now()
        )
        
        setContext(context)
        
        // MDC에 설정 (로그에 자동 포함)
        MDC.put("correlationId", correlationId)
        MDC.put("requestId", requestId)
        MDC.put("userId", context.userId)
        
        // Response 헤더에 추가
        response.setHeader("X-Correlation-ID", correlationId)
        response.setHeader("X-Request-ID", requestId)
        
        return true
    }
    
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        clear()
        MDC.clear()
    }
    
    private fun extractUserId(request: HttpServletRequest): String {
        // 인증 정보에서 추출 (Spring Security 연동)
        return request.getHeader("X-User-ID") ?: "anonymous"
    }
    
    private fun extractClientIp(request: HttpServletRequest): String {
        return request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddr
    }
}
```

### 7.2 HTTP Response 규격

#### 7.2.1 Response DTO
```kotlin
// Request 정보 (응답에 포함)
data class HttpRequestSpec<T, E>(
    val method: String,
    val path: String,
    val timestamp: Instant,
    val requestBody: T? = null,
    val errorContext: E? = null
)

// 에러 상세
data class ErrorBody(
    val code: String,              // 에러 코드 (INV_001, LOC_002 등)
    val message: String,           // 에러 메시지
    val details: Map<String, Any>  // 추가 정보
)

// 통합 응답 규격 (사용자 제공 스펙 기준)
data class HttpResponseSpec<T, E>(
    val statusCode: Int,
    var message: String,
    val request: HttpRequestSpec<T, E>,
    val headers: Map<String, List<String>> = emptyMap(),
    val successBody: T? = null,    // 성공 시 데이터
    val errorBody: E? = null       // 실패 시 에러 정보
) {
    val isSuccess: Boolean
        get() = statusCode in 200..299
        
    val timestamp: Instant = Instant.now()
}
```

#### 7.2.2 응답 생성 유틸
```kotlin
object ResponseBuilder {
    
    fun <T> success(
        data: T,
        message: String = "Success",
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<T, Nothing>> {
        val context = RequestContextInterceptor.getContext()
        
        return ResponseEntity.ok(HttpResponseSpec(
            statusCode = 200,
            message = message,
            request = HttpRequestSpec(
                method = request.method,
                path = request.requestURI,
                timestamp = context?.requestedAt ?: Instant.now()
            ),
            headers = mapOf(
                "X-Correlation-ID" to listOf(context?.correlationId ?: ""),
                "X-Request-ID" to listOf(context?.requestId ?: "")
            ),
            successBody = data
        ))
    }
    
    fun <T> created(
        data: T,
        message: String = "Created",
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<T, Nothing>> {
        val context = RequestContextInterceptor.getContext()
        
        return ResponseEntity.status(HttpStatus.CREATED).body(HttpResponseSpec(
            statusCode = 201,
            message = message,
            request = HttpRequestSpec(
                method = request.method,
                path = request.requestURI,
                timestamp = context?.requestedAt ?: Instant.now()
            ),
            headers = mapOf(
                "X-Correlation-ID" to listOf(context?.correlationId ?: ""),
                "X-Request-ID" to listOf(context?.requestId ?: "")
            ),
            successBody = data
        ))
    }
    
    fun <T, E> pagedSuccess(
        pagedData: PagedResponse<T>,
        message: String = "Success",
        request: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<PagedResponse<T>, Nothing>> {
        val context = RequestContextInterceptor.getContext()
        
        return ResponseEntity.ok(HttpResponseSpec(
            statusCode = 200,
            message = message,
            request = HttpRequestSpec(
                method = request.method,
                path = request.requestURI,
                timestamp = context?.requestedAt ?: Instant.now()
            ),
            headers = mapOf(
                "X-Correlation-ID" to listOf(context?.correlationId ?: ""),
                "X-Request-ID" to listOf(context?.requestId ?: "")
            ),
            successBody = pagedData
        ))
    }
}
```

#### 7.2.3 Controller에서 사용
```kotlin
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryCommandController(
    private val allocateHandler: AllocateInventoryCommandHandler
) {
    
    @PostMapping("/commands/allocate")
    fun allocate(
        @Valid @RequestBody request: AllocateInventoryRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<AllocationResultDto>> {
        val context = RequestContextInterceptor.getContext()
            ?: throw IllegalStateException("Request context not found")
        
        val result = allocateHandler.handle(
            request.toCommand(),
            context
        )
        
        return ResponseBuilder.created(
            data = result.toDto(),
            message = "재고 할당이 완료되었습니다",
            request = httpRequest
        )
    }
}
```

### 7.3 필수 HTTP 헤더

#### 요청 헤더 (Request)
| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-Correlation-ID` | 선택 | 클라이언트 제공 추적 ID (없으면 서버 생성) |
| `X-User-ID` | 조건부 | 인증된 사용자 ID (인증 필요 API) |
| `Content-Type` | 필수 | `application/json` |

#### 응답 헤더 (Response)
| 헤더 | 항상 포함 | 설명 |
|------|----------|------|
| `X-Correlation-ID` | ⭕ | 요청 추적 ID |
| `X-Request-ID` | ⭕ | 서버 생성 요청 ID |
| `Content-Type` | ⭕ | `application/json` |

### 7.4 응답 예시

#### 성공 응답
```json
{
    "statusCode": 200,
    "message": "재고 조회 성공",
    "request": {
        "method": "GET",
        "path": "/api/v1/inventory/queries",
        "timestamp": "2024-01-15T10:30:00Z",
        "requestBody": null,
        "errorContext": null
    },
    "headers": {
        "X-Correlation-ID": ["req-abc-123"],
        "X-Request-ID": ["srv-xyz-789"]
    },
    "successBody": {
        "content": [...],
        "page": 0,
        "size": 20,
        "totalElements": 150,
        "totalPages": 8,
        "hasNext": true,
        "hasPrevious": false
    },
    "errorBody": null,
    "isSuccess": true,
    "timestamp": "2024-01-15T10:30:00.123Z"
}
```

#### 에러 응답
```json
{
    "statusCode": 409,
    "message": "가용 재고가 부족합니다",
    "request": {
        "method": "POST",
        "path": "/api/v1/inventory/commands/allocate",
        "timestamp": "2024-01-15T10:30:00Z",
        "requestBody": null,
        "errorContext": null
    },
    "headers": {
        "X-Correlation-ID": ["req-abc-123"],
        "X-Request-ID": ["srv-xyz-789"]
    },
    "successBody": null,
    "errorBody": {
        "code": "INV_002",
        "message": "가용 재고가 부족합니다",
        "details": {
            "inventoryId": 123,
            "requested": 100,
            "available": 50
        }
    },
    "isSuccess": false,
    "timestamp": "2024-01-15T10:30:00.456Z"
}
```

### 7.5 API 문서화 (Redoc)

> **원칙**: OpenAPI 3.0 스펙 기반으로 자동 문서 생성, Redoc UI로 제공

#### 7.5.1 설정 (application.yml)
```yaml
springdoc:
  api-docs:
    path: /api-docs
    enabled: true
  swagger-ui:
    enabled: false  # Swagger UI 비활성화 (Redoc 사용)
  
  # Redoc 설정
  show-actuator: false
  packages-to-scan: com.wms.infrastructure.web.rest
  paths-to-match: /api/**
```

#### 7.5.2 Redoc 설정
```kotlin
@Configuration
class RedocConfig {
    
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("WMS API")
                .description("Warehouse Management System API Documentation")
                .version("v1.0.0")
                .contact(Contact()
                    .name("WMS Team")
                    .email("wms@company.com")
                )
            )
            .addServersItem(Server().url("/").description("Current Server"))
    }
}

@Controller
class RedocController {
    
    @GetMapping("/docs")
    fun redirectToRedoc(): String {
        return "redirect:/redoc.html"
    }
}
```

#### 7.5.3 Controller에 OpenAPI 어노테이션
```kotlin
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "재고 관리 API")
class InventoryCommandController(
    private val allocateHandler: AllocateInventoryCommandHandler
) {
    
    @Operation(
        summary = "재고 할당",
        description = "출고 오더에 대한 재고를 할당합니다"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "201",
            description = "할당 성공",
            content = [Content(schema = Schema(implementation = HttpResponseSpec::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "가용 재고 부족",
            content = [Content(schema = Schema(implementation = HttpResponseSpec::class))]
        )
    ])
    @PostMapping("/commands/allocate")
    fun allocate(
        @Valid @RequestBody request: AllocateInventoryRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<HttpResponseSpec<AllocationResultDto>> {
        // ...
    }
}
```

#### 7.5.4 접근 URL
| URL | 설명 |
|-----|------|
| `/docs` | Redoc 문서 UI |
| `/api-docs` | OpenAPI JSON 스펙 |
| `/api-docs.yaml` | OpenAPI YAML 스펙 |

---

## 8. 개발 Phase 및 의존성

### Phase 1: 기반 구축 (Week 1-3)
```
[시스템관리] → [기준정보] 
     ↓              ↓
  사용자/권한    창고 → 존 → 로케이션 → 품목
```
- **SY-001~003**: 사용자/권한/코드 관리
- **MD-001~003**: 창고 관리
- **MD-010~012**: 존 관리
- **MD-020~023**: 로케이션 관리
- **MD-030~032**: 품목 관리
- **MD-040~041**: 거래처 관리

### Phase 2: 입고 프로세스 (Week 4-5)
```
[입고예정] → [검수] → [적치] → [재고생성] → [이력기록]
```
- **IB-001~003**: 입고예정 관리
- **IB-010~012**: 입고검수
- **IB-020~022**: 입고적치

### Phase 3: 재고 관리 (Week 6-7)
```
[재고조회] ← [재고조정] ← [재고이동] ← [재고실사]
     ↓            ↓            ↓            ↓
  [이력조회]   [이력기록]   [이력기록]   [이력기록]
```
- **IV-001~002**: 재고 조회
- **IV-010~012**: 재고 조정
- **IV-020~021**: 재고 이동
- **IV-031~032**: 재고 실사

### Phase 4: 출고 프로세스 (Week 8-10)
```
[출고예정] → [할당(락)] → [피킹] → [패킹] → [출고확정]
                ↓            ↓                    ↓
           [이력기록]   [이력기록]           [이력기록]
```
- **OB-001~003**: 출고예정 관리
- **OB-010,012,013**: 재고 할당 **(Pessimistic Lock 필수)**
- **OB-020~022**: 피킹
- **OB-030~031**: 패킹
- **OB-040,042**: 출고확정

### Phase 5: 보고서 (Week 11)
- **RP-001~002**: 입출고 현황, 재고 리포트

---

## 9. 기준정보 관리 (Master Data)

### 9.1 창고 관리

#### MD-001: 창고 등록 (Command)
| 항목 | 내용 |
|------|------|
| **기능 설명** | 새로운 창고 정보를 시스템에 등록 |
| **Endpoint** | `POST /api/v1/warehouses/commands` |
| **Request** | `CreateWarehouseCommand { warehouseCode, warehouseName, address, warehouseType }` |
| **Response** | `CommandResult { id, success }` |
| **관련 엔티티** | `Warehouse` |
| **검증 규칙** | - warehouseCode: 필수, 고유, 영문+숫자 10자 이내<br>- warehouseName: 필수, 50자 이내<br>- warehouseType: WarehouseType (Sealed Class) |

#### MD-002: 창고 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/warehouses/commands/{id}` |
| **Request** | `UpdateWarehouseCommand { warehouseName, address, warehouseType, isActive }` |
| **검증 규칙** | - warehouseCode 수정 불가<br>- Optimistic Lock 사용 |

#### MD-003: 창고 조회 (Query)
| 항목 | 내용 |
|------|------|
| **목록 조회** | `GET /api/v1/warehouses/queries` |
| **상세 조회** | `GET /api/v1/warehouses/queries/{id}` |
| **Query Params** | `?keyword=&warehouseType=&isActive=&page=0&size=20&sortBy=createdAt&sortDirection=DESC` |
| **Response** | `PagedResponse<WarehouseDto>` |

---

### 9.2 존(Zone) 관리

#### MD-010: 존 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/zones/commands` |
| **Request** | `CreateZoneCommand { warehouseId, zoneCode, zoneName, zoneType, temperatureMin, temperatureMax }` |
| **검증 규칙** | - zoneCode: 창고 내 고유<br>- zoneType: ZoneType (Sealed Class)<br>- 냉장/냉동 시 온도 범위 필수 |

#### MD-011: 존 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/zones/commands/{id}` |
| **검증 규칙** | - 존 타입 변경 시 해당 존에 재고 없어야 함<br>- 창고 변경 불가 |

#### MD-012: 존 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/zones/queries` |
| **Query Params** | `?warehouseId=&zoneType=&keyword=&page=&size=&sortBy=&sortDirection=` |

---

### 9.3 로케이션 관리

#### MD-020: 로케이션 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/locations/commands` |
| **Request** | `CreateLocationCommand { zoneId, locationCode, row, column, level, locationType, maxWeight, maxVolume }` |
| **검증 규칙** | - locationCode: 전체 시스템 내 고유<br>- locationType: LocationType (Sealed Class)<br>- status: LocationStatus (Sealed Class) |

#### MD-021: 로케이션 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/locations/commands/{id}` |
| **검증 규칙** | - 재고 존재 시 용량 축소 불가<br>- 존 변경 불가 |

#### MD-022: 로케이션 조회 (Query)
| 항목 | 내용 |
|------|------|
| **목록 조회** | `GET /api/v1/locations/queries` |
| **빈 로케이션** | `GET /api/v1/locations/queries/available` |
| **Query Params** | `?zoneId=&warehouseId=&status=&locationType=&page=&size=&sortBy=&sortDirection=` |

#### MD-023: 로케이션 상태변경 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PATCH /api/v1/locations/commands/{id}/status` |
| **Request** | `ChangeLocationStatusCommand { status, reason }` |
| **상태 관리** | LocationStatus (Sealed Class) - 상태 전이 규칙 내장 |

---

### 9.4 품목(Item) 관리

#### MD-030: 품목 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/items/commands` |
| **Request** | `CreateItemCommand { itemCode, itemName, barcode, category, unit, storageType, expiryManaged, lotManaged }` |
| **검증 규칙** | - itemCode: 필수, 고유<br>- barcode: 고유 (nullable)<br>- unit: ItemUnit (Sealed Class), storageType: StorageType (Sealed Class) |

#### MD-031: 품목 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/items/commands/{id}` |
| **검증 규칙** | - itemCode 수정 불가<br>- 재고 존재 시 storageType 변경 불가 |

#### MD-032: 품목 조회 (Query)
| 항목 | 내용 |
|------|------|
| **목록 조회** | `GET /api/v1/items/queries` |
| **바코드 조회** | `GET /api/v1/items/queries/barcode/{barcode}` |
| **Query Params** | `?keyword=&category=&storageType=&page=&size=&sortBy=&sortDirection=` |

---

### 9.5 거래처 관리

#### MD-040: 공급업체 관리
| 항목 | 내용 |
|------|------|
| **Command** | `POST/PUT /api/v1/suppliers/commands` |
| **Query** | `GET /api/v1/suppliers/queries` |

#### MD-041: 고객사 관리
| 항목 | 내용 |
|------|------|
| **Command** | `POST/PUT /api/v1/customers/commands` |
| **Query** | `GET /api/v1/customers/queries` |

---

## 10. 입고 관리 (Inbound)

### 10.1 입고예정 관리

#### IB-001: 입고예정 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inbound-orders/commands` |
| **Request** | `CreateInboundOrderCommand { supplierId, warehouseId, expectedDate, items: [...] }` |
| **검증 규칙** | - expectedDate: 오늘 이후<br>- items: 최소 1개 이상 |

#### IB-002: 입고예정 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/inbound-orders/commands/{id}` |
| **검증 규칙** | - status가 EXPECTED일 때만 수정 가능 |

#### IB-003: 입고예정 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/inbound-orders/queries` |
| **Query Params** | `?status=&supplierId=&expectedDateFrom=&expectedDateTo=&page=&size=&sortBy=&sortDirection=` |

---

### 10.2 입고검수

#### IB-010: 입고검수 시작 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inbound-orders/commands/{id}/inspection/start` |
| **상태 전이** | EXPECTED → INSPECTING |
| **동시성** | 동시 검수 방지 락 적용 |

#### IB-011: 입고검수 처리 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/inbound-orders/commands/{id}/items/{itemId}/inspection` |
| **Request** | `InspectItemCommand { inspectedQty, acceptedQty, rejectedQty, rejectionReason }` |

#### IB-012: 입고검수 완료 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inbound-orders/commands/{id}/inspection/complete` |
| **상태 전이** | INSPECTING → INSPECTED |

---

### 10.3 입고적치

#### IB-020: 적치지시 생성 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inbound-orders/commands/{id}/putaway` |
| **Request** | `CreatePutawayCommand { items: [{ inboundItemId, locationId, qty }] }` 또는 `{ autoAssign: true }` |

#### IB-021: 적치지시 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/putaway-tasks/queries` |
| **Query Params** | `?inboundOrderId=&status=&page=&size=&sortBy=&sortDirection=` |

#### IB-022: 적치 처리 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/putaway-tasks/commands/{id}/complete` |
| **Request** | `CompletePutawayCommand { actualLocationId, actualQty }` |
| **필수 처리** | 1. Inventory 생성/갱신<br>2. **InventoryHistory 기록 (INBOUND)**<br>3. Location 상태 갱신<br>4. 도메인 이벤트 발행 |

```
입고 상태 흐름:
[EXPECTED] → [INSPECTING] → [INSPECTED] → [PUTAWAY_IN_PROGRESS] → [COMPLETED]
                  ↓
            [REJECTED] → [RETURNED/DISPOSED]
```

---

## 11. 재고 관리 (Inventory)

### 11.1 재고 조회 (Query)

#### IV-001: 재고현황 조회
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/inventory/queries` |
| **Query Params** | `?itemId=&warehouseId=&zoneId=&locationId=&status=&page=0&size=20&sortBy=createdAt&sortDirection=DESC` |
| **Response** | `PagedResponse<InventoryDto>` with `summary: { totalQty, totalAvailable, totalAllocated }` |

#### IV-002: 재고이력 조회
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/inventory/queries/history` |
| **Query Params** | `?itemId=&locationId=&transactionType=&dateFrom=&dateTo=&page=&size=&sortBy=&sortDirection=` |
| **Response** | `PagedResponse<InventoryHistoryDto>` |
| **이력 필드** | `{ id, item, location, transactionType, changeQuantity, beforeQuantity, afterQuantity, referenceType, referenceId, createdAt, createdBy }` |

---

### 11.2 재고 조정

#### IV-010: 재고조정 등록 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inventory/commands/adjustments` |
| **Request** | `CreateAdjustmentCommand { inventoryId, adjustmentType, quantity, reason }` |
| **조정 유형** | AdjustmentType (Sealed Class): INCREASE, DECREASE, DAMAGE, LOSS, FOUND |

#### IV-011: 재고조정 승인 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inventory/commands/adjustments/{id}/approve` |
| **필수 처리** | 1. Inventory 수량 반영<br>2. **InventoryHistory 기록 (ADJUSTMENT_INCREASE/DECREASE)**<br>3. 도메인 이벤트 발행 |
| **동시성** | **Pessimistic Lock 필수** |

#### IV-012: 재고조정 이력 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/inventory/queries/adjustments` |
| **Query Params** | `?status=&adjustmentType=&dateFrom=&dateTo=&page=&size=&sortBy=&sortDirection=` |

---

### 11.3 재고 이동

#### IV-020: 로케이션 이동 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inventory/commands/movements` |
| **Request** | `CreateMovementCommand { inventoryId, fromLocationId, toLocationId, quantity, reason }` |
| **필수 처리** | 1. 출발지 Inventory 감소<br>2. **InventoryHistory 기록 (MOVEMENT_OUT)**<br>3. 도착지 Inventory 증가/생성<br>4. **InventoryHistory 기록 (MOVEMENT_IN)**<br>5. 로케이션 상태 갱신 |
| **동시성** | **Pessimistic Lock 필수** (출발지, 도착지 순서대로) |

#### IV-021: 존 간 이동 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/inventory/commands/movements/zone-transfer` |
| **필수 처리** | 출발지 **TRANSFER_OUT** + 도착지 **TRANSFER_IN** 이력 기록 |

---

### 11.4 재고실사

#### IV-031: 실사 진행 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/cycle-counts/commands/{countId}/items` |
| **Request** | `CountItemCommand { inventoryId, countedQty }` |

#### IV-032: 실사 확정 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/cycle-counts/commands/{countId}/confirm` |
| **Request** | `ConfirmCycleCountCommand { autoAdjust: true/false }` |
| **필수 처리** | 차이 발생 시 **InventoryHistory 기록 (CYCLE_COUNT)** |

---

## 12. 출고 관리 (Outbound)

### 12.1 출고예정 관리

#### OB-001: 출고예정 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands` |
| **Request** | `CreateOutboundOrderCommand { customerId, warehouseId, requestedDate, priority, items: [...] }` |

#### OB-002: 출고예정 수정 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/outbound-orders/commands/{id}` |
| **검증 규칙** | - status가 PENDING일 때만 수정 가능 |

#### OB-003: 출고예정 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/outbound-orders/queries` |
| **Query Params** | `?status=&customerId=&requestedDateFrom=&requestedDateTo=&page=&size=&sortBy=&sortDirection=` |

---

### 12.2 재고 할당

#### OB-010: 자동 할당 (Command) ⚠️ 동시성 제어 핵심
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands/{id}/allocate` |
| **Request** | `AllocateCommand { allocationStrategy: 'FIFO' \| 'FEFO' }` |
| **동시성 제어** | **Pessimistic Lock 필수** - SELECT FOR UPDATE |
| **필수 처리** | 1. 재고 조회 WITH LOCK<br>2. availableQty 감소, allocatedQty 증가<br>3. **InventoryHistory 기록 (ALLOCATE)**<br>4. Allocation 레코드 생성 |

```kotlin
// 할당 시 동시성 제어 예시
@Transactional
fun allocate(command: AllocateCommand) {
    // 1. PESSIMISTIC_WRITE 락으로 재고 조회
    val inventories = inventoryRepository
        .findAllocatableWithLock(command.itemId)
    
    // 2. 할당 처리
    for (inventory in inventories) {
        inventory.allocate(qty)  // 내부에서 이력 자동 기록
    }
    
    // 3. 저장 (트랜잭션 내)
    inventoryRepository.saveAll(inventories)
}
```

#### OB-012: 할당 취소 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands/{id}/deallocate` |
| **필수 처리** | 1. allocatedQty 감소, availableQty 증가<br>2. **InventoryHistory 기록 (DEALLOCATE)** |
| **동시성** | **Pessimistic Lock 필수** |

#### OB-013: 할당 현황 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/outbound-orders/queries/{id}/allocations` |

---

### 12.3 피킹(Picking)

#### OB-020: 피킹지시 생성 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands/{id}/picking/create` |

#### OB-021: 피킹지시 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/picking-tasks/queries` |
| **Query Params** | `?outboundOrderId=&status=&page=&size=&sortBy=&sortDirection=` |

#### OB-022: 피킹 처리 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/picking-tasks/commands/{id}/complete` |
| **Request** | `CompletePickingCommand { pickedQty }` |

---

### 12.4 패킹(Packing)

#### OB-030: 패킹 처리 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands/{id}/packing` |
| **Request** | `PackCommand { packages: [...] }` |

#### OB-031: 박스 라벨 출력 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/packages/queries/{id}/label` |

---

### 12.5 출고확정

#### OB-040: 출고확정 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/outbound-orders/commands/{id}/confirm` |
| **Request** | `ConfirmOutboundCommand { shippedAt, trackingNo, carrier }` |
| **필수 처리** | 1. Inventory 수량 최종 차감 (allocatedQty → 0)<br>2. **InventoryHistory 기록 (OUTBOUND)**<br>3. Location 상태 갱신<br>4. 도메인 이벤트 발행 |
| **동시성** | **Pessimistic Lock 필수** |

#### OB-042: 출고이력 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/outbound-orders/queries/history` |
| **Query Params** | `?customerId=&dateFrom=&dateTo=&page=&size=&sortBy=&sortDirection=` |

```
출고 상태 흐름:
[PENDING] → [ALLOCATED] → [PICKING] → [PICKED] → [PACKED] → [SHIPPED]
    ↓           ↓            ↓
[CANCELLED] [DEALLOCATED] [PICK_SHORTAGE]
```

---

## 13. 보고서/통계

### RP-001: 일일 입출고 현황 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/reports/queries/daily-io` |
| **Query Params** | `?date=&warehouseId=` |

### RP-002: 재고 현황 리포트 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/reports/queries/inventory-status` |
| **Query Params** | `?warehouseId=&groupBy=item\|location\|category&page=&size=&sortBy=&sortDirection=` |

---

## 14. 시스템 관리

### SY-001: 사용자 관리
| 항목 | 내용 |
|------|------|
| **Command** | `POST/PUT /api/v1/users/commands` |
| **Query** | `GET /api/v1/users/queries` (페이징/정렬 필수) |

### SY-002: 권한 관리
| 항목 | 내용 |
|------|------|
| **Command** | `POST/PUT /api/v1/roles/commands` |
| **Query** | `GET /api/v1/roles/queries` |

### SY-003: 상태 타입 관리 (Sealed Class)
> ⚠️ **코드 테이블이 아닌 Sealed Class로 관리** - 타입 안전성, 컴파일 타임 검증

| 상태 타입 | Sealed Class | 설명 |
|----------|--------------|------|
| 재고 상태 | `InventoryStatus` | Available, Allocated, OnHold, Damaged 등 |
| 로케이션 상태 | `LocationStatus` | Empty, Occupied, Locked, Maintenance |
| 입고 상태 | `InboundStatus` | Expected, Inspecting, Completed 등 |
| 출고 상태 | `OutboundStatus` | Pending, Allocated, Shipped 등 |
| 창고 타입 | `WarehouseType` | General, ColdStorage, Hazardous 등 |
| 존 타입 | `ZoneType` | Ambient, Refrigerated, Frozen 등 |
| 품목 단위 | `ItemUnit` | EA, BOX, PALLET, KG 등 |
| 보관 타입 | `StorageType` | Normal, Cold, Frozen, Hazardous |

---

## 15. 공통 설계 규칙

### 15.1 API 설계 원칙 (CQRS)
```
Command API: /api/v1/{resource}/commands
  - POST: 생성
  - PUT: 수정
  - PATCH: 부분 수정
  - DELETE: 삭제

Query API: /api/v1/{resource}/queries
  - GET: 조회 (항상 페이징/정렬 파라미터 포함)
```

### 15.2 Query 파라미터 표준
```kotlin
// 모든 Query는 아래 파라미터 필수
data class BaseSearchCriteria(
    val page: Int = 0,           // 페이지 번호 (0부터 시작)
    val size: Int = 20,          // 페이지 크기 (기본 20, 최대 100)
    val sortBy: String = "createdAt",  // 정렬 필드
    val sortDirection: SortDirection = SortDirection.DESC
)
```

### 15.3 응답 형식
```kotlin
// Command 응답
data class CommandResult(
    val id: Long,
    val success: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Query 응답 (페이징)
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

// 에러 응답
data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)
```

### 15.4 엔티티 공통 필드
```kotlin
@MappedSuperclass
abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
    
    @Version  // Optimistic Lock
    val version: Long = 0
    
    val createdAt: LocalDateTime = LocalDateTime.now()
    var createdBy: String = ""
    
    var updatedAt: LocalDateTime = LocalDateTime.now()
    var updatedBy: String = ""
    
    var isDeleted: Boolean = false
}
```

### 15.5 재고 이력 (InventoryHistory) 필수 필드
```kotlin
@Entity
class InventoryHistory(
    @Id @GeneratedValue
    val id: Long = 0,
    
    @ManyToOne
    val inventory: Inventory,
    
    val transactionType: String,    // INBOUND, OUTBOUND, ADJUSTMENT_*, MOVEMENT_*, ALLOCATE, DEALLOCATE, CYCLE_COUNT
    val changeQuantity: Int,        // 변동 수량 (+/-)
    val beforeQuantity: Int,        // 변경 전 수량
    val afterQuantity: Int,         // 변경 후 수량
    
    val referenceType: String?,     // INBOUND_ORDER, OUTBOUND_ORDER, ADJUSTMENT, MOVEMENT
    val referenceId: Long?,         // 참조 ID
    
    val reason: String?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String
)

// ⚠️ 불변 규칙: SUM(inventory.quantity) == SUM(inventoryHistory.changeQuantity)
```

### 15.6 동시성 제어 체크리스트
```
□ 재고 할당 시 Pessimistic Lock 적용
□ 재고 조정 시 Pessimistic Lock 적용
□ 재고 이동 시 출발지/도착지 순차 락 (ID 오름차순)
□ 락 타임아웃 설정 (3초)
□ 트랜잭션 내 외부 호출 금지
□ 기준정보는 Optimistic Lock (@Version)
```

### 15.7 재고 이력 기록 체크리스트
```
□ 입고 적치 완료 시 - INBOUND
□ 출고 확정 시 - OUTBOUND
□ 재고 조정 승인 시 - ADJUSTMENT_INCREASE / ADJUSTMENT_DECREASE
□ 로케이션 이동 시 - MOVEMENT_OUT (출발지), MOVEMENT_IN (도착지)
□ 존 간 이동 시 - TRANSFER_OUT, TRANSFER_IN
□ 재고 할당 시 - ALLOCATE
□ 할당 해제 시 - DEALLOCATE
□ 재고 실사 차이 반영 시 - CYCLE_COUNT
□ 불량 전환 시 - STATUS_CHANGE
□ 반품 입고 시 - RETURN_INBOUND
```

---

## 16. 테스트 가이드

> ⚠️ **testing.md 참조** - 테스트 커버리지 70% 이상, 모든 API E2E 테스트 필수

### 16.1 테스트 계층 및 도구

| 계층 | 정의 | 주요 검증 대상 | 도구 |
|------|------|---------------|------|
| **Unit Test** | 개별 단위 코드 검증 | 도메인 로직, 유틸리티 | JUnit5, Mockito, AssertJ |
| **Integration Test** | 모듈 간 상호작용 검증 | DB 연동, Repository | SpringBootTest, @DataJpaTest |
| **E2E Test** | 사용자 관점 전체 흐름 | HTTP API 요청-응답 | MockMvc, RestAssured |

### 16.2 도메인 Unit Test 예시

```kotlin
class InventoryTest {
    
    @Test
    fun `재고 할당 성공 - 가용 재고가 충분한 경우`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 0
        )
        
        // When
        val result = inventory.allocate(qty = 30, orderId = 1L)
        
        // Then
        assertThat(inventory.allocatedQty).isEqualTo(30)
        assertThat(inventory.availableQty).isEqualTo(70)
        assertThat(inventory.status).isEqualTo(InventoryStatus.Available)
        assertThat(result.allocatedQty).isEqualTo(30)
    }
    
    @Test
    fun `재고 할당 실패 - 가용 재고 부족`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 90  // 가용 재고 10
        )
        
        // When & Then
        assertThatThrownBy {
            inventory.allocate(qty = 20, orderId = 1L)
        }
            .isInstanceOf(InventoryException.InsufficientQuantity::class.java)
            .hasFieldOrPropertyWithValue("errorCode", "INV_002")
    }
    
    @Test
    fun `전량 할당 시 상태가 FullyAllocated로 변경`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 0
        )
        
        // When
        inventory.allocate(qty = 100, orderId = 1L)
        
        // Then
        assertThat(inventory.status).isEqualTo(InventoryStatus.FullyAllocated)
        assertThat(inventory.availableQty).isEqualTo(0)
    }
    
    @Test
    fun `할당 시 이력이 자동 기록됨`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 0
        )
        
        // When
        inventory.allocate(qty = 30, orderId = 1L)
        
        // Then
        val history = inventory.histories.last()
        assertThat(history.transactionType).isEqualTo("ALLOCATE")
        assertThat(history.changeQuantity).isEqualTo(30)
        assertThat(history.beforeQuantity).isEqualTo(0)
        assertThat(history.afterQuantity).isEqualTo(30)
    }
}

class InventoryStatusTest {
    
    @Test
    fun `Available 상태에서 Allocated로 전이 가능`() {
        // Given
        val status = InventoryStatus.Available
        
        // When & Then
        assertThat(status.canTransitionTo(InventoryStatus.Allocated)).isTrue()
        assertThat(status.canAllocate()).isTrue()
    }
    
    @Test
    fun `Shipped 상태에서는 어떤 전이도 불가`() {
        // Given
        val status = InventoryStatus.Shipped
        
        // When & Then
        assertThat(status.allowedTransitions()).isEmpty()
        assertThat(status.canAllocate()).isFalse()
    }
}
```

### 16.3 Service Integration Test 예시

```kotlin
@SpringBootTest
@Transactional
class AllocateInventoryCommandHandlerTest {
    
    @Autowired
    private lateinit var handler: AllocateInventoryCommandHandler
    
    @Autowired
    private lateinit var inventoryRepository: InventoryRepository
    
    @Autowired
    private lateinit var entityManager: EntityManager
    
    @Test
    fun `재고 할당 통합 테스트 - DB 저장 확인`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 0
        )
        inventoryRepository.save(inventory)
        entityManager.flush()
        entityManager.clear()
        
        val command = AllocateInventoryCommand(
            outboundOrderId = 1L,
            itemId = 1L,
            requiredQty = 30,
            strategy = AllocationStrategy.FIFO
        )
        val context = RequestContext(
            correlationId = "test-correlation-id",
            requestId = "test-request-id",
            userId = "test-user",
            clientIp = "127.0.0.1",
            userAgent = null,
            requestedAt = Instant.now()
        )
        
        // When
        val result = handler.handle(command, context)
        entityManager.flush()
        entityManager.clear()
        
        // Then
        val savedInventory = inventoryRepository.findById(inventory.id)
        assertThat(savedInventory).isNotNull
        assertThat(savedInventory!!.allocatedQty).isEqualTo(30)
        assertThat(savedInventory.availableQty).isEqualTo(70)
        
        // 이력 확인
        val histories = savedInventory.histories
        assertThat(histories).hasSize(1)
        assertThat(histories.first().transactionType).isEqualTo("ALLOCATE")
    }
    
    @Test
    fun `동시 할당 요청 시 락으로 순차 처리`() {
        // Given
        val inventory = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = 100,
            allocatedQty = 0
        )
        inventoryRepository.save(inventory)
        entityManager.flush()
        
        val latch = CountDownLatch(2)
        val results = ConcurrentHashMap<String, Any>()
        
        // When - 동시에 2개의 할당 요청
        val executor = Executors.newFixedThreadPool(2)
        executor.submit {
            try {
                val result = handler.handle(
                    AllocateInventoryCommand(1L, 1L, 60, AllocationStrategy.FIFO),
                    createTestContext("request-1")
                )
                results["request-1"] = result
            } catch (e: Exception) {
                results["request-1"] = e
            } finally {
                latch.countDown()
            }
        }
        executor.submit {
            try {
                val result = handler.handle(
                    AllocateInventoryCommand(1L, 1L, 60, AllocationStrategy.FIFO),
                    createTestContext("request-2")
                )
                results["request-2"] = result
            } catch (e: Exception) {
                results["request-2"] = e
            } finally {
                latch.countDown()
            }
        }
        
        latch.await(10, TimeUnit.SECONDS)
        
        // Then - 하나는 성공, 하나는 실패 (재고 부족)
        val successCount = results.values.count { it !is Exception }
        val failCount = results.values.count { it is InventoryException.InsufficientQuantity }
        
        assertThat(successCount).isEqualTo(1)
        assertThat(failCount).isEqualTo(1)
    }
}
```

### 16.4 E2E API Test 예시

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class InventoryApiE2ETest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @Test
    fun `재고 할당 API E2E 테스트`() {
        // Given
        val request = """
            {
                "outboundOrderId": 1,
                "itemId": 1,
                "quantity": 30,
                "allocationStrategy": "FIFO"
            }
        """.trimIndent()
        
        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/commands/allocate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .header("X-Correlation-ID", "e2e-test-123")
                .header("X-User-ID", "test-user")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.successBody.allocatedQty").value(30))
            .andExpect(header().exists("X-Correlation-ID"))
            .andExpect(header().exists("X-Request-ID"))
    }
    
    @Test
    fun `재고 조회 API E2E 테스트 - 페이징`() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/inventory/queries")
                .param("warehouseId", "1")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC")
                .header("X-Correlation-ID", "e2e-test-456")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.successBody.content").isArray)
            .andExpect(jsonPath("$.successBody.page").value(0))
            .andExpect(jsonPath("$.successBody.size").value(20))
            .andExpect(jsonPath("$.successBody.totalElements").exists())
    }
    
    @Test
    fun `재고 부족 시 에러 응답 확인`() {
        // Given - 가용 재고보다 많은 수량 요청
        val request = """
            {
                "outboundOrderId": 1,
                "itemId": 1,
                "quantity": 999999,
                "allocationStrategy": "FIFO"
            }
        """.trimIndent()
        
        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/commands/allocate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .header("X-Correlation-ID", "e2e-test-error")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.statusCode").value(409))
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.errorBody.code").value("INV_002"))
            .andExpect(jsonPath("$.errorBody.message").value("가용 재고가 부족합니다"))
    }
}
```

### 16.5 필수 테스트 시나리오 목록

#### 재고 관리 테스트
| 시나리오 | 테스트 타입 | 검증 포인트 |
|---------|-----------|------------|
| 재고 할당 성공 | Unit, Integration, E2E | 상태 변경, 이력 기록, 응답 형식 |
| 재고 할당 실패 (가용 부족) | Unit, E2E | 예외 발생, 에러 응답 형식 |
| 동시 할당 요청 처리 | Integration | 락 동작, 데이터 정합성 |
| 재고 조정 승인 | Unit, Integration | 수량 변경, 이력 기록 |
| 재고 이동 | Unit, Integration | 출발지/도착지 수량, 이력 2건 |

#### 입고 관리 테스트
| 시나리오 | 테스트 타입 | 검증 포인트 |
|---------|-----------|------------|
| 입고예정 등록 | E2E | Request 검증, 응답 형식 |
| 입고검수 완료 | Unit, Integration | 상태 전이, 검수 수량 |
| 입고적치 완료 | Integration, E2E | 재고 생성, 이력 기록 |

#### 출고 관리 테스트
| 시나리오 | 테스트 타입 | 검증 포인트 |
|---------|-----------|------------|
| 출고예정 등록 | E2E | Request 검증, 응답 형식 |
| 자동 할당 (FIFO) | Integration | 할당 순서, 수량 분배 |
| 할당 취소 | Unit, Integration | 수량 복원, 이력 기록 |
| 출고 확정 | Integration, E2E | 재고 차감, 상태 완료 |

---

## 17. 인증/인가

### 17.1 Spring Security 설정

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 API
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/docs/**", "/api-docs/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    
                    // 인증 필요 API
                    .requestMatchers("/api/v1/**").authenticated()
                    
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint())
                ex.accessDeniedHandler(jwtAccessDeniedHandler())
            }
            .build()
    }
    
    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(jwtTokenProvider())
    }
    
    @Bean
    fun jwtTokenProvider(): JwtTokenProvider {
        return JwtTokenProvider()
    }
}
```

### 17.2 JWT 토큰 처리

```kotlin
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secretKey: String,
    @Value("\${jwt.expiration}") private val expirationMs: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }
    
    fun createToken(userId: String, roles: List<String>): String {
        val now = Date()
        val expiration = Date(now.time + expirationMs)
        
        return Jwts.builder()
            .setSubject(userId)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiration)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
            
            !claims.body.expiration.before(Date())
        } catch (e: JwtException) {
            false
        }
    }
    
    fun getUserId(token: String): String {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
            .subject
    }
    
    fun getRoles(token: String): List<String> {
        val claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
        
        @Suppress("UNCHECKED_CAST")
        return claims["roles"] as? List<String> ?: emptyList()
    }
}

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        
        if (token != null && jwtTokenProvider.validateToken(token)) {
            val userId = jwtTokenProvider.getUserId(token)
            val roles = jwtTokenProvider.getRoles(token)
            
            val authentication = UsernamePasswordAuthenticationToken(
                userId,
                null,
                roles.map { SimpleGrantedAuthority("ROLE_$it") }
            )
            
            SecurityContextHolder.getContext().authentication = authentication
            
            // RequestContext에 userId 설정
            request.setAttribute("X-User-ID", userId)
        }
        
        filterChain.doFilter(request, response)
    }
    
    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }
}
```

### 17.3 권한 기반 접근 제어

```kotlin
// 역할 정의 (Sealed Class)
sealed class WmsRole(val code: String, val displayName: String) {
    data object Admin : WmsRole("ADMIN", "관리자")
    data object Manager : WmsRole("MANAGER", "매니저")
    data object Operator : WmsRole("OPERATOR", "운영자")
    data object Viewer : WmsRole("VIEWER", "조회자")
    
    companion object {
        fun fromCode(code: String): WmsRole = when (code) {
            "ADMIN" -> Admin
            "MANAGER" -> Manager
            "OPERATOR" -> Operator
            "VIEWER" -> Viewer
            else -> throw IllegalArgumentException("Unknown role: $code")
        }
    }
}

// Controller에서 권한 체크
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryCommandController {
    
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @PostMapping("/commands/allocate")
    fun allocate(@RequestBody request: AllocateInventoryRequest): ResponseEntity<*> {
        // 할당은 Operator 이상만 가능
    }
    
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/commands/adjustments/{id}/approve")
    fun approveAdjustment(@PathVariable id: Long): ResponseEntity<*> {
        // 조정 승인은 Manager 이상만 가능
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/commands/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<*> {
        // 삭제는 Admin만 가능
    }
}

// 권한 체크 실패 시 예외 처리
@Component
class JwtAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        
        val errorResponse = HttpResponseSpec<Nothing, ErrorBody>(
            statusCode = 403,
            message = "접근 권한이 없습니다",
            request = HttpRequestSpec(
                method = request.method,
                path = request.requestURI,
                timestamp = Instant.now()
            ),
            errorBody = ErrorBody(
                code = "AUTH_002",
                message = "Access denied",
                details = emptyMap()
            )
        )
        
        response.writer.write(ObjectMapper().writeValueAsString(errorResponse))
    }
}
```

---

## 18. 반품 관리 (Return)

### 18.1 반품 프로세스 개요

```
반품 상태 흐름:
[REQUESTED] → [APPROVED] → [RECEIVED] → [INSPECTED] → [COMPLETED]
     ↓            ↓                           ↓
[REJECTED]   [CANCELLED]              [DISPOSED/RESTOCKED]
```

### 18.2 반품 API

#### RT-001: 반품요청 등록 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/returns/commands` |
| **Request** | `CreateReturnCommand { outboundOrderId, customerId, reason, items: [{ itemId, qty, condition }] }` |
| **검증 규칙** | - 출고 확정된 오더만 반품 가능<br>- 반품 수량 ≤ 출고 수량 |

#### RT-002: 반품요청 승인 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/returns/commands/{id}/approve` |
| **상태 전이** | REQUESTED → APPROVED |

#### RT-003: 반품 입고 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/returns/commands/{id}/receive` |
| **Request** | `ReceiveReturnCommand { receivedAt, items: [{ itemId, receivedQty }] }` |
| **상태 전이** | APPROVED → RECEIVED |

#### RT-004: 반품 검수 (Command)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/returns/commands/{id}/inspect` |
| **Request** | `InspectReturnCommand { items: [{ itemId, condition, decision }] }` |
| **decision** | `RESTOCK` (재입고) / `DISPOSE` (폐기) / `REPAIR` (수리) |

#### RT-005: 반품 완료 (Command) ⚠️ 재고 이력 기록 필수
| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/returns/commands/{id}/complete` |
| **필수 처리** | - RESTOCK: Inventory 증가 + **InventoryHistory (RETURN_INBOUND)**<br>- DISPOSE: 폐기 이력 기록 |

#### RT-006: 반품 조회 (Query)
| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/returns/queries` |
| **Query Params** | `?status=&customerId=&dateFrom=&dateTo=&page=&size=&sortBy=&sortDirection=` |

### 18.3 반품 상태 (Sealed Class)

```kotlin
sealed class ReturnStatus(val code: String, val displayName: String) {
    abstract fun allowedTransitions(): Set<KClass<out ReturnStatus>>
    
    data object Requested : ReturnStatus("REQUESTED", "반품요청") {
        override fun allowedTransitions() = setOf(Approved::class, Rejected::class)
    }
    data object Approved : ReturnStatus("APPROVED", "승인") {
        override fun allowedTransitions() = setOf(Received::class, Cancelled::class)
    }
    data object Rejected : ReturnStatus("REJECTED", "거절") {
        override fun allowedTransitions() = emptySet<KClass<out ReturnStatus>>()
    }
    data object Received : ReturnStatus("RECEIVED", "입고완료") {
        override fun allowedTransitions() = setOf(Inspected::class)
    }
    data object Inspected : ReturnStatus("INSPECTED", "검수완료") {
        override fun allowedTransitions() = setOf(Completed::class)
    }
    data object Completed : ReturnStatus("COMPLETED", "처리완료") {
        override fun allowedTransitions() = emptySet<KClass<out ReturnStatus>>()
    }
    data object Cancelled : ReturnStatus("CANCELLED", "취소") {
        override fun allowedTransitions() = emptySet<KClass<out ReturnStatus>>()
    }
    
    companion object {
        fun fromCode(code: String): ReturnStatus = when (code) {
            "REQUESTED" -> Requested
            "APPROVED" -> Approved
            "REJECTED" -> Rejected
            "RECEIVED" -> Received
            "INSPECTED" -> Inspected
            "COMPLETED" -> Completed
            "CANCELLED" -> Cancelled
            else -> throw IllegalArgumentException("Unknown ReturnStatus: $code")
        }
    }
}
```

---

## 19. 이벤트 핸들러 및 알림

### 19.1 이벤트 핸들러 정의

```kotlin
@Component
class InventoryEventHandler(
    private val notificationService: NotificationService,
    private val externalSystemClient: ExternalSystemClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    @EventListener
    fun handleInventoryAllocated(event: InventoryAllocatedEvent) {
        logger.info(LogMessage(
            event = "INVENTORY_ALLOCATED",
            message = "재고 할당 이벤트 처리",
            data = mapOf(
                "inventoryId" to event.inventoryId,
                "allocatedQty" to event.allocatedQty,
                "outboundOrderId" to event.outboundOrderId
            )
        ).toStructured())
    }
    
    @EventListener
    fun handleInventoryShortage(event: InventoryShortageEvent) {
        logger.warn(LogMessage(
            event = "INVENTORY_SHORTAGE",
            message = "재고 부족 경고",
            data = mapOf(
                "itemId" to event.itemId,
                "currentQty" to event.currentQty,
                "threshold" to event.threshold
            )
        ).toStructured())
        
        // 재고 부족 알림 발송
        notificationService.sendAlert(
            type = AlertType.INVENTORY_SHORTAGE,
            title = "재고 부족 경고",
            message = "품목 ${event.itemCode}의 재고가 ${event.threshold}개 미만입니다. (현재: ${event.currentQty}개)",
            recipients = listOf("warehouse-manager@company.com")
        )
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleInventoryChangedAfterCommit(event: InventoryChangedEvent) {
        // 트랜잭션 커밋 후 외부 시스템 연동
        try {
            externalSystemClient.notifyInventoryChange(
                ExternalInventoryUpdate(
                    itemCode = event.itemCode,
                    locationCode = event.locationCode,
                    quantity = event.afterQty,
                    changeType = event.changeType,
                    timestamp = event.occurredAt
                )
            )
        } catch (e: Exception) {
            logger.error("외부 시스템 연동 실패: ${e.message}", e)
            // 실패 시 재시도 큐에 등록 (별도 처리)
        }
    }
}

@Component
class OutboundEventHandler(
    private val notificationService: NotificationService
) {
    
    @EventListener
    fun handleOutboundShipped(event: OutboundShippedEvent) {
        // 출고 완료 알림
        notificationService.sendNotification(
            type = NotificationType.OUTBOUND_SHIPPED,
            title = "출고 완료",
            message = "주문 ${event.orderNumber}이 출고되었습니다. 송장번호: ${event.trackingNo}",
            recipients = listOf(event.customerEmail)
        )
    }
    
    @EventListener
    fun handleAllocationFailed(event: AllocationFailedEvent) {
        // 할당 실패 알림 (운영자에게)
        notificationService.sendAlert(
            type = AlertType.ALLOCATION_FAILED,
            title = "재고 할당 실패",
            message = "출고 오더 ${event.orderId} 할당 실패: ${event.reason}",
            recipients = listOf("operations@company.com")
        )
    }
}
```

### 19.2 알림 서비스

```kotlin
interface NotificationService {
    fun sendNotification(type: NotificationType, title: String, message: String, recipients: List<String>)
    fun sendAlert(type: AlertType, title: String, message: String, recipients: List<String>)
}

enum class NotificationType {
    OUTBOUND_SHIPPED,
    INBOUND_COMPLETED,
    RETURN_APPROVED
}

enum class AlertType {
    INVENTORY_SHORTAGE,
    ALLOCATION_FAILED,
    SYSTEM_ERROR
}

@Service
class NotificationServiceImpl(
    private val emailSender: EmailSender
) : NotificationService {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    override fun sendNotification(
        type: NotificationType,
        title: String,
        message: String,
        recipients: List<String>
    ) {
        logger.info("Sending notification: type=$type, title=$title, recipients=$recipients")
        
        recipients.forEach { recipient ->
            emailSender.send(
                to = recipient,
                subject = "[$type] $title",
                body = message
            )
        }
    }
    
    override fun sendAlert(
        type: AlertType,
        title: String,
        message: String,
        recipients: List<String>
    ) {
        logger.warn("Sending alert: type=$type, title=$title")
        
        // 긴급 알림은 즉시 발송
        recipients.forEach { recipient ->
            emailSender.sendUrgent(
                to = recipient,
                subject = "[ALERT][$type] $title",
                body = message
            )
        }
    }
}
```

---

## 20. 인프라스트럭처 상세

### 20.1 JPA Entity - Domain 매핑

> **원칙**: 도메인 모델과 JPA 엔티티 분리로 순수 도메인 보존 + 테스트 용이성 향상

```kotlin
// Domain Entity (도메인 계층 - 순수 Kotlin)
class Inventory private constructor(
    val id: Long,
    val itemId: Long,
    val locationId: Long,
    private var _status: InventoryStatus,
    private var _quantity: Int,
    private var _allocatedQty: Int,
    private val _histories: MutableList<InventoryHistory>,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime
) : AggregateRoot() {
    
    val status: InventoryStatus get() = _status
    val quantity: Int get() = _quantity
    val allocatedQty: Int get() = _allocatedQty
    val availableQty: Int get() = _quantity - _allocatedQty
    val histories: List<InventoryHistory> get() = _histories.toList()
    
    // 도메인 로직...
}

// JPA Entity (인프라 계층 - JPA 어노테이션)
@Entity
@Table(name = "inventory")
class InventoryJpaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    
    @Column(name = "location_id", nullable = false)
    val locationId: Long,
    
    @Convert(converter = InventoryStatusConverter::class)
    @Column(name = "status", nullable = false)
    var status: InventoryStatus,
    
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
    
    @Column(name = "allocated_qty", nullable = false)
    var allocatedQty: Int,
    
    @OneToMany(mappedBy = "inventory", cascade = [CascadeType.ALL], orphanRemoval = true)
    val histories: MutableList<InventoryHistoryJpaEntity> = mutableListOf(),
    
    @Version
    val version: Long = 0,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

// Mapper (인프라 계층)
@Component
class InventoryMapper {
    
    fun toDomain(entity: InventoryJpaEntity): Inventory {
        return Inventory.reconstitute(
            id = entity.id,
            itemId = entity.itemId,
            locationId = entity.locationId,
            status = entity.status,
            quantity = entity.quantity,
            allocatedQty = entity.allocatedQty,
            histories = entity.histories.map { toHistoryDomain(it) }.toMutableList(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
    
    fun toEntity(domain: Inventory): InventoryJpaEntity {
        return InventoryJpaEntity(
            id = domain.id,
            itemId = domain.itemId,
            locationId = domain.locationId,
            status = domain.status,
            quantity = domain.quantity,
            allocatedQty = domain.allocatedQty,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        ).also { entity ->
            domain.histories.forEach { history ->
                entity.histories.add(toHistoryEntity(history, entity))
            }
        }
    }
    
    fun updateEntity(entity: InventoryJpaEntity, domain: Inventory) {
        entity.status = domain.status
        entity.quantity = domain.quantity
        entity.allocatedQty = domain.allocatedQty
        entity.updatedAt = domain.updatedAt
        
        // 새 이력 추가
        val existingHistoryIds = entity.histories.map { it.id }.toSet()
        domain.histories
            .filter { it.id !in existingHistoryIds }
            .forEach { entity.histories.add(toHistoryEntity(it, entity)) }
    }
    
    private fun toHistoryDomain(entity: InventoryHistoryJpaEntity): InventoryHistory {
        return InventoryHistory(
            id = entity.id,
            transactionType = entity.transactionType,
            changeQuantity = entity.changeQuantity,
            beforeQuantity = entity.beforeQuantity,
            afterQuantity = entity.afterQuantity,
            referenceType = entity.referenceType,
            referenceId = entity.referenceId,
            reason = entity.reason,
            createdAt = entity.createdAt,
            createdBy = entity.createdBy
        )
    }
    
    private fun toHistoryEntity(
        domain: InventoryHistory,
        parent: InventoryJpaEntity
    ): InventoryHistoryJpaEntity {
        return InventoryHistoryJpaEntity(
            id = domain.id,
            inventory = parent,
            transactionType = domain.transactionType,
            changeQuantity = domain.changeQuantity,
            beforeQuantity = domain.beforeQuantity,
            afterQuantity = domain.afterQuantity,
            referenceType = domain.referenceType,
            referenceId = domain.referenceId,
            reason = domain.reason,
            createdAt = domain.createdAt,
            createdBy = domain.createdBy
        )
    }
}

// Repository Adapter (인프라 계층)
@Repository
class InventoryRepositoryAdapter(
    private val jpaRepository: InventoryJpaRepository,
    private val mapper: InventoryMapper
) : InventoryRepository {
    
    override fun findById(id: Long): Inventory? {
        return jpaRepository.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }
    
    override fun findByIdWithLock(id: Long): Inventory? {
        return jpaRepository.findByIdWithLock(id)
            ?.let { mapper.toDomain(it) }
    }
    
    override fun save(inventory: Inventory): Inventory {
        val existing = jpaRepository.findById(inventory.id)
        
        val entity = if (existing.isPresent) {
            mapper.updateEntity(existing.get(), inventory)
            existing.get()
        } else {
            mapper.toEntity(inventory)
        }
        
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }
}
```

### 20.2 Event Sourcing 확장 가능성

> **참고**: 현재 재고 이력(InventoryHistory)은 Event Sourcing과 유사한 패턴으로 구현됨. 향후 확장 시 고려사항:

```kotlin
// 현재 구조: 상태 저장 + 이력 추적
// - Inventory: 현재 상태 (quantity, allocatedQty, status)
// - InventoryHistory: 변경 이력 (changeQuantity, transactionType)
// - 불변 규칙: SUM(quantity) == SUM(history.changeQuantity)

// 향후 Event Sourcing 전환 시:
// 1. Event Store 도입
// 2. Inventory를 History에서 재구성 (Replay)
// 3. CQRS Read Model 분리
// 4. Snapshot 전략 적용

// 현재 구조의 장점:
// - 즉각적인 조회 성능 (현재 상태 바로 조회)
// - 이력 추적 가능 (History 테이블)
// - 정합성 검증 가능 (불변 규칙)

// Event Sourcing 전환 시 장점:
// - 완전한 이벤트 재생 가능
// - 시간 여행 쿼리 (특정 시점 상태 조회)
// - 더 유연한 프로젝션 생성
```

### 20.3 Redoc 정적 파일 설정

```kotlin
// WebMvcConfigurer에서 정적 리소스 설정
@Configuration
class WebConfig : WebMvcConfigurer {
    
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Redoc 정적 파일 서빙
        registry.addResourceHandler("/redoc.html")
            .addResourceLocations("classpath:/static/")
    }
}

// src/main/resources/static/redoc.html
/*
<!DOCTYPE html>
<html>
<head>
    <title>WMS API Documentation</title>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
    <style>
        body { margin: 0; padding: 0; }
    </style>
</head>
<body>
    <redoc spec-url='/api-docs'></redoc>
    <script src="https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js"></script>
</body>
</html>
*/
```

### 20.4 로그 파일 로테이션 상세 설정

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.wms: DEBUG
    com.wms.infrastructure.persistence: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-N/A}] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-N/A}] %-5level %logger{36} - %msg%n"

# logback-spring.xml에서 상세 설정
```

```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <property name="LOG_PATH" value="logs"/>
    <property name="LOG_FILE" value="${LOG_PATH}/wms-application"/>
    
    <!-- Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-N/A}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Rolling File Appender - 일별 로테이션 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <!-- 일별 로테이션 + 압축 -->
            <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <!-- 30일 보관 -->
            <maxHistory>30</maxHistory>
            <!-- 전체 로그 크기 제한 (10GB) -->
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-N/A}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 에러 로그 별도 파일 -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/wms-error.log</file>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/wms-error.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-N/A}] %-5level %logger{36} - %msg%n%ex</pattern>
        </encoder>
    </appender>
    
    <!-- Query 로그 별도 파일 -->
    <appender name="QUERY_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/wms-query.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/wms-query.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>7</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{correlationId:-N/A}] %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Logger 설정 -->
    <logger name="com.wms" level="DEBUG"/>
    <logger name="com.wms.infrastructure.persistence.QueryLoggingInterceptor" level="DEBUG" additivity="false">
        <appender-ref ref="QUERY_FILE"/>
    </logger>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>
</configuration>
```

### 20.5 H2 → 운영 DB 마이그레이션 가이드

```yaml
# application.yml - 프로파일 분리

---
# 개발/테스트 환경 (H2)
spring:
  config:
    activate:
      on-profile: local, test
  datasource:
    url: jdbc:h2:mem:wmsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

---
# 운영 환경 (PostgreSQL)
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    hibernate:
      ddl-auto: validate  # 운영에서는 validate만
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

---
# 스테이징 환경 (MySQL)
spring:
  config:
    activate:
      on-profile: staging
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=true&serverTimezone=Asia/Seoul
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

```kotlin
// 마이그레이션 시 주의사항

/*
1. DDL 스크립트 준비
   - H2에서 자동 생성된 스키마를 기반으로 운영 DB용 DDL 작성
   - AUTO_INCREMENT → SERIAL (PostgreSQL) 또는 그대로 (MySQL)
   - 인덱스 전략 검토 (조회 패턴 기반)

2. 데이터 타입 호환성
   - H2 TIMESTAMP → PostgreSQL TIMESTAMP WITH TIME ZONE
   - H2 CLOB → PostgreSQL TEXT
   - H2 BLOB → PostgreSQL BYTEA

3. 락 쿼리 호환성
   - PESSIMISTIC_WRITE는 모든 DB에서 동일하게 동작
   - 타임아웃 설정은 DB별로 다름
     - PostgreSQL: SET lock_timeout = '3s'
     - MySQL: innodb_lock_wait_timeout

4. 시퀀스 전략
   - H2: IDENTITY (auto_increment)
   - PostgreSQL: SEQUENCE 권장 (배치 인서트 최적화)
   
5. Flyway/Liquibase 도입 권장
   - 스키마 버전 관리
   - 마이그레이션 스크립트 이력 추적
*/
```

---

## Appendix: ERD (주요 테이블)

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Warehouse  │────<│    Zone     │────<│  Location   │
└─────────────┘     └─────────────┘     └─────────────┘
                                              │
┌─────────────┐     ┌─────────────┐           │
│  Supplier   │────<│    Item     │           │
└─────────────┘     └─────────────┘           │
       │                   │                  │
       │            ┌──────┴──────┐           │
       │            │             │           │
┌──────┴──────┐  ┌──┴───┐  ┌─────┴─────┐     │
│InboundOrder │  │ Inv- │  │OutboundOrd│     │
│    Item     │  │entory│──│   Item    │     │
└─────────────┘  └──┬───┘  └───────────┘     │
       │            │             │           │
       │     ┌──────┴──────┐      │           │
       │     │ Inventory   │      │           │
       │     │ History     │      │           │
       │     └─────────────┘      │           │
       │                          │           │
┌──────┴──────┐            ┌──────┴──────┐    │
│ PutawayTask │            │ PickingTask │    │
└─────────────┘            └─────────────┘    │
                                              │
┌─────────────────────────────────────────────┘
│  Sealed Class 상태 타입 (코드 정의)
│  - InventoryStatus: Available, Allocated, OnHold, ...
│  - LocationStatus: Empty, Occupied, Locked, ...
│  - InboundStatus: Expected, Inspecting, Completed, ...
│  - OutboundStatus: Pending, Allocated, Shipped, ...
└─────────────────────────────────────────────
```

---

## Appendix B: 설계 원칙 체크리스트

### 객체지향 설계 체크리스트
```
□ 모든 상태 변경은 해당 객체의 메서드를 통해서만 가능한가?
□ setter가 직접 노출되어 있지 않은가? (private set 또는 val 사용)
□ 상태 전이 규칙이 객체 내부에 정의되어 있는가?
□ 검증 로직이 도메인 객체 내부에 있는가?
□ 이력 기록이 도메인 메서드 내부에서 수행되는가?
```

### Sealed Class 상태 관리 체크리스트
```
□ 모든 상태가 Sealed Class로 정의되어 있는가?
□ 각 상태가 allowedTransitions()를 구현했는가?
□ 비즈니스 규칙(canAllocate, canPick 등)이 상태 객체에 있는가?
□ JPA AttributeConverter가 구현되어 있는가?
□ fromCode() companion object 메서드가 있는가?
```

### 로깅 체크리스트
```
□ 모든 쿼리에 QueryContext가 전달되는가?
□ 쿼리 의도(intent)가 명확히 기록되는가?
□ correlationId가 기록되는가?
□ 도메인 이벤트가 로깅되는가?
□ 느린 작업(1초 이상)이 WARN 레벨로 기록되는가?
```

### 예외 처리 체크리스트
```
□ 비즈니스 예외가 WmsException을 상속받는가?
□ 예외에 errorCode, message, details가 포함되는가?
□ GlobalExceptionHandler에서 모든 예외가 처리되는가?
□ 예외 발생 시 correlationId가 로깅되는가?
□ 민감한 정보가 에러 응답에 노출되지 않는가?
```

### HTTP 응답 체크리스트
```
□ 모든 응답이 HttpResponseSpec 형식을 따르는가?
□ X-Correlation-ID 헤더가 항상 포함되는가?
□ X-Request-ID 헤더가 항상 포함되는가?
□ 에러 응답에 errorCode와 details가 포함되는가?
□ 요청 정보(method, path, timestamp)가 응답에 포함되는가?
```

### 코딩 규칙 체크리스트 (coding-rules.md 참조)
```
□ Immutable 객체 원칙 준수 (Setter 금지, 빌더 패턴 사용)
□ 일급 컬렉션 사용 (Map 사용 금지)
□ DTO, Domain Object, Data Object 분리
□ 패키지 의존성 단방향 유지 (domain → controller → service → repository)
□ DB Join 최대 2개 테이블, Subquery 사유 입증 시만 허용
```

### 테스트 체크리스트 (testing.md 참조)
```
□ 핵심 비즈니스 로직 테스트 작성 완료
□ 테스트 커버리지 70% 이상
□ 모든 API E2E 테스트 작성
□ 외부 의존성 Mocking 처리
□ 테스트 계층 분리 (Unit/Integration/E2E)
```

---

*본 문서는 WMS 개발 시 참조용 지침서로 활용됩니다. 핵심 규칙(객체지향 설계, CQRS, 재고이력 필수, 정합성 불변규칙, Sealed Class 상태관리, 동시성 제어, 도메인 분리, 로깅, AOP 예외처리, HTTP 통신 규격)을 반드시 준수하여 구현합니다. 상세 코딩 규칙은 `coding-rules.md`, 테스트 가이드는 `testing.md`를 참조하세요.*
