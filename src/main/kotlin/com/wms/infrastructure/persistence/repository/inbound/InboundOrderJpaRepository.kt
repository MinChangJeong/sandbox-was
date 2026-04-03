package com.wms.infrastructure.persistence.repository.inbound

import com.wms.infrastructure.persistence.entity.InboundOrderEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InboundOrderJpaRepository : JpaRepository<InboundOrderEntity, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT io FROM InboundOrderEntity io WHERE io.id = :id AND io.isDeleted = false")
    fun findByIdWithLock(@Param("id") id: Long): InboundOrderEntity?
    
    @Query("SELECT io FROM InboundOrderEntity io WHERE io.id = :id AND io.isDeleted = false")
    fun findByIdIfExists(@Param("id") id: Long): InboundOrderEntity?
    
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM inbound_orders WHERE is_deleted = false ORDER BY created_at DESC"
    )
    fun findAllNotDeleted(): List<InboundOrderEntity>
    
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM inbound_orders WHERE warehouse_id = :warehouseId AND is_deleted = false"
    )
    fun findByWarehouseId(@Param("warehouseId") warehouseId: Long): List<InboundOrderEntity>
    
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM inbound_orders WHERE supplier_id = :supplierId AND is_deleted = false"
    )
    fun findBySupplierId(@Param("supplierId") supplierId: Long): List<InboundOrderEntity>
    
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM inbound_orders WHERE status = :status AND is_deleted = false"
    )
    fun findByStatus(@Param("status") status: String): List<InboundOrderEntity>
}
