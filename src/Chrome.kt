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
    private val addressRect: Rect
    private val backRect: Rect
    val bottom: Int

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

        val urlBarTop = tabbarBottom
        val urlBarBottom = urlBarTop + 2 * padding + fontHeight.toInt()
        val backWidth = font.getStringBounds("<", fontRenderContext).width.toInt() + 2 * padding
        backRect = Rect(padding, urlBarTop + padding, padding + backWidth, urlBarBottom - padding)
        addressRect = Rect(backRect.right + padding, urlBarTop + padding, browser.width - padding, urlBarBottom - padding)

        bottom = urlBarBottom
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
            add(DrawLine(0, bottom, browser.width, bottom, "black", 1))

            add(DrawOutline(newTabRect, "black", 1))
            add(DrawText(newTabRect.top, newTabRect.left + padding, "+", font, "black"))

            browser.tabs.forEachIndexed { index, _ ->
                val rect = tabRect(index)
                add(DrawLine(rect.left, 0, rect.left, rect.bottom, "black", 1))
                add(DrawLine(rect.right, 0, rect.right, rect.bottom, "black", 1))
                add(DrawText(rect.top + padding, rect.left + padding, "Tab ${index + 1}", font, "black"))

                if (index == browser.currentTab) {
                    add(DrawLine(0, rect.bottom, rect.left, rect.bottom, "black", 1))
                    add(DrawLine(rect.right, rect.bottom, browser.width, rect.bottom, "black", 1))
                }
            }

            add(DrawOutline(backRect, "black", 1))
            add(DrawText(backRect.top, backRect.left + padding, "<", font, "black"))
            add(DrawOutline(addressRect, "black", 1))
            add(DrawText(addressRect.top, addressRect.left + padding, browser.tabs[browser.currentTab].url.toString(), font, "black"))
        }

    fun click(
        x: Int,
        y: Int,
    ) {
        if (newTabRect.contains(x, y)) {
            browser.newTab("about:blank")
        } else if (backRect.contains(x, y)) {
            browser.tabs[browser.currentTab].goBack()
        } else {
            browser.tabs.forEachIndexed { index, _ ->
                if (tabRect(index).contains(x, y)) {
                    browser.selectTab(index)
                }
            }
        }
    }
}
