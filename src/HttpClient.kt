import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Socket
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import kotlin.io.encoding.Base64

data class Response(
    val status: Int,
    val reason: String,
    val headers: Map<String, String>,
    val body: String,
    val method: String,
    val url: Url,
) {
    override fun toString(): String = "v $method ${url.path} HTTP/1.1 $status ${body.encodeToByteArray().size}"

    companion object {
        fun ok(
            body: String,
            headers: Map<String, String> = mapOf(),
            url: Url,
        ) = Response(200, "OK", headers, body, "GET", url)

        fun badRequest(
            body: String,
            url: Url,
        ) = Response(400, "Bad Request", mapOf(), body, "GET", url)

        fun notFound(
            body: String,
            url: Url,
        ) = Response(404, "Not Found", mapOf(), body, "GET", url)
    }
}

data class Request(
    val method: String,
    val url: Url,
    val headers: Map<String, String>,
    val body: String?,
) {
    fun encode(): String {
        val defaultHeaders =
            buildMap {
                put("Host", url.host)
                put("Connection", "keep-alive")
                put("User-Agent", "MyBrowser/0.0")
                if (body != null) {
                    put("Content-Length", body.encodeToByteArray().size.toString())
                }
            }
        val headersString =
            defaultHeaders
                .plus(headers)
                .map { "${it.key}: ${it.value}" }
                .joinToString("\r\n", postfix = "\r\n")
        return "$method ${url.path} HTTP/1.1\r\n$headersString\r\n${body ?: ""}"
    }

    override fun toString(): String = "^ $method ${url.path} HTTP/1.1 ${body?.encodeToByteArray()?.size ?: 0}"

    companion object {
        fun get(
            url: Url,
            headers: Map<String, String> = mapOf(),
        ) = Request("GET", url, headers, null)

        fun post(
            url: Url,
            headers: Map<String, String> = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body: String,
        ) = Request(
            "POST",
            url,
            headers,
            body,
        )
    }
}

class HttpClient {
    private var socket: Socket? = null
    private var origin: String? = null
    private val cache = mutableMapOf<Url, Pair<Instant, Response>>()
    private val logger = KotlinLogging.logger {}

    fun get(
        url: String,
        headers: Map<String, String> = mapOf(),
    ) = get(Url(url), headers)

    fun get(
        url: Url,
        headers: Map<String, String> = mapOf(),
    ): Response = request(Request.get(url, headers))

    fun post(
        url: Url,
        headers: Map<String, String>,
        body: String,
    ): Response = request(Request.post(url, headers, body))

    fun request(
        request: Request,
        redirectCount: Int = 0,
    ): Response {
        if (redirectCount > 10) {
            return Response.badRequest("Too many redirects.", request.url)
        }
        logger.info { request.toString() }

        return when (request.url.scheme) {
            "http" -> httpRequest(request, redirectCount)
            "https" -> httpRequest(request, redirectCount)
            "file" -> getFile(request.url)
            "data" -> getData(request.url)
            else -> throw Exception("Unknown scheme: ${request.url.scheme}")
        }
    }

    private fun httpRequest(
        request: Request,
        redirectCount: Int,
    ): Response {
        if (request.body == null && request.url in cache && Instant.now().isBefore(cache[request.url]!!.first)) {
            return cache[request.url]!!.second
        }

        if (origin != null && request.url.origin != origin) {
            socket?.close()
            socket = null
        }

        if (socket == null || socket?.isConnected != true || socket?.isClosed != false) {
            socket =
                try {
                    when (request.url.scheme) {
                        "http" -> SocketFactory.getDefault().createSocket(request.url.host, request.url.port)
                        "https" -> SSLSocketFactory.getDefault().createSocket(request.url.host, request.url.port)
                        else -> throw Exception("Unknown scheme: ${request.url.scheme}")
                    }
                } catch (_: UnknownHostException) {
                    return Response.notFound("Unknown host.", request.url)
                }
            origin = request.url.origin
        }

        val requestString = request.encode()

        logger.debug { "Send request to ${request.url}" }

        try {
            socket!!.getOutputStream().write(requestString.toByteArray())
            val response = parseResponse(socket!!.getInputStream(), request.method, request.url)
            if (response.status in 300..<400) {
                val location = response.headers["location"] ?: return Response.badRequest("Missing location header.", request.url)
                val nextUrl =
                    if (location.startsWith("/")) {
                        request.url.copy(path = location)
                    } else {
                        Url(location)
                    }
                return request(request.copy(url = nextUrl), redirectCount + 1)
            }
            cacheResponse(request.url, response)
            logger.info { response.toString() }
            return response
        } catch (e: Exception) {
            socket?.close()
            socket = null
            return Response.badRequest("Failed to connect to server: ${e.message}", request.url)
        }
    }

