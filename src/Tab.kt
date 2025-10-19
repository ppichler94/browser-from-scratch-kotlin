import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.awt.Graphics

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
    var url = Url("about:blank")
        private set
    private val defaultStyleSheet =
        CssParser(
            this::class.java
                .getResourceAsStream("user-agent.css")
                ?.reader()
                ?.readText() ?: "",
        ).parse()

    fun click(
        x: Int,
        y: Int,
    ) {
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
            }

            element = element.parent
        }
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
        if (urlToLoad == "about:blank") {
            url = Url(urlToLoad)
            return
        }
        url =
            if (urlToLoad.startsWith("view-source:")) {
                Url(urlToLoad.removePrefix("view-source:"))
            } else {
                Url(urlToLoad)
            }
        val viewSource = urlToLoad.startsWith("view-source")
        load(url, viewSource)
    }

    fun load(
        url: Url,
        viewSource: Boolean = false,
    ) {
        this.url = url
        history.add(url)
        val response = httpClient.get(url)
        val parser = if (viewSource) ViewSourceHtmlParser(response.body) else HtmlParser(response.body)
        root = parser.parse()
        val rules = mutableListOf<CssParser.CssRule>()
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
                } catch (e: Exception) {
                    logger.warn { "Failed to load stylesheet: $it" }
                    null
                }
            }.forEach {
                rules.addAll(CssParser(it.body).parse())
            }
        rules.sortBy { it.first.specificity }
        HtmlParser.style(root!!, rules)
        document = DocumentLayout(root!!)
        document.layout(width)
        displayList = mutableListOf()
        paintTree(document, displayList)
    }

    fun goBack() {
        if (history.size <= 1) {
            return
        }
        history.removeLast()
        load(history.removeLast())
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
        g.color = Color.BLUE
        g.fillRect(width - 12, scrollbarY.toInt(), 8, scrollbarHeight)
    }
}
