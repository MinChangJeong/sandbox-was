package com.wms.domain.outbound.model

import com.wms.domain.common.AggregateRoot
import com.wms.domain.common.status.OutboundOrderStatus
import com.wms.domain.outbound.event.OutboundOrderCreatedEvent
import com.wms.domain.outbound.event.OutboundOrderStatusChangedEvent
import com.wms.domain.outbound.event.OutboundOrderAllocatedEvent
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "outbound_orders",
    indexes = [
        Index(name = "idx_outbound_customer_id", columnList = "customer_id"),
        Index(name = "idx_outbound_warehouse_id", columnList = "warehouse_id"),
        Index(name = "idx_outbound_status", columnList = "status"),
        Index(name = "idx_outbound_requested_date", columnList = "requested_date")
    ]
)
class OutboundOrder private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    
    @Column(name = "customer_id", nullable = false)
    val customerId: Long,
    
    @Column(name = "warehouse_id", nullable = false)
    val warehouseId: Long,
    
    @Convert(converter = OutboundOrderStatusConverter::class)
    @Column(name = "status", nullable = false, length = 50)
    private var _status: OutboundOrderStatus = OutboundOrderStatus.Pending,
    
    @Column(name = "requested_date", nullable = false)
    val requestedDate: LocalDateTime,
    
    @Column(name = "priority", nullable = false)
    val priority: Int = 0,
    
    @Column(name = "allocated_at", nullable = true)
    private var _allocatedAt: LocalDateTime? = null,
    
    @Column(name = "picked_at", nullable = true)
    private var _pickedAt: LocalDateTime? = null,
    
    @Column(name = "shipped_at", nullable = true)
    private var _shippedAt: LocalDateTime? = null,
    
    @OneToMany(
        mappedBy = "outboundOrder",
        fetch = FetchType.EAGER
    )
    private var _items: MutableList<OutboundOrderItem> = mutableListOf(),
    
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
    
    val status: OutboundOrderStatus get() = _status
    val allocatedAt: LocalDateTime? get() = _allocatedAt
    val pickedAt: LocalDateTime? get() = _pickedAt
    val shippedAt: LocalDateTime? get() = _shippedAt
    
    fun getItems(): List<OutboundOrderItem> = _items.toList()
    fun getItemCount(): Int = _items.size
    
    companion object {
        fun create(
            customerId: Long,
            warehouseId: Long,
            requestedDate: LocalDateTime,
            priority: Int,
            items: List<OutboundOrderItem>,
            createdBy: String
        ): OutboundOrder {
            require(customerId > 0) { "고객 ID는 필수입니다" }
            require(warehouseId > 0) { "창고 ID는 필수입니다" }
            require(requestedDate.isAfter(LocalDateTime.now())) { "요청 출고일은 현재 시간 이후여야 합니다" }
            require(priority in 1..5) { "우선순위는 1-5 사이여야 합니다" }
            require(items.isNotEmpty()) { "출고 항목은 최소 1개 이상이어야 합니다" }
            
            val order = OutboundOrder(
                customerId = customerId,
                warehouseId = warehouseId,
                requestedDate = requestedDate,
                priority = priority,
                _items = items.toMutableList(),
                _status = OutboundOrderStatus.Pending,
                createdBy = createdBy,
                updatedBy = createdBy
            )
            
            order.registerEvent(OutboundOrderCreatedEvent(
                outboundOrderId = order.id,
                customerId = customerId,
                warehouseId = warehouseId,
                requestedDate = requestedDate,
                priority = priority,
                itemCount = items.size,
                aggregateId = order.id
            ))
            
            return order
        }
        
        fun reconstruct(
            id: Long,
            customerId: Long,
            warehouseId: Long,
            status: OutboundOrderStatus,
            requestedDate: LocalDateTime,
            priority: Int,
            items: List<OutboundOrderItem>,
            allocatedAt: LocalDateTime?,
            pickedAt: LocalDateTime?,
            shippedAt: LocalDateTime?,
            createdAt: LocalDateTime,
            createdBy: String,
            updatedAt: LocalDateTime,
            updatedBy: String,
            isDeleted: Boolean
        ): OutboundOrder {
            return OutboundOrder(
                id = id,
                customerId = customerId,
                warehouseId = warehouseId,
                requestedDate = requestedDate,
                priority = priority,
                _items = items.toMutableList(),
                _status = status,
                _allocatedAt = allocatedAt,
                _pickedAt = pickedAt,
                _shippedAt = shippedAt,
                createdAt = createdAt,
                createdBy = createdBy,
                updatedAt = updatedAt,
                updatedBy = updatedBy,
                isDeleted = isDeleted
            )
        }
    }
    
    fun allocate(updatedBy: String) {
        require(_status == OutboundOrderStatus.Pending) {
            "할당은 대기 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(_items.isNotEmpty()) { "할당할 항목이 없습니다" }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Allocated
        _allocatedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "재고 할당 완료",
            aggregateId = this.id
        ))
        
        registerEvent(OutboundOrderAllocatedEvent(
            outboundOrderId = this.id,
            itemCount = _items.size,
            aggregateId = this.id
        ))
    }
    
    fun deallocate(updatedBy: String) {
        require(_status in listOf(OutboundOrderStatus.Allocated, OutboundOrderStatus.Picking)) {
            "할당 취소는 할당 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Deallocated
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "할당 취소",
            aggregateId = this.id
        ))
    }
    
    fun startPicking(updatedBy: String) {
        require(_status == OutboundOrderStatus.Allocated) {
            "피킹은 할당 상태에서만 시작 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Picking
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "피킹 시작",
            aggregateId = this.id
        ))
    }
    
    fun completePicking(updatedBy: String) {
        require(_status == OutboundOrderStatus.Picking) {
            "피킹 완료는 피킹 중 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Picked
        _pickedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "피킹 완료",
            aggregateId = this.id
        ))
    }
    
    fun pack(updatedBy: String) {
        require(_status == OutboundOrderStatus.Picked) {
            "패킹은 피킹 완료 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Packed
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "패킹 완료",
            aggregateId = this.id
        ))
    }
    
    fun ship(updatedBy: String) {
        require(_status == OutboundOrderStatus.Packed) {
            "출고는 패킹 완료 상태에서만 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Shipped
        _shippedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = "출고 완료",
            aggregateId = this.id
        ))
    }
    
    fun cancel(reason: String, updatedBy: String) {
        require(_status != OutboundOrderStatus.Shipped) {
            "출고 완료된 주문은 취소할 수 없습니다"
        }
        
        val previousStatus = _status
        _status = OutboundOrderStatus.Cancelled
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
        
        registerEvent(OutboundOrderStatusChangedEvent(
            outboundOrderId = this.id,
            previousStatus = previousStatus.code,
            newStatus = _status.code,
            reason = reason,
            aggregateId = this.id
        ))
    }
    
    fun addItem(item: OutboundOrderItem, updatedBy: String) {
        require(_status == OutboundOrderStatus.Pending) {
            "항목은 대기 상태에서만 추가 가능합니다. 현재 상태: ${_status.displayName}"
        }
        
        _items.add(item)
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
    
    fun removeItem(itemId: Long, updatedBy: String) {
        require(_status == OutboundOrderStatus.Pending) {
            "항목은 대기 상태에서만 제거 가능합니다. 현재 상태: ${_status.displayName}"
        }
        require(_items.size > 1) { "최소 1개의 항목은 유지되어야 합니다" }
        
        _items.removeIf { it.id == itemId }
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
}

@Entity
@Table(
    name = "outbound_order_items",
    indexes = [
        Index(name = "idx_ooitem_outbound_order_id", columnList = "outbound_order_id"),
        Index(name = "idx_ooitem_item_id", columnList = "item_id")
    ]
)
class OutboundOrderItem private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_order_id", nullable = false)
    var outboundOrder: OutboundOrder? = null,
    
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    
    @Column(name = "requested_qty", nullable = false)
    val requestedQty: Int,
    
    @Column(name = "allocated_qty", nullable = true)
    private var _allocatedQty: Int? = null,
    
    @Column(name = "picked_qty", nullable = true)
    private var _pickedQty: Int? = null,
    
    @Column(name = "shipped_qty", nullable = true)
    private var _shippedQty: Int? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: String = "",
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_by", nullable = false)
    var updatedBy: String = ""
) {
    val allocatedQty: Int? get() = _allocatedQty
    val pickedQty: Int? get() = _pickedQty
    val shippedQty: Int? get() = _shippedQty
    
    companion object {
        fun create(
            itemId: Long,
            requestedQty: Int,
            createdBy: String
        ): OutboundOrderItem {
            require(itemId > 0) { "품목 ID는 필수입니다" }
            require(requestedQty > 0) { "요청 수량은 1 이상이어야 합니다" }
            
            return OutboundOrderItem(
                itemId = itemId,
                requestedQty = requestedQty,
                createdBy = createdBy,
                updatedBy = createdBy
            )
        }
    }
    
    fun recordAllocation(allocatedQty: Int, updatedBy: String) {
        require(allocatedQty > 0 && allocatedQty <= requestedQty) {
            "할당 수량은 1 이상 요청 수량 이하여야 합니다"
        }
        _allocatedQty = allocatedQty
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
    
    fun recordPicking(pickedQty: Int, updatedBy: String) {
        require(pickedQty > 0 && (_allocatedQty == null || pickedQty <= _allocatedQty!!)) {
            "피킹 수량은 할당 수량 이하여야 합니다"
        }
        _pickedQty = pickedQty
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
    
    fun recordShipping(shippedQty: Int, updatedBy: String) {
        require(shippedQty > 0 && (_pickedQty == null || shippedQty <= _pickedQty!!)) {
            "출고 수량은 피킹 수량 이하여야 합니다"
        }
        _shippedQty = shippedQty
        updatedAt = LocalDateTime.now()
        this.updatedBy = updatedBy
    }
}

@Converter(autoApply = true)
class OutboundOrderStatusConverter : jakarta.persistence.AttributeConverter<OutboundOrderStatus, String> {
    override fun convertToDatabaseColumn(attribute: OutboundOrderStatus?): String? = attribute?.code
    override fun convertToEntityAttribute(dbData: String?): OutboundOrderStatus? =
        dbData?.let { OutboundOrderStatus.fromCode(it) }
}
