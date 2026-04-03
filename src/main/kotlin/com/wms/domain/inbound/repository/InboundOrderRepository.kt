package com.wms.domain.inbound.repository

import com.wms.domain.inbound.model.InboundOrder

interface InboundOrderRepository {
    fun findById(id: Long): InboundOrder?
    fun findByIdWithLock(id: Long): InboundOrder?
    fun save(inboundOrder: InboundOrder): InboundOrder
    fun update(inboundOrder: InboundOrder): InboundOrder
    fun delete(id: Long)
    fun findAll(): List<InboundOrder>
}
