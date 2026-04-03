package com.wms.application.outbound.query

import com.wms.domain.outbound.repository.OutboundOrderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class OutboundOrderSearchCriteria(
    val customerId: Long? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "requestedDate",
    val sortDirection: Sort.Direction = Sort.Direction.DESC
) {
    fun toPageable(): Pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy))
}

data class OutboundOrderDto(
    val id: Long,
    val customerId: Long,
    val warehouseId: Long,
    val status: String,
    val requestedDate: LocalDateTime,
    val priority: Int,
    val itemCount: Int,
    val allocatedAt: LocalDateTime?,
    val pickedAt: LocalDateTime?,
    val shippedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val updatedAt: LocalDateTime,
    val updatedBy: String
)

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

@Service
@Transactional(readOnly = true)
class GetOutboundOrderQueryHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    fun handle(outboundOrderId: Long): OutboundOrderDto? {
        val order = outboundOrderRepository.findById(outboundOrderId)
        return order?.let { toDomain(it) }
    }
}

@Service
@Transactional(readOnly = true)
class SearchOutboundOrdersQueryHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    fun handle(criteria: OutboundOrderSearchCriteria): PagedResponse<OutboundOrderDto> {
        val (orders, totalCount) = outboundOrderRepository.findByCustmerIdOrderByRequestedDateDesc(
            criteria.customerId ?: 0L,
            criteria.toPageable()
        )
        
        val totalPages = ((totalCount.toInt() + criteria.size - 1) / criteria.size).toLong()
        
        return PagedResponse(
            content = orders.map { toDomain(it) },
            page = criteria.page,
            size = criteria.size,
            totalElements = totalCount,
            totalPages = totalPages.toInt(),
            hasNext = criteria.page < totalPages - 1,
            hasPrevious = criteria.page > 0
        )
    }
}

private fun toDomain(order: com.wms.domain.outbound.model.OutboundOrder): OutboundOrderDto =
    OutboundOrderDto(
        id = order.id,
        customerId = order.customerId,
        warehouseId = order.warehouseId,
        status = order.status.code,
        requestedDate = order.requestedDate,
        priority = order.priority,
        itemCount = order.getItemCount(),
        allocatedAt = order.allocatedAt,
        pickedAt = order.pickedAt,
        shippedAt = order.shippedAt,
        createdAt = order.createdAt,
        createdBy = order.createdBy,
        updatedAt = order.updatedAt,
        updatedBy = order.updatedBy
    )
