package ir.pinbom.app

import ir.pinbom.app.data.remote.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReconstructedModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    @Test fun feedReadsEveryFieldUsedByCards() {
        val page = json.decodeFromString<PinPage>("""{"items":[{"id":12,"title":"خانه","image_url":"/media/p.jpg","width":800,"height":1200,"tags":["home"],"liked":true,"likes":3,"author":{"username":"sara","display_name":"Sara"},"unknown":1}],"next_cursor":"c2"}""")
        assertEquals(12L, page.items.single().id)
        assertEquals("sara", page.items.single().author?.username)
        assertEquals(listOf("home"), page.items.single().tags)
        assertTrue(page.items.single().liked)
        assertEquals("c2", page.next_cursor)
    }
    @Test fun nullableServerValuesUseDefaults() {
        val pin = json.decodeFromString<Pin>("""{"id":1,"tags":null,"image_url":null,"author":null}""")
        assertEquals("", pin.image_url)
        assertTrue(pin.tags.isEmpty())
        assertNull(pin.author)
    }
    @Test fun privateBoardsReadBothWireSpellings() {
        assertTrue(json.decodeFromString<Board>("""{"private":true}""").private_board)
        assertTrue(json.decodeFromString<Board>("""{"private_board":true}""").private_board)
        assertTrue(json.decodeFromString<SearchBoard>("""{"private":true}""").private_board)
    }
    @Test fun notificationsReadUnreadCountAndActor() {
        val page = json.decodeFromString<NotificationPage>("""{"unread":4,"items":[{"id":2,"pin_id":12,"type":"like","actor":{"username":"ali"},"pin_title":"P"}]}""")
        assertEquals(4, page.unread)
        assertEquals("ali", page.items.single().actor?.username)
        assertFalse(page.items.single().read)
    }
    @Test fun sessionsAndInterestsMatchSettings() {
        assertTrue(json.decodeFromString<SessionPage>("""{"items":[{"id":9,"current":true,"device_name":"Android","last_seen_at":"2026-10-31T00:00:00Z"}]}""").items.single().current)
        val page = json.decodeFromString<InterestPage>("""{"items":[{"id":1,"name_fa":"هنر","name_en":"Art"}]}""")
        assertEquals("هنر", page.items.single().name_fa)
    }
}
