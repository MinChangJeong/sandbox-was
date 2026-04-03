package com.wms.infrastructure.persistence.entity

import com.wms.domain.common.status.InboundOrderStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "inbound_orders",
    indexes = [
        Index(name = "idx_inbound_warehouse_id", columnList = "warehouse_id"),
        Index(name = "idx_inbound_supplier_id", columnList = "supplier_id"),
        Index(name = "idx_inbound_status", columnList = "status"),
        Index(name = "idx_inbound_expected_date", columnList = "expected_date")
    ]
)
class InboundOrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "supplier_id", nullable = false)
    val supplierId: Long,
    
    @Column(name = "warehouse_id", nullable = false)
    val warehouseId: Long,
    
    @Column(name = "status", nullable = false, length = 50)
    val status: String,
    
    @Column(name = "expected_date", nullable = false)
    val expectedDate: LocalDateTime,
    
    @Column(name = "inspection_completed_at", nullable = true)
    val inspectionCompletedAt: LocalDateTime? = null,
    
    @Column(name = "putaway_started_at", nullable = true)
    val putawayStartedAt: LocalDateTime? = null,
    
    @Column(name = "completed_at", nullable = true)
    val completedAt: LocalDateTime? = null,
    
    @OneToMany(
        mappedBy = "inboundOrder",
        fetch = FetchType.LAZY
    )
    val items: List<InboundOrderItemEntity> = emptyList(),
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: String = "",
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_by", nullable = false)
    val updatedBy: String = "",
    
    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,
    
    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false
)

@Entity
@Table(
    name = "inbound_order_items",
    indexes = [
        Index(name = "idx_ioitem_inbound_order_id", columnList = "inbound_order_id"),
        Index(name = "idx_ioitem_item_id", columnList = "item_id")
    ]
)
class InboundOrderItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_order_id", nullable = false)
    var inboundOrder: InboundOrderEntity? = null,
    
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    
    @Column(name = "expected_qty", nullable = false)
    val expectedQty: Int,
    
    @Column(name = "inspected_qty", nullable = true)
    val inspectedQty: Int? = null,
    
    @Column(name = "accepted_qty", nullable = true)
    val acceptedQty: Int? = null,
    
    @Column(name = "rejected_qty", nullable = true)
    val rejectedQty: Int? = null,
    
    @Column(name = "rejection_reason", nullable = true, length = 500)
    val rejectionReason: String? = null,
    
    @Column(name = "is_inspection_completed", nullable = false)
    val isInspectionCompleted: Boolean = false,
    
    @Column(name = "putaway_qty", nullable = true)
    val putawayQty: Int? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: String = "",
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_by", nullable = false)
    val updatedBy: String = ""
)
