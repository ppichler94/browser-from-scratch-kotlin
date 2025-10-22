package layout

import DrawCommand
import Node

class DocumentLayout(
    override val node: Node,
) : BlockLayout(node, null, null) {
    override fun layout(frameWidth: Int) {
        children.clear()
        val child = BlockLayout(node, this, null)
        children.add(child)

        width = frameWidth - 2 * HSTEP
        x = HSTEP
        y = VSTEP
        children.forEach { child ->
            child.layout(width)
        }
        height = children.sumOf { it.height }
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "Document(x=$x, y=$y, width=$width, height=$height)"
}
