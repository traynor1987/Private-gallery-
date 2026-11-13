package uk.co.traynor.privategallery.home2

import uk.co.traynor.privategallery.core.arcade.SudokuGame

internal enum class SanctuarySudoku(val label: String) { OFF("Off"), EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }
internal data class SanctuaryChallenges(
    val sudoku: SanctuarySudoku = SanctuarySudoku.OFF,
    val pin: Boolean = false,
    val hold: Boolean = false,
    val extraPhrase: String = "",
) {
    fun passed(solved: Boolean, verifiedPin: Boolean, held: Boolean, phrase: String = "") =
        (sudoku == SanctuarySudoku.OFF || solved) && (!pin || verifiedPin) && (!hold || held) && (extraPhrase.isEmpty() || phrase == extraPhrase)
}

internal fun sanctuarySudokuSolved(puzzle: SudokuGame, board: String): Boolean {
    if (board.length != 81 || board.any { it !in '0'..'9' }) return false
    var game = puzzle
    for (index in board.indices) if (puzzle.given(index) == 0) game = game.enter(index, board[index].digitToInt(), false)
    return game.complete()
}

internal fun sanctuarySudokuDraft(value: String?): String =
    value?.takeIf { it.length == 81 && it.all { cell -> cell in '0'..'9' } } ?: "0".repeat(81)
