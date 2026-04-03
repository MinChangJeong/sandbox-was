package com.wms.domain.outbound.model

import com.wms.domain.common.status.OutboundOrderStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OutboundOrderTest {
    
    @Test
    fun `should create outbound order successfully`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val requestedDate = LocalDateTime.now().plusDays(1)
        
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = requestedDate,
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        assertEquals(OutboundOrderStatus.Pending, order.status)
        assertEquals(100L, order.customerId)
        assertEquals(200L, order.warehouseId)
        assertEquals(1, order.getItemCount())
        assertNotNull(order.createdAt)
    }
    
    @Test
    fun `should fail create with invalid customer id`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        
        assertThrows<IllegalArgumentException> {
            OutboundOrder.create(
                customerId = 0L,
                warehouseId = 200L,
                requestedDate = LocalDateTime.now().plusDays(1),
                priority = 3,
                items = items,
                createdBy = "user1"
            )
        }
    }
    
    @Test
    fun `should fail create with invalid warehouse id`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        
        assertThrows<IllegalArgumentException> {
            OutboundOrder.create(
                customerId = 100L,
                warehouseId = 0L,
                requestedDate = LocalDateTime.now().plusDays(1),
                priority = 3,
                items = items,
                createdBy = "user1"
            )
        }
    }
    
    @Test
    fun `should fail create with invalid requested date`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        
        assertThrows<IllegalArgumentException> {
            OutboundOrder.create(
                customerId = 100L,
                warehouseId = 200L,
                requestedDate = LocalDateTime.now().minusDays(1),
                priority = 3,
                items = items,
                createdBy = "user1"
            )
        }
    }
    
    @Test
    fun `should fail create with invalid priority`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        
        assertThrows<IllegalArgumentException> {
            OutboundOrder.create(
                customerId = 100L,
                warehouseId = 200L,
                requestedDate = LocalDateTime.now().plusDays(1),
                priority = 6,
                items = items,
                createdBy = "user1"
            )
        }
    }
    
    @Test
    fun `should fail create with empty items`() {
        assertThrows<IllegalArgumentException> {
            OutboundOrder.create(
                customerId = 100L,
                warehouseId = 200L,
                requestedDate = LocalDateTime.now().plusDays(1),
                priority = 3,
                items = emptyList(),
                createdBy = "user1"
            )
        }
    }
    
    @Test
    fun `should transition from pending to allocated`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        
        assertEquals(OutboundOrderStatus.Allocated, order.status)
        assertNotNull(order.allocatedAt)
    }
    
    @Test
    fun `should fail allocate when not in pending status`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        
        assertThrows<IllegalArgumentException> {
            order.allocate("user3")
        }
    }
    
    @Test
    fun `should transition through complete picking workflow`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        order.startPicking("user2")
        order.completePicking("user2")
        
        assertEquals(OutboundOrderStatus.Picked, order.status)
        assertNotNull(order.pickedAt)
    }
    
    @Test
    fun `should transition through full workflow to shipped`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        order.startPicking("user2")
        order.completePicking("user2")
        order.pack("user2")
        order.ship("user2")
        
        assertEquals(OutboundOrderStatus.Shipped, order.status)
        assertNotNull(order.shippedAt)
    }
    
    @Test
    fun `should cancel order and update status`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        order.cancel("Customer cancelled", "user2")
        
        assertEquals(OutboundOrderStatus.Cancelled, order.status)
    }
    
    @Test
    fun `should fail cancel when already shipped`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        order.startPicking("user2")
        order.completePicking("user2")
        order.pack("user2")
        order.ship("user2")
        
        assertThrows<IllegalArgumentException> {
            order.cancel("Too late", "user2")
        }
    }
    
    @Test
    fun `should add item to pending order`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        val newItem = OutboundOrderItem.create(itemId = 2L, requestedQty = 5, createdBy = "user1")
        order.addItem(newItem, "user2")
        
        assertEquals(2, order.getItemCount())
    }
    
    @Test
    fun `should fail add item when not pending`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        
        val newItem = OutboundOrderItem.create(itemId = 2L, requestedQty = 5, createdBy = "user1")
        
        assertThrows<IllegalArgumentException> {
            order.addItem(newItem, "user2")
        }
    }
    
    @Test
    fun `should not remove last item from pending order`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        assertThrows<IllegalArgumentException> {
            order.removeItem(1L, "user2")
        }
    }
    
    @Test
    fun `should fail remove all items from order`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        assertThrows<IllegalArgumentException> {
            order.removeItem(items[0].id, "user2")
        }
    }
    
    @Test
    fun `should transition from allocated to deallocated`() {
        val items = listOf(
            OutboundOrderItem.create(itemId = 1L, requestedQty = 10, createdBy = "user1")
        )
        val order = OutboundOrder.create(
            customerId = 100L,
            warehouseId = 200L,
            requestedDate = LocalDateTime.now().plusDays(1),
            priority = 3,
            items = items,
            createdBy = "user1"
        )
        
        order.allocate("user2")
        order.deallocate("user2")
        
        assertEquals(OutboundOrderStatus.Deallocated, order.status)
    }
}
