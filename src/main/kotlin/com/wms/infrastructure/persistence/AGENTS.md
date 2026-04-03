# INFRASTRUCTURE/PERSISTENCE LAYER - AGENTS.md

**Purpose**: JPA adapters, mappers, repository implementations. Database integration.

---

## 📍 STRUCTURE

```
infrastructure/persistence/
├── repository/          # JPA Repository interfaces (Spring Data)
│   ├── InventoryJpaRepository.kt
│   ├── ItemJpaRepository.kt
│   └── ...
├── adapter/            # Implement domain ports (Hexagonal adapters)
│   ├── InventoryRepositoryAdapter.kt
│   └── ...
├── mapper/             # Domain ↔ JPA Entity transformation
│   ├── InventoryMapper.kt
│   └── ...
```

---

## ⚠️ CRITICAL: Entity-Query Compatibility (Section 3.8)

**Kotlin + JPA = Runtime Errors if Not Careful**

### Private Backing Fields BREAK JPQL

```kotlin
// ❌ WRONG - JPQL cannot access private field
@Entity
class Inventory {
    @Column(name = "quantity")
    private var _quantity: Int = 0
    
    val quantity: Int get() = _quantity
}

// @Query will FAIL at Spring startup:
// PathElementException: Could not resolve attribute 'quantity'

// ✅ CORRECT
@Entity
class Inventory {
    @Column(name = "quantity")
    val quantity: Int = 0  // PUBLIC
}
```

### JPQL vs Native SQL

```kotlin
// ✅ JPQL (type-safe, preferred)
@Query("SELECT i FROM Inventory i WHERE i.quantity > 0")

// ✅ Native SQL (when needed - DB functions, complex logic)
@Query(nativeQuery = true, value = "SELECT * FROM inventories WHERE quantity > 0")

// ❌ NEVER mix: Don't access private fields in either!
@Query("... WHERE i._quantity > 0")  // BOTH fail
```

### @Query Parameter Validation CHECKLIST

```
Before writing any @Query:
[ ] Entity fields are PUBLIC or have @Column + public getter
[ ] Computed properties (@Transient) are NOT in @Query
[ ] All :paramName in @Query exist in method @Param
[ ] All @Param in method are used in @Query
[ ] Parameter types match (Long, String, Enum, etc.)
[ ] IDE type checker shows no errors
```

---

## 🔒 PESSIMISTIC LOCK FOR INVENTORY

ALL inventory mutations MUST lock:

```kotlin
@Repository
interface InventoryJpaRepository : JpaRepository<Inventory, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    fun findByIdWithLock(@Param("id") id: Long): Inventory?
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(nativeQuery = true, value = """
        SELECT * FROM inventories i
        WHERE (i.quantity - i.allocated_qty) > 0
        ORDER BY i.created_at ASC
    """)
    fun findAllocatableWithLock(@Param("itemId") itemId: Long): List<Inventory>
}
```

**Why**: Concurrent allocation requests must serialize on DB row lock.

---

## 🔄 MAPPER PATTERN (Domain ↔ JPA Entity)

```kotlin
class InventoryMapper {
    fun toEntity(domain: Inventory): InventoryJpaEntity {
        return InventoryJpaEntity(
            id = domain.id,
            itemId = domain.itemId,
            quantity = domain.quantity,
            allocatedQty = domain.allocatedQty
            // ... map all fields
        )
    }
    
    fun toDomain(entity: InventoryJpaEntity): Inventory {
        // Reconstruct domain object from entity
        // May need custom constructor accepting entity
    }
}
```

---

## 🚫 ANTI-PATTERNS IN THIS LAYER

| What | Why | Alternative |
|------|-----|-------------|
| Domain logic in adapters | Duplicates logic | Call domain methods from adapter |
| Spring annotations in mapper | Mixes concerns | Pure Kotlin mapper |
| @Query on computed properties | JPQL doesn't see them | Mark @Transient + use DB columns |
| Method signature ≠ @Query params | Spring startup crash | Use checklist (see above) |
| Optimistic Lock without @Version | Lost updates possible | Add @Version for master data |

---

## 📋 CHECKLIST: Adding New Adapter

- [ ] Create interface extending JpaRepository in repository/
- [ ] Implement domain port interface in adapter/
- [ ] All entity fields PUBLIC or @Column + public getter
- [ ] @Lock(PESSIMISTIC_WRITE) on mutation queries
- [ ] @Query parameters validated via checklist
- [ ] Mapper converts Entity ↔ Domain
- [ ] No domain logic in adapter (thin adapter)
- [ ] Tests verify @Query parameter binding works

---

## 🔧 QUERY DEBUGGING

**If @Query fails at Spring startup:**

1. Read error: `PathElementException: Could not resolve attribute`
2. Check: Is field private with only public getter?
3. Fix: Change to public field OR use Native SQL
4. Verify: Field has @Column annotation
5. Test: `./gradlew bootRun` (must succeed)

**If parameter mismatch:**

1. Error: `parameter 'X' not found in annotated query`
2. Check: All @Param in method signature are in :paramName in @Query
3. Check: All :paramName in @Query are in method @Param
4. Test: IDE type checker on method (should show error if mismatched)

