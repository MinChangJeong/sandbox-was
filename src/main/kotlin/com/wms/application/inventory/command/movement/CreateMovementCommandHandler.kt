package com.wms.application.inventory.command.movement

import com.wms.application.inventory.dto.CommandResult
import com.wms.domain.common.exception.WmsException
import com.wms.domain.inventory.repository.InventoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMovementCommandHandler(
    private val inventoryRepository: InventoryRepository
) {
    
    private val logger = LoggerFactory.getLogger(CreateMovementCommandHandler::class.java)
    
    @Transactional
    fun handle(command: CreateMovementCommand): CommandResult {
        logger.info("Processing movement command: inventoryId={}, toLocation={}, qty={}", 
            command.inventoryId, command.toLocationId, command.quantity)
        
        val inventory = inventoryRepository.findByIdWithLock(command.inventoryId)
            ?: throw WmsException.InventoryNotFound(command.inventoryId)
        
        logger.debug("Source inventory: id={}, location={}, qty={}", 
            inventory.id, inventory.locationId, inventory.quantity)
        
        inventory.moveOut(
            quantity = command.quantity,
            toLocationId = command.toLocationId,
            updatedBy = command.createdBy
        )
        
        logger.debug("Source inventory after moveOut: qty={}", inventory.quantity)
        
        val sourceLocationId = inventory.locationId
        
        var targetInventory = inventoryRepository.findByItemIdAndLocationId(
            itemId = inventory.itemId,
            locationId = command.toLocationId
        )
        
        if (targetInventory == null) {
            logger.debug("Creating new target inventory at location={}", command.toLocationId)
            targetInventory = com.wms.domain.inventory.model.Inventory.create(
                itemId = inventory.itemId,
                locationId = command.toLocationId,
                quantity = command.quantity,
                createdBy = command.createdBy
            )
        } else {
            logger.debug("Target inventory exists: id={}, current qty={}", 
                targetInventory.id, targetInventory.quantity)
            targetInventory.moveIn(
                quantity = command.quantity,
                fromLocationId = sourceLocationId,
                updatedBy = command.createdBy
            )
            logger.debug("Target inventory after moveIn: qty={}", targetInventory.quantity)
        }
        
        inventoryRepository.save(inventory)
        inventoryRepository.save(targetInventory)
        
        logger.info("Movement completed successfully: source={}, target={}", 
            inventory.id, targetInventory.id)
        
        return CommandResult(
            id = inventory.id,
            success = true,
            message = "재고 이동 완료"
        )
    }
}
