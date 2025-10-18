import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Canvas
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

class Browser : Canvas() {
    private var displayList: MutableList<DrawCommand> = mutableListOf()
    private var scroll = 0
    private var root: Node? = null
    private lateinit var document: BlockLayout
    private val httpClient = HttpClient()
    private val logger = KotlinLogging.logger {}
    private var url = Url("http://localhost:8080")
    var onLoad: (url: String) -> Unit = {}
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
                    displayList = mutableListOf()
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

        addMouseListener(
            object : MouseInputAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        click(e.x, e.y)
                    }
                }
            },
        )
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

    fun load(urlToLoad: String) {
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
        repaint()
        onLoad(url.toString())
    }

    private fun click(
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
}
