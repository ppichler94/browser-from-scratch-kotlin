import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlTest {
    @Test
    fun `parses HTTP URL correctly`() {
        val url = Url("http://example.com/path")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("example.com")
        assertThat(url.port).isEqualTo(80)
        assertThat(url.path).isEqualTo("/path")
        assertThat(url.origin).isEqualTo("http://example.com")
        assertThat(url.toString()).isEqualTo("http://example.com/path")
    }

    @Test
    fun `parses HTTPS URL correctly`() {
        val url = Url("https://example.com/path")

        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("example.com")
        assertThat(url.port).isEqualTo(443)
        assertThat(url.path).isEqualTo("/path")
        assertThat(url.origin).isEqualTo("https://example.com")
        assertThat(url.toString()).isEqualTo("https://example.com/path")
    }

    @Test
    fun `parses HTTP URL with custom port`() {
        val url = Url("http://example.com:8080/path")

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("example.com")
        assertThat(url.port).isEqualTo(8080)
        assertThat(url.path).isEqualTo("/path")
        assertThat(url.origin).isEqualTo("http://example.com:8080")
        assertThat(url.toString()).isEqualTo("http://example.com:8080/path")
    }

    @Test
    fun `parses HTTPS URL with custom port`() {
        val url = Url("https://example.com:8443/path")

        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("example.com")
        assertThat(url.port).isEqualTo(8443)
        assertThat(url.path).isEqualTo("/path")
        assertThat(url.origin).isEqualTo("https://example.com:8443")
        assertThat(url.toString()).isEqualTo("https://example.com:8443/path")
    }

    @Test
    fun `parses file URL correctly`() {
        val url = Url("file:///path/to/file")

        assertThat(url.scheme).isEqualTo("file")
        assertThat(url.host).isEqualTo("")
        assertThat(url.port).isEqualTo(0)
        assertThat(url.path).isEqualTo("/path/to/file")
        assertThat(url.origin).isEqualTo("file://")
        assertThat(url.toString()).isEqualTo("file:///path/to/file")
    }

    @Test
    fun `parses data URL correctly`() {
        val url = Url("data:text/plain;base64,SGVsbG8=")

        assertThat(url.scheme).isEqualTo("data")
        assertThat(url.dataContent).isEqualTo("text/plain;base64,SGVsbG8=")
        assertThat(url.origin).isEqualTo("data://")
        assertThat(url.toString()).isEqualTo("data://")
    }

    @Test
    fun `handles unknown scheme`() {
        val url = Url("about:blank")
        assertThat(url.toString()).isEqualTo("about://blank")
    }

    @Test
    fun `resolves absolute URL`() {
        val baseUrl = Url("http://example.com/base/")
        val resolved = baseUrl.resolve("https://other.com/path")

        assertThat(resolved.scheme).isEqualTo("https")
        assertThat(resolved.host).isEqualTo("other.com")
        assertThat(resolved.port).isEqualTo(443)
        assertThat(resolved.path).isEqualTo("/path")
    }

    @Test
    fun `resolves relative URL starting with slash`() {
        val baseUrl = Url("http://example.com/base/page")
        val resolved = baseUrl.resolve("/newpath")

        assertThat(resolved.scheme).isEqualTo("http")
        assertThat(resolved.host).isEqualTo("example.com")
        assertThat(resolved.port).isEqualTo(80)
        assertThat(resolved.path).isEqualTo("/newpath")
    }

    @Test
    fun `resolves relative URL without slash`() {
        val baseUrl = Url("http://example.com/base/page")
        val resolved = baseUrl.resolve("newpath")

        assertThat(resolved.scheme).isEqualTo("http")
        assertThat(resolved.host).isEqualTo("example.com")
        assertThat(resolved.port).isEqualTo(80)
        assertThat(resolved.path).isEqualTo("/base/newpath")
    }

    @Test
    fun `resolves relative URL with double slash`() {
        val baseUrl = Url("http://example.com/base/page")
        val resolved = baseUrl.resolve("//other.com/path")

        assertThat(resolved.scheme).isEqualTo("http")
        assertThat(resolved.host).isEqualTo("other.com")
        assertThat(resolved.port).isEqualTo(80)
        assertThat(resolved.path).isEqualTo("/path")
    }

    @Test
    fun `resolves relative URL with parent directory`() {
        val baseUrl = Url("http://example.com/dir1/dir2/page")
        val resolved = baseUrl.resolve("../newpath")

        assertThat(resolved.scheme).isEqualTo("http")
        assertThat(resolved.host).isEqualTo("example.com")
        assertThat(resolved.port).isEqualTo(80)
        assertThat(resolved.path).isEqualTo("/dir1/newpath")
    }
}
