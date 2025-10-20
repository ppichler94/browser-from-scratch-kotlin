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
    private val tabbarTop = 0
    private val tabbarBottom: Int
    private val newTabRect: Rect
    private var addressRect: Rect
    private var addressBar: String = ""
    private val backRect: Rect
    private val forwardRect: Rect
    private val urlBarTop: Int
    private val urlBarBottom: Int
    private var focus = FocusElement.None
    val bottom: Int

    private val currentTab: Tab? get() = browser.tabs.getOrNull(browser.currentTab)

    init {
        val fontHeight = font.getLineMetrics("Tab", fontRenderContext).height
        tabbarBottom = (fontHeight + padding * 2).toInt()
        val plusWidth = font.getStringBounds("+", fontRenderContext).width.toInt() + 2 * padding
        newTabRect =
            Rect(
                padding,
                padding,
                padding + plusWidth,
                padding + fontHeight.toInt(),
            )

        urlBarTop = tabbarBottom
        urlBarBottom = urlBarTop + 2 * padding + fontHeight.toInt()
        val backWidth = font.getStringBounds("<", fontRenderContext).width.toInt() + 2 * padding
        backRect = Rect(padding, urlBarTop + padding, padding + backWidth, urlBarBottom)
        forwardRect = Rect(backRect.right + padding, urlBarTop + padding, backRect.right + padding + backWidth, urlBarBottom)
        addressRect = Rect(forwardRect.right + padding, urlBarTop + padding, browser.width - padding, urlBarBottom)

        bottom = urlBarBottom + padding
    }

    fun tabRect(index: Int): Rect {
        val tabsStart = newTabRect.right + padding
        val tabWidth = font.getStringBounds("Tab X", fontRenderContext).width.toInt()
        return Rect(
            tabsStart + (tabWidth + padding * 2) * index,
            tabbarTop,
            tabsStart + (tabWidth + padding * 2) * (index + 1),
            tabbarBottom,
        )
    }

    fun paint(): List<DrawCommand> =
        buildList {
            add(DrawRect(0, 0, bottom, browser.width, "white"))
            add(DrawLine(0, bottom, browser.width, bottom, "gray", 1))

            add(DrawOutline(newTabRect, "dimgray", 1))
            add(DrawText(newTabRect.top, newTabRect.left + padding, "+", font, "black"))

            browser.tabs.forEachIndexed { index, _ ->
                val rect = tabRect(index)
                add(DrawLine(rect.left, 0, rect.left, rect.bottom, "gray", 1))
                add(DrawLine(rect.right, 0, rect.right, rect.bottom, "gray", 1))
                add(DrawText(rect.top + padding, rect.left + padding, "Tab ${index + 1}", font, "black"))

                if (index == browser.currentTab) {
                    add(DrawLine(0, rect.bottom, rect.left, rect.bottom, "gray", 1))
                    add(DrawLine(rect.right, rect.bottom, browser.width, rect.bottom, "gray", 1))
                }
            }

            // back button
            val backBgColor = if (currentTab?.historyEmpty == true) "lightgray" else "dimgray"
            val backColor = if (currentTab?.historyEmpty == true) "lightgray" else "black"
            add(DrawOutline(backRect, backBgColor, 1))
            add(DrawText(backRect.top, backRect.left + padding, "<", font, backColor))
            // Forward button
            val forwardBgColor = if (currentTab?.forwardHistoryEmpty == true) "lightgray" else "dimgray"
            val forwardColor = if (currentTab?.forwardHistoryEmpty == true) "lightgray" else "black"
            add(DrawOutline(forwardRect, forwardBgColor, 1))
            add(DrawText(forwardRect.top, forwardRect.left + padding, ">", font, forwardColor))
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
        when {
            newTabRect.contains(x, y) -> browser.newTab("about:blank")
            backRect.contains(x, y) -> currentTab?.goBack()
            forwardRect.contains(x, y) -> currentTab?.goForward()
            addressRect.contains(x, y) -> {
                focus = FocusElement.AddressBar
                addressBar = ""
            }
            else -> {
                browser.tabs.forEachIndexed { index, _ ->
                    if (tabRect(index).contains(x, y)) {
                        browser.currentTab = index
                    }
                }
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
        addressRect = Rect(forwardRect.right + padding, urlBarTop + padding, browser.width - padding, urlBarBottom)
    }

    enum class FocusElement {
        AddressBar,
        None,
    }
}
