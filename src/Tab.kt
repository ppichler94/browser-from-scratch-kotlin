import io.github.oshai.kotlinlogging.KotlinLogging
import layout.*
import java.awt.Color
import java.awt.Graphics
import java.net.URLEncoder

class Tab(
    private var width: Int,
    private var height: Int,
) {
    private var displayList: MutableList<DrawCommand> = mutableListOf()
    private var scroll = 0
    private var root: Node? = null
    private lateinit var document: BlockLayout
    private val httpClient = HttpClient()
    private val logger = KotlinLogging.logger {}
    private val history = mutableListOf<Url>()
    private val forwardHistory = mutableListOf<Url>()
    val historyEmpty get() = history.size <= 1
    val forwardHistoryEmpty get() = forwardHistory.isEmpty()
    var title: String = "Untitled"
        private set
    var url = Url("about:blank")
        private set
    private val defaultStyleSheet =
        CssParser(
            this::class.java
                .getResourceAsStream("user-agent.css")
                ?.reader()
                ?.readText() ?: "",
        ).parse()
    private var rules = mutableListOf<CssParser.CssRule>()
    private var focus: Element? = null

    fun click(
        x: Int,
        y: Int,
    ) {
        if (focus != null) {
            focus!!.isFocused = false
            focus = null
            render()
        }
        val y = y + scroll
        val objects =
            document
                .treeToList()
                .filter { x in it.x..it.x + it.width && y in it.y..it.y + it.height }
        if (objects.isEmpty()) {
            return
        }
        var element: Node? = objects.last().node
        while (element != null) {
            if (element is Element && element.tag == "a" && "href" in element.attributes) {
                val url = url.resolve(element.attributes["href"]!!)
                load(url)
                return
            } else if (element is Element && element.tag == "input") {
                focus = element
                focus!!.isFocused = true
                element.attributes["value"] = ""
            } else if (element is Element && element.tag == "button") {
                while (element != null) {
                    if (element is Element && element.tag == "form" && "action" in element.attributes) {
                        submitForm(element)
                        return
                    }
                    element = element.parent
                }
            }

            element = element!!.parent
        }
        render()
    }

    fun scroll(rotation: Int) {
        if (!::document.isInitialized) {
            return
        }
        scroll += rotation * 30
        if (scroll < 0) {
            scroll = 0
        }
        if (scroll > document.height - height) {
            scroll = document.height - height
        }
    }

    fun resized(
        width: Int,
        height: Int,
    ) {
        this.height = height
        this.width = width
        if (root == null || !::document.isInitialized) {
            return
        }
        document.layout(width)
        displayList = mutableListOf()
        paintTree(document, displayList)
    }

    fun paint(
        g: Graphics,
        offset: Int,
    ) {
        displayList.forEach { it.paint(g, scroll - offset) }
        drawScrollbar(g, offset)
    }

    fun load(urlToLoad: String) {
        forwardHistory.clear()
        url =
            if (urlToLoad.startsWith("view-source:")) {
                Url(urlToLoad.removePrefix("view-source:"))
            } else {
                Url(urlToLoad)
            }
        val viewSource = urlToLoad.startsWith("view-source")
        doLoad(viewSource)
    }

    fun load(urlToLoad: Url) {
        forwardHistory.clear()
        url = urlToLoad
        doLoad(false)
    }

    fun doLoad(
        viewSource: Boolean = false,
        body: String? = null,
    ) {
        history.add(url)
        val responseBody = getContent(url, body)
        val parser = if (viewSource) ViewSourceHtmlParser(responseBody) else HtmlParser(responseBody)
        root = parser.parse()
        rules = mutableListOf()
        rules.addAll(defaultStyleSheet)
        root!!
            .treeToList()
            .filterIsInstance<Element>()
            .filter { it.tag == "link" && it.attributes["rel"] == "stylesheet" && "href" in it.attributes }
            .map { it.attributes["href"]!! }
            .map { url.resolve(it).toString() }
            .mapNotNull {
                try {
                    httpClient.get(it)
                } catch (_: Exception) {
                    logger.warn { "Failed to load stylesheet: $it" }
                    null
                }
            }.forEach {
                rules.addAll(CssParser(it.body).parse())
            }
        rules.sortBy { it.first.specificity }
        title =
            root!!
                .treeToList()
                .filterIsInstance<Element>()
                .firstOrNull { it.tag == "title" }
                ?.children
                ?.firstOrNull()
                ?.toString()
                ?: "Untitled"
        render()
    }

    private fun render() {
        HtmlParser.style(root!!, rules)
        document = DocumentLayout(root!!)
        document.layout(width)
        displayList = mutableListOf()
        paintTree(document, displayList)
    }

    private fun getContent(
        url: Url,
        body: String?,
    ): String {
        when (url.scheme) {
            "http", "https", "file" -> {
                val response =
                    if (body != null) {
                        httpClient.post(url, mapOf("Content-Type" to "application/x-www-form-urlencoded"), body)
                    } else {
                        httpClient.get(url)
                    }
                if (response.status != 200) {
                    return """<html>
                        <p>HTTP error: ${response.status}</p>
                        <p>${response.body}</p>
                        </html>
                        """.trimMargin()
                }
                return response.body
            }
            "about" if (url.path == "blank") -> {
                return "<html></html>"
            }
        }

        return "<html>Unknown protocol: ${url.scheme}</html>"
    }

    fun goBack() {
        if (history.size <= 1) {
            return
        }
        val current = history.removeLast()
        forwardHistory.add(current)
        url = history.removeLast()
        doLoad()
    }

    fun goForward() {
        if (forwardHistory.isEmpty()) {
            return
        }
        url = forwardHistory.removeLast()
        doLoad()
    }

    fun keyPress(char: Char) {
        if (focus != null) {
            focus!!.attributes["value"] += char
            render()
        }
    }

    private fun drawScrollbar(
        g: Graphics,
        offset: Int,
    ) {
        if (!::document.isInitialized || document.height <= height) {
            return
        }
        val relativePageHeight = height.toDouble() / document.height.toDouble()
        val scrollbarHeight = (height * relativePageHeight).toInt()
        val scrollbarY = scroll / document.height.toDouble() * height + offset
        g.color = Color(105, 105, 105)
        g.fillRect(width - 12, scrollbarY.toInt(), 8, scrollbarHeight)
    }

    private fun submitForm(form: Element) {
        val inputs =
            form
                .treeToList()
                .filter { it is Element && it.tag == "input" && "name" in it.attributes }
                .map { it as Element }
        val body =
            inputs.joinToString("&") {
                val name = URLEncoder.encode(it.attributes["name"]!!, "UTF-8")
                val value = URLEncoder.encode(it.attributes["value"] ?: "", "UTF-8")
                "$name=$value"
            }
        url = url.resolve(form.attributes["action"]!!)
        doLoad(false, body)
    }
}
