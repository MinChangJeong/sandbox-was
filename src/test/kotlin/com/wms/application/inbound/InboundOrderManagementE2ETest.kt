package com.wms.application.inbound

import com.wms.application.inbound.command.*
import com.wms.application.inbound.query.*
import com.wms.domain.inbound.repository.InboundOrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InboundOrderManagementE2ETest {
    
    @Autowired
    private lateinit var createHandler: CreateInboundOrderCommandHandler
    
    @Autowired
    private lateinit var startInspectionHandler: StartInboundInspectionCommandHandler
    
    @Autowired
    private lateinit var inspectItemHandler: InspectInboundItemCommandHandler
    
    @Autowired
    private lateinit var completeInspectionHandler: CompleteInboundInspectionCommandHandler
    
    @Autowired
    private lateinit var startPutawayHandler: StartInboundPutawayCommandHandler
    
    @Autowired
    private lateinit var completeOrderHandler: CompleteInboundOrderCommandHandler
    
    @Autowired
    private lateinit var getOrderHandler: GetInboundOrderQueryHandler
    
    @Autowired
    private lateinit var searchOrdersHandler: SearchInboundOrdersQueryHandler
    
    @Autowired
    private lateinit var inboundOrderRepository: InboundOrderRepository
    
    private val TEST_USER = "test-user"
    private val SUPPLIER_ID = 1L
    private val WAREHOUSE_ID = 1L
    private val ITEM_ID_1 = 1L
    private val ITEM_ID_2 = 2L
    
    @BeforeEach
    fun setUp() {
        inboundOrderRepository.findAll().forEach { inboundOrderRepository.delete(it.id) }
    }
    
    @Test
    fun createInboundOrderSuccessfully() {
        val command = CreateInboundOrderCommand(
            supplierId = SUPPLIER_ID,
            warehouseId = WAREHOUSE_ID,
            expectedDate = LocalDateTime.now().plusDays(1),
            items = listOf(
                CreateInboundOrderItemRequest(itemId = ITEM_ID_1, expectedQty = 100),
                CreateInboundOrderItemRequest(itemId = ITEM_ID_2, expectedQty = 50)
            )
        )
        
        val result = createHandler.handle(command, TEST_USER)
        
        assertNotNull(result)
        assertTrue(result.id > 0)
        assertEquals("입고예정이 생성되었습니다", result.message)
    }
    
    @Test
    fun createInboundOrderAndStartInspection() {
        val inboundOrderId = createInboundOrder()
        
        val result = startInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        assertNotNull(result)
        val updated = getOrderHandler.handle(inboundOrderId)
        assertEquals("INSPECTING", updated.status)
    }
    
    @Test
    fun inspectItemRecordsInspectionData() {
        val inboundOrderId = createInboundOrder()
        startInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        val command = InspectInboundItemCommand(
            inboundOrderId = inboundOrderId,
            itemId = ITEM_ID_1,
            inspectedQty = 100,
            acceptedQty = 95,
            rejectedQty = 5,
            rejectionReason = "품질 불량"
        )
        
        val result = inspectItemHandler.handle(command, TEST_USER)
        
        assertNotNull(result)
        val order = getOrderHandler.handle(inboundOrderId)
        assertNotNull(order)
    }
    
    @Test
    fun completeInspectionFlow() {
        val inboundOrderId = createInboundOrder()
        startInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        inspectItemHandler.handle(
            InspectInboundItemCommand(
                inboundOrderId = inboundOrderId,
                itemId = ITEM_ID_1,
                inspectedQty = 100,
                acceptedQty = 100,
                rejectedQty = 0
            ),
            TEST_USER
        )
        
        inspectItemHandler.handle(
            InspectInboundItemCommand(
                inboundOrderId = inboundOrderId,
                itemId = ITEM_ID_2,
                inspectedQty = 50,
                acceptedQty = 50,
                rejectedQty = 0
            ),
            TEST_USER
        )
        
        val result = completeInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        assertNotNull(result)
        val updated = getOrderHandler.handle(inboundOrderId)
        assertEquals("INSPECTED", updated.status)
        assertNotNull(updated.inspectionCompletedAt)
    }
    
    @Test
    fun startPutawayAfterInspection() {
        val inboundOrderId = createAndInspectInboundOrder()
        
        val result = startPutawayHandler.handle(inboundOrderId, TEST_USER)
        
        assertNotNull(result)
        val updated = getOrderHandler.handle(inboundOrderId)
        assertEquals("PUTAWAY_IN_PROGRESS", updated.status)
        assertNotNull(updated.putawayStartedAt)
    }
    
    @Test
    fun completeInboundOrderFlow() {
        val inboundOrderId = createAndInspectInboundOrder()
        startPutawayHandler.handle(inboundOrderId, TEST_USER)
        
        val result = completeOrderHandler.handle(inboundOrderId, TEST_USER)
        
        assertNotNull(result)
        val updated = getOrderHandler.handle(inboundOrderId)
        assertEquals("COMPLETED", updated.status)
        assertNotNull(updated.completedAt)
    }
    
    @Test
    fun searchInboundOrdersWithPagination() {
        repeat(5) {
            createInboundOrder()
        }
        
        val criteria = InboundOrderSearchCriteria(
            page = 0,
            size = 3
        )
        
        val result = searchOrdersHandler.handle(criteria)
        
        assertEquals(5, result.totalElements)
        assertEquals(2, result.totalPages)
        assertEquals(3, result.content.size)
        assertTrue(result.hasNext)
    }
    
    @Test
    fun searchInboundOrdersFilterByStatus() {
        val id1 = createInboundOrder()
        val id2 = createInboundOrder()
        
        startInspectionHandler.handle(id1, TEST_USER)
        
        val criteria = InboundOrderSearchCriteria(
            status = "INSPECTING",
            page = 0,
            size = 20
        )
        
        val result = searchOrdersHandler.handle(criteria)
        
        assertEquals(1, result.totalElements)
        assertEquals(1, result.content.size)
        assertEquals("INSPECTING", result.content[0].status)
    }
    
    @Test
    fun searchInboundOrdersFilterBySupplier() {
        createInboundOrder(supplierId = 1L)
        createInboundOrder(supplierId = 2L)
        
        val criteria = InboundOrderSearchCriteria(
            supplierId = 1L,
            page = 0,
            size = 20
        )
        
        val result = searchOrdersHandler.handle(criteria)
        
        assertEquals(1, result.totalElements)
        assertEquals(1L, result.content[0].supplierId)
    }
    
    @Test
    fun searchInboundOrdersFilterByWarehouse() {
        createInboundOrder(warehouseId = 1L)
        createInboundOrder(warehouseId = 2L)
        
        val criteria = InboundOrderSearchCriteria(
            warehouseId = 2L,
            page = 0,
            size = 20
        )
        
        val result = searchOrdersHandler.handle(criteria)
        
        assertEquals(1, result.totalElements)
        assertEquals(2L, result.content[0].warehouseId)
    }
    
    @Test
    fun fullInboundOrderLifecycle() {
        val createCommand = CreateInboundOrderCommand(
            supplierId = SUPPLIER_ID,
            warehouseId = WAREHOUSE_ID,
            expectedDate = LocalDateTime.now().plusDays(1),
            items = listOf(
                CreateInboundOrderItemRequest(itemId = ITEM_ID_1, expectedQty = 100)
            )
        )
        val createResult = createHandler.handle(createCommand, TEST_USER)
        val inboundOrderId = createResult.id
        
        var order = getOrderHandler.handle(inboundOrderId)
        assertEquals("EXPECTED", order.status)
        
        startInspectionHandler.handle(inboundOrderId, TEST_USER)
        order = getOrderHandler.handle(inboundOrderId)
        assertEquals("INSPECTING", order.status)
        
        inspectItemHandler.handle(
            InspectInboundItemCommand(
                inboundOrderId = inboundOrderId,
                itemId = ITEM_ID_1,
                inspectedQty = 100,
                acceptedQty = 100,
                rejectedQty = 0
            ),
            TEST_USER
        )
        
        completeInspectionHandler.handle(inboundOrderId, TEST_USER)
        order = getOrderHandler.handle(inboundOrderId)
        assertEquals("INSPECTED", order.status)
        
        startPutawayHandler.handle(inboundOrderId, TEST_USER)
        order = getOrderHandler.handle(inboundOrderId)
        assertEquals("PUTAWAY_IN_PROGRESS", order.status)
        
        completeOrderHandler.handle(inboundOrderId, TEST_USER)
        order = getOrderHandler.handle(inboundOrderId)
        assertEquals("COMPLETED", order.status)
        
        assertNotNull(order.inspectionCompletedAt)
        assertNotNull(order.putawayStartedAt)
        assertNotNull(order.completedAt)
    }
    
    private fun createInboundOrder(
        supplierId: Long = SUPPLIER_ID,
        warehouseId: Long = WAREHOUSE_ID
    ): Long {
        val command = CreateInboundOrderCommand(
            supplierId = supplierId,
            warehouseId = warehouseId,
            expectedDate = LocalDateTime.now().plusDays(1),
            items = listOf(
                CreateInboundOrderItemRequest(itemId = ITEM_ID_1, expectedQty = 100),
                CreateInboundOrderItemRequest(itemId = ITEM_ID_2, expectedQty = 50)
            )
        )
        return createHandler.handle(command, TEST_USER).id
    }
    
    private fun createAndInspectInboundOrder(): Long {
        val inboundOrderId = createInboundOrder()
        startInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        inspectItemHandler.handle(
            InspectInboundItemCommand(
                inboundOrderId = inboundOrderId,
                itemId = ITEM_ID_1,
                inspectedQty = 100,
                acceptedQty = 100,
                rejectedQty = 0
            ),
            TEST_USER
        )
        
        inspectItemHandler.handle(
            InspectInboundItemCommand(
                inboundOrderId = inboundOrderId,
                itemId = ITEM_ID_2,
                inspectedQty = 50,
                acceptedQty = 50,
                rejectedQty = 0
            ),
            TEST_USER
        )
        
        completeInspectionHandler.handle(inboundOrderId, TEST_USER)
        
        return inboundOrderId
    }
}
