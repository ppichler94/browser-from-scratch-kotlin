import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val initialUrl = args.getOrNull(0) ?: "about:blank"
    val window = Frame("Browser")
    window.setSize(800, 600)
    window.layout = BorderLayout()

    // Create canvas
    val canvas = Browser()
    canvas.background = Color.WHITE

    // Create address bar panel
    val addressBar = Panel(FlowLayout(FlowLayout.LEFT))
    val urlLabel = Label("URL:")
    val urlField = TextField(50) // Set width for the text field
    urlField.addActionListener { canvas.load(urlField.text) }
    canvas.onLoad = { urlField.text = it }
    val button = Button("Go!")
    button.addActionListener { canvas.load(urlField.text) }
    addressBar.add(urlLabel)
    addressBar.add(urlField)
    addressBar.add(button)

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

    canvas.newTab(initialUrl)
}
