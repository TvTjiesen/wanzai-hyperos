package com.muyao.wanzaihyperos.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

/**
 * 单测用的极小 HTTP 服务器。
 *
 * ### 为什么不用 MockWebServer
 * 我们只需要两件事：「按顺序回几个固定响应」和「把收到的请求原样记下来」。
 * 用 JDK 自带的 `com.sun.net.httpserver` 就能做完，**少一个依赖**，
 * 也少一类「测试库自己的 API 在版本间改名」的风险。
 * 真实网络行为（跳转、Cookie、超时）由 OkHttp 自己保证，不需要测试替身去模拟。
 *
 * 只在 JVM 单测里使用 —— `com.sun.net.httpserver` 在 Android 运行时不存在。
 */
class TestHttpServer {

    /** 我们收到的一条请求。**只记结构，不记凭据以外的判断**。 */
    data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val body: String,
        val contentType: String?,
        val cookieHeader: String?,
        /** 门户的 token 头。用来验证「token 同时放 header」这条约定。 */
        val tokenHeader: String?,
    )

    private data class QueuedResponse(
        val status: Int,
        val body: String,
        val contentType: String,
        val headers: Map<String, String>,
    )

    private val queue = LinkedBlockingDeque<QueuedResponse>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    private val executor = Executors.newFixedThreadPool(2)

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> handle(exchange) }
            this.executor = this@TestHttpServer.executor
            start()
        }

    /** 形如 `http://127.0.0.1:34567`，**不带尾斜杠**。 */
    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    val requestCount: Int get() = requests.size

    /** 排一个响应。按排队顺序一一对应收到的请求。 */
    fun enqueue(
        status: Int = 200,
        body: String = "",
        contentType: String = "application/json;charset=UTF-8",
        headers: Map<String, String> = emptyMap(),
    ) {
        queue.add(QueuedResponse(status, body, contentType, headers))
    }

    /** 队列空了以后默认回什么。默认 500，好让「少排了一个响应」立刻暴露。 */
    var fallbackStatus: Int = 500

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        requests.add(
            RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.query,
                body = body,
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                cookieHeader = exchange.requestHeaders.getFirst("Cookie"),
                tokenHeader = exchange.requestHeaders.getFirst("token"),
            ),
        )

        val response = queue.poll() ?: QueuedResponse(fallbackStatus, "", "text/plain", emptyMap())
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", response.contentType)
        for ((name, value) in response.headers) exchange.responseHeaders.add(name, value)

        if (bytes.isEmpty()) {
            exchange.sendResponseHeaders(response.status, -1)
        } else {
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
