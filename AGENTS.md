# WMS PROJECT KNOWLEDGE BASE

**Generated:** 2026-04-03  
**Project:** Warehouse Management System (Spring Boot + Kotlin)  
**Phases Completed:** Phase 1 (Foundation), Phase 2 (Inventory Management)  
**Last Commit:** 99fb7bd (docs: Add Entity-Query compatibility rules)

---

## 🎯 PROJECT OVERVIEW

Kotlin/Spring Boot WMS implementing **Hexagonal Architecture** + **Domain-Driven Design** + **CQRS** patterns.

**Stack:**
- **Framework**: Spring Boot 3.2+ (Gradle 8.x)
- **Language**: Kotlin 1.9+
- **ORM**: Spring Data JPA + Hibernate
- **Database**: H2 (in-memory for development)
- **Architecture**: Hexagonal (Ports & Adapters)

**Key Principle:** All objects are active agents managing their own state (Tell, Don't Ask).

---

## 📁 STRUCTURE

```
├── src/
│   ├── main/kotlin/com/wms/
│   │   ├── WmsApplication.kt               # Spring Boot entry point
│   │   ├── domain/                         # Domain layer (DDD)
│   │   │   ├── inventory/                  # Phase 2: Inventory management
│   │   │   ├── warehouse/                  # Master data: Warehouse
│   │   │   ├── zone/                       # Master data: Zone
│   │   │   ├── location/                   # Master data: Location
│   │   │   ├── item/                       # Master data: Item
│   │   │   └── common/                     # Shared: entities, events, exceptions
│   │   ├── application/                    # Application layer (CQRS)
│   │   │   ├── inventory/                  # Commands (Adjustment, Movement)
│   │   │   ├── command/                    # Query handlers
│   │   │   ├── warehouse/ zone/ item/      # Master data handlers
│   │   ├── infrastructure/                 # Infrastructure layer
│   │   │   ├── persistence/                # JPA repositories + mappers
│   │   │   ├── web/                        # REST controllers
│   │   │   └── config/                     # Spring configuration
│   │   └── presentation/                   # REST API layer
│   └── test/                               # Integration + E2E tests
├── opencode/
│   ├── plan.md                             # Phase-by-phase implementation guide (**READ THIS**)
│   ├── coding-rules.md                     # Kotlin + DDD rules (immutability, no data class)
│   └── testing.md                          # Testing pyramid, coverage (70%+)
├── build.gradle.kts                        # Gradle configuration
└── logs/                                   # Gitignored
```

---

## 🗺️ WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add new domain entity | `src/main/kotlin/com/wms/domain/{domain}/model/` | AggregateRoot, immutable fields, domain methods |
| Add CQRS command | `src/main/kotlin/com/wms/application/{domain}/command/` | CommandHandler + @Transactional |
| Add query handler | `src/main/kotlin/com/wms/application/{domain}/query/` | @Transactional(readOnly=true), PagedResponse |
| Add REST endpoint | `src/main/kotlin/com/wms/infrastructure/web/` | Separate Command + Query controllers |
| Fix JPA queries | `src/main/kotlin/com/wms/infrastructure/persistence/repository/` | **See plan.md Section 3.8 for query rules!** |
| Add test | `src/test/kotlin/com/wms/{domain}/` | E2E tests, 70%+ coverage required |
| Master data logic | `src/main/kotlin/com/wms/domain/{warehouse\|zone\|location\|item}/` | Pure domain (no Spring deps) |
| Exception handling | `src/main/kotlin/com/wms/domain/common/exception/` | Custom WmsException hierarchy |

---

## 🏗️ ARCHITECTURE

### Layers (Dependency Direction: →)

```
presentation (REST DTOs, error handling)
     ↓
application (CQRS handlers, use cases)
     ↓
domain (entities, value objects, events, repositories PORT INTERFACE)
     ↓
infrastructure (JPA adapters, mappers, external I/O)
```

**Key Rule**: Domain has NO Spring dependencies. Application defines ports (interfaces), infrastructure implements adapters.

### Domain-Driven Design

- **Aggregate Roots**: Inventory, Warehouse, Location, Zone, Item
- **Value Objects**: InventoryStatus, LocationStatus, TransactionType (Sealed Classes)
- **Repositories** (Ports): InventoryRepository, WarehouseRepository, etc. (interfaces only in domain/)
- **Domain Events**: InventoryAllocatedEvent, InventoryDeallocatedEvent, etc.
- **Domain Services**: Business logic spanning multiple aggregates

### CQRS Pattern

- **Commands**: State-changing operations (CreateInventoryCommand, AllocateCommand)
  - Handler: `@Service + @Transactional` + pessimistic lock for inventory
  - Located: `application/{domain}/command/`
- **Queries**: Read-only operations (GetInventoryQuery)
  - Handler: `@Service + @Transactional(readOnly=true)`
  - Located: `application/{domain}/query/`

**Response Format**: Always use `PagedResponse<T>` for list queries (includes pagination metadata).

---

## ⚠️ CRITICAL RULES (From plan.md)

### 1. Entity-Query Compatibility (Section 3.8)

**PROBLEM**: Kotlin private backing fields + public getters cause JPQL runtime errors.

```kotlin
// ❌ WRONG - JPQL cannot access private backing field
class Inventory {
    private var _quantity: Int = 0
    val quantity: Int get() = _quantity
}

// ✅ CORRECT - Public field, JPQL accessible
class Inventory {
    @Column(name = "quantity")
    val quantity: Int = 0
}
```

**Checklist before @Query**:
- [ ] Entity fields are public or properly @Column mapped
- [ ] Computed properties have @Transient
- [ ] @Query parameters match method @Param exactly
- [ ] JPQL/Native SQL choice follows guidelines (Section 3.8.4)

### 2. Tell, Don't Ask (Section 1.1)

```kotlin
// ❌ Passive object pattern (FORBIDDEN)
inventory.allocatedQty += 10
inventory.status = "ALLOCATED"

// ✅ Active object pattern (REQUIRED)
inventory.allocate(qty = 10, orderId = orderId)  // handles all state changes internally
```

### 3. Immutability (from coding-rules.md)

- **NO data class**: Use regular `class` for domain entities
- **NO direct setters**: Use domain methods for state changes
- **NO mutable collections**: Use immutable by default, wrapper in domain service
- **Private var for internal state**: Expose via val getters only

### 4. Pessimistic Lock for Inventory

All inventory-changing operations MUST use `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Transactional`.

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query(nativeQuery = true, value = "SELECT * FROM inventories WHERE item_id = :itemId ...")
fun findAllocatable(@Param("itemId") itemId: Long): List<Inventory>
```

---

## 🛠️ COMMON PATTERNS

### Adding a New Command Handler

```kotlin
// 1. Command definition
data class AllocateInventoryCommand(val orderId: Long, val itemId: Long, val qty: Int)

// 2. Handler
@Service
class AllocateInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository
) {
    @Transactional
    fun handle(command: AllocateInventoryCommand) {
        val inventory = inventoryRepository.findByIdWithLock(command.inventoryId)
            ?: throw InventoryNotFoundException()
        
        inventory.allocate(command.qty, command.orderId)  // Domain method
        inventoryRepository.save(inventory)  // Adapter persistence
    }
}

