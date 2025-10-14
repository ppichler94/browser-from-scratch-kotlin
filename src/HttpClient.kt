import java.io.BufferedReader
import java.net.Socket
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
    }
}

class HttpClient {
    private var socket: Socket? = null
    private var origin: String? = null
    private val cache = mutableMapOf<Url, Pair<Instant, Response>>()

    fun get(
        url: String,
        headers: Map<String, String> = mapOf(),
        redirectCount: Int = 0,
    ) = get(Url(url), headers, redirectCount)

    fun get(
        url: Url,
        headers: Map<String, String> = mapOf(),
        redirectCount: Int = 0,
    ): Response {
        if (redirectCount > 10) {
            return Response.badRequest("Too many redirects.")
        }

        return when (url.scheme) {
            "http" -> getHttp(url, headers, redirectCount)
            "https" -> getHttp(url, headers, redirectCount)
            "file" -> getFile(url)
            "data" -> getData(url)
            else -> throw Exception("Unknown scheme: ${url.scheme}")
        }
    }

    private fun getHttp(
        url: Url,
        headers: Map<String, String>,
        redirectCount: Int,
    ): Response {
        if (url in cache && Instant.now().isBefore(cache[url]!!.first)) {
            return cache[url]!!.second
        }

        if (socket == null || socket?.isClosed == true || origin != url.origin) {
            socket =
                when (url.scheme) {
                    "http" -> SocketFactory.getDefault().createSocket(url.host, 80)
                    "https" -> SSLSocketFactory.getDefault().createSocket(url.host, 443)
                    else -> throw Exception("Unknown scheme: ${url.scheme}")
                }
            origin = url.origin
        }

        var request = "GET ${url.path} HTTP/1.1\r\n"
        request += "Host: ${url.host}\r\n"
        request += "Connection: keep-alive\r\n"
        request += "User-Agent: MyBrowser/0.0\r\n"
        headers.forEach { (key, value) ->
            request += "$key: $value\r\n"
        }
        request += "\r\n"

        socket!!.getOutputStream().write(request.toByteArray())
        val response = parseResponse(socket!!.getInputStream().bufferedReader())
        if (response.status in 300..<400) {
            val location = response.headers["location"] ?: return Response.badRequest("Missing location header.")
            val nextUrl =
                if (location.startsWith("/")) {
                    url.copy(path = location)
                } else {
                    Url(location)
                }
            return get(nextUrl, headers, redirectCount + 1)
        }
        cacheResponse(url, response)
        return response
    }

    private fun parseResponse(reader: BufferedReader): Response {
        val headerLines = mutableListOf<String>()
        val statusLine = reader.readLine().split(" ")
        val status = statusLine[1].toInt()
        val reason = statusLine[2]

        while (true) {
            val line = reader.readLine()
            if (line.isBlank()) {
                break
            }
            headerLines.add(line)
        }
        val headers = headerLines.associate { it.split(": ").let { (key, value) -> key.lowercase() to value.trim() } }
        val length = headers["content-length"] ?: "0"
        val buffer = CharArray(length.toInt())
        reader.read(buffer, 0, length.toInt())
        val body = buffer.concatToString()
        return Response(status, reason, headers, body)
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
