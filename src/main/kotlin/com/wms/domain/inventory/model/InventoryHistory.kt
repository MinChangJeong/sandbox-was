package com.wms.domain.inventory.model

import com.wms.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "inventory_histories",
    indexes = [
        Index(name = "idx_inventory_id", columnList = "inventory_id"),
        Index(name = "idx_transaction_type", columnList = "transaction_type"),
        Index(name = "idx_created_at", columnList = "created_at"),
        Index(name = "idx_reference", columnList = "reference_type,reference_id")
    ]
)
class InventoryHistory private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    
    @Column(name = "inventory_id", nullable = false)
    val inventoryId: Long,
    
    @Column(name = "transaction_type", nullable = false, length = 50)
    val transactionType: String,
    
    @Column(name = "change_quantity", nullable = false)
    val changeQuantity: Int,
    
    @Column(name = "before_quantity", nullable = false)
    val beforeQuantity: Int,
    
    @Column(name = "after_quantity", nullable = false)
    val afterQuantity: Int,
    
    @Column(name = "allocated_qty_before")
    val allocatedQtyBefore: Int? = null,
    
    @Column(name = "allocated_qty_after")
    val allocatedQtyAfter: Int? = null,
    
    @Column(name = "reference_type", length = 50)
    val referenceType: String? = null,
    
    @Column(name = "reference_id")
    val referenceId: Long? = null,
    
    @Column(name = "reason", columnDefinition = "TEXT")
    val reason: String? = null,
    
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var createdBy: String = "",
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
    override var updatedBy: String = "",
    override var isDeleted: Boolean = false
) : BaseEntity() {
    
    companion object {
        fun create(
            inventoryId: Long,
            transactionType: String,
            changeQuantity: Int,
            beforeQuantity: Int,
            afterQuantity: Int,
            referenceType: String? = null,
            referenceId: Long? = null,
            reason: String? = null,
            createdBy: String,
            allocatedQtyBefore: Int? = null,
            allocatedQtyAfter: Int? = null
        ): InventoryHistory {
            require(inventoryId > 0) { "재고 ID는 필수입니다" }
            require(transactionType.isNotBlank()) { "거래 유형은 필수입니다" }
            require(beforeQuantity >= 0) { "변경 전 수량은 0 이상이어야 합니다" }
            require(afterQuantity >= 0) { "변경 후 수량은 0 이상이어야 합니다" }
            require(beforeQuantity + changeQuantity == afterQuantity) { 
                "수량 계산이 맞지 않습니다: $beforeQuantity + $changeQuantity != $afterQuantity"
            }
            
            if (allocatedQtyBefore != null && allocatedQtyAfter != null) {
                require(allocatedQtyBefore >= 0) { "할당 전 수량은 0 이상이어야 합니다" }
                require(allocatedQtyAfter >= 0) { "할당 후 수량은 0 이상이어야 합니다" }
            }
            
            return InventoryHistory(
                inventoryId = inventoryId,
                transactionType = transactionType,
                changeQuantity = changeQuantity,
                beforeQuantity = beforeQuantity,
                afterQuantity = afterQuantity,
                allocatedQtyBefore = allocatedQtyBefore,
                allocatedQtyAfter = allocatedQtyAfter,
                referenceType = referenceType,
                referenceId = referenceId,
                reason = reason,
                createdBy = createdBy,
                updatedBy = createdBy
            )
        }
    }
}
