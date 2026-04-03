# DOMAIN LAYER - AGENTS.md

**Purpose**: Pure domain logic, no Spring dependencies. Heart of DDD.

---

## 📍 STRUCTURE

```
domain/
├── inventory/               # Phase 2 - Inventory Management
│   ├── model/              # Inventory, InventoryHistory (AggregateRoots)
│   ├── event/              # InventoryAllocatedEvent, etc.
│   └── repository/         # InventoryRepository (PORT interface)
├── warehouse/              # Master data - Warehouse aggregate
├── zone/                   # Master data - Zone (child of Warehouse)
├── location/               # Master data - Location (child of Zone)
├── item/                   # Master data - Item (product catalog)
└── common/                 # Shared infrastructure (BaseEntity, Events, Exceptions)
```

---

## 🎯 KEY PATTERNS

### 1. AggregateRoot (Tell-Don't-Ask)

All state changes via domain methods:
```kotlin
// ✅ REQUIRED
class Inventory : AggregateRoot() {
    fun allocate(qty: Int, orderId: Long) {  // Domain method
        // Self-validates, self-modifies, records history
        // Publishes domain events
    }
}

// ❌ FORBIDDEN
inventory.allocatedQty += 10  // Direct manipulation
inventory.status = "ALLOCATED"  // No traceability
```

### 2. Immutability + Private Backing Fields

```kotlin
class Inventory {
    private var _quantity: Int = 0  // PRIVATE
    private var _status: InventoryStatus  // PRIVATE
    
    val quantity: Int get() = _quantity  // PUBLIC getter (read-only)
    val status: InventoryStatus get() = _status  // PUBLIC getter
    
    // Never expose setters - use domain methods only
}
```

### 3. Value Objects (Sealed Classes)

```kotlin
sealed class InventoryStatus(val code: String) {
    data object Available : InventoryStatus("AVAILABLE")
    data object Allocated : InventoryStatus("ALLOCATED")
    
    // State machines live in value objects
    fun allowedTransitions(): Set<KClass<out InventoryStatus>> = when(this) {
        Available -> setOf(Allocated::class, OnHold::class)
        Allocated -> setOf(Available::class, Picked::class)
        // ...
    }
}
```

### 4. Repository Ports (Interfaces Only)

```kotlin
// Domain defines WHAT (interface)
interface InventoryRepository {
    fun findByIdWithLock(id: Long): Inventory?
    fun save(inventory: Inventory): Inventory
}

// Infrastructure implements HOW (Adapter)
// @Repository
// class InventoryRepositoryAdapter(jpaRepo: InventoryJpaRepository) 
//     : InventoryRepository { ... }
```

### 5. Domain Events

Events record ALL state changes (audit trail + external notifications):
```kotlin
// In AggregateRoot
fun allocate(qty: Int, orderId: Long) {
    _allocatedQty += qty
    registerEvent(InventoryAllocatedEvent(id, qty, orderId))
}

// Events stored temporarily, published after @Transactional commit
```

---

## 🚫 PROHIBITED IN DOMAIN

| What | Why | Use Instead |
|------|-----|-------------|
| Spring annotations | Breaks independence | Pure Kotlin only |
| HTTP calls | Violates architecture | Use infrastructure adapters |
| Direct DB access | Not testable | Via repository port |
| Mutable collections | Breaks invariants | Immutable wrapper |
| Static fields | Non-deterministic | Constructor injection |
| data class | Auto-generates setters | Regular class |

---

## 📋 CHECKLIST: Adding New Domain Entity

- [ ] Create aggregate root class extending AggregateRoot
- [ ] Private backing fields (`var`) for mutable state
- [ ] Public getters only (`val` with `get()`)
- [ ] All state changes via domain methods (verbs: allocate, deallocate, etc.)
- [ ] Record state change in history/events
- [ ] Value objects as Sealed Class if applicable
- [ ] Repository port as interface (no @Repository, no JPA)
- [ ] No Spring imports in domain package

---

## 🎬 NEXT PHASE (3: Inbound)

When adding InboundOrder aggregate:
1. Follow same patterns as Inventory
2. Use domain methods for state transitions
3. Record domain events for each state change
4. Create port interface in domain/
5. Implement adapter in infrastructure/

