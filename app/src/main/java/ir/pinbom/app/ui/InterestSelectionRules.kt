package ir.pinbom.app.ui

/** Pure selection rules shared by the onboarding ViewModel and its unit tests. */
object InterestSelectionRules {
    const val MIN_SELECTION = 3
    const val MAX_SELECTION = 10

    fun canContinue(count: Int): Boolean = count in MIN_SELECTION..MAX_SELECTION

    fun toggle(selected: Set<Long>, id: Long): Set<Long> = when {
        id <= 0L -> selected
        id in selected -> selected - id
        selected.size < MAX_SELECTION -> selected + id
        else -> selected
    }
}
