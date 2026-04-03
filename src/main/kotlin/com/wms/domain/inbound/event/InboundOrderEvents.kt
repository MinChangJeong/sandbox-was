package com.wms.domain.inbound.event

import com.wms.domain.common.DomainEvent
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

data class InboundOrderCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "InboundOrder",
    
    val inboundOrderId: Long,
    val supplierId: Long,
    val warehouseId: Long,
    val expectedDate: LocalDateTime,
    val itemCount: Int
) : DomainEvent

data class InboundOrderStatusChangedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "InboundOrder",
    
    val inboundOrderId: Long,
    val previousStatus: String,
    val newStatus: String,
    val reason: String
) : DomainEvent

data class InboundItemInspectedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "InboundOrder",
    
    val inboundOrderId: Long,
    val inboundItemId: Long,
    val inspectedQty: Int,
    val acceptedQty: Int,
    val rejectedQty: Int
) : DomainEvent

data class InboundOrderPutawayStartedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: Long,
    override val aggregateType: String = "InboundOrder",
    
    val inboundOrderId: Long,
    val itemCount: Int
) : DomainEvent
