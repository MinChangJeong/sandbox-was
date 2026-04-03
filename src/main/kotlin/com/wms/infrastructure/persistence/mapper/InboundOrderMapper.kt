package com.wms.infrastructure.persistence.mapper

import com.wms.domain.inbound.model.InboundOrder
import com.wms.domain.inbound.model.InboundOrderItem
import com.wms.domain.common.status.InboundOrderStatus
import com.wms.infrastructure.persistence.entity.InboundOrderEntity
import com.wms.infrastructure.persistence.entity.InboundOrderItemEntity
import org.springframework.stereotype.Component

@Component
class InboundOrderMapper {
    
    fun toDomain(entity: InboundOrderEntity): InboundOrder {
        val items = entity.items.map { itemEntity ->
            toDomainItem(itemEntity)
        }
        
        return InboundOrder.reconstruct(
            id = entity.id,
            supplierId = entity.supplierId,
            warehouseId = entity.warehouseId,
            status = InboundOrderStatus.fromCode(entity.status),
            expectedDate = entity.expectedDate,
            items = items,
            inspectionCompletedAt = entity.inspectionCompletedAt,
            putawayStartedAt = entity.putawayStartedAt,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            createdBy = entity.createdBy,
            updatedAt = entity.updatedAt,
            updatedBy = entity.updatedBy,
            isDeleted = entity.isDeleted
        )
    }
    
    fun toDomainItem(entity: InboundOrderItemEntity): InboundOrderItem {
        val item = InboundOrderItem.create(
            itemId = entity.itemId,
            expectedQty = entity.expectedQty,
            createdBy = entity.createdBy
        )
        
        if (entity.inspectedQty != null && entity.inspectedQty > 0) {
            item.recordInspection(
                inspectedQty = entity.inspectedQty,
                acceptedQty = entity.acceptedQty ?: 0,
                rejectedQty = entity.rejectedQty ?: 0,
                rejectionReason = entity.rejectionReason,
                updatedBy = entity.updatedBy
            )
        }
        
        if (entity.putawayQty != null && entity.putawayQty > 0) {
            item.recordPutaway(entity.putawayQty, entity.updatedBy)
        }
        
        return item
    }
    
    fun toEntity(domain: InboundOrder): InboundOrderEntity {
        val entity = InboundOrderEntity(
            id = domain.id,
            supplierId = domain.supplierId,
            warehouseId = domain.warehouseId,
            status = domain.status.code,
            expectedDate = domain.expectedDate,
            inspectionCompletedAt = domain.inspectionCompletedAt,
            putawayStartedAt = domain.putawayStartedAt,
            completedAt = domain.completedAt,
            createdAt = domain.createdAt,
            createdBy = domain.createdBy,
            updatedAt = domain.updatedAt,
            updatedBy = domain.updatedBy,
            isDeleted = domain.isDeleted
        )
        
        val itemEntities = domain.getItems().map { item ->
            InboundOrderItemEntity(
                id = item.id,
                inboundOrder = entity,
                itemId = item.itemId,
                expectedQty = item.expectedQty,
                inspectedQty = item.inspectedQty,
                acceptedQty = item.acceptedQty,
                rejectedQty = item.rejectedQty,
                rejectionReason = item.rejectionReason,
                isInspectionCompleted = item.isInspectionCompleted,
                putawayQty = item.putawayQty,
                createdAt = item.createdAt,
                createdBy = item.createdBy,
                updatedAt = item.updatedAt,
                updatedBy = item.updatedBy
            )
        }
        
        return InboundOrderEntity(
            id = domain.id,
            supplierId = domain.supplierId,
            warehouseId = domain.warehouseId,
            status = domain.status.code,
            expectedDate = domain.expectedDate,
            inspectionCompletedAt = domain.inspectionCompletedAt,
            putawayStartedAt = domain.putawayStartedAt,
            completedAt = domain.completedAt,
            items = itemEntities,
            createdAt = domain.createdAt,
            createdBy = domain.createdBy,
            updatedAt = domain.updatedAt,
            updatedBy = domain.updatedBy,
            isDeleted = domain.isDeleted
        )
    }
}
