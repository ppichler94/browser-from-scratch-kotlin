import java.awt.Color
import java.awt.Font
import java.awt.Graphics

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
    val color: Color,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val previousColor = g.color
        g.font = font
        g.color = color
        g.drawString(text, left, top - scroll + g.fontMetrics.ascent)
        g.color = previousColor
    }
}

data class DrawRect(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val color: Color,
) : DrawCommand() {
    override fun paint(
        g: Graphics,
        scroll: Int,
    ) {
        val previousColor = g.color
        g.color = color
        g.fillRect(left, top - scroll, right - left, bottom - top)
        g.color = previousColor
    }
}
