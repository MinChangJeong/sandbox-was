package com.wms.infrastructure.web

import com.wms.application.outbound.command.*
import com.wms.application.outbound.query.GetOutboundOrderQueryHandler
import com.wms.application.outbound.query.OutboundOrderSearchCriteria
import com.wms.application.outbound.query.SearchOutboundOrdersQueryHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/outbound-orders/commands")
class OutboundOrderCommandController(
    private val createHandler: CreateOutboundOrderCommandHandler,
    private val allocateHandler: AllocateOutboundOrderCommandHandler,
    private val startPickingHandler: StartPickingOutboundOrderCommandHandler,
    private val completePickingHandler: CompletePickingOutboundOrderCommandHandler,
    private val packHandler: PackOutboundOrderCommandHandler,
    private val shipHandler: ShipOutboundOrderCommandHandler,
    private val cancelHandler: CancelOutboundOrderCommandHandler,
    private val deallocateHandler: DeallocateOutboundOrderCommandHandler
) {
    @PostMapping
    fun createOrder(
        @RequestBody request: CreateOutboundOrderRequest,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val command = CreateOutboundOrderCommand(
            customerId = request.customerId,
            warehouseId = request.warehouseId,
            requestedDate = request.requestedDate,
            priority = request.priority,
            items = request.items
        )
        val result = createHandler.handle(command, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/allocate")
    fun allocateOrder(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = allocateHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/start-picking")
    fun startPicking(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = startPickingHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/complete-picking")
    fun completePicking(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = completePickingHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/pack")
    fun packOrder(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = packHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/ship")
    fun shipOrder(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = shipHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/cancel")
    fun cancelOrder(
        @PathVariable outboundOrderId: Long,
        @RequestBody request: CancelOrderRequest,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val command = CancelOutboundOrderCommand(
            outboundOrderId = outboundOrderId,
            reason = request.reason
        )
        val result = cancelHandler.handle(command, username)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/{outboundOrderId}/deallocate")
    fun deallocateOrder(
        @PathVariable outboundOrderId: Long,
        principal: Principal?
    ): ResponseEntity<CommandResult> {
        val username = principal?.name ?: "system"
        val result = deallocateHandler.handle(outboundOrderId, username)
        return ResponseEntity.ok(result)
    }
}

data class CreateOutboundOrderRequest(
    val customerId: Long,
    val warehouseId: Long,
    val requestedDate: java.time.LocalDateTime,
    val priority: Int,
    val items: List<OutboundOrderItemRequest>
)

data class CancelOrderRequest(
    val reason: String
)

@RestController
@RequestMapping("/api/v1/outbound-orders/queries")
class OutboundOrderQueryController(
    private val getHandler: GetOutboundOrderQueryHandler,
    private val searchHandler: SearchOutboundOrdersQueryHandler
) {
    @GetMapping("/{outboundOrderId}")
    fun getOrder(@PathVariable outboundOrderId: Long): ResponseEntity<Any> {
        val result = getHandler.handle(outboundOrderId)
        return if (result != null) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @GetMapping
    fun searchOrders(
        @RequestParam(required = false) customerId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "requestedDate") sortBy: String
    ): ResponseEntity<Any> {
        val criteria = OutboundOrderSearchCriteria(
            customerId = customerId,
            page = page,
            size = size,
            sortBy = sortBy
        )
        val result = searchHandler.handle(criteria)
        return ResponseEntity.ok(result)
    }
}
