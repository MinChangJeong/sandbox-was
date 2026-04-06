package com.wms.domain.inventory.event

import com.wms.domain.common.DomainEvent
import java.time.Instant
import java.util.*

data class InventoryAllocatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val inventoryId: Long,
    val allocatedQty: Int,
    val orderId: Long
) : DomainEvent

data class InventoryDeallocatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val inventoryId: Long,
    val deallocatedQty: Int,
    val reason: String
) : DomainEvent

data class InventoryAdjustedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val inventoryId: Long,
    val adjustmentType: String,
    val changeQty: Int,
    val reason: String
) : DomainEvent

data class InventoryMovedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val inventoryId: Long,
    val fromLocationId: Long,
    val toLocationId: Long,
    val movedQty: Int
) : DomainEvent

data class InventoryHistoryRecordedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "Inventory",
    
    val inventoryId: Long,
    val transactionType: String,
    val changeQuantity: Int,
    val beforeQuantity: Int,
    val afterQuantity: Int,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val reason: String? = null,
    val createdBy: String
) : DomainEvent
