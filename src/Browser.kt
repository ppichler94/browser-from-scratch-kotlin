import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Canvas
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

class Browser : Canvas() {
    val tabs = mutableListOf<Tab>()
    var currentTab = 0
    var onLoad: (url: String) -> Unit = {}
    private val logger = KotlinLogging.logger {}
    private val chrome = Chrome(this)

    init {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (currentTab >= tabs.size) {
                        return
                    }
                    tabs[currentTab].resized(e.component.width, e.component.height - chrome.bottom)
                    repaint()
                }
            },
        )

        addMouseWheelListener {
            if (currentTab >= tabs.size) {
                return@addMouseWheelListener
            }
            tabs[currentTab].scroll(it.wheelRotation)
            repaint()
        }

        addMouseListener(
            object : MouseInputAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        if (e.y < chrome.bottom) {
                            chrome.click(e.x, e.y)
                        } else {
                            tabs[currentTab].click(e.x, e.y - chrome.bottom)
                        }
                    }
                    repaint()
                }
            },
        )
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        if (currentTab < tabs.size) {
            tabs[currentTab].paint(g, chrome.bottom)
        }
        chrome.paint().forEach { it.paint(g, 0) }
    }

    fun load(url: String) {
        if (currentTab >= tabs.size) {
            newTab(url)
            return
        }
        tabs[currentTab].load(url)
        repaint()
        onLoad(url)
    }

    fun newTab(url: String) {
        val newTab = Tab(width, height - chrome.bottom)
        newTab.load(url)
        tabs.add(newTab)
        currentTab = tabs.size - 1
        onLoad(url)
        repaint()
    }

    fun selectTab(index: Int) {
        currentTab = index
        repaint()
    }
}
