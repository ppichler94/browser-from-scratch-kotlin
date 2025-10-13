import java.nio.file.Files
import java.nio.file.Path
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import kotlin.io.encoding.Base64

data class Response(
    val status: Int,
    val reason: String,
    val headers: Map<String, String>,
    val body: String,
) {
    companion object {
        fun fromText(text: String): Response {
            val (header, body) = text.split("\r\n\r\n", limit = 2)
            val headerLines = header.split("\r\n")
            val statusLine = headerLines[0].split(" ")
            val status = statusLine[1].toInt()
            val reason = statusLine[2]
            val headers = headerLines.drop(1).associate { line -> line.split(": ").let { it[0] to it[1] } }
            return Response(status, reason, headers, body)
        }

        fun ok(body: String) = Response(200, "OK", mapOf(), body)
    }
}

class HttpClient(
    url: String,
    private val headers: Map<String, String> = mapOf(),
) {
    private val url = Url(url)

    fun get(): Response {
        when (url.scheme) {
            "http" -> return getHttp()
            "https" -> return getHttp()
            "file" -> return getFile()
            "data" -> return getData()
            else -> throw Exception("Unknown scheme: ${url.scheme}")
        }
    }

    private fun getHttp(): Response {
        val socket =
            when (url.scheme) {
                "http" -> SocketFactory.getDefault().createSocket(url.host, 80)
                "https" -> SSLSocketFactory.getDefault().createSocket(url.host, 443)
                else -> throw Exception("Unknown scheme: ${url.scheme}")
            }
        var request = "GET ${url.path} HTTP/1.1\r\n"
        request += "Host: ${url.host}\r\n"
        request += "Connection: close\r\n"
        request += "User-Agent: MyBrowser/0.0\r\n"
        headers.forEach { (key, value) ->
            request += "$key: $value\r\n"
        }
        request += "\r\n"
        socket.getOutputStream().write(request.toByteArray())
        val response = socket.getInputStream().bufferedReader().readText()
        socket.close()
        return Response.fromText(response)
    }

    private fun getFile(): Response {
        try {
            val path = Path.of(url.path)
            return Response.ok(Files.readString(path))
        } catch (e: Exception) {
            return Response(404, "Not Found", mapOf(), "File not found.")
        }
    }

    private fun getData(): Response {
        val dataContent = url.dataContent
        if (',' !in dataContent) {
            return Response(400, "Bad Request", mapOf(), "Missing comma in data URL.")
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
