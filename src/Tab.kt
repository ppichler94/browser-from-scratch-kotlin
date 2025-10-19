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
    private var url = Url("http://localhost:8080")
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
        logger.debug { "Clicked at ($x, $y)" }
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
