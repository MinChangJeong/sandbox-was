package com.wms.infrastructure.logging

import com.wms.domain.inventory.event.InventoryHistoryRecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class EventLogger {
    
    private val logger = LoggerFactory.getLogger(EventLogger::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_INSTANT
    
    @EventListener
    fun onInventoryHistoryRecorded(event: InventoryHistoryRecordedEvent) {
        logger.info("""
            |=== DOMAIN EVENT: InventoryHistoryRecorded ===
            |Event Type: {}
            |Inventory ID: {}
            |Transaction Type: {}
            |Change Quantity: {}
            |Before Quantity: {}
            |After Quantity: {}
            |Reason: {}
            |Created By: {}
            |Timestamp: {}
        """.trimMargin(),
            event::class.simpleName,
            event.inventoryId,
            event.transactionType,
            event.changeQuantity,
            event.beforeQuantity,
            event.afterQuantity,
            event.reason,
            event.createdBy,
            dateFormatter.format(event.occurredAt)
        )
    }
}
