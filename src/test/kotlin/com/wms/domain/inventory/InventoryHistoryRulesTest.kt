package com.wms.domain.inventory

import com.wms.domain.inventory.model.Inventory
import com.wms.domain.inventory.model.InventoryHistory
import com.wms.domain.common.status.InventoryStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("재고 이력 시스템 - 규칙 1, 규칙 2 검증")
class InventoryHistoryRulesTest {
    
    private lateinit var inventory: Inventory
    
    @BeforeEach
    fun setup() {
        inventory = createTestInventory(1000)
        inventory.recordInitialStockHistory("test-user")
    }
    
    private fun createTestInventory(quantity: Int): Inventory {
        val inv = Inventory.create(
            itemId = 1L,
            locationId = 1L,
            quantity = quantity,
            createdBy = "test-user"
        )
        setInventoryId(inv, 1L)
        return inv
    }
    
    @Nested
    @DisplayName("규칙 1: 재고 수량 = 재고 이력의 합")
    inner class Rule1QuantitySumTests {
        
        @Test
        @DisplayName("입고(INBOUND) - changeQuantity = +수량")
        fun testInboundChangeQuantity() {
            inventory.increase(quantity = 100, reason = "입고", updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("INBOUND", histories[1].transactionType)
            assertEquals(100, histories[1].changeQuantity)
            assertEquals(1100, inventory.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inventory.quantity, historySum)
        }
        
        @Test
        @DisplayName("출고(OUTBOUND) - changeQuantity = -수량")
        fun testOutboundChangeQuantity() {
            inventory.decrease(quantity = 200, reason = "출고", updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("OUTBOUND", histories[1].transactionType)
            assertEquals(-200, histories[1].changeQuantity)
            assertEquals(800, inventory.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inventory.quantity, historySum)
        }
        
        @Test
        @DisplayName("조정증가(ADJUSTMENT_INCREASE) - changeQuantity = +수량")
        fun testAdjustmentIncreaseChangeQuantity() {
            inventory.adjust("INCREASE", 50, "조정", "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("ADJUSTMENT_INCREASE", histories[1].transactionType)
            assertEquals(50, histories[1].changeQuantity)
            assertEquals(1050, inventory.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inventory.quantity, historySum)
        }
        
        @Test
        @DisplayName("조정감소(ADJUSTMENT_DECREASE) - changeQuantity = -수량")
        fun testAdjustmentDecreaseChangeQuantity() {
            inventory.adjust("DECREASE", 75, "조정", "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("ADJUSTMENT_DECREASE", histories[1].transactionType)
            assertEquals(-75, histories[1].changeQuantity)
            assertEquals(925, inventory.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inventory.quantity, historySum)
        }
        
        @Test
        @DisplayName("이동출고(MOVEMENT_OUT) - changeQuantity = -수량")
        fun testMovementOutChangeQuantity() {
            inventory.moveOut(quantity = 100, toLocationId = 2L, updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("MOVEMENT_OUT", histories[1].transactionType)
            assertEquals(-100, histories[1].changeQuantity)
            assertEquals(900, inventory.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inventory.quantity, historySum)
        }
        
        @Test
        @DisplayName("이동입고(MOVEMENT_IN) - changeQuantity = +수량")
        fun testMovementInChangeQuantity() {
            val inv2 = createTestInventory(500)
            inv2.recordInitialStockHistory("user")
            inv2.moveIn(quantity = 150, fromLocationId = 1L, updatedBy = "user")
            
            val histories = inv2.getHistories()
            assertEquals(2, histories.size)
            assertEquals("MOVEMENT_IN", histories[1].transactionType)
            assertEquals(150, histories[1].changeQuantity)
            assertEquals(650, inv2.quantity)
            
            val historySum = histories.sumOf { it.changeQuantity }
            assertEquals(inv2.quantity, historySum)
        }
        
        @Test
        @DisplayName("할당(ALLOCATE) - changeQuantity = 0 (quantity 불변)")
        fun testAllocateChangeQuantityZero() {
            val beforeQty = inventory.quantity
            inventory.allocate(quantity = 300, orderId = 1L, updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertEquals(2, histories.size)
            assertEquals("ALLOCATE", histories[1].transactionType)
            assertEquals(0, histories[1].changeQuantity)
            assertEquals(beforeQty, inventory.quantity)
            assertEquals(1000, histories.sumOf { it.changeQuantity })
        }
        
        @Test
        @DisplayName("할당해제(DEALLOCATE) - changeQuantity = 0 (quantity 불변)")
        fun testDeallocateChangeQuantityZero() {
            inventory.allocate(quantity = 300, orderId = 1L, updatedBy = "user")
            val beforeQty = inventory.quantity
            
            inventory.deallocate(quantity = 200, reason = "취소", updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertEquals(3, histories.size)
            assertEquals("DEALLOCATE", histories[2].transactionType)
            assertEquals(0, histories[2].changeQuantity)
            assertEquals(beforeQty, inventory.quantity)
            assertEquals(1000, histories.sumOf { it.changeQuantity })
        }
        
        @Test
        @DisplayName("규칙 1 복합 검증: 다중 거래의 합계")
        fun testRule1ComplexTransactions() {
            inventory.increase(200, "입고1", updatedBy = "user")
            inventory.decrease(300, "출고1", updatedBy = "user")
            inventory.adjust("INCREASE", 100, "조정", "user")
            inventory.adjust("DECREASE", 50, "조정", "user")
            
            val histories = inventory.getHistories()
            val totalChange = histories.sumOf { it.changeQuantity }
            
            assertEquals(5, histories.size)
            assertEquals(950, inventory.quantity)
            assertEquals(950, totalChange)
            assertEquals(inventory.quantity, totalChange)
        }
    }
    
    @Nested
    @DisplayName("규칙 2: 모든 필수 거래 유형 구현")
    inner class Rule2TransactionTypesTest {
        
        @Test
        @DisplayName("거래 유형: INBOUND 기록")
        fun testTransactionTypeInbound() {
            inventory.increase(100, "입고", updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "INBOUND" })
        }
        
        @Test
        @DisplayName("거래 유형: OUTBOUND 기록")
        fun testTransactionTypeOutbound() {
            inventory.decrease(100, "출고", updatedBy = "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "OUTBOUND" })
        }
        
        @Test
        @DisplayName("거래 유형: ADJUSTMENT_INCREASE 기록")
        fun testTransactionTypeAdjustmentIncrease() {
            inventory.adjust("INCREASE", 50, "조정", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "ADJUSTMENT_INCREASE" })
        }
        
        @Test
        @DisplayName("거래 유형: ADJUSTMENT_DECREASE 기록")
        fun testTransactionTypeAdjustmentDecrease() {
            inventory.adjust("DECREASE", 50, "조정", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "ADJUSTMENT_DECREASE" })
        }
        
        @Test
        @DisplayName("거래 유형: MOVEMENT_OUT 기록")
        fun testTransactionTypeMovementOut() {
            inventory.moveOut(100, 2L, "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "MOVEMENT_OUT" })
        }
        
        @Test
        @DisplayName("거래 유형: MOVEMENT_IN 기록")
        fun testTransactionTypeMovementIn() {
            val inv2 = createTestInventory(500)
            inv2.moveIn(100, 1L, "user")
            
            val histories = inv2.getHistories()
            assertTrue(histories.any { it.transactionType == "MOVEMENT_IN" })
        }
        
        @Test
        @DisplayName("거래 유형: TRANSFER_OUT 기록")
        fun testTransactionTypeTransferOut() {
            inventory.transferToZone(100, 2L, "존 이동", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "TRANSFER_OUT" })
        }
        
        @Test
        @DisplayName("거래 유형: TRANSFER_IN 기록")
        fun testTransactionTypeTransferIn() {
            val inv2 = createTestInventory(500)
            inv2.receiveFromTransfer(100, 1L, "존 이동 입고", "user")
            
            val histories = inv2.getHistories()
            assertTrue(histories.any { it.transactionType == "TRANSFER_IN" })
        }
        
        @Test
        @DisplayName("거래 유형: ALLOCATE 기록")
        fun testTransactionTypeAllocate() {
            inventory.allocate(200, 1L, "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "ALLOCATE" })
        }
        
        @Test
        @DisplayName("거래 유형: DEALLOCATE 기록")
        fun testTransactionTypeDeallocate() {
            inventory.allocate(200, 1L, "user")
            inventory.deallocate(100, "취소", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "DEALLOCATE" })
        }
        
        @Test
        @DisplayName("거래 유형: CYCLE_COUNT 기록")
        fun testTransactionTypeCycleCount() {
            inventory.performCycleCounting(950, "실사", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "CYCLE_COUNT" })
            assertEquals(-50, histories.find { it.transactionType == "CYCLE_COUNT" }?.changeQuantity)
        }
        
        @Test
        @DisplayName("거래 유형: RETURN_INBOUND 기록")
        fun testTransactionTypeReturnInbound() {
            inventory.addReturnInbound(100, 1L, "반품 입고", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "RETURN_INBOUND" })
        }
        
        @Test
        @DisplayName("거래 유형: STATUS_CHANGE 기록")
        fun testTransactionTypeStatusChange() {
            inventory.transitionTo(InventoryStatus.OnHold, "보류", "user")
            
            val histories = inventory.getHistories()
            assertTrue(histories.any { it.transactionType == "STATUS_CHANGE" })
        }
    }
    
    @Nested
    @DisplayName("이력 객체 검증")
    inner class HistoryObjectValidationTest {
        
        @Test
        @DisplayName("이력 생성: 필수 필드 포함")
        fun testHistoryRequiredFields() {
            val history = InventoryHistory.create(
                inventoryId = 1L,
                transactionType = "INBOUND",
                changeQuantity = 100,
                beforeQuantity = 1000,
                afterQuantity = 1100,
                reason = "입고",
                createdBy = "user"
            )
            
            assertNotNull(history)
            assertEquals(1L, history.inventoryId)
            assertEquals("INBOUND", history.transactionType)
            assertEquals(100, history.changeQuantity)
            assertEquals("입고", history.reason)
            assertEquals("user", history.createdBy)
        }
        
        @Test
        @DisplayName("이력 검증: beforeQuantity + changeQuantity == afterQuantity")
        fun testHistoryMathematicalValidation() {
            val history = InventoryHistory.create(
                inventoryId = 1L,
                transactionType = "INBOUND",
                changeQuantity = 100,
                beforeQuantity = 1000,
                afterQuantity = 1100,
                createdBy = "user"
            )
            
            assertEquals(1000 + 100, 1100)
            assertEquals(history.beforeQuantity + history.changeQuantity, history.afterQuantity)
        }
        
        @Test
        @DisplayName("할당 이력: allocatedQtyBefore/After 기록")
        fun testAllocatedQtyTracking() {
            inventory.allocate(200, 1L, "user")
            
            val histories = inventory.getHistories()
            val allocateHistory = histories.find { it.transactionType == "ALLOCATE" }
            
            assertNotNull(allocateHistory)
            assertEquals(0, allocateHistory.allocatedQtyBefore)
            assertEquals(200, allocateHistory.allocatedQtyAfter)
        }
    }
    
    private fun setInventoryId(inventory: Inventory, id: Long) {
        val idField = inventory.javaClass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(inventory, id)
    }
}
