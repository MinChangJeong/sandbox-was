package com.wms.infrastructure.logging

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.*

/**
 * HTTP Logging Filter
 * - Logs all HTTP requests and responses with full details
 * - Generates and tracks correlation IDs for distributed tracing
 * - Supports request body/response body capture via wrapper classes
 * - Correlation ID stored in MDC (Mapped Diagnostic Context) for thread-local access
 */
@Component
class HttpLoggingFilter : Filter {
    
    private val logger: Logger = LoggerFactory.getLogger(HttpLoggingFilter::class.java)
    private val CORRELATION_ID_HEADER = "X-Correlation-ID"
    private val CORRELATION_ID_MDC_KEY = "correlationId"
    
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request !is HttpServletRequest || response !is HttpServletResponse) {
            chain.doFilter(request, response)
            return
        }
        
        // Generate or retrieve correlation ID
        val correlationId = request.getHeader(CORRELATION_ID_HEADER) 
            ?: UUID.randomUUID().toString()
        
        // Store correlation ID in MDC for all logs in this thread
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId)
        
        try {
            // Wrap request to capture body (can be read multiple times)
            val wrappedRequest = HttpRequestWrapper(request)
            
            // Wrap response to capture body
            val wrappedResponse = HttpResponseWrapper(response)
            
            // Log incoming request
            logRequest(wrappedRequest, correlationId)
            
            val startTime = System.currentTimeMillis()
            
            // Process request through chain
            chain.doFilter(wrappedRequest, wrappedResponse)
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // Log outgoing response with execution time
            logResponse(wrappedResponse, executionTime, correlationId)
            
            // Copy response body back to client
            wrappedResponse.copyBodyToResponse()
            
        } finally {
            // Always clear MDC to prevent thread pool pollution
            MDC.remove(CORRELATION_ID_MDC_KEY)
        }
    }
    
    private fun logRequest(request: HttpRequestWrapper, correlationId: String) {
        val method = request.method
        val path = request.requestURI
        val queryString = request.queryString
        val clientIp = getClientIpAddress(request)
        val headers = extractHeaders(request)
        val body = captureRequestBody(request)
        
        logger.info("""
            |>>> HTTP REQUEST [$correlationId]
            |Method: $method
            |Path: $path${if (queryString != null) "?$queryString" else ""}
            |Client IP: $clientIp
            |Headers: $headers
            |Body: $body
        """.trimMargin())
    }
    
    private fun logResponse(response: HttpResponseWrapper, executionTime: Long, correlationId: String) {
        val status = response.status
        val headers = extractResponseHeaders(response)
        val body = captureResponseBody(response)
        
        logger.info("""
            |<<< HTTP RESPONSE [$correlationId]
            |Status: $status
            |Execution Time: ${executionTime}ms
            |Headers: $headers
            |Body: $body
        """.trimMargin())
    }
    
    private fun extractHeaders(request: HttpServletRequest): Map<String, String> {
        return request.headerNames.toList().associate { headerName ->
            headerName to (request.getHeader(headerName) ?: "")
        }
    }
    
    private fun extractResponseHeaders(response: HttpResponseWrapper): Map<String, String> {
        return response.headerNames.associate { headerName ->
            headerName to (response.getHeader(headerName) ?: "")
        }
    }
    
    private fun captureRequestBody(request: HttpRequestWrapper): String {
        return try {
            val contentType = request.contentType
            val body = request.body
            
            if (body.isBlank()) {
                "[empty]"
            } else if (contentType?.contains("application/json") == true) {
                body
            } else if (contentType?.contains("application/x-www-form-urlencoded") == true) {
                // Log form data (truncated if too long)
                if (body.length > 500) "${body.substring(0, 500)}..." else body
            } else {
                "[non-JSON content]"
            }
        } catch (e: Exception) {
            "[error reading body: ${e.message}]"
        }
    }
    
    private fun captureResponseBody(response: HttpResponseWrapper): String {
        return try {
            val contentType = response.contentType
            val body = response.body
            
            if (body.isBlank()) {
                "[empty]"
            } else if (contentType?.contains("application/json") == true) {
                // Truncate large responses for readability
                if (body.length > 1000) "${body.substring(0, 1000)}..." else body
            } else {
                "[non-JSON content]"
            }
        } catch (e: Exception) {
            "[error reading body: ${e.message}]"
        }
    }
    
    private fun getClientIpAddress(request: HttpServletRequest): String {
        return request.getHeader("X-Forwarded-For")?.split(",")?.get(0)?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddr
    }
}
