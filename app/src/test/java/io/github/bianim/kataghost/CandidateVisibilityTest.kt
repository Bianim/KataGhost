package io.github.bianim.kataghost

import io.github.bianim.kataghost.ui.AppPreferences
import org.junit.Assert.*
import org.junit.Test

class CandidateVisibilityTest {
    @Test fun candidateVisibilityIsIndependentOfAutomaticPlayers() {
        for (black in listOf(false, true)) for (white in listOf(false, true)) {
            val settings = AppPreferences(autoBlack = black, autoWhite = white)
            assertTrue(settings.showCandidateMoves)
            assertTrue(settings.engineEnabled)
            assertFalse(settings.copy(candidates = false).showCandidateMoves)
            assertTrue(settings.copy(engineEnabled = false).showCandidateMoves)
        }
    }
}
