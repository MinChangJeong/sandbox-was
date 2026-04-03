package com.wms.application.inbound.command

import com.wms.domain.inbound.model.InboundOrder
import com.wms.domain.inbound.model.InboundOrderItem
import com.wms.domain.inbound.repository.InboundOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class CreateInboundOrderCommand(
    val supplierId: Long,
    val warehouseId: Long,
    val expectedDate: LocalDateTime,
    val items: List<CreateInboundOrderItemRequest>
)

data class CreateInboundOrderItemRequest(
    val itemId: Long,
    val expectedQty: Int
)

data class CommandResult(
    val id: Long,
    val message: String = "Success"
)

@Service
class CreateInboundOrderCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(command: CreateInboundOrderCommand, createdBy: String): CommandResult {
        val items = command.items.map { item ->
            InboundOrderItem.create(
                itemId = item.itemId,
                expectedQty = item.expectedQty,
                createdBy = createdBy
            )
        }
        
        val inboundOrder = InboundOrder.create(
            supplierId = command.supplierId,
            warehouseId = command.warehouseId,
            expectedDate = command.expectedDate,
            items = items,
            createdBy = createdBy
        )
        
        val saved = inboundOrderRepository.save(inboundOrder)
        
        return CommandResult(
            id = saved.id,
            message = "입고예정이 생성되었습니다"
        )
    }
}

@Service
class StartInboundInspectionCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(inboundOrderId: Long, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $inboundOrderId")
        
        inboundOrder.startInspection(updatedBy)
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "검수가 시작되었습니다"
        )
    }
}

data class InspectInboundItemCommand(
    val inboundOrderId: Long,
    val itemId: Long,
    val inspectedQty: Int,
    val acceptedQty: Int,
    val rejectedQty: Int,
    val rejectionReason: String? = null
)

@Service
class InspectInboundItemCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(command: InspectInboundItemCommand, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(command.inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: ${command.inboundOrderId}")
        
        inboundOrder.inspectItem(
            itemId = command.itemId,
            inspectedQty = command.inspectedQty,
            acceptedQty = command.acceptedQty,
            rejectedQty = command.rejectedQty,
            rejectionReason = command.rejectionReason,
            updatedBy = updatedBy
        )
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "항목 검수가 기록되었습니다"
        )
    }
}

@Service
class CompleteInboundInspectionCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(inboundOrderId: Long, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $inboundOrderId")
        
        inboundOrder.completeInspection(updatedBy)
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "검수가 완료되었습니다"
        )
    }
}

@Service
class StartInboundPutawayCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(inboundOrderId: Long, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $inboundOrderId")
        
        inboundOrder.startPutaway(updatedBy)
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "적치가 시작되었습니다"
        )
    }
}

@Service
class CompleteInboundOrderCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(inboundOrderId: Long, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $inboundOrderId")
        
        inboundOrder.complete(updatedBy)
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "입고가 완료되었습니다"
        )
    }
}

@Service
class RejectInboundOrderCommandHandler(
    private val inboundOrderRepository: InboundOrderRepository
) {
    @Transactional
    fun handle(inboundOrderId: Long, reason: String, updatedBy: String): CommandResult {
        val inboundOrder = inboundOrderRepository.findByIdWithLock(inboundOrderId)
            ?: throw IllegalArgumentException("입고예정을 찾을 수 없습니다: $inboundOrderId")
        
        inboundOrder.reject(reason, updatedBy)
        inboundOrderRepository.update(inboundOrder)
        
        return CommandResult(
            id = inboundOrder.id,
            message = "입고예정이 거절되었습니다"
        )
    }
}
