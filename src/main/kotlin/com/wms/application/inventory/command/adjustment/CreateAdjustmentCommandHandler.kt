package com.wms.application.inventory.command.adjustment

import com.wms.application.inventory.dto.CommandResult
import com.wms.domain.common.exception.WmsException
import com.wms.domain.inventory.repository.InventoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateAdjustmentCommandHandler(
    private val inventoryRepository: InventoryRepository
) {
    
    private val logger = LoggerFactory.getLogger(CreateAdjustmentCommandHandler::class.java)
    
    @Transactional
    fun handle(command: CreateAdjustmentCommand): CommandResult {
        logger.info("Processing adjustment command: inventoryId={}, type={}, qty={}", 
            command.inventoryId, command.adjustmentType, command.quantity)
        
        val inventory = inventoryRepository.findByIdWithLock(command.inventoryId)
            ?: throw WmsException.InventoryNotFound(command.inventoryId)
        
        logger.debug("Found inventory: id={}, current qty={}, allocated={}", 
            inventory.id, inventory.quantity, inventory.allocatedQty)
        
        inventory.adjust(
            adjustmentType = command.adjustmentType,
            quantity = command.quantity,
            reason = command.reason,
            updatedBy = command.createdBy
        )
        
        logger.debug("Adjusted inventory: new qty={}, reason={}", inventory.quantity, command.reason)
        
        inventoryRepository.save(inventory)
        
        logger.info("Adjustment completed successfully: inventoryId={}", inventory.id)
        
        return CommandResult(
            id = inventory.id,
            success = true,
            message = "재고 조정 완료"
        )
    }
}
