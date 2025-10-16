interface Selector {
    fun matches(node: Node): Boolean = false
}

data class TagSelector(
    val tag: String,
) : Selector {
    override fun matches(node: Node): Boolean = node is Element && node.tag == tag
}

data class DescendantSelector(
    val ancestor: Selector,
    val descendant: Selector,
) : Selector {
    override fun matches(node: Node): Boolean {
        if (!descendant.matches(node)) {
            return false
        }
        var current = node
        while (current is Element && current.parent != null) {
            if (ancestor.matches(current.parent)) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
