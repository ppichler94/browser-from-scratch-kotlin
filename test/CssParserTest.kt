import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class CssParserTest {
    @Test
    fun `word() returns the next word`() {
        val parser = CssParser("hello world")
        assertThat(parser.word()).isEqualTo("hello")
    }

    @Test
    fun `word() throws if no word is found`() {
        val parser = CssParser(" word")
        assertThatThrownBy { parser.word() }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `whitespace() skips whitespace`() {
        val parser = CssParser("hello \tworld")
        assertThat(parser.word()).isEqualTo("hello")
        parser.whitespace()
        assertThat(parser.word()).isEqualTo("world")
    }

    @Test
    fun `literal() parses a literal`() {
        val parser = CssParser(":value")
        parser.literal(':')
        assertThat(parser.word()).isEqualTo("value")
    }

    @Test
    fun `literal() throws if literal not found`() {
        val parser = CssParser("value")
        assertThatThrownBy { parser.literal(':') }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `pair() parses a property-value pair`() {
        val parser = CssParser("width: 100px;")
        val (prop, value) = parser.pair()
        assertThat(prop).isEqualTo("width")
        assertThat(value).isEqualTo("100px")
    }

    @Test
    fun `body() parses a rule body`() {
        val parser = CssParser("width: 100px; color: red;")
        val body = parser.body()
        assertThat(body).isEqualTo(mapOf("width" to "100px", "color" to "red"))
    }

    @Test
    fun `body() skips invalid pairs`() {
        val parser = CssParser("width: 100px; color:!; height: 100px;")
        val body = parser.body()
        assertThat(body).isEqualTo(mapOf("width" to "100px", "height" to "100px"))
    }
}
