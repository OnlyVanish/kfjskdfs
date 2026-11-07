package ir.pinbom.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.remote.ApiClient
import ir.pinbom.app.data.remote.PinPage
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReconstructedApiTest {
    private lateinit var server: ServerSocket
    private lateinit var serving: Thread
    private lateinit var context: Context
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private val seen = CopyOnWriteArrayList<Pair<String, String?>>()
    @Volatile private var reply: (String, String?) -> Pair<Int, String> = { _, _ -> 200 to "{}" }

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pinbom_session", Context.MODE_PRIVATE).edit().clear().commit()
        server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        serving = thread(isDaemon = true, name = "test-http-server") {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().buffered()
                        val headerBytes = java.io.ByteArrayOutputStream()
                        var tail = ""
                        while (!tail.endsWith("\r\n\r\n")) {
                            val byte = input.read()
                            if (byte < 0) break
                            headerBytes.write(byte)
                            tail = (tail + byte.toChar()).takeLast(4)
                        }
                        val lines = headerBytes.toString("UTF-8").split("\r\n")
                        val path = lines.first().split(' ')[1]
                        val headers = lines.drop(1).filter { ':' in it }.associate {
                            it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                        }
                        repeat(headers["content-length"]?.toIntOrNull() ?: 0) { input.read() }
                        val auth = headers["authorization"]
                        seen += path to auth
                        val (status, text) = reply(path, auth)
                        val bytes = text.toByteArray()
                        socket.getOutputStream().use { output ->
                            output.write(("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            output.write(bytes)
                            output.flush()
                        }
                    }
                } catch (e: java.net.SocketException) { if (!server.isClosed) throw e }
            }
        }
        session = SessionManager(context)
        session.setServer("http://127.0.0.1:${server.localPort}")
        api = ApiClient(session)
    }
    @After fun tearDown() { server.close(); serving.join(2000) }

    @Test fun requestUsesChosenHostPathQueriesAndBearer() = runBlocking {
        session.setTokens("token", "refresh", "ali")
        reply = { _, _ -> 200 to """{"items":[{"id":5}],"next_cursor":"next"}""" }
        val page = api.get<PinPage>("feed/home", mapOf("cursor" to "a:b", "limit" to "20"))
        assertEquals(5L, page.items.single().id)
        assertEquals("Bearer token", seen.single().second)
        assertTrue(seen.single().first.startsWith("/v1/feed/home?"))
        assertTrue(seen.single().first.contains("cursor=a%3Ab"))
    }
    @Test fun anonymousAuthenticationDoesNotSendOldBearer() = runBlocking {
        session.setTokens("old", "refresh", "ali")
        api.request("POST", "auth/login", authenticated = false)
        assertNull(seen.single().second)
    }
    @Test fun expiredTokenRefreshesOnceAndRetriesOriginalRequest() = runBlocking {
        session.setTokens("old", "refresh", "ali")
        reply = { path, auth ->
            when {
                path == "/v1/auth/refresh" -> 200 to """{"access_token":"new","refresh_token":"new-refresh","user":{"username":"ali"}}"""
                auth == "Bearer old" -> 401 to """{"error":{"code":"unauthorized"}}"""
                else -> 200 to """{"items":[]}"""
            }
        }
        api.get<PinPage>("feed/home")
        assertEquals(3, seen.size)
        assertEquals("/v1/auth/refresh", seen[1].first)
        assertNull(seen[1].second)
        assertEquals("Bearer new", seen[2].second)
        assertEquals("new-refresh", session.refreshToken)
    }
    @Test fun unauthorizedWithoutRefreshInvalidatesSession() = runBlocking {
        session.setTokens("old", "", "ali")
        reply = { _, _ -> 401 to """{"error":{"code":"unauthorized"}}""" }
        try { api.request("GET", "users/me"); fail("Expected HTTP error") } catch (e: HttpException) { assertEquals(401, e.code()) }
        assertNull(session.accessToken)
        assertEquals(1L, session.authInvalidations.value)
    }
    @Test fun forbiddenMutationDoesNotLogOutAnOtherwiseValidUser() = runBlocking {
        session.setTokens("valid", "refresh", "ali")
        reply = { _, _ -> 403 to """{"error":{"code":"forbidden","message":"Not your pin"}}""" }
        try { api.request("DELETE", "pins/1"); fail("Expected HTTP error") } catch (e: HttpException) { assertEquals(403, e.code()) }
        assertEquals("valid", session.accessToken)
    }
    @Test fun sessionPersistsAndChangingOriginClearsCredentials() = runBlocking {
        session.setTokens("valid", "refresh", "ali")
        val restored = SessionManager(context)
        restored.load()
        assertEquals("ali", restored.username)
        assertEquals("valid", restored.accessToken)
        restored.setServer("https://other.example")
        assertNull(restored.accessToken)
        assertNull(restored.refreshToken)
        assertEquals("", restored.username)
    }
}
