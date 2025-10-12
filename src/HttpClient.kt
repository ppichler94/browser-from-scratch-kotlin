import java.net.Socket

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
            else -> throw Exception("Unknown scheme: ${url.scheme}")
        }
    }

    private fun getHttp(): Response {
        val socket = Socket(url.host, url.port)
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
}
