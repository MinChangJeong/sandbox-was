package com.wms.infrastructure.persistence.repository.outbound

import com.wms.domain.outbound.model.OutboundOrder
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OutboundOrderJpaRepository : JpaRepository<OutboundOrder, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboundOrder o WHERE o.id = :id AND o.isDeleted = false")
    fun findByIdWithLock(@Param("id") id: Long): OutboundOrder?
    
    @Query("SELECT o FROM OutboundOrder o WHERE o.id = :id AND o.isDeleted = false")
    fun findByIdIfExists(@Param("id") id: Long): OutboundOrder?
    
    @Query(
        nativeQuery = true,
        value = """
            SELECT * FROM outbound_orders 
            WHERE customer_id = :customerId AND is_deleted = false
            ORDER BY requested_date DESC
        """
    )
    fun findByCustomerId(
        @Param("customerId") customerId: Long,
        pageable: Pageable
    ): org.springframework.data.domain.Page<OutboundOrder>
    
    @Query(
        nativeQuery = true,
        value = """
            SELECT COUNT(*) FROM outbound_orders 
            WHERE customer_id = :customerId AND is_deleted = false
        """
    )
    fun countByCustomerId(@Param("customerId") customerId: Long): Long
    
    @Query(
        nativeQuery = true,
        value = """
            SELECT * FROM outbound_orders 
            WHERE warehouse_id = :warehouseId AND is_deleted = false
        """
    )
    fun findByWarehouseId(@Param("warehouseId") warehouseId: Long): List<OutboundOrder>
    
    @Query(
        nativeQuery = true,
        value = """
            SELECT * FROM outbound_orders 
            WHERE status = :status AND is_deleted = false
        """
    )
    fun findByStatus(@Param("status") status: String): List<OutboundOrder>
    
    @Query(
        nativeQuery = true,
        value = """
            SELECT * FROM outbound_orders 
            WHERE is_deleted = false
            ORDER BY created_at DESC
        """
    )
    fun findAllNotDeleted(): List<OutboundOrder>
}
