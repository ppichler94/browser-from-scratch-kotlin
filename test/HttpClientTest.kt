import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpResponse.response
import kotlin.test.Test

@ExtendWith(MockServerExtension::class)
@MockServerSettings(ports = [80])
class HttpClientTest(
    private val mockServer: ClientAndServer,
) {
    @BeforeEach
    fun setUp() {
        mockServer.reset()
    }

    @Test
    fun `test http get`() {
        mockGet("/test", "Hello, MockServer!")

        val response = HttpClient().get("http://localhost/test")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("Hello, MockServer!")
    }

    @Test
    fun `returns 404 for unknown urls`() {
        val response = HttpClient().get("http://localhost/unknown")

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `follows relative redirects`() {
        mockRedirect("/redirect", "/redirected")
        mockGet("/redirected", "Redirected!")

        val response = HttpClient().get("http://localhost/redirect")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("Redirected!")
    }

    @Test
    fun `follows absolute redirects`() {
        mockRedirect("/redirect", "http://localhost/redirected")
        mockGet("/redirected", "Redirected!")

        val response = HttpClient().get("http://localhost/redirect")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("Redirected!")
    }

    @Test
    fun `aborts on too many redirects`() {
        mockRedirect("/redirect", "/redirected")
        mockRedirect("/redirected", "/redirect")

        val response = HttpClient().get("http://localhost/redirect")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.body).isEqualTo("Too many redirects.")
    }

    @Test
    fun `returns 400 if location header is missing`() {
        mockServer
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/redirect"),
            ).respond { response().withStatusCode(302) }

        val response = HttpClient().get("http://localhost/redirect")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.body).isEqualTo("Missing location header.")
    }

    @Test
    fun `caches responses`() {
        val client = HttpClient()
        mockGet("/test", "Hello, MockServer!") {
            withHeader("Cache-Control", "max-age=31536000")
        }
        val response = client.get("http://localhost/test")
        mockServer.reset()

        mockGet("/test", "Not hello, MockServer!")
        val cachedResponse = client.get("http://localhost/test")

        assertThat(response.body).isEqualTo("Hello, MockServer!")
        assertThat(cachedResponse.body).isEqualTo("Hello, MockServer!")
    }

    @Test
    fun `does not cache different requests`() {
        val client = HttpClient()
        mockGet("/test", "Hello, MockServer!") {
            withHeader("Cache-Control", "max-age=31536000")
        }
        mockGet("/test2", "Hello, MockServer!") {
            withHeader("Cache-Control", "max-age=31536000")
        }
        val response1 = client.get("http://localhost/test")
        val response2 = client.get("http://localhost/test2")

        assertThat(response1.body).isEqualTo("Hello, MockServer!")
        assertThat(response2.body).isEqualTo("Hello, MockServer!")
    }

    @Test
    fun `does not cache if no-cache is set`() {
        val client = HttpClient()
        mockGet("/test", "Hello, MockServer!") {
            withHeader("Cache-Control", "max-age=31536000,no-cache")
        }
        val response1 = client.get("http://localhost/test")
        mockServer.reset()
        mockGet("/test", "Hello again, MockServer!") {
            withHeader("Cache-Control", "max-age=31536000,no-cache")
        }
        val response2 = client.get("http://localhost/test")

        assertThat(response1.body).isEqualTo("Hello, MockServer!")
        assertThat(response2.body).isEqualTo("Hello again, MockServer!")
    }

    private fun mockGet(
        path: String,
        response: String,
        customizer: HttpResponse.() -> Unit = {},
    ) {
        val serverResponse = response()
        serverResponse.customizer()
        mockServer
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath(path),
            ).respond {
                serverResponse
                    .withStatusCode(200)
                    .withBody(response)
            }
    }

    private fun mockRedirect(
        from: String,
        to: String,
    ) {
        mockServer
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath(from),
            ).respond { response().withStatusCode(302).withHeader("Location", to) }
    }
}
