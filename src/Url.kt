data class Url(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val dataContent: String,
) {
    val origin get() = if (host.isNotEmpty()) "$scheme://$host:$port" else "$scheme://"

    override fun toString() = "$origin$path"
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

fun Url.resolve(url: String): Url {
    if ("://" in url) {
        return Url(url)
    }
    var url = url
    if (!url.startsWith("/")) {
        var dir = path.substringBeforeLast("/")
        while (url.startsWith("../")) {
            url = url.split("/", limit = 2)[1]
            dir = dir.substringBeforeLast("/")
        }
        url = "$dir/$url"
    }
    if (url.startsWith("//")) {
        return Url("${this.scheme}:$url")
    }
    return Url("$origin$url")
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
