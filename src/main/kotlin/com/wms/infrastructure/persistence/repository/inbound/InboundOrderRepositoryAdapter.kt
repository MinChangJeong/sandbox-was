package com.wms.infrastructure.persistence.repository.inbound

import com.wms.domain.inbound.model.InboundOrder
import com.wms.domain.inbound.repository.InboundOrderRepository
import com.wms.infrastructure.persistence.mapper.InboundOrderMapper
import org.springframework.stereotype.Repository

@Repository
class InboundOrderRepositoryAdapter(
    private val jpaRepository: InboundOrderJpaRepository,
    private val mapper: InboundOrderMapper
) : InboundOrderRepository {
    
    override fun findById(id: Long): InboundOrder? {
        return jpaRepository.findByIdIfExists(id)?.let { mapper.toDomain(it) }
    }
    
    override fun findByIdWithLock(id: Long): InboundOrder? {
        return jpaRepository.findByIdWithLock(id)?.let { mapper.toDomain(it) }
    }
    
    override fun save(inboundOrder: InboundOrder): InboundOrder {
        val entity = mapper.toEntity(inboundOrder)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }
    
    override fun update(inboundOrder: InboundOrder): InboundOrder {
        val entity = mapper.toEntity(inboundOrder)
        val updated = jpaRepository.save(entity)
        return mapper.toDomain(updated)
    }
    
    override fun delete(id: Long) {
        jpaRepository.deleteById(id)
    }
    
    override fun findAll(): List<InboundOrder> {
        return jpaRepository.findAllNotDeleted().map { mapper.toDomain(it) }
    }
}
