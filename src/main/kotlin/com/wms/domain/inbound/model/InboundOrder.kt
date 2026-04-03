package com.wms.domain.inbound.model

import com.wms.domain.common.AggregateRoot
import com.wms.domain.common.status.InboundOrderStatus
import com.wms.domain.inbound.event.InboundOrderCreatedEvent
import com.wms.domain.inbound.event.InboundOrderStatusChangedEvent
import com.wms.domain.inbound.event.InboundItemInspectedEvent
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * InboundOrder - 입고예정 집계근(AggregateRoot)
 *
 * 상태 흐름:
 * EXPECTED → INSPECTING → INSPECTED → PUTAWAY_IN_PROGRESS → COMPLETED
 *     ↓
 * REJECTED → RETURNED/DISPOSED
 *
 * 도메인 메서드를 통해서만 상태 변경 가능 (Tell-Don't-Ask)
 */
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
class InboundOrder private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    
    @Column(name = "supplier_id", nullable = false)
    val supplierId: Long,
    
    @Column(name = "warehouse_id", nullable = false)
    val warehouseId: Long,
    
    @Convert(converter = InboundOrderStatusConverter::class)
    @Column(name = "status", nullable = false, length = 50)
    private var _status: InboundOrderStatus = InboundOrderStatus.Expected,
    
    @Column(name = "expected_date", nullable = false)
    val expectedDate: LocalDateTime,
    
    @Column(name = "inspection_completed_at", nullable = true)
    private var _inspectionCompletedAt: LocalDateTime? = null,
    
    @Column(name = "putaway_started_at", nullable = true)
    private var _putawayStartedAt: LocalDateTime? = null,
    
    @Column(name = "completed_at", nullable = true)
    private var _completedAt: LocalDateTime? = null,
    
    @OneToMany(
        mappedBy = "inboundOrder",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private var _items: MutableList<InboundOrderItem> = mutableListOf(),
    
    @Column(name = "created_at", nullable = false, updatable = false)
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_by", nullable = false, updatable = false)
    override var createdBy: String = "",
    
    @Column(name = "updated_at", nullable = false)
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_by", nullable = false)
    override var updatedBy: String = "",
    
    @Column(name = "is_deleted", nullable = false)
    override var isDeleted: Boolean = false
) : AggregateRoot() {
    
    // 읽기 전용 프로퍼티
    val status: InboundOrderStatus get() = _status
    val inspectionCompletedAt: LocalDateTime? get() = _inspectionCompletedAt
    val putawayStartedAt: LocalDateTime? get() = _putawayStartedAt
    val completedAt: LocalDateTime? get() = _completedAt
    
    fun getItems(): List<InboundOrderItem> = _items.toList()
    fun getItemCount(): Int = _items.size
    
    companion object {
        fun create(
            supplierId: Long,
            warehouseId: Long,
            expectedDate: LocalDateTime,
            items: List<InboundOrderItem>,
            createdBy: String
        ): InboundOrder {
            require(supplierId > 0) { "공급사 ID는 필수입니다" }
            require(warehouseId > 0) { "창고 ID는 필수입니다" }
            require(expectedDate.isAfter(LocalDateTime.now())) { "예상 입고일은 현재 시간 이후여야 합니다" }
            require(items.isNotEmpty()) { "입고 항목은 최소 1개 이상이어야 합니다" }
            
            val order = InboundOrder(
                supplierId = supplierId,
                warehouseId = warehouseId,
                expectedDate = expectedDate,
                _items = items.toMutableList(),
                _status = InboundOrderStatus.Expected,
                createdBy = createdBy,
                updatedBy = createdBy
            )
            
            order.registerEvent(InboundOrderCreatedEvent(
                inboundOrderId = order.id,
                supplierId = supplierId,
                warehouseId = warehouseId,
                expectedDate = expectedDate,
                itemCount = items.size,
                aggregateId = order.id
            ))
            
            return order
        }
        
        fun reconstruct(
            id: Long,
            supplierId: Long,
            warehouseId: Long,
            status: InboundOrderStatus,
            expectedDate: LocalDateTime,
            items: List<InboundOrderItem>,
            inspectionCompletedAt: LocalDateTime?,
            putawayStartedAt: LocalDateTime?,
            completedAt: LocalDateTime?,
            createdAt: LocalDateTime,
            createdBy: String,
            updatedAt: LocalDateTime,
            updatedBy: String,
            isDeleted: Boolean
        ): InboundOrder {
            return InboundOrder(
                id = id,
                supplierId = supplierId,
                warehouseId = warehouseId,
                expectedDate = expectedDate,
                _items = items.toMutableList(),
                _status = status,
                _inspectionCompletedAt = inspectionCompletedAt,
                _putawayStartedAt = putawayStartedAt,
                _completedAt = completedAt,
                createdAt = createdAt,
                createdBy = createdBy,
                updatedAt = updatedAt,
                updatedBy = updatedBy,
                isDeleted = isDeleted
            )
        }
    }
    
    /**
     * 검수 시작 (EXPECTED → INSPECTING)
     */
    fun startInspection(updatedBy: String) {
        require(_status == InboundOrderStatus.Expected) {
            "검수는 예상 상태에서만 시작 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(_items.isNotEmpty()) { "검수할 항목이 없습니다" }
        
        val previousStatus = _status
        _status = InboundOrderStatus.Inspecting
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundOrderStatusChangedEvent(
            inboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "검수 시작",
            aggregateId = this.id
        ))
    }
    
    /**
     * 항목 검수 완료
     */
    fun inspectItem(
        itemId: Long,
        inspectedQty: Int,
        acceptedQty: Int,
        rejectedQty: Int,
        rejectionReason: String? = null,
        updatedBy: String
    ) {
        require(_status == InboundOrderStatus.Inspecting) {
            "항목 검수는 검수 중 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(inspectedQty > 0) { "검수 수량은 1 이상이어야 합니다" }
        require(acceptedQty + rejectedQty == inspectedQty) {
            "수용 + 거절 수량이 검수 수량과 일치해야 합니다"
        }
        
        val item = _items.find { it.id == itemId }
            ?: throw IllegalArgumentException("항목을 찾을 수 없습니다: $itemId")
        
        item.recordInspection(
            inspectedQty = inspectedQty,
            acceptedQty = acceptedQty,
            rejectedQty = rejectedQty,
            rejectionReason = rejectionReason,
            updatedBy = updatedBy
        )
        
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundItemInspectedEvent(
            inboundOrderId = this.id,
            inboundItemId = itemId,
            inspectedQty = inspectedQty,
            acceptedQty = acceptedQty,
            rejectedQty = rejectedQty,
            aggregateId = this.id
        ))
    }
    
    /**
     * 검수 완료 (INSPECTING → INSPECTED)
     */
    fun completeInspection(updatedBy: String) {
        require(_status == InboundOrderStatus.Inspecting) {
            "검수 완료는 검수 중 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        // 모든 항목이 검수 완료되었는지 확인
        require(_items.all { it.isInspectionCompleted }) {
            "모든 항목의 검수가 완료되어야 합니다"
        }
        
        val previousStatus = _status
        _status = InboundOrderStatus.Inspected
        _inspectionCompletedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundOrderStatusChangedEvent(
            inboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "검수 완료",
            aggregateId = this.id
        ))
    }
    
    /**
     * 적치 시작 (INSPECTED → PUTAWAY_IN_PROGRESS)
     */
    fun startPutaway(updatedBy: String) {
        require(_status == InboundOrderStatus.Inspected) {
            "적치는 검수 완료 상태에서만 시작 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = InboundOrderStatus.PutawayInProgress
        _putawayStartedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundOrderStatusChangedEvent(
            inboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "적치 시작",
            aggregateId = this.id
        ))
    }
    
    /**
     * 완료 (PUTAWAY_IN_PROGRESS → COMPLETED)
     */
    fun complete(updatedBy: String) {
        require(_status == InboundOrderStatus.PutawayInProgress) {
            "완료는 적치 진행 중 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = InboundOrderStatus.Completed
        _completedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundOrderStatusChangedEvent(
            inboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "입고 완료",
            aggregateId = this.id
        ))
    }
    
    /**
     * 거절 (EXPECTED/INSPECTING → REJECTED)
     */
    fun reject(reason: String, updatedBy: String) {
        require(_status in listOf(InboundOrderStatus.Expected, InboundOrderStatus.Inspecting)) {
            "거절은 예상 또는 검수 중 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(reason.isNotBlank()) { "거절 사유는 필수입니다" }
        
        val previousStatus = _status
        _status = InboundOrderStatus.Rejected
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(InboundOrderStatusChangedEvent(
            inboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = reason,
            aggregateId = this.id
        ))
    }
    
    /**
     * 항목 추가 (EXPECTED 상태에서만 가능)
     */
    fun addItem(item: InboundOrderItem, updatedBy: String) {
        require(_status == InboundOrderStatus.Expected) {
            "항목은 예상 상태에서만 추가 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(_items.none { it.itemId == item.itemId }) {
            "이미 추가된 항목입니다: ${item.itemId}"
        }
        
        _items.add(item)
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
    
    /**
     * 항목 제거 (EXPECTED 상태에서만 가능)
     */
    fun removeItem(itemId: Long, updatedBy: String) {
        require(_status == InboundOrderStatus.Expected) {
            "항목은 예상 상태에서만 제거 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(_items.size > 1) { "최소 1개의 항목은 유지되어야 합니다" }
        
        _items.removeIf { it.itemId == itemId }
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
}

/**
 * InboundOrderItem - 입고예정 항목 (Value Object + Entity)
 */
@Entity
@Table(
    name = "inbound_order_items",
    indexes = [
        Index(name = "idx_ioitem_inbound_order_id", columnList = "inbound_order_id"),
        Index(name = "idx_ioitem_item_id", columnList = "item_id")
    ]
)
class InboundOrderItem private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_order_id", nullable = false)
    val inboundOrder: InboundOrder? = null,
    
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    
    @Column(name = "expected_qty", nullable = false)
    val expectedQty: Int,
    
    @Column(name = "inspected_qty", nullable = true)
    private var _inspectedQty: Int? = null,
    
    @Column(name = "accepted_qty", nullable = true)
    private var _acceptedQty: Int? = null,
    
    @Column(name = "rejected_qty", nullable = true)
    private var _rejectedQty: Int? = null,
    
    @Column(name = "rejection_reason", nullable = true, length = 500)
    private var _rejectionReason: String? = null,
    
    @Column(name = "is_inspection_completed", nullable = false)
    private var _isInspectionCompleted: Boolean = false,
    
    @Column(name = "putaway_qty", nullable = true)
    private var _putawayQty: Int? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: String = "",
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_by", nullable = false)
    var updatedBy: String = ""
) {
    // 읽기 전용 프로퍼티
    val inspectedQty: Int? get() = _inspectedQty
    val acceptedQty: Int? get() = _acceptedQty
    val rejectedQty: Int? get() = _rejectedQty
    val rejectionReason: String? get() = _rejectionReason
    val isInspectionCompleted: Boolean get() = _isInspectionCompleted
    val putawayQty: Int? get() = _putawayQty
    
    companion object {
        fun create(
            itemId: Long,
            expectedQty: Int,
            createdBy: String
        ): InboundOrderItem {
            require(itemId > 0) { "품목 ID는 필수입니다" }
            require(expectedQty > 0) { "예상 수량은 1 이상이어야 합니다" }
            
            return InboundOrderItem(
                itemId = itemId,
                expectedQty = expectedQty,
                createdBy = createdBy,
                updatedBy = createdBy
            )
        }
    }
    
    fun recordInspection(
        inspectedQty: Int,
        acceptedQty: Int,
        rejectedQty: Int,
        rejectionReason: String? = null,
        updatedBy: String
    ) {
        require(inspectedQty > 0) { "검수 수량은 1 이상이어야 합니다" }
        require(acceptedQty >= 0) { "수용 수량은 0 이상이어야 합니다" }
        require(rejectedQty >= 0) { "거절 수량은 0 이상이어야 합니다" }
        require(acceptedQty + rejectedQty == inspectedQty) {
            "수용 + 거절 수량이 검수 수량과 일치해야 합니다"
        }
        
        _inspectedQty = inspectedQty
        _acceptedQty = acceptedQty
        _rejectedQty = rejectedQty
        _rejectionReason = rejectionReason
        _isInspectionCompleted = true
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
    
    fun recordPutaway(putawayQty: Int, updatedBy: String) {
        require(putawayQty > 0) { "적치 수량은 1 이상이어야 합니다" }
        require(_acceptedQty != null && putawayQty <= _acceptedQty!!) {
            "적치 수량이 수용 수량을 초과할 수 없습니다"
        }
        
        _putawayQty = putawayQty
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
}

@Converter(autoApply = true)
class InboundOrderStatusConverter : jakarta.persistence.AttributeConverter<InboundOrderStatus, String> {
    override fun convertToDatabaseColumn(attribute: InboundOrderStatus?): String? = attribute?.code
    override fun convertToEntityAttribute(dbData: String?): InboundOrderStatus? =
        dbData?.let { InboundOrderStatus.fromCode(it) }
}
