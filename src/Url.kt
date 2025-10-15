data class Url(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val dataContent: String,
) {
    val origin get() = "$scheme://$host:$port"

    override fun toString() = "$scheme://$host:$port$path"
}

fun Url(url: String): Url {
    val (scheme, _) = url.split(":")
    return when (scheme) {
        "http" -> parseHttpUrl(url)
        "https" -> parseHttpUrl(url)
        "file" -> parseFileUrl(url)
        "data" -> parseDataUrl(url)
        else -> throw Exception("Unknown scheme: $scheme")
    }
}

private fun parseHttpUrl(url: String): Url {
    var (schema, rest) = url.split("://")
    var port = if (schema == "http") 80 else 443
    if ("/" !in rest) {
        rest += "/"
    }
    val parts = rest.split("/", limit = 2)
    var host = parts[0]
    rest = parts[1]
    if (":" in host) {
        val (hostStr, portStr) = host.split(":", limit = 2)
        host = hostStr
        port = portStr.toInt()
    }
    val path = "/" + rest

    return Url(schema, host, port, path, "")
}

private fun parseFileUrl(url: String): Url {
    val (schema, rest) = url.split("://")
    return Url(schema, "", 0, rest, "")
}

private fun parseDataUrl(url: String): Url {
    val (schema, rest) = url.split(":", limit = 2)
    return Url(schema, "", 0, "", rest)
}
