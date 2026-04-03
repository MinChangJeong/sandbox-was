package com.wms.infrastructure.web.controller.inbound

import com.wms.application.inbound.command.*
import com.wms.application.inbound.query.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/inbound-orders/commands")
class InboundOrderCommandController(
    private val createHandler: CreateInboundOrderCommandHandler,
    private val startInspectionHandler: StartInboundInspectionCommandHandler,
    private val inspectItemHandler: InspectInboundItemCommandHandler,
    private val completeInspectionHandler: CompleteInboundInspectionCommandHandler,
    private val startPutawayHandler: StartInboundPutawayCommandHandler,
    private val completeOrderHandler: CompleteInboundOrderCommandHandler,
    private val rejectOrderHandler: RejectInboundOrderCommandHandler
) {
    
    @PostMapping
    fun createInboundOrder(
        @RequestBody request: CreateInboundOrderRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val command = CreateInboundOrderCommand(
            supplierId = request.supplierId,
            warehouseId = request.warehouseId,
            expectedDate = request.expectedDate,
            items = request.items
        )
        val result = createHandler.handle(command, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }
    
    @PostMapping("/{id}/inspection/start")
    fun startInspection(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val result = startInspectionHandler.handle(id, userId)
        return ResponseEntity.ok(result)
    }
    
    @PutMapping("/{id}/items/{itemId}/inspection")
    fun inspectItem(
        @PathVariable id: Long,
        @PathVariable itemId: Long,
        @RequestBody request: InspectItemRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val command = InspectInboundItemCommand(
            inboundOrderId = id,
            itemId = itemId,
            inspectedQty = request.inspectedQty,
            acceptedQty = request.acceptedQty,
            rejectedQty = request.rejectedQty,
            rejectionReason = request.rejectionReason
        )
        val result = inspectItemHandler.handle(command, userId)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{id}/inspection/complete")
    fun completeInspection(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val result = completeInspectionHandler.handle(id, userId)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{id}/putaway")
    fun startPutaway(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val result = startPutawayHandler.handle(id, userId)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{id}/complete")
    fun completeInboundOrder(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val result = completeOrderHandler.handle(id, userId)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{id}/reject")
    fun rejectInboundOrder(
        @PathVariable id: Long,
        @RequestBody request: RejectInboundOrderRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "system") userId: String
    ): ResponseEntity<CommandResult> {
        val result = rejectOrderHandler.handle(id, request.reason, userId)
        return ResponseEntity.ok(result)
    }
}

data class CreateInboundOrderRequest(
    val supplierId: Long,
    val warehouseId: Long,
    val expectedDate: LocalDateTime,
    val items: List<CreateInboundOrderItemRequest>
)

data class InspectItemRequest(
    val inspectedQty: Int,
    val acceptedQty: Int,
    val rejectedQty: Int,
    val rejectionReason: String? = null
)

data class RejectInboundOrderRequest(
    val reason: String
)

@RestController
@RequestMapping("/api/v1/inbound-orders/queries")
class InboundOrderQueryController(
    private val getOrderHandler: GetInboundOrderQueryHandler,
    private val searchOrdersHandler: SearchInboundOrdersQueryHandler
) {
    
    @GetMapping("/{id}")
    fun getInboundOrder(@PathVariable id: Long): ResponseEntity<InboundOrderDto> {
        val result = getOrderHandler.handle(id)
        return ResponseEntity.ok(result)
    }
    
    @GetMapping
    fun searchInboundOrders(
        @RequestParam(required = false) supplierId: Long?,
        @RequestParam(required = false) warehouseId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) expectedDateFrom: LocalDateTime?,
        @RequestParam(required = false) expectedDateTo: LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedResponse<InboundOrderDto>> {
        val criteria = InboundOrderSearchCriteria(
            supplierId = supplierId,
            warehouseId = warehouseId,
            status = status,
            expectedDateFrom = expectedDateFrom,
            expectedDateTo = expectedDateTo,
            page = page,
            size = size
        )
        val result = searchOrdersHandler.handle(criteria)
        return ResponseEntity.ok(result)
    }
}
