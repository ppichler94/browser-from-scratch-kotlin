package layout

import DrawCommand
import Element
import Node
import Text
import java.awt.Font

class AnonymousBlockBoxLayout(
    private val nodes: List<Node>,
    override val parent: Layout?,
    private val previous: Layout?,
) : BlockLayout(nodes.first(), parent, previous) {
    override fun layout(frameWidth: Int) {
        x = parent?.x ?: 0
        width = parent?.width ?: (frameWidth - x)
        y =
            if (previous != null) {
                previous.y + previous.height
            } else {
                parent?.y ?: 0
            }

        newLine(nodes.first())
        nodes.forEach { recurse(it) }

        children.forEach { it.layout(width) }
        height = children.sumOf { it.height }
    }

    private fun recurse(node: Node) {
        when (node) {
            is Text -> text(node)
            is Element -> {
                if (node.tag == "br") {
                    newLine(node)
                }
                node.children.forEach { recurse(it) }
            }
        }
    }

    private fun text(node: Text) {
        var style = 0
        if (node.style["font-weight"] == "bold") {
            style += Font.BOLD
        }
        if (node.style["font-style"] == "italic") {
            style += Font.ITALIC
        }
        val size = node.style["font-size"]?.removeSuffix("px")?.toIntOrNull() ?: 12
        val fontName = node.style["font-family"] ?: "SansSerif"
        val font = Font(fontName, style, size)
        node.content.split("\\s+".toRegex()).forEach { word(it, font, node) }
    }

    private fun word(
        word: String,
        font: Font,
        node: Text,
    ) {
        val width = font.getStringBounds(word, fontRenderContext).width.toInt()
        if (cursorX + width > this.width) {
            newLine(node)
        }
        val line = children.last()
        val previousWord = line.children.lastOrNull() as TextLayout?
        val text = TextLayout(word, node, line, previousWord)
        line.children.add(text)
        cursorX += width + font.getStringBounds(" ", fontRenderContext).width.toInt()
    }

    private fun newLine(node: Node) {
        cursorX = 0
        val lastLine = children.lastOrNull()
        val newLine = LineLayout(node, this, lastLine)
        children.add(newLine)
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "AnonymousBlockBox(nodes=$nodes)"
}
