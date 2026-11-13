package io.github.bianim.kataghost

import io.github.bianim.kataghost.engine.*
import org.junit.Assert.assertEquals
import org.junit.Test

class OpponentSearchTest {
    @Test fun expertUsesExplicitBestMove() {
        val expert = checkNotNull(OpponentPreset.EXPERT.settings)
        assertEquals(500, expert.visits)
        assertEquals(9, expert.selectMove(result(1, best = 9), { true }))
    }

    @Test fun filtersByPlayingColorAndFallsBackToEarlierRank() {
        val difficulty = OpponentSearch(32, 5, 10, 5)
        assertEquals(1, difficulty.selectMove(result(1), { true }))
        assertEquals(2, difficulty.selectMove(result(2), { true }))
        assertEquals(1, difficulty.selectMove(result(2), { it != 2 }))
    }

    @Test fun retainsBestPass() {
        val difficulty = checkNotNull(OpponentPreset.BEGINNER.settings)
        assertEquals(PASS, difficulty.selectMove(result(1, best = PASS), { false }))
    }

    private fun result(player: Int, best: Int = 1) = PositionAnalysis(
        AnalysisPosition(emptyList(), initialPlayer = player), 32, 55f, 0f, 0f,
        listOf(
            AnalysisCandidate("A", 1, 20, 55f, 0f, listOf(1)),
            AnalysisCandidate("B", 2, 12, 40f, 0f, listOf(2)),
        ), "test", bestMove = best,
    )
}
