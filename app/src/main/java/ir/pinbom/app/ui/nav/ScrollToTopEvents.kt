package ir.pinbom.app.ui.nav

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Tap the tab you are already on → go back to the top", the way every feed app
 * behaves. The shell emits the tab's route; the screen that owns the list (and
 * its scroll state) decides what to do with it. No replay and a one-slot buffer,
 * so a stale tap can never scroll a screen that appears later.
 */
object ScrollToTopEvents {
    private val flow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<String> = flow.asSharedFlow()

    fun emit(route: String) {
        flow.tryEmit(route)
    }
}
