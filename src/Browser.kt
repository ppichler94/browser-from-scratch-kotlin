import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Canvas
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class Browser : Canvas() {
    private var displayList: MutableList<DrawCommand> = mutableListOf()
    private var scroll = 0
    private var root: Node? = null
    private lateinit var document: BlockLayout
    private val httpClient = HttpClient()
    private val logger = KotlinLogging.logger {}
    private val defaultStyleSheet =
        CssParser(
            this::class.java
                .getResourceAsStream("user-agent.css")
                ?.reader()
                ?.readText() ?: "",
        ).parse()

    init {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (root == null || !::document.isInitialized) {
                        return
                    }
                    document.layout(width)
                    paintTree(document, displayList)
                    repaint()
                }
            },
        )

        addMouseWheelListener {
            if (!::document.isInitialized) {
                return@addMouseWheelListener
            }
            scroll += it.wheelRotation * 30
            if (scroll < 0) {
                scroll = 0
            }
            if (scroll > document.height - height) {
                scroll = document.height - height
            }
            repaint()
        }
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        displayList.forEach { it.paint(g, scroll) }
        drawScrollbar(g)
    }

    private fun drawScrollbar(g: Graphics) {
        if (!::document.isInitialized || document.height <= height) {
            return
        }
        val relativePageHeight = height.toDouble() / document.height.toDouble()
        val scrollbarHeight = (height * relativePageHeight).toInt()
        val scrollbarY = scroll / document.height.toDouble() * height
        g.color = Color.BLUE
        g.fillRect(width - 12, scrollbarY.toInt(), 8, scrollbarHeight)
    }

    fun load(url: String) {
        val requestUrl =
            if (url.startsWith("view-source:")) {
                Url(url.removePrefix("view-source:"))
            } else {
                Url(url)
            }
        val response = httpClient.get(requestUrl)
        val parser = if (url.startsWith("view-source")) ViewSourceHtmlParser(response.body) else HtmlParser(response.body)
        root = parser.parse()
        val rules = mutableListOf<CssParser.CssRule>()
        rules.addAll(defaultStyleSheet)
        root!!
            .treeToList()
            .filterIsInstance<Element>()
            .filter { it.tag == "link" && it.attributes["rel"] == "stylesheet" && "href" in it.attributes }
            .map { it.attributes["href"]!! }
            .map { requestUrl.resolve(it).toString() }
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
        paintTree(document, displayList)
        repaint()
    }
}
