import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class HtmlTagParserTest {
    @Test
    fun `name() returns the tag name`() {
        val parser = HtmlTagParser("div")
        assertThat(parser.name()).isEqualTo("div")
    }

    @Test
    fun `name() throws an error if there is no name`() {
        val parser = HtmlTagParser("<>")
        assertThatThrownBy { parser.name() }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `quotedValue() returns the attribute value`() {
        val parser = HtmlTagParser("editor-note\"")
        assertThat(parser.quotedValue()).isEqualTo("editor-note")
    }

    @Test
    fun `quotedValue() handles special characters`() {
        val parser = HtmlTagParser("<=>;''\"")
        assertThat(parser.quotedValue()).isEqualTo("<=>;''")
    }

    @Test
    fun `value() returns the attribute value without quotes`() {
        val parser = HtmlTagParser("editor")
        assertThat(parser.value()).isEqualTo("editor")
    }

    @Test
    fun `value() throws an error if there is no value`() {
        val parser = HtmlTagParser(" class")
        assertThatThrownBy { parser.value() }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `attribute() returns the attribute name`() {
        val parser = HtmlTagParser("class=\"editor-note\"")
        assertThat(parser.attribute()).isEqualTo("class" to "editor-note")
    }

    @Test
    fun `body() returns the body of the tag`() {
        val parser = HtmlTagParser("class=\"editor-note\" style=\"color: red;\"")
        assertThat(parser.body()).isEqualTo(mapOf("class" to "editor-note", "style" to "color: red;"))
    }

    @Test
    fun `tag() returns the tag name and body`() {
        val parser = HtmlTagParser("div class=\"editor-note\" style=\"color: red;\"")
        assertThat(parser.tag()).isEqualTo("div" to mapOf("class" to "editor-note", "style" to "color: red;"))
    }

    @Test
    fun `tag() parses the html tag`() {
        val parser = HtmlTagParser("html lang=\"en-US\" xml:lang=\"en-US\"")
        assertThat(parser.tag()).isEqualTo("html" to mapOf("lang" to "en-US", "xml:lang" to "en-US"))
    }

    @Test
    fun `tag() handles attribute values without quotes`() {
        val parser = HtmlTagParser("link rel=\"preload\" crossorigin href=\"https://test/woff2\" as=font type=\"font/woff2\" /")
        assertThat(
            parser.tag(),
        ).isEqualTo(
            "link" to
                mapOf(
                    "rel" to "preload",
                    "crossorigin" to "true",
                    "href" to "https://test/woff2",
                    "as" to "font",
                    "type" to "font/woff2",
                ),
        )
    }

    @Test
    fun `tag() handles closing tags`() {
        val parser = HtmlTagParser("/div")
        assertThat(parser.tag()).isEqualTo("/div" to mapOf<String, String>())
    }
}
