import java.awt.*

sealed class DrawCommand {
    abstract fun paint(
        g: Graphics,
        scroll: Int,
    )
}

data class DrawText(
    val top: Int,
    val left: Int,
    val text: String,
    val font: Font,
    val color: String,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val previousColor = g.color
        g.color = Color.decode(colorCode(color))
        g.font = font
        g.drawString(text, left, top - scroll + g.fontMetrics.ascent)
        g.color = previousColor
    }
}

data class DrawRect(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val color: String,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val previousColor = g.color
        g.color = Color.decode(colorCode(color))
        g.fillRect(left, top - scroll, right - left, bottom - top)
        g.color = previousColor
    }
}

data class DrawOutline(
    val rect: Rect,
    val color: String,
    val thickness: Int,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val g = g as Graphics2D
        val previousColor = g.color
        val previousStroke = g.stroke
        g.color = Color.decode(colorCode(color))
        g.stroke = BasicStroke(thickness.toFloat())
        g.drawRect(rect.left, rect.top - scroll, rect.right - rect.left, rect.bottom - rect.top)
        g.color = previousColor
        g.stroke = previousStroke
    }
}

data class DrawLine(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val color: String,
    val thickness: Int,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val g = g as Graphics2D
        val previousColor = g.color
        val previousStroke = g.stroke
        g.color = Color.decode(colorCode(color))
        g.stroke = BasicStroke(thickness.toFloat())
        g.drawLine(x1, y1 - scroll, x2, y2 - scroll)
        g.color = previousColor
        g.stroke = previousStroke
    }
}
