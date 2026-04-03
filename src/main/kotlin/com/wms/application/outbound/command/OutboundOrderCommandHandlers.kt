package com.wms.application.outbound.command

import com.wms.domain.outbound.model.OutboundOrder
import com.wms.domain.outbound.model.OutboundOrderItem
import com.wms.domain.outbound.repository.OutboundOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// ============ Commands ============

data class CreateOutboundOrderCommand(
    val customerId: Long,
    val warehouseId: Long,
    val requestedDate: LocalDateTime,
    val priority: Int,
    val items: List<OutboundOrderItemRequest>
)

data class OutboundOrderItemRequest(
    val itemId: Long,
    val requestedQty: Int
)

data class CommandResult(
    val id: Long,
    val message: String = "Success"
)

// ============ Handlers ============

@Service
class CreateOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(command: CreateOutboundOrderCommand, createdBy: String): CommandResult {
        val items = command.items.map { item ->
            OutboundOrderItem.create(
                itemId = item.itemId,
                requestedQty = item.requestedQty,
                createdBy = createdBy
            )
        }
        
        val outboundOrder = OutboundOrder.create(
            customerId = command.customerId,
            warehouseId = command.warehouseId,
            requestedDate = command.requestedDate,
            priority = command.priority,
            items = items,
            createdBy = createdBy
        )
        
        val saved = outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = saved.id,
            message = "출고주문이 생성되었습니다"
        )
    }
}

@Service
class AllocateOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.allocate(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "재고 할당이 완료되었습니다"
        )
    }
}

@Service
class StartPickingOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.startPicking(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "피킹이 시작되었습니다"
        )
    }
}

@Service
class CompletePickingOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.completePicking(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "피킹이 완료되었습니다"
        )
    }
}

@Service
class PackOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.pack(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "패킹이 완료되었습니다"
        )
    }
}

@Service
class ShipOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.ship(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "출고가 완료되었습니다"
        )
    }
}

data class CancelOutboundOrderCommand(
    val outboundOrderId: Long,
    val reason: String
)

@Service
class CancelOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(command: CancelOutboundOrderCommand, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(command.outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: ${command.outboundOrderId}")
        
        outboundOrder.cancel(command.reason, updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "주문이 취소되었습니다"
        )
    }
}

@Service
class DeallocateOutboundOrderCommandHandler(
    private val outboundOrderRepository: OutboundOrderRepository
) {
    @Transactional
    fun handle(outboundOrderId: Long, updatedBy: String): CommandResult {
        val outboundOrder = outboundOrderRepository.findByIdWithLock(outboundOrderId)
            ?: throw IllegalArgumentException("출고주문을 찾을 수 없습니다: $outboundOrderId")
        
        outboundOrder.deallocate(updatedBy)
        outboundOrderRepository.save(outboundOrder)
        
        return CommandResult(
            id = outboundOrder.id,
            message = "할당이 취소되었습니다"
        )
    }
}
