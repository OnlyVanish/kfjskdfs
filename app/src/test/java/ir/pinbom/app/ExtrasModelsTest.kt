package ir.pinbom.app

import ir.pinbom.app.data.extras.ErrorEnvelope
import ir.pinbom.app.data.extras.PersonPage
import ir.pinbom.app.data.extras.ServerNotificationPrefs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DTOs must read exactly what the Go backend writes (and survive extra / missing fields). */
class ExtrasModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun followersPageDecodes() {
        val page = json.decodeFromString(
            PersonPage.serializer(),
            """{"items":[{"id":7,"username":"sara","display_name":"Sara","bio":"","avatar_url":"/media/a.jpg","extra":1}],"next_cursor":"c2"}""",
        )
        assertEquals(1, page.items.size)
        assertEquals("sara", page.items[0].username)
        assertEquals("c2", page.next_cursor)
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val prefs = json.decodeFromString(ServerNotificationPrefs.serializer(), """{"push_enabled":false}""")
        assertTrue(!prefs.push_enabled)
        assertTrue(prefs.likes_enabled && prefs.mentions_enabled)
    }

    @Test
    fun errorEnvelopeCarriesTheServerSentence() {
        val e = json.decodeFromString(
            ErrorEnvelope.serializer(),
            """{"error":{"code":"forbidden","message":"You can only delete your own comments."},"detail":"x"}""",
        )
        assertEquals("forbidden", e.error.code)
        assertEquals("You can only delete your own comments.", e.error.message)
    }
}
