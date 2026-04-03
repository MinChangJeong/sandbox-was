package com.wms.domain.outbound.event

import com.wms.domain.common.BaseDomainEvent
import java.time.Instant
import java.util.UUID

data class OutboundOrderCreatedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val customerId: Long,
    val warehouseId: Long,
    val requestedDate: java.time.LocalDateTime,
    val priority: Int,
    val itemCount: Int,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderStatusChangedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val previousStatus: String,
    val newStatus: String,
    val reason: String,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderAllocatedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val itemCount: Int,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderPickingStartedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderPickedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val itemCount: Int,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderPackedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val itemCount: Int,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderShippedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val itemCount: Int,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderCancelledEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    val reason: String,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)

data class OutboundOrderDeallocatedEvent(
    override val aggregateId: Long,
    val outboundOrderId: Long,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, "OutboundOrder", eventId, occurredAt)
