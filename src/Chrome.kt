import java.awt.Font
import java.awt.font.FontRenderContext

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun contains(
        x: Int,
        y: Int,
    ) = x in left until right && y in top until bottom
}

class Chrome(
    private val browser: Browser,
) {
    private val font = Font("SansSerif", Font.PLAIN, 12)
    private val fontRenderContext = FontRenderContext(null, true, true)
    private val padding = 5
    private var addressRect: Rect
    private var addressBar: String = ""
    private val backButton: Button
    private val forwardButton: Button
    private val urlBarTop: Int
    private val urlBarBottom: Int
    private var focus = FocusElement.None
    private val tabBar = TabBar(0, 0, font)
    val bottom: Int

    private val currentTab: Tab? get() = browser.tabs.getOrNull(browser.currentTab)

    init {
        val fontHeight = font.getLineMetrics("Tab", fontRenderContext).height
        urlBarTop = tabBar.bottom
        urlBarBottom = urlBarTop + 2 * padding + fontHeight.toInt()
        backButton = Button(padding, urlBarTop + padding, "<", font)
        forwardButton = Button(backButton.right + padding, urlBarTop + padding, ">", font)
        addressRect = Rect(forwardButton.right + padding, urlBarTop + padding, browser.width - padding, urlBarBottom)

        bottom = urlBarBottom + padding
    }

    fun paint(): List<DrawCommand> =
        buildList {
            add(DrawRect(0, 0, bottom, browser.width, "white"))
            add(DrawLine(0, bottom, browser.width, bottom, "gray", 1))

            addAll(tabBar.paint(browser.tabs.size, browser.currentTab, browser.width))

            addAll(backButton.paint(currentTab?.historyEmpty == false))
            addAll(forwardButton.paint(currentTab?.forwardHistoryEmpty == false))
            // address bar
            add(DrawOutline(addressRect, "dimgray", 1))
            if (focus == FocusElement.AddressBar) {
                add(DrawText(addressRect.top, addressRect.left + padding, addressBar, font, "black"))
                val textWidth = font.getStringBounds(addressBar, fontRenderContext).width.toInt()
                add(
                    DrawLine(
                        addressRect.left + padding + textWidth,
                        addressRect.top,
                        addressRect.left + padding + textWidth,
                        addressRect.bottom,
                        "red",
                        1,
                    ),
                )
            } else {
                val url = currentTab?.url?.toString() ?: ""
                add(DrawText(addressRect.top, addressRect.left + padding, url, font, "black"))
            }
        }

    fun click(
        x: Int,
        y: Int,
    ) {
        focus = FocusElement.None
        when (val tabEvent = tabBar.click(x, y, browser.tabs.size)) {
            is TabBar.TabSelected -> browser.currentTab = tabEvent.index
            is TabBar.NewTab -> browser.newTab("about:blank")
            null -> {}
        }
        when {
            backButton.contains(x, y) -> currentTab?.goBack()
            forwardButton.contains(x, y) -> currentTab?.goForward()
            addressRect.contains(x, y) -> {
                focus = FocusElement.AddressBar
                addressBar = ""
            }
        }
    }

    fun keyPress(key: Char) {
        if (focus == FocusElement.AddressBar) {
            addressBar += key
        }
    }

    fun enter() {
        if (focus == FocusElement.AddressBar) {
            browser.load(addressBar)
            addressBar = ""
            focus = FocusElement.None
        }
    }

    fun backspace() {
        if (focus == FocusElement.AddressBar) {
            addressBar = addressBar.dropLast(1)
        }
    }

    fun resized() {
        addressRect = Rect(forwardButton.right + padding, urlBarTop + padding, browser.width - padding, urlBarBottom)
    }

    enum class FocusElement {
        AddressBar,
        None,
    }
}

class TabBar(
    private val x: Int,
    private val y: Int,
    private val font: Font,
) {
    private val padding = 5
    val bottom: Int
    private val fontRenderContext = FontRenderContext(null, true, true)
    private val newTabButton = Button(x + padding, y + padding, "+", font)

    init {
        val fontHeight = font.getLineMetrics("Tab", fontRenderContext).height
        bottom = (fontHeight + padding * 2).toInt()
    }

    private fun tabRect(index: Int): Rect {
        val tabsStart = newTabButton.right + padding
        val tabWidth = font.getStringBounds("Tab X", fontRenderContext).width.toInt()
        return Rect(
            tabsStart + (tabWidth + padding * 2) * index,
            y,
            tabsStart + (tabWidth + padding * 2) * (index + 1),
            bottom,
        )
    }

    fun paint(
        numTabs: Int,
        currentTab: Int,
        browserWidth: Int,
    ): List<DrawCommand> =
        buildList {
            addAll(newTabButton.paint(true))

            (0 until numTabs).forEach { index ->
                val rect = tabRect(index)
                add(DrawLine(rect.left, 0, rect.left, rect.bottom, "gray", 1))
                add(DrawLine(rect.right, 0, rect.right, rect.bottom, "gray", 1))
                add(DrawText(rect.top + padding, rect.left + padding, "Tab ${index + 1}", font, "black"))

                if (index == currentTab) {
                    add(DrawLine(0, rect.bottom, rect.left, rect.bottom, "gray", 1))
                    add(DrawLine(rect.right, rect.bottom, browserWidth, rect.bottom, "gray", 1))
                }
            }
        }

    fun click(
        x: Int,
        y: Int,
        numTabs: Int,
    ): Event? {
        when {
            newTabButton.contains(x, y) -> return NewTab()
            else -> {
                (0 until numTabs).forEach { index ->
                    if (tabRect(index).contains(x, y)) {
                        return TabSelected(index)
                    }
                }
            }
        }
        return null
    }

    sealed interface Event

    data class TabSelected(
        val index: Int,
    ) : Event

    class NewTab : Event
}

class Button(
    x: Int,
    y: Int,
    private val text: String,
    private val font: Font,
) {
    private val rect: Rect
    private val padding = 5
    private val fontRenderContext = FontRenderContext(null, true, true)
    val right: Int get() = rect.right
    val bottom: Int get() = rect.bottom

    init {
        val fontHeight = font.getLineMetrics("Tab", fontRenderContext).height.toInt()
        val textWidth = font.getStringBounds(text, fontRenderContext).width.toInt() + 2 * padding
        rect = Rect(x, y, x + textWidth, y + fontHeight + padding)
    }

    fun paint(enabled: Boolean): List<DrawCommand> =
        buildList {
            val backBgColor = if (!enabled) "lightgray" else "dimgray"
            val backColor = if (!enabled) "lightgray" else "black"
            add(DrawOutline(rect, backBgColor, 1))
            add(DrawText(rect.top, rect.left + padding, text, font, backColor))
        }

    fun contains(
        x: Int,
        y: Int,
    ) = this.rect.contains(x, y)
}
