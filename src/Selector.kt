interface Selector {
    fun matches(node: Node): Boolean = false

    val specificity: Int
}

data class TagSelector(
    val tag: String,
) : Selector {
    override fun matches(node: Node): Boolean = node is Element && node.tag == tag

    override val specificity: Int = 1
}

data class DescendantSelector(
    val ancestor: Selector,
    val descendant: Selector,
) : Selector {
    override val specificity: Int = ancestor.specificity + descendant.specificity

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

data class ClassSelector(
    val className: String,
) : Selector {
    override fun matches(node: Node): Boolean = node is Element && node.attributes["class"]?.split(" ")?.contains(className) == true

    override val specificity: Int = 2
}

data class SequenceSelector(
    val selectors: List<Selector>,
) : Selector {
    override fun matches(node: Node): Boolean = selectors.all { it.matches(node) }

    override val specificity: Int = selectors.sumOf { it.specificity }
}
