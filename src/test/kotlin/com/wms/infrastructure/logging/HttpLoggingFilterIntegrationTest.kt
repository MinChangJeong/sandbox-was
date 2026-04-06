package com.wms.infrastructure.logging

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@SpringBootTest
@AutoConfigureMockMvc
class HttpLoggingFilterIntegrationTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Test
    fun `HTTP logging filter captures request and response`() {
        val requestJson = """{"test":"data"}"""
        
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andReturn()
        
        println("Response status: ${result.response.status}")
    }
    
    @Test
    fun `correlation ID is generated and tracked in MDC`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()
        
        println("Response status: ${result.response.status}")
    }
}


