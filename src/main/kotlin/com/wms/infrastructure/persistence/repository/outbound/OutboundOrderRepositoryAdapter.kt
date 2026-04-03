package com.wms.infrastructure.persistence.repository.outbound

import com.wms.domain.outbound.model.OutboundOrder
import com.wms.domain.outbound.repository.OutboundOrderRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class OutboundOrderRepositoryAdapter(
    private val jpaRepository: OutboundOrderJpaRepository
) : OutboundOrderRepository {
    
    override fun findById(id: Long): OutboundOrder? {
        return jpaRepository.findByIdIfExists(id)
    }
    
    override fun findByIdWithLock(id: Long): OutboundOrder? {
        return jpaRepository.findByIdWithLock(id)
    }
    
    override fun findByCustmerIdOrderByRequestedDateDesc(
        customerId: Long,
        pageable: Pageable
    ): Pair<List<OutboundOrder>, Long> {
        val page = jpaRepository.findByCustomerId(customerId, pageable)
        val totalCount = jpaRepository.countByCustomerId(customerId)
        return Pair(page.content, totalCount)
    }
    
    override fun findByIdIn(ids: List<Long>): List<OutboundOrder> {
        return jpaRepository.findAllById(ids)
    }
    
    override fun save(order: OutboundOrder): OutboundOrder {
        return jpaRepository.save(order)
    }
    
    override fun delete(id: Long) {
        jpaRepository.deleteById(id)
    }
}