// 3. Adapter call
class InventoryRepositoryAdapter(private val jpaRepo: InventoryJpaRepository) 
    : InventoryRepository {
    override fun findByIdWithLock(id: Long) = jpaRepo.findByIdWithLock(id)
}

// 4. REST endpoint
@RestController
@RequestMapping("/api/v1/inventory/commands")
class InventoryCommandController(private val handler: AllocateInventoryCommandHandler) {
    @PostMapping("/allocate")
    fun allocate(@RequestBody req: AllocateRequest) = 
        ResponseEntity.ok(handler.handle(req.toCommand()))
}
```

### Adding a Domain Event

```kotlin
// In domain entity
fun allocate(qty: Int, orderId: Long) {
    // ... validation
    _allocatedQty += qty
    
    registerEvent(InventoryAllocatedEvent(
        aggregateId = this.id,
        itemId = this.itemId,
        qty = qty,
        orderId = orderId
    ))
}

// In infrastructure event handler
@Component
class InventoryEventListener {
    @EventListener
    fun onInventoryAllocated(event: InventoryAllocatedEvent) {
        // External system integration (HTTP, message queue, etc.)
    }
}
```

---

## 📋 CONVENTIONS (Kotlin + Spring + DDD)

### Naming
- **Aggregates**: PascalCase (Inventory, Warehouse)
- **Commands**: PascalCase + "Command" (AllocateInventoryCommand)
- **Queries**: PascalCase + "Query" (GetInventoryQuery)
- **Events**: PascalCase + "Event" (InventoryAllocatedEvent)
- **Repositories (ports)**: PascalCase + "Repository" interface, DTO suffix for response
- **Repository methods**: `find*`, `search*`, `*WithLock` for locking
- **Domain methods**: verb-based, camelCase (allocate, deallocate, adjust)

### Sealed Classes (for State)
```kotlin
sealed class InventoryStatus(val code: String) {
    data object Available : InventoryStatus("AVAILABLE")
    data object Allocated : InventoryStatus("ALLOCATED")
    // ...
    companion object {
        fun fromCode(code: String): InventoryStatus = when (code) { ... }
    }
}
```

### DTOs (Request/Response)
- **Request**: No validation done in DTO, done in command handler
- **Response**: Use `fromDomain()` companion function (data class OK here)
- **Pagination**: Wrap in `PagedResponse<T>` with metadata

---

## 🚫 ANTI-PATTERNS (THIS PROJECT)

### Absolute Prohibitions

| Pattern | Why | Alternative |
|---------|-----|-------------|
| Setter methods on domain entities | Breaks Tell-Don't-Ask principle | Use domain methods (allocate, adjust, etc.) |
| data class for domain entities | Generates unwanted copy(), equals(), hashCode(), setter | Regular class with sealed class for state |
| Direct manipulation of private fields from outside | Breaks encapsulation | Always use domain methods |
| @Query accessing private backing fields | JPQL runtime errors | See Section 3.8: public fields or Native SQL |
| @Query without parameter validation | Crashes at Spring startup | Use checklist (Section 3.8.3) |
| Mutable collections in domain | Breaks invariants | Immutable by default, document mutation points |
| HTTP calls inside domain/application layer | Violates Hexagonal Architecture | Move to infrastructure adapters |
| Empty catch blocks | Hides errors | Always log and rethrow or handle meaningfully |
| `as any` or `@ts-ignore` for type errors | Defeats Kotlin's type safety | Fix root cause, never suppress |
| Global state or static fields in domain | Non-deterministic behavior | Inject dependencies |
| Committing with auto-generated messages | Breaks commit history | Atomic commits, semantic messages |

---

## 💡 GOTCHAS

### 1. Private Backing Fields + JPQL
**Symptom**: `PathElementException: Could not resolve attribute 'quantity'`  
**Cause**: Private backing field `_quantity` is not accessible to JPQL  
**Fix**: Read plan.md Section 3.8.1 for correct Entity design

### 2. Gradle Build Success ≠ Runtime Success
**Symptom**: `gradle build OK` but `gradle bootRun FAILED`  
**Cause**: Spring Bean initialization validates @Query at startup, not compile time  
**Fix**: Follow Section 3.8.3 @Query parameter checklist strictly

### 3. Computed Properties in @Query
**Symptom**: `availableQty: Int get() = quantity - allocatedQty` crashes at startup  
**Cause**: DB has no `availableQty` column  
**Fix**: Mark as `@Transient`, use raw columns in @Query (Section 3.8.2)

### 4. Method Signature ≠ @Query Binding
**Symptom**: `parameter 'warehouseId' not found in annotated query` at startup  
**Cause**: Adapter passes warehouseId but @Query doesn't bind it  
**Fix**: Use checklist (Section 3.8.3) - **ALL** @Param must match :paramName

---

## 📚 DOCUMENTATION MAP

| File | Purpose | When to Read |
|------|---------|--------------|
| **plan.md** (Section 3.8) | Entity-Query compatibility rules | **BEFORE writing @Query** |
| **coding-rules.md** | Kotlin immutability, DDD rules | Design new entities |
| **testing.md** | Test pyramid, coverage targets | Write tests (70%+ required) |
| **AGENTS.md** (this file) | Quick navigation | Start here |
| **Sub-AGENTS.md** | Layer/domain-specific | When working in specific modules |

---

## 🔧 QUICK COMMANDS

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Test
./gradlew test

# Format
./gradlew spotlessApply

# Dependency check
./gradlew dependencyCheck

# Git (commits only on explicit request)
git status
git add .
git commit -m "feat: Add X" OR "fix: Bug Y" OR "docs: Update Z"
```

---

## 🚀 NEXT STEPS

**Immediate (before Phase 3):**
- [ ] Review plan.md Section 3.8 (Entity-Query rules) - critical for Inbound phase
- [ ] Update Inventory entities if needed (now public fields or readonly)
- [ ] Add @Query validation tests

**Phase 3 (Inbound Management):**
- [ ] Implement InboundOrder aggregate + events
- [ ] Follow same patterns: domain methods → CQRS handlers → REST endpoints
- [ ] 70%+ test coverage

**Phase 4+ (Outbound + Reports):**
- [ ] Complex CQRS queries with state transitions
- [ ] Consider QueryDSL for type-safe queries
- [ ] Pessimistic lock coordination across multiple aggregates

---

## 📞 QUICK REFERENCE

- **Architecture issue**: Read STRUCTURE + ARCHITECTURE sections
- **Query fails**: Read plan.md Section 3.8 (Entity-Query compatibility)
- **Adding new feature**: Read COMMON PATTERNS section
- **Test coverage**: Read coding-rules.md + testing.md
- **Git commits**: Never auto-commit (explicit "git commit" only)

