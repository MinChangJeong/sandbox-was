package com.wms.domain.outbound.repository

import com.wms.domain.outbound.model.OutboundOrder
import org.springframework.data.domain.Pageable

interface OutboundOrderRepository {
    fun save(order: OutboundOrder): OutboundOrder
    fun findById(id: Long): OutboundOrder?
    fun findByIdWithLock(id: Long): OutboundOrder?
    fun findByCustmerIdOrderByRequestedDateDesc(customerId: Long, pageable: Pageable): Pair<List<OutboundOrder>, Long>
    fun findByIdIn(ids: List<Long>): List<OutboundOrder>
    fun delete(id: Long)
}
