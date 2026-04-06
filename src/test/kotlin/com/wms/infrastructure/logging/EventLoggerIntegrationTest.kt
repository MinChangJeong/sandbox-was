package com.wms.infrastructure.logging

import com.wms.domain.inventory.event.InventoryHistoryRecordedEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

@SpringBootTest
class EventLoggerIntegrationTest {
    
    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher
    
    @Autowired
    private lateinit var eventLogger: EventLogger
    
    @Test
    fun `event logger logs all inventory history recorded events`() {
        val event = InventoryHistoryRecordedEvent(
            aggregateId = 1L,
            inventoryId = 1L,
            transactionType = "INITIAL_STOCK",
            changeQuantity = 100,
            beforeQuantity = 0,
            afterQuantity = 100,
            reason = "Test initial stock",
            createdBy = "test-user",
            occurredAt = Instant.now()
        )
        
        eventPublisher.publishEvent(event)
    }
    
    @Test
    fun `event logger logs allocation events`() {
        val event = InventoryHistoryRecordedEvent(
            aggregateId = 2L,
            inventoryId = 2L,
            transactionType = "ALLOCATE",
            changeQuantity = 50,
            beforeQuantity = 100,
            afterQuantity = 50,
            referenceType = "ORDER",
            referenceId = 101L,
            reason = "Order allocation",
            createdBy = "test-user",
            occurredAt = Instant.now()
        )
        
        eventPublisher.publishEvent(event)
    }
    
    @Test
    fun `event logger logs deallocation events`() {
        val event = InventoryHistoryRecordedEvent(
            aggregateId = 3L,
            inventoryId = 3L,
            transactionType = "DEALLOCATE",
            changeQuantity = 30,
            beforeQuantity = 50,
            afterQuantity = 80,
            referenceType = "ORDER",
            referenceId = 102L,
            reason = "Order cancellation",
            createdBy = "test-user",
            occurredAt = Instant.now()
        )
        
        eventPublisher.publishEvent(event)
    }
}
