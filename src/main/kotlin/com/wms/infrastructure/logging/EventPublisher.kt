package com.wms.infrastructure.logging

import com.wms.domain.common.DomainEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class EventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) {
    
    fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
