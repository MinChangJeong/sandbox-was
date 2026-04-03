# INBOUND DOMAIN - AGENTS.md

**Purpose**: Phase 3 InboundOrder aggregate - manage supplier receipts from EXPECTED through COMPLETED states.

---

## 📋 INBOUND ORDER LIFECYCLE

```
[EXPECTED] → [INSPECTING] → [INSPECTED] → [PUTAWAY_IN_PROGRESS] → [COMPLETED]
    ↓
[REJECTED] → [RETURNED/DISPOSED]
```

---

## 🎯 KEY AGGREGATES

### InboundOrder (AggregateRoot)
- **States**: Expected, Inspecting, Inspected, PutawayInProgress, Completed, Rejected
- **Domain Methods**:
  - `startInspection()` - EXPECTED → INSPECTING
  - `inspectItem(itemId, inspectedQty, acceptedQty, rejectedQty)` - Record item inspection
  - `completeInspection()` - INSPECTING → INSPECTED (all items must be inspected)
  - `startPutaway()` - INSPECTED → PUTAWAY_IN_PROGRESS
  - `complete()` - PUTAWAY_IN_PROGRESS → COMPLETED
  - `reject(reason)` - Any state → REJECTED

### InboundOrderItem (Value Object + Entity)
- Fields: itemId, expectedQty, inspectedQty, acceptedQty, rejectedQty, putawayQty
- Methods:
  - `recordInspection(inspectedQty, acceptedQty, rejectedQty, rejectionReason)`
  - `recordPutaway(putawayQty)`

---

## 🛣️ API ROUTES

### Commands
```
POST /api/v1/inbound-orders/commands
  → CreateInboundOrderCommand
  
POST /api/v1/inbound-orders/commands/{id}/inspection/start
  → StartInboundInspectionCommand
  
PUT /api/v1/inbound-orders/commands/{id}/items/{itemId}/inspection
  → InspectInboundItemCommand
  
POST /api/v1/inbound-orders/commands/{id}/inspection/complete
  → CompleteInboundInspectionCommand
  
POST /api/v1/inbound-orders/commands/{id}/putaway
  → StartInboundPutawayCommand
  
POST /api/v1/inbound-orders/commands/{id}/complete
  → CompleteInboundOrderCommand
  
POST /api/v1/inbound-orders/commands/{id}/reject
  → RejectInboundOrderCommand
```

### Queries
```
GET /api/v1/inbound-orders/queries/{id}
  → GetInboundOrderQueryHandler
  → InboundOrderDto
  
GET /api/v1/inbound-orders/queries
  ?supplierId=&warehouseId=&status=&page=&size=
  → SearchInboundOrdersQueryHandler
  → PagedResponse<InboundOrderDto>
```

---

## 💾 DATA ACCESS

### JPA Repository
- **Entity**: InboundOrderEntity (main), InboundOrderItemEntity (items)
- **Repository**: InboundOrderJpaRepository (Spring Data)
- **Adapter**: InboundOrderRepositoryAdapter (implements domain port)
- **Mapper**: InboundOrderMapper (domain ↔ entity transformation)

### Query Methods (Follow Section 3.8 Entity-Query Rules)
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT io FROM InboundOrderEntity io WHERE io.id = :id AND io.isDeleted = false")
fun findByIdWithLock(@Param("id") id: Long): InboundOrderEntity?

@Query(nativeQuery = true, value = "SELECT * FROM inbound_orders WHERE is_deleted = false ...")
fun findAllNotDeleted(): List<InboundOrderEntity>
```

---

## 🧪 TESTING

**Test File**: `src/test/kotlin/com/wms/application/inbound/InboundOrderManagementE2ETest.kt`

**Test Cases** (11 total):
1. createInboundOrderSuccessfully()
2. createInboundOrderAndStartInspection()
3. inspectItemRecordsInspectionData()
4. completeInspectionFlow()
5. startPutawayAfterInspection()
6. completeInboundOrderFlow()
7. searchInboundOrdersWithPagination()
8. searchInboundOrdersFilterByStatus()
9. searchInboundOrdersFilterBySupplier()
10. searchInboundOrdersFilterByWarehouse()
11. fullInboundOrderLifecycle()

---

## ⚠️ CRITICAL RULES

1. **State Transitions**: Only via domain methods (startInspection, completeInspection, etc.)
2. **Validation**: All items must be inspected before completeInspection() succeeds
3. **Pessimistic Lock**: Use @Lock(PESSIMISTIC_WRITE) on all mutating queries
4. **Entity-Query Compatibility**: Follow plan.md Section 3.8 for @Query rules
5. **Domain Events**: Published after state changes (not currently persisted, only registered)

---

## 📂 FILE STRUCTURE

```
domain/inbound/
├── model/
│   ├── InboundOrder.kt (AggregateRoot + InboundOrderItem value object)
│   └── [This file documents both entities]
├── event/
│   └── InboundOrderEvents.kt (4 event types)
└── repository/
    └── InboundOrderRepository.kt (port interface)

application/inbound/
├── command/
│   └── InboundOrderCommandHandlers.kt (6 command handlers)
└── query/
    └── InboundOrderQueryHandlers.kt (2 query handlers)

infrastructure/
├── persistence/
│   ├── entity/InboundOrderEntity.kt (JPA entities)
│   ├── repository/inbound/InboundOrderJpaRepository.kt
│   ├── repository/inbound/InboundOrderRepositoryAdapter.kt
│   └── mapper/InboundOrderMapper.kt
└── web/controller/inbound/InboundOrderController.kt (REST endpoints)

test/kotlin/com/wms/application/inbound/
└── InboundOrderManagementE2ETest.kt (E2E integration tests)
```

---

## 🚀 NEXT: PHASE 4 (OUTBOUND)

When implementing outbound management, follow the same patterns:
1. Domain aggregate with state machine
2. CQRS command/query handlers
3. JPA persistence with Entity-Query compatibility
4. REST endpoints (separate command/query controllers)
5. E2E integration tests

Reference: `/domain/AGENTS.md` + `/application/AGENTS.md` + `/infrastructure/persistence/AGENTS.md`
