package com.wms.infrastructure.logging

import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.ByteArrayInputStream
import java.io.OutputStreamWriter

/**
 * HTTP Request Wrapper
 * Allows request body to be read multiple times by caching it in memory
 * Original request body stream can only be read once; this wrapper enables re-reading
 */
class HttpRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    
    val body: String
    private val bodyBytes: ByteArray
    
    init {
        bodyBytes = request.inputStream.readAllBytes()
        body = String(bodyBytes, Charsets.UTF_8)
    }
    
    override fun getInputStream(): ServletInputStream {
        return CachedServletInputStream(bodyBytes)
    }
}

/**
 * HTTP Response Wrapper
 * Captures response body while still allowing it to be sent to client
 * Maintains a buffer copy of all written data
 */
class HttpResponseWrapper(response: jakarta.servlet.http.HttpServletResponse) 
    : jakarta.servlet.http.HttpServletResponseWrapper(response) {
    
    private val buffer = ByteArrayOutputStream()
    val body: String get() = String(buffer.toByteArray(), Charsets.UTF_8)
    
    override fun getOutputStream(): jakarta.servlet.ServletOutputStream {
        return TeeServletOutputStream(super.getOutputStream(), buffer)
    }
    
    override fun getWriter(): java.io.PrintWriter {
        return java.io.PrintWriter(
            OutputStreamWriter(TeeByteArrayOutputStream(buffer), Charsets.UTF_8),
            true
        )
    }
    
    fun copyBodyToResponse() {
        val originalOs = super.getOutputStream()
        originalOs.write(buffer.toByteArray())
        originalOs.flush()
    }
}

class CachedServletInputStream(private val data: ByteArray) : ServletInputStream() {
    private val inputStream = ByteArrayInputStream(data)
    
    override fun read(): Int = inputStream.read()
    
    override fun read(b: ByteArray, off: Int, len: Int): Int = 
        inputStream.read(b, off, len)
    
    override fun isFinished(): Boolean = inputStream.available() == 0
    
    override fun isReady(): Boolean = true
    
    override fun setReadListener(listener: jakarta.servlet.ReadListener?) {}
}

class TeeServletOutputStream(
    private val delegate: jakarta.servlet.ServletOutputStream,
    private val buffer: ByteArrayOutputStream
) : jakarta.servlet.ServletOutputStream() {
    
    override fun write(b: Int) {
        delegate.write(b)
        buffer.write(b)
    }
    
    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        buffer.write(b, off, len)
    }
    
    override fun flush() {
        delegate.flush()
    }
    
    override fun close() {
        delegate.close()
    }
    
    override fun isReady(): Boolean = delegate.isReady()
    
    override fun setWriteListener(listener: jakarta.servlet.WriteListener?) {
        delegate.setWriteListener(listener)
    }
}

class TeeByteArrayOutputStream(private val buffer: ByteArrayOutputStream) : java.io.OutputStream() {
    override fun write(b: Int) {
        buffer.write(b)
    }
    
    override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.write(b, off, len)
    }
    
    override fun flush() {
    }
    
    override fun close() {
    }
}

class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