    private fun parseResponse(
        inputStream: java.io.InputStream,
        method: String,
        url: Url,
    ): Response {
        val statusLine = readLine(inputStream)
        val (_, status, reason) = statusLine.split(" ", limit = 3)

        val headerLines = mutableListOf<String>()
        while (true) {
            val line = readLine(inputStream)
            if (line.isBlank()) {
                break
            }
            headerLines.add(line)
        }
        val headers = headerLines.associate { it.split(": ").let { (key, value) -> key.lowercase() to value.trim() } }
        val length = headers["content-length"]?.toInt() ?: 0
        require("content-encoding" !in headers) { "Content encoding is not supported." }

        val transferEncoding = headers["transfer-encoding"]?.split(",")?.map { it.trim().lowercase() } ?: listOf()
        val body =
            if ("chunked" in transferEncoding) {
                readChunkedBody(inputStream)
            } else {
                readBody(inputStream, length)
            }

        return Response(status.toInt(), reason, headers, body, method, url)
    }

    private fun readBody(
        inputStream: java.io.InputStream,
        length: Int,
    ): String {
        val bodyData = inputStream.readNBytes(length)
        require(bodyData.size == length) { "Unexpected end of body." }
        return bodyData.decodeToString()
    }

    private fun readChunkedBody(inputStream: java.io.InputStream): String {
        var body = ""
        while (true) {
            val chunkHeader = readLine(inputStream)
            val chunkSize = chunkHeader.toInt(16)
            if (chunkSize == 0) {
                // read \r\n
                inputStream.readNBytes(2)
                return body
            }
            body += inputStream.readNBytes(chunkSize).decodeToString()
            inputStream.readNBytes(2)
        }
    }

    private fun readLine(inputStream: java.io.InputStream): String {
        var line = ""
        while (true) {
            val char = inputStream.read()
            if (char == '\n'.code) {
                break
            }
            if (char == '\r'.code) {
                continue
            }
            line += char.toChar()
        }
        return line
    }

    private fun cacheResponse(
        url: Url,
        response: Response,
    ) {
        if (response.status != 200) {
            return
        }
        val cacheControl =
            response.headers["cache-control"]
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?: listOf()
        if ("no-cache" in cacheControl) {
            return
        }
        val maxAge =
            cacheControl
                .find { it.startsWith("max-age=") }
                ?.split("=")
                ?.get(1)
                ?.toLong() ?: 0
        if (maxAge > 0) {
            cache[url] = Instant.now().plusSeconds(maxAge) to response
        }
    }

    private fun getFile(url: Url): Response {
        try {
            val path = Path.of(url.path)
            return Response.ok(Files.readString(path), mapOf(), url)
        } catch (_: Exception) {
            return Response.notFound("File not found.", url)
        }
    }

    private fun getData(url: Url): Response {
        val dataContent = url.dataContent
        if (',' !in dataContent) {
            return Response.badRequest("Missing comma in data URL.", url)
        }

        val (header, data) = dataContent.split(",", limit = 2)
        val body =
            if (header.endsWith(";base64")) {
                Base64.decode(data).decodeToString()
            } else {
                data
            }
        return Response.ok(body, mapOf(), url)
    }
}
