package ir.pinbom.app.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hooks the profile header uses without threading three more lambdas through
 * every private composable: tapping the followers / following counters opens
 * the people list.
 */
internal class ProfileExtrasActions(
    val onOpenFollowers: () -> Unit,
    val onOpenFollowing: () -> Unit,
)

internal val LocalProfileExtras = staticCompositionLocalOf<ProfileExtrasActions?> { null }
