package layout

import DrawCommand
import DrawRect
import Element
import Node
import Text
import java.awt.font.FontRenderContext

open class BlockLayout(
    override val node: Node,
    override val parent: Layout?,
    private val previous: Layout?,
) : Layout() {
    var cursorX = 0
    val fontRenderContext = FontRenderContext(null, true, true)

    override fun layout(frameWidth: Int) {
        if (node is Element && (node as Element).tag == "head") {
            return
        }
        x = parent?.x ?: 0
        width = parent?.width ?: (frameWidth - x)
        y =
            if (previous != null) {
                previous.y + previous.height
            } else {
                parent?.y ?: 0
            }

        if (node is Element && (node as Element).tag == "li") {
            x += 12
        }

        val mode = layoutMode()
        var previous: Layout? = null
        when (mode) {
            LayoutMode.BLOCK -> {
                groupInlineElements().forEach {
                    val next =
                        if (it.first == LayoutMode.INLINE) {
                            AnonymousBlockBoxLayout(it.second, this, previous)
                        } else {
                            BlockLayout(it.second.first(), this, previous)
                        }
                    children.add(next)
                    previous = next
                }
            }
            LayoutMode.INLINE -> {
                val next = AnonymousBlockBoxLayout(listOf(node), this, previous)
                children.add(next)
                previous = next
            }
        }

        children.forEach { it.layout(width) }

        height = children.sumOf { it.height }
    }

    private fun groupInlineElements(): List<Pair<LayoutMode, List<Node>>> {
        val result = mutableListOf<Pair<LayoutMode, List<Node>>>()
        var current = mutableListOf<Node>()
        node.children.forEach {
            if (it is Element && it.tag in BLOCK_ELEMENTS) {
                if (current.isNotEmpty()) {
                    result.add(LayoutMode.INLINE to current)
                }
                result.add(LayoutMode.BLOCK to listOf(it))
                current = mutableListOf()
            } else {
                current.add(it)
            }
        }
        if (current.isNotEmpty()) {
            result.add(LayoutMode.INLINE to current)
        }
        return result
    }

    private fun layoutMode(): LayoutMode {
        if (node is Text) {
            return LayoutMode.INLINE
        }
        if (node.children.any { it is Element && it.tag in BLOCK_ELEMENTS }) {
            return LayoutMode.BLOCK
        }
        if (node.children.isNotEmpty() || (node as Element).tag == "input") {
            return LayoutMode.INLINE
        }
        return LayoutMode.BLOCK
    }

    override fun paint(): List<DrawCommand> =
        buildList {
            val bgcolor = if (node is Element) node.style["background-color"] ?: "transparent" else "transparent"
            if (bgcolor != "transparent") {
                val x2 = x + width
                val y2 = y + height
                add(DrawRect(y, x, y2, x2, bgcolor))
            }
            if (node is Element && (node as Element).tag == "li") {
                add(DrawRect(y + 6, x - 10, y + 14, x - 2, "lightgray"))
            }
        }

    override fun toString(): String = "Block(x=$x, y=$y, width=$width, height=$height)"

    companion object {
        protected const val HSTEP = 13
        protected const val VSTEP = 18
        private val BLOCK_ELEMENTS =
            setOf(
                "html",
                "body",
                "article",
                "section",
                "nav",
                "aside",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "hgroup",
                "header",
                "footer",
                "address",
                "p",
                "hr",
                "pre",
                "blockquote",
                "ol",
                "ul",
                "menu",
                "li",
                "dl",
                "dt",
                "dd",
                "figure",
                "figcaption",
                "main",
                "div",
                "table",
                "form",
                "fieldset",
                "legend",
                "details",
                "summary",
            )
    }
}
