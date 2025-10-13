import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

fun main() {
    val window = Frame("Browser")
    window.setSize(800, 600)
    window.layout = BorderLayout()

    // Create canvas
    val canvas = BrowserCanvas()
    canvas.background = Color.WHITE

    // Create address bar panel
    val addressBar = Panel(FlowLayout(FlowLayout.LEFT))
    val urlLabel = Label("URL:")
    val urlField = TextField(50) // Set width for the text field
    urlField.addActionListener { canvas.load(urlField.text) }
    val button = Button("Go!")
    button.addActionListener { canvas.load(urlField.text) }
    addressBar.add(urlLabel)
    addressBar.add(urlField)
    addressBar.add(button)

    // Add components to window using BorderLayout
    window.add(addressBar, BorderLayout.NORTH)
    window.add(canvas, BorderLayout.CENTER)

    window.addWindowListener(
        object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                exitProcess(0)
            }
        },
    )

    window.isVisible = true
}

class BrowserCanvas : Canvas() {
    private var displayList: List<DisplayElement> = mutableListOf()
    private var contentHeight = 0
    private var scroll = 0
    private var root: Node? = null

    init {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (root == null) {
                        return
                    }
                    val layout = Layout(root!!, width)
                    displayList = layout.displayList
                    contentHeight = layout.contentHeight
                    repaint()
                }
            },
        )

        addMouseWheelListener {
            scroll += it.wheelRotation * 30
            if (scroll < 0) {
                scroll = 0
            }
            if (scroll > contentHeight - height) {
                scroll = contentHeight - height
            }
            repaint()
        }
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        drawContent(g)
        drawScrollbar(g)
    }

    private fun drawContent(g: Graphics) {
        displayList
            .filter { it.y <= scroll + height && it.y + 18 >= scroll }
            .forEach { (x, y, text, font) ->
                g.font = font
                g.drawString(text, x, y - scroll)
            }
    }

    private fun drawScrollbar(g: Graphics) {
        if (contentHeight <= height) {
            return
        }
        val relativePageHeight = height.toDouble() / contentHeight.toDouble()
        val scrollbarHeight = (height * relativePageHeight).toInt()
        val scrollbarY = scroll / contentHeight.toDouble() * height
        g.color = Color.BLUE
        g.fillRect(width - 12, scrollbarY.toInt(), 8, scrollbarHeight)
    }

    fun load(url: String) {
        var _url = url
        if (url.startsWith("view-source:")) {
            _url = url.removePrefix("view-source:")
        }
        val response = HttpClient(_url).get()
        val parser = if (url.startsWith("view-source")) ViewSourceHtmlParser(response.body) else HtmlParser(response.body)
        root = parser.parse()
        val layout = Layout(root!!, width)
        displayList = layout.displayList
        contentHeight = layout.contentHeight
        repaint()
    }
}
