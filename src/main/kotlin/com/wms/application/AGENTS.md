# APPLICATION LAYER - AGENTS.md

**Purpose**: CQRS handlers, use cases, orchestration. Application logic (no domain rules).

---

## 📍 STRUCTURE

```
application/
├── inventory/
│   ├── command/            # State-changing operations
│   │   ├── adjustment/    # CreateAdjustmentCommand + Handler
│   │   ├── movement/      # CreateMovementCommand + Handler
│   │   └── ...
│   └── query/             # Read-only operations
│       ├── GetInventoryQuery.kt
│       └── InventorySearchCriteria.kt
├── warehouse/
│   ├── command/
│   ├── query/
│   └── service/
└── ... (zone, location, item)
```

---

## 🎯 CQRS PATTERN

### Commands (Modify State)

```kotlin
// 1. Command DTO
data class AllocateInventoryCommand(
    val outboundOrderId: Long,
    val itemId: Long,
    val requiredQty: Int,
    val strategy: AllocationStrategy = FIFO
)

// 2. Handler (@Service + @Transactional)
@Service
class AllocateInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository  // Domain port
) {
    @Transactional
    fun handle(command: AllocateInventoryCommand): AllocationResult {
        // 1. Fetch with pessimistic lock
        val inventories = inventoryRepository
            .findAllocatableInventoriesWithLock(command.itemId, command.requiredQty)
        
        // 2. Call domain method (orchestration, not logic)
        inventories.forEach { it.allocate(command.requiredQty, command.outboundOrderId) }
        
        // 3. Save via port
        inventories.forEach { inventoryRepository.save(it) }
        
        // 4. Publish events (infrastructure handles)
        return AllocationResult(inventories)
    }
}

// 3. REST endpoint
@RestController
@RequestMapping("/api/v1/inventory/commands")
class InventoryCommandController(
    private val allocateHandler: AllocateInventoryCommandHandler
) {
    @PostMapping("/allocate")
    fun allocate(@RequestBody req: AllocateRequest): ResponseEntity<AllocationResult> {
        return ResponseEntity.ok(allocateHandler.handle(req.toCommand()))
    }
}
```

### Queries (Read-Only)

```kotlin
// 1. Search Criteria DTO
data class InventorySearchCriteria(
    val itemId: Long? = null,
    val locationId: Long? = null,
    val status: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "createdAt",
    val sortDirection: SortDirection = DESC
)

// 2. Handler (@Service + @Transactional(readOnly = true))
@Service
@Transactional(readOnly = true)
class GetInventoryQueryHandler(
    private val inventoryRepository: InventoryRepository
) {
    fun handle(criteria: InventorySearchCriteria): PagedResponse<InventoryDto> {
        val page = inventoryRepository.searchWithCriteria(criteria)
        return PagedResponse(
            content = page.content.map { InventoryDto.fromDomain(it) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            hasPrevious = page.hasPrevious()
        )
    }
}

// 3. REST endpoint (separate from commands)
@RestController
@RequestMapping("/api/v1/inventory/queries")
class InventoryQueryController(
    private val getInventoryHandler: GetInventoryQueryHandler
) {
    @GetMapping
    fun search(
        @RequestParam itemId: Long?,
        @RequestParam locationId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedResponse<InventoryDto>> {
        val criteria = InventorySearchCriteria(itemId, locationId, page = page, size = size)
        return ResponseEntity.ok(getInventoryHandler.handle(criteria))
    }
}
```

---

## 📐 RESPONSE FORMAT (Always Paginated)

```kotlin
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

// DTOs use fromDomain() pattern (respects dependency direction)
data class InventoryDto(val id: Long, val itemId: Long, ...) {
    companion object {
        fun fromDomain(domain: Inventory): InventoryDto {
            return InventoryDto(
                id = domain.id,
                itemId = domain.itemId,
                // ... map all fields
            )
        }
    }
}
```

---

## 🎬 ORCHESTRATION (Application Logic)

Application layer orchestrates domain + infrastructure:

```kotlin
@Service
class AdjustInventoryCommandHandler(
    private val inventoryRepository: InventoryRepository,
    private val auditLog: AuditLog  // Infrastructure service
) {
    @Transactional
    fun handle(command: AdjustInventoryCommand) {
        // 1. Call domain port
        val inventory = inventoryRepository.findByIdWithLock(command.inventoryId)
        
        // 2. Domain object does all the logic
        inventory.adjust(command.adjustmentType, command.quantity, command.reason)
        
        // 3. Persist
        inventoryRepository.save(inventory)
        
        // 4. Call infrastructure (optional)
        auditLog.record(command.userId, inventory.id, "adjust")
    }
}
```

**Rules**:
- ✅ Call domain methods
- ✅ Use domain ports (repository, event publisher)
- ❌ NO business logic (validation, state transitions)
- ❌ NO HTTP calls (move to infrastructure)

---

## 🚫 ANTI-PATTERNS

| What | Why | Use Instead |
|------|-----|-------------|
| Business logic in handler | Duplicates domain logic | Call domain methods |
| Direct entity field access | Breaks encapsulation | Call domain methods only |
| Validation in handler | Belongs in domain | Domain::constructor or domain method |
| HTTP calls in handler | Violates architecture | Infrastructure adapters (events, messaging) |
| Mutable DTOs | Data consistency | Immutable data classes |
| Query handlers modifying state | Violates CQRS | Commands only modify state |

---

## 📋 CHECKLIST: Adding New Command Handler

- [ ] Command DTO is immutable (data class with val)
- [ ] Handler extends @Service + @Transactional
- [ ] Handler injects only PORTS (from domain/) not ADAPTERS
- [ ] Handler calls domain methods, not manipulates fields
- [ ] Saves via domain port (repository.save)
- [ ] Returns typed Result DTO (not PagedResponse for commands)
- [ ] REST endpoint calls handler, maps result
- [ ] No validation in handler (belongs in domain)

---

## 📋 CHECKLIST: Adding New Query Handler

- [ ] Query DTO contains search criteria + pagination params
- [ ] Handler extends @Service + @Transactional(readOnly = true)
- [ ] Handler calls repository query method
- [ ] Maps result to PagedResponse with metadata
- [ ] Response DTO uses fromDomain() pattern
- [ ] REST endpoint is GET (read-only)
- [ ] Separate controller from commands

---

## 🔄 DEPENDENCY FLOW

```
REST Request
    ↓ (DTO)
Controller
    ↓ (Command/Query)
Handler (Application)
    ↓ (Port interface)
Repository (Domain)
    ↓ (Adapter)
JpaRepository (Infrastructure)
```

**Each layer calls the layer below ONLY via ports/interfaces.**

