import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

fun main() {
    val window = Frame("Browser")
    window.setSize(800, 600)
    window.layout = BorderLayout()

    // Create address bar panel
    val addressBar = Panel(FlowLayout(FlowLayout.LEFT))
    val urlLabel = Label("URL:")
    val urlField = TextField(50) // Set width for the text field
    urlField.addActionListener { load(urlField.text) }
    val button = Button("Go!")
    button.addActionListener { load(urlField.text) }
    addressBar.add(urlLabel)
    addressBar.add(urlField)
    addressBar.add(button)

    // Create canvas
    val canvas = Canvas()
    canvas.background = Color.WHITE
    canvas.addComponentListener(
        object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                println("Canvas resized: ${e.component.width}x${e.component.height}")
            }
        },
    )

    // Add components to window using BorderLayout
    window.add(addressBar, BorderLayout.NORTH)
    window.add(canvas, BorderLayout.CENTER)

    window.addWindowListener(
        object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                exitProcess(0)
            }
        },
    )

    window.isVisible = true
}

fun load(url: String) {
    println("Loading: $url")
    val response = HttpClient(url).get()
    println(response)
}
