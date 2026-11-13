package io.github.bianim.kataghost.engine

import kotlin.random.Random

enum class OpponentPreset(val settings: OpponentSearch?) {
    ANALYSIS(null),
    BEGINNER(OpponentSearch(32, 5, 10, 30)),
    EASY(OpponentSearch(64, 3, 7, 20)),
    NORMAL(OpponentSearch(128, 2, 4, 10)),
    HARD(OpponentSearch(256, 1, 2, 4)),
    EXPERT(OpponentSearch(500, 1, 1, 0)),
}

data class OpponentSearch(val visits: Int, val firstRank: Int, val lastRank: Int, val maxLoss: Int) {
    init {
        require(visits > 0)
        require(firstRank in 1..lastRank && lastRank <= 10)
        require(maxLoss in 0..30)
    }

    fun selectMove(result: PositionAnalysis, isLegal: (Int) -> Boolean, random: Random = Random.Default): Int? {
        val best = result.bestMove ?: return null
        if (best == PASS) return best
        if (lastRank == 1) return best.takeIf(isLegal)
        val player = result.position.nextPlayer
        val reference = result.candidates.firstOrNull { it.move == best } ?: return best.takeIf(isLegal)
        fun eligible(candidate: AnalysisCandidate) =
            candidate.move in 0 until PASS && isLegal(candidate.move) &&
                reference.winRateFor(player) - candidate.winRateFor(player) <= maxLoss

        val ranked = result.candidates.take(lastRank)
        val alternatives = ranked.drop(firstRank - 1).filter(::eligible)
        return alternatives.randomOrNull(random)?.move
            ?: ranked.take(firstRank - 1).lastOrNull(::eligible)?.move
            ?: best.takeIf(isLegal)
    }
}
