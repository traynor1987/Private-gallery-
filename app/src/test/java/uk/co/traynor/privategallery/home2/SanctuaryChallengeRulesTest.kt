package uk.co.traynor.privategallery.home2

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.arcade.SudokuGame
import uk.co.traynor.privategallery.core.arcade.SudokuSolver

class SanctuaryChallengeRulesTest {
    @Test fun combinedChallengesRequireEveryEnabledStep() {
        val rules = SanctuaryChallenges(SanctuarySudoku.MEDIUM, pin = true, hold = true)
        assertFalse(rules.passed(false, true, true))
        assertFalse(rules.passed(true, false, true))
        assertFalse(rules.passed(true, true, false))
        assertTrue(rules.passed(true, true, true))
        assertTrue(SanctuaryChallenges().passed(false, false, false))
    }

    @Test fun everyOfferedDifficultyHasAUniqueSolution() {
        for (difficulty in 0..2) {
            val puzzle = SudokuGame.create(difficulty, 42)
            assertEquals(1, SudokuSolver.count(puzzle.givens(), 2))
            assertFalse(sanctuarySudokuSolved(puzzle, "0".repeat(81)))
            assertTrue(sanctuarySudokuSolved(puzzle, puzzle.solution().joinToString("")))
        }
    }
}
