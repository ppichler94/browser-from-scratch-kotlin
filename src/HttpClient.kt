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
) {
    override fun toString(): String {
        val headers = headers.entries.joinToString("\n") { (key, value) -> "$key: $value" }
        return "HTTP/1.1 $status $reason\n$headers\n\nBody length: ${body.length}"
    }

    companion object {
        fun ok(
            body: String,
            headers: Map<String, String> = mapOf(),
        ) = Response(200, "OK", headers, body)

        fun badRequest(body: String) = Response(400, "Bad Request", mapOf(), body)

        fun notFound(body: String) = Response(404, "Not Found", mapOf(), body)
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
    ): Response {
        logger.info { "GET $url" }
        return request(url, headers, "GET", null)
    }

    fun post(
        url: String,
        headers: Map<String, String> = mapOf(),
        body: String,
    ) = post(Url(url), headers, body)

    fun post(
        url: Url,
        headers: Map<String, String> = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        body: String,
    ): Response {
        logger.info { "POST $url" }
        return request(url, headers, "POST", body)
    }

    fun request(
        url: Url,
        headers: Map<String, String> = mapOf(),
        method: String,
        body: String? = null,
        redirectCount: Int = 0,
    ): Response {
        if (redirectCount > 10) {
            return Response.badRequest("Too many redirects.")
        }
        logger.info { "$method request to $url" }

        return when (url.scheme) {
            "http" -> httpRequest(url, headers, method, body, redirectCount)
            "https" -> httpRequest(url, headers, method, body, redirectCount)
            "file" -> getFile(url)
            "data" -> getData(url)
            else -> throw Exception("Unknown scheme: ${url.scheme}")
        }
    }

    private fun httpRequest(
        url: Url,
        headers: Map<String, String>,
        method: String,
        body: String? = null,
        redirectCount: Int,
    ): Response {
        if (body == null && url in cache && Instant.now().isBefore(cache[url]!!.first)) {
            return cache[url]!!.second
        }

        if (origin != null && url.origin != origin) {
            socket?.close()
            socket = null
        }

        if (socket == null || socket?.isConnected != true || socket?.isClosed != false) {
            socket =
                try {
                    when (url.scheme) {
                        "http" -> SocketFactory.getDefault().createSocket(url.host, url.port)
                        "https" -> SSLSocketFactory.getDefault().createSocket(url.host, url.port)
                        else -> throw Exception("Unknown scheme: ${url.scheme}")
                    }
                } catch (_: UnknownHostException) {
                    return Response.notFound("Unknown host.")
                }
            origin = url.origin
        }

        var request = "$method ${url.path} HTTP/1.1\r\n"
        request += "Host: ${url.host}\r\n"
        request += "Connection: keep-alive\r\n"
        request += "User-Agent: MyBrowser/0.0\r\n"

        if (body != null) {
            val length = body.encodeToByteArray().size
            request += "Content-Length: $length\r\n"
        }

        headers.forEach { (key, value) ->
            request += "$key: $value\r\n"
        }
        request += "\r\n"

        if (body != null) {
            request += body
        }

        logger.debug { "Send request to $url" }

        try {
            socket!!.getOutputStream().write(request.toByteArray())
            val response = parseResponse(socket!!.getInputStream())
            if (response.status in 300..<400) {
                val location = response.headers["location"] ?: return Response.badRequest("Missing location header.")
                val nextUrl =
                    if (location.startsWith("/")) {
                        url.copy(path = location)
                    } else {
                        Url(location)
                    }
                return request(nextUrl, headers, method, body, redirectCount + 1)
            }
            cacheResponse(url, response)
            logger.info { "Received response: $response" }
            return response
        } catch (e: Exception) {
            socket?.close()
            socket = null
            return Response.badRequest("Failed to connect to server: ${e.message}")
        }
    }

    private fun parseResponse(inputStream: java.io.InputStream): Response {
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

        return Response(status.toInt(), reason, headers, body)
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
            return Response.ok(Files.readString(path))
        } catch (_: Exception) {
            return Response(404, "Not Found", mapOf(), "File not found.")
        }
    }

    private fun getData(url: Url): Response {
        val dataContent = url.dataContent
        if (',' !in dataContent) {
            return Response.badRequest("Missing comma in data URL.")
        }

        val (header, data) = dataContent.split(",", limit = 2)
        val body =
            if (header.endsWith(";base64")) {
                Base64.decode(data).decodeToString()
            } else {
                data
            }
        return Response.ok(body)
    }
}
