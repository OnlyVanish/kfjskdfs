package ir.pinbom.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestSelectionRulesTest {
    @Test
    fun continueButtonRequiresThreeThroughTenSelections() {
        assertFalse(InterestSelectionRules.canContinue(0))
        assertFalse(InterestSelectionRules.canContinue(2))
        assertTrue(InterestSelectionRules.canContinue(3))
        assertTrue(InterestSelectionRules.canContinue(10))
        assertFalse(InterestSelectionRules.canContinue(11))
    }

    @Test
    fun togglingAddsRemovesAndNeverExceedsTen() {
        val initial = (1L..9L).toSet()
        val ten = InterestSelectionRules.toggle(initial, 10L)
        assertEquals(10, ten.size)
        assertTrue(10L in ten)
        assertEquals(ten, InterestSelectionRules.toggle(ten, 11L))
        assertEquals(9, InterestSelectionRules.toggle(ten, 1L).size)
        assertEquals(initial, InterestSelectionRules.toggle(ten, 10L))
    }

    @Test
    fun invalidIdsDoNotChangeSelection() {
        val selected = setOf(1L, 2L)
        assertEquals(selected, InterestSelectionRules.toggle(selected, 0L))
        assertEquals(selected, InterestSelectionRules.toggle(selected, -1L))
    }
}
