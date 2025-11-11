package layout

import DrawCommand
import Node
import java.awt.Font

abstract class Layout {
    val children: MutableList<Layout> = mutableListOf()
    abstract val parent: Layout?
    abstract val node: Node

    abstract fun layout(frameWidth: Int)

    abstract fun paint(): List<DrawCommand>

    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
}

enum class LayoutMode {
    INLINE,
    BLOCK,
}

fun paintTree(
    layoutObject: Layout,
    displayList: MutableList<DrawCommand>,
) {
    displayList.addAll(layoutObject.paint())
    layoutObject.children.forEach { paintTree(it, displayList) }
}

fun printTree(
    layoutObject: Layout,
    indent: String = "",
) {
    println("$indent$layoutObject")
    layoutObject.children.forEach { printTree(it, "$indent  ") }
}

fun Layout.treeToList(): List<Layout> {
    val result = mutableListOf<Layout>()
    result.add(this)
    children.forEach { result.addAll(it.treeToList()) }
    return result
}

fun getFont(style: Map<String, String>): Font {
    var s = 0
    if (style["font-weight"] == "bold") {
        s += Font.BOLD
    }
    if (style["font-style"] == "italic") {
        s += Font.ITALIC
    }
    val size = style["font-size"]?.removeSuffix("px")?.toIntOrNull() ?: 12
    val fontName = style["font-family"] ?: "SansSerif"
    return Font(fontName, s, size)
}
