import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.model.HttpRequest.request
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

    private fun mockGet(
        path: String,
        response: String,
    ) {
        mockServer
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath(path),
            ).respond {
                response()
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
