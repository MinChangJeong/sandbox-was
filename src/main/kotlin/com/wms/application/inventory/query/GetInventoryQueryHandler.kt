package com.wms.application.inventory.query

import com.wms.application.inventory.dto.InventoryDto
import com.wms.application.inventory.dto.PagedResponse
import com.wms.domain.inventory.repository.InventoryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetInventoryQueryHandler(
    private val inventoryRepository: InventoryRepository
) {
    
    private val logger = LoggerFactory.getLogger(GetInventoryQueryHandler::class.java)
    
    fun handle(criteria: InventorySearchCriteria): PagedResponse<InventoryDto> {
        logger.info("Searching inventories: itemId={}, locationId={}, status={}, page={}, size={}", 
            criteria.itemId, criteria.locationId, criteria.status, criteria.page, criteria.size)
        
        val sortOrder = if (criteria.sortDirection == SortDirection.ASC) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        
        val pageable = PageRequest.of(
            criteria.page,
            criteria.size,
            Sort.by(sortOrder, criteria.sortBy)
        )
        
        val page = inventoryRepository.searchWithCriteria(
            itemId = criteria.itemId,
            locationId = criteria.locationId,
            warehouseId = criteria.warehouseId,
            status = criteria.status,
            pageable = pageable
        )
        
        logger.info("Search completed: found {} inventories, totalElements={}", 
            page.content.size, page.totalElements)
        
        return PagedResponse.from(
            content = InventoryDto.fromDomainList(page.content),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements
        )
    }
}
