package com.wms.infrastructure.persistence.adapter

import com.wms.domain.inventory.model.Inventory
import com.wms.domain.inventory.repository.InventoryRepository
import com.wms.domain.inventory.repository.InventoryHistoryRepository
import com.wms.infrastructure.persistence.mapper.InventoryMapper
import com.wms.infrastructure.persistence.repository.InventoryJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class InventoryRepositoryAdapter(
    private val jpaRepository: InventoryJpaRepository,
    private val mapper: InventoryMapper,
    private val historyRepository: InventoryHistoryRepository
) : InventoryRepository {
    
    override fun findById(id: Long): Inventory? {
        return jpaRepository.findById(id).orElse(null)?.let { mapper.toDomain(it) }
    }
    
    override fun findByItemIdAndLocationId(itemId: Long, locationId: Long): Inventory? {
        return jpaRepository.findByItemIdAndLocationId(itemId, locationId)?.let { mapper.toDomain(it) }
    }
    
    override fun findByIdWithLock(id: Long): Inventory? {
        return jpaRepository.findByIdWithLock(id)?.let { mapper.toDomain(it) }
    }
    
    override fun findAllocatableWithLock(itemId: Long, requiredQty: Int): List<Inventory> {
        return jpaRepository.findAllocatableWithLock(itemId)
            .map { mapper.toDomain(it) }
            .filter { it.availableQty >= requiredQty }
    }
    
    override fun findByItemId(itemId: Long): List<Inventory> {
        return jpaRepository.findByItemId(itemId)
            .map { mapper.toDomain(it) }
    }
    
    override fun findByLocationId(locationId: Long): List<Inventory> {
        return jpaRepository.findByLocationId(locationId)
            .map { mapper.toDomain(it) }
    }
    
    override fun findAll(pageable: Pageable): Page<Inventory> {
        return jpaRepository.findAll(pageable)
            .map { mapper.toDomain(it) }
    }
    
    override fun searchWithCriteria(
        itemId: Long?,
        locationId: Long?,
        warehouseId: Long?,
        status: String?,
        pageable: Pageable
    ): Page<Inventory> {
        return jpaRepository.searchWithCriteria(
            itemId = itemId,
            locationId = locationId,
            status = status,
            pageable = pageable
        ).map { mapper.toDomain(it) }
    }
    
    override fun save(inventory: Inventory): Inventory {
        val isNewEntity = inventory.id == 0L
        val saved = jpaRepository.save(mapper.toEntity(inventory))
        val savedDomain = mapper.toDomain(saved)
        
        if (isNewEntity && savedDomain.id > 0) {
            // ✅ 규칙 1 준수: Domain 객체의 ID가 할당된 후에야 INITIAL_STOCK 이력 기록
            savedDomain.recordInitialStockHistory(inventory.createdBy)
        }
        
        // ✅ 재고 변경 이력 영속화 (Domain이 recordHistory()로 기록한 모든 이력)
        persistInventoryHistories(savedDomain)
        return savedDomain
    }
    
    private fun persistInventoryHistories(inventory: Inventory) {
        val histories = inventory.getHistories()
        if (histories.isNotEmpty()) {
            historyRepository.saveAll(histories)
        }
    }
    
    override fun saveAll(inventories: List<Inventory>): List<Inventory> {
        val saved = jpaRepository.saveAll(inventories.map { mapper.toEntity(it) })
            .map { mapper.toDomain(it) }
        
        inventories.forEach { persistInventoryHistories(it) }
        
        return saved
    }
    
    override fun delete(inventory: Inventory) {
        jpaRepository.deleteById(inventory.id)
    }
}
