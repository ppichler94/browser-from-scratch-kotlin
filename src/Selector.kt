interface Selector {
    fun matches(node: Element): Boolean = false
}

class TagSelector(
    val tag: String,
) : Selector {
    override fun matches(node: Element): Boolean = node.tag == tag
}

class DescendantSelector(
    val ancestor: Selector,
    val descendant: Selector,
) : Selector {
    override fun matches(node: Element): Boolean {
        if (!descendant.matches(node)) {
            return false
        }
        while (node.parent != null) {
            if (ancestor.matches(node)) {
                return true
            }
            node = node.parent!!
        }
    }
}
