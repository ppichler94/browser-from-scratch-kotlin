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
        var requestUrl = url
        if (url.startsWith("view-source:")) {
            requestUrl = url.removePrefix("view-source:")
        }
        val response = httpClient.get(requestUrl)
        val parser = if (url.startsWith("view-source")) ViewSourceHtmlParser(response.body) else HtmlParser(response.body)
        root = parser.parse()
        HtmlParser.style(root!!, defaultStyleSheet)
        document = DocumentLayout(root!!)
        document.layout(width)
        paintTree(document, displayList)
        repaint()
    }
}
