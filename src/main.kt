import java.awt.BorderLayout
import java.awt.Color
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val initialUrl = args.getOrNull(0) ?: "about:blank"
    val window = Frame("Browser")
    window.setSize(800, 600)
    window.layout = BorderLayout()

    // Create canvas
    val canvas = Browser(window)
    canvas.background = Color.WHITE
    window.add(canvas, BorderLayout.CENTER)

    window.addWindowListener(
        object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                exitProcess(0)
            }
        },
    )

    window.isVisible = true

    canvas.newTab(initialUrl)
}
