import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Canvas
import java.awt.Graphics
import java.awt.event.*
import javax.swing.event.MouseInputAdapter

class Browser : Canvas() {
    val tabs = mutableListOf<Tab>()
    var currentTab = 0
    private val logger = KotlinLogging.logger {}
    private val chrome = Chrome(this)

    init {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (currentTab < tabs.size) {
                        tabs[currentTab].resized(e.component.width, e.component.height - chrome.bottom)
                    }
                    chrome.resized()
                    repaint()
                }
            },
        )

        addMouseWheelListener {
            if (currentTab < tabs.size) {
                tabs[currentTab].scroll(it.wheelRotation)
            }
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

        addKeyListener(
            object : KeyAdapter() {
                override fun keyTyped(e: KeyEvent) {
                    if (e.keyChar in 0x20.toChar()..0x7F.toChar()) {
                        chrome.keyPress(e.keyChar)
                    }

                    repaint()
                }

                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        chrome.enter()
                    }

                    if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                        chrome.backspace()
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
    }

    fun newTab(url: String) {
        val newTab = Tab(width, height - chrome.bottom)
        newTab.load(url)
        tabs.add(newTab)
        currentTab = tabs.size - 1
        repaint()
    }
}
