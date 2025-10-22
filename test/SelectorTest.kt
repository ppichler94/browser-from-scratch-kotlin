import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class SelectorTest {
    @Test
    fun `TagSelector selects tags`() {
        val element = Element("div")
        val selector = TagSelector("div")
        assertThat(selector.matches(element)).isTrue()
    }

    @Test
    fun `TagSelector does not match other tags`() {
        val element = Element("p")
        val selector = TagSelector("div")
        assertThat(selector.matches(element)).isFalse()
    }

    @Test
    fun `ClassSelector selects classes`() {
        val element = Element("div", null, mapOf("class" to "editor-note"))
        val selector = ClassSelector("editor-note")
        assertThat(selector.matches(element)).isTrue()
    }

    @Test
    fun `ClassSelector does not match other classes`() {
        val element = Element("div", null, mapOf("class" to "editor-note"))
        val selector = ClassSelector("editor-header")
        assertThat(selector.matches(element)).isFalse()
    }

    @Test
    fun `DescendantSelector matches descendants`() {
        val parent = Element("div", null, emptyMap())
        val child = Element("p", parent)
        val selector = DescendantSelector(TagSelector("div"), TagSelector("p"))
        assertThat(selector.matches(child)).isTrue()
    }

    @Test
    fun `DescendantSelector does not match other descendants`() {
        val parent = Element("div", null, emptyMap())
        val child = Element("span", parent)
        val selector = DescendantSelector(TagSelector("div"), TagSelector("p"))
        assertThat(selector.matches(child)).isFalse()
    }

    @Test
    fun `DescendantSelector does not match other parents`() {
        val parent = Element("span", null, emptyMap())
        val child = Element("p", parent)
        val selector = DescendantSelector(TagSelector("div"), TagSelector("p"))
        assertThat(selector.matches(child)).isFalse()
    }

    @Test
    fun `SequenceSelector matches sequence`() {
        val element = Element("div", null, mapOf("class" to "editor-note"))
        val selector = SequenceSelector(listOf(TagSelector("div"), ClassSelector("editor-note")))
        assertThat(selector.matches(element)).isTrue()
    }

    @Test
    fun `SequenceSelector does not match if first selector does not match`() {
        val element = Element("p", null, mapOf("class" to "editor-note"))
        val selector = SequenceSelector(listOf(TagSelector("div"), ClassSelector("editor-note")))
        assertThat(selector.matches(element)).isFalse()
    }

    @Test
    fun `SequenceSelector does not match if second selector does not match`() {
        val element = Element("div", null, mapOf("class" to "editor-header"))
        val selector = SequenceSelector(listOf(TagSelector("div"), ClassSelector("editor-note")))
        assertThat(selector.matches(element)).isFalse()
    }
}
