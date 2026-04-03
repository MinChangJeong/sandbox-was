package com.wms.application.inbound.query

import com.wms.domain.inbound.repository.InboundOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class InboundOrderDto(
    val id: Long,
    val supplierId: Long,
    val warehouseId: Long,
    val status: String,
    val statusDisplayName: String,
    val expectedDate: LocalDateTime,
    val itemCount: Int,
    val inspectionCompletedAt: LocalDateTime?,
    val putawayStartedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val createdBy: String
) {
    companion object {
        fun fromDomain(domain: com.wms.domain.inbound.model.InboundOrder): InboundOrderDto {
            return InboundOrderDto(
                id = domain.id,
                supplierId = domain.supplierId,
                warehouseId = domain.warehouseId,
                status = domain.status.code,
                statusDisplayName = domain.status.displayName,
                expectedDate = domain.expectedDate,
                itemCount = domain.getItemCount(),
                inspectionCompletedAt = domain.inspectionCompletedAt,
                putawayStartedAt = domain.putawayStartedAt,
                completedAt = domain.completedAt,
                createdAt = domain.createdAt,
                createdBy = domain.createdBy
            )
        }
    }
}

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

data class InboundOrderSearchCriteria(
    val supplierId: Long? = null,
    val warehouseId: Long? = null,
    val status: String? = null,
    val expectedDateFrom: LocalDateTime? = null,
    val expectedDateTo: LocalDateTime? = null,
    val page: Int = 0,
    val size: Int = 20
)

@Service
@Transactional(readOnly = true)
class GetInboundOrderQueryHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    fun handle(id: Long): InboundOrderDto {
        val inboundOrder = inboundOrderRepository.findById(id)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $id")
        return InboundOrderDto.fromDomain(inboundOrder)
    }
}

@Service
@Transactional(readOnly = true)
class SearchInboundOrdersQueryHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    fun handle(criteria: InboundOrderSearchCriteria): PagedResponse<InboundOrderDto> {
        val allOrders = inboundOrderRepository.findAll()
        
        val filtered = allOrders.filter { order ->
            (criteria.supplierId == null || order.supplierId == criteria.supplierId) &&
            (criteria.warehouseId == null || order.warehouseId == criteria.warehouseId) &&
            (criteria.status == null || order.status.code == criteria.status) &&
            (criteria.expectedDateFrom == null || order.expectedDate.isAfter(criteria.expectedDateFrom)) &&
            (criteria.expectedDateTo == null || order.expectedDate.isBefore(criteria.expectedDateTo))
        }
        
        val total = filtered.size.toLong()
        val totalPages = (total + criteria.size - 1) / criteria.size
        val hasNext = criteria.page < totalPages - 1
        val hasPrevious = criteria.page > 0
        
        val paginatedContent = filtered
            .sortedByDescending { it.createdAt }
            .drop(criteria.page * criteria.size)
            .take(criteria.size)
            .map { InboundOrderDto.fromDomain(it) }
        
        return PagedResponse(
            content = paginatedContent,
            page = criteria.page,
            size = criteria.size,
            totalElements = total,
            totalPages = totalPages.toInt(),
            hasNext = hasNext,
            hasPrevious = hasPrevious
        )
    }
}
