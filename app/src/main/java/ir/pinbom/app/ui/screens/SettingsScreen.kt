package ir.pinbom.app.ui.screens

import androidx.compose.ui.semantics.role
import android.Manifest
import ir.pinbom.app.ui.components.icons.PinBomIcons
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.FeaturePreferences
import ir.pinbom.app.ui.SettingsViewModel
import ir.pinbom.app.ui.CacheSettingsViewModel
import ir.pinbom.app.ui.ProfileViewModel
import ir.pinbom.app.ui.components.ActionToastHost
import ir.pinbom.app.ui.components.Avatar
import ir.pinbom.app.ui.components.PinBomBottomSheet
import ir.pinbom.app.ui.components.PinBomDialog
import ir.pinbom.app.ui.components.PinBomDialogContent
import ir.pinbom.app.ui.components.ContentTextField
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.SectionHeader
import ir.pinbom.app.ui.components.SheetHeader
import ir.pinbom.app.ui.components.rememberPinBomToastState
import ir.pinbom.app.ui.components.PinImagePreferences
import ir.pinbom.app.ui.components.PinBomSwitch
import ir.pinbom.app.ui.theme.AppLanguage
import ir.pinbom.app.ui.theme.BrandRed
import ir.pinbom.app.ui.theme.AccentPalette
import ir.pinbom.app.ui.theme.AbsoluteWhite
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.LocalAppLanguage
import ir.pinbom.app.ui.theme.LocalAppLanguageState
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.pinbomTextStyles
import ir.pinbom.app.ui.theme.Localizer
import ir.pinbom.app.ui.nav.Routes
import kotlinx.coroutines.launch

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · settings
 *  Profile fields, avatar upload, theme and language preferences, and the two
 *  ways out of the account — the destructive one behind a
 *  dialog. Changing the language re-renders the whole screen (strings, direction,
 *  digits, font) and keeps everything else exactly where it was.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun SettingsScreen(
    nav: NavHostController,
    session: SessionManager,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    accentColor: Color = BrandRed,
    onAccentChange: (Color) -> Unit = {},
    specialEffectsEnabled: Boolean = true,
    onSpecialEffectsChange: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val colors = PinBomTheme.colors
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val lastAction by vm.lastAction.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val mediaVm: ProfileViewModel = hiltViewModel()
    val mediaBusy by mediaVm.mediaBusy.collectAsStateWithLifecycle()
    val mediaAction by mediaVm.mediaAction.collectAsStateWithLifecycle()
    val cacheVm: CacheSettingsViewModel = hiltViewModel()
    val cacheState by cacheVm.state.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { FeaturePreferences(context) }
    val scope = rememberCoroutineScope()
    var dataSaver by remember { mutableStateOf(false) }
    var wifiOnlyUploads by remember { mutableStateOf(false) }
    var notifyLikes by remember { mutableStateOf(true) }
    var notifyComments by remember { mutableStateOf(true) }
    var notifyFollows by remember { mutableStateOf(true) }
    var confirmClearCache by remember { mutableStateOf(false) }

    val toast = rememberPinBomToastState()
    val languageState = LocalAppLanguageState.current
    val language = LocalAppLanguage.current

    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var languageSheet by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && session.username.isNotBlank()) {
            mediaVm.uploadProfileMedia(uri, "avatar", session.username, refreshProfile = false)
        }
    }

    LaunchedEffect(Unit) {
        dataSaver = prefs.dataSaver()
        PinImagePreferences.dataSaverEnabled = dataSaver
        wifiOnlyUploads = prefs.wifiOnlyUploads()
        val enabled = prefs.notificationTypes()
        notifyLikes = "like" in enabled
        notifyComments = "comment" in enabled
        notifyFollows = "follow" in enabled
    }
    LaunchedEffect(session.username) { vm.loadProfile(session.username) }
    LaunchedEffect(profile?.username) {
        profile?.let {
            name = it.display_name
            bio = it.bio
        }
    }
    val avatarUploadFailed = stringResource(R.string.settings_avatar_upload_failed)
    LaunchedEffect(mediaAction) {
        when {
            mediaAction.startsWith("saved:") -> vm.loadProfile(session.username)
            mediaAction.startsWith("failed:") -> toast.show(avatarUploadFailed)
        }
    }

    /* Success and failure that carry no server text are worded here, so they
     * arrive in the language the app is in right now. */
    val savedLabel = stringResource(R.string.settings_saved)
    val errorLabel = stringResource(R.string.state_error_title)
    LaunchedEffect(lastAction) {
        when (lastAction.substringBefore(':')) {
            "saved" -> toast.show(savedLabel)
            "failed" -> toast.show(message.ifBlank { errorLabel })
        }
    }

    /* Toast after a language switch: read in the *new* language, which is why it
     * fires from the language change instead of from the tap. */
    val languageChangedLabel = stringResource(R.string.settings_language_changed)
    val initialLanguage = remember { language }
    LaunchedEffect(language) {
        if (language != initialLanguage) toast.show(languageChangedLabel)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.topBar)
                    .padding(horizontal = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircleButton(
                    icon = PinBomIcons.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = { nav.popBackStack() },
                    containerColor = colors.surfaceGrey,
                )
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = PinBomText.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                item(key = "profile-header") {
                    SectionHeader(title = stringResource(R.string.settings_profile_section))
                }
                item(key = "profile-fields") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                name = profile?.display_name?.ifBlank { profile?.username.orEmpty() } ?: session.username,
                                url = profile?.avatar_url,
                                baseUrl = session.baseUrl,
                                size = 68.dp,
                            )
                            Spacer(Modifier.width(Dimens.md))
                            SecondaryPillButton(
                                text = stringResource(R.string.settings_upload_avatar),
                                onClick = { avatarPicker.launch("image/*") },
                                leadingIcon = PinBomIcons.PhotoCamera,
                                enabled = !mediaBusy,
                                loading = mediaBusy,
                                size = ir.pinbom.app.ui.components.PillButtonSize.Small,
                            )
                        }
                        ContentTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = stringResource(R.string.settings_display_name),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Dimens.md))
                        ContentTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            placeholder = stringResource(R.string.settings_bio),
                            singleLine = false,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Dimens.md))
                        PrimaryPillButton(
                            text = stringResource(R.string.settings_save_profile),
                            onClick = { vm.save(name.trim(), bio.trim()) },
                            enabled = !busy && (name.isNotBlank() || bio.isNotBlank()),
                            loading = busy,
                            fillWidth = true,
                        )
                    }
                }
                item(key = "appearance-header") {
                    SectionHeader(title = stringResource(R.string.settings_appearance))
                }
                item(key = "appearance-row") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        Text(
                            text = stringResource(R.string.settings_button_color),
                            style = PinBomText.body,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = Dimens.sm, bottom = Dimens.xs),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccentPalette.forEach { choice ->
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(choice)
                                        .border(if (choice == accentColor) 3.dp else 1.dp,
                                            if (choice == accentColor) colors.textPrimary else colors.divider, CircleShape)
                                        .pinbomClick(onClick = { onAccentChange(choice) }, role = Role.RadioButton, pressedScale = 0.90f)
                                        .semantics { selected = choice == accentColor; role = Role.RadioButton },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (choice == accentColor) Icon(PinBomIcons.Check, contentDescription = null, tint = AbsoluteWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        SettingsToggleRow(
                            icon = if (dark) PinBomIcons.DarkMode else PinBomIcons.LightMode,
                            title = stringResource(R.string.settings_dark_mode),
                            checked = dark,
                            onCheckedChange = onDarkChange,
                        )
                        SettingsToggleRow(
                            icon = PinBomIcons.Tune,
                            title = stringResource(R.string.settings_special_effects),
                            subtitle = stringResource(R.string.settings_special_effects_desc),
                            checked = specialEffectsEnabled,
                            onCheckedChange = onSpecialEffectsChange,
                        )
                    }
                }
                item(key = "language-row") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        SettingsValueRow(
                            icon = PinBomIcons.Translate,
                            title = stringResource(R.string.settings_language),
                            value = language.nativeName,
                            onClick = { languageSheet = true },
                        )
                    }
                }
                item(key = "data-settings-header") {
                    SectionHeader(title = stringResource(R.string.settings_data_storage))
                }
                item(key = "data-settings-controls") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        SettingsToggleRow(
                            icon = PinBomIcons.LightMode,
                            title = stringResource(R.string.settings_data_saver),
                            checked = dataSaver,
                            onCheckedChange = { enabled ->
                                dataSaver = enabled
                                PinImagePreferences.dataSaverEnabled = enabled
                                scope.launch { prefs.setDataSaver(enabled) }
                            },
                        )
                        SettingsToggleRow(
                            icon = PinBomIcons.PhotoCamera,
                            title = stringResource(R.string.settings_upload_wifi_only),
                            checked = wifiOnlyUploads,
                            onCheckedChange = { enabled ->
                                wifiOnlyUploads = enabled
                                scope.launch { prefs.setWifiOnlyUploads(enabled) }
                            },
                        )
                        Text(
                            text = stringResource(R.string.settings_cache_size_kb, Localizer.number((cacheState.bytes + 1023L) / 1024L)),
                            style = PinBomText.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = Dimens.sm),
                        )
                        cacheState.error?.let { Text(it, style = PinBomText.bodySmall, color = MaterialTheme.colorScheme.error) }
                        SecondaryPillButton(
                            text = stringResource(R.string.settings_clear_cache),
                            onClick = { confirmClearCache = true },
                            enabled = !cacheState.clearing,
                            loading = cacheState.clearing,
                            fillWidth = true,
                        )
                    }
                }
                item(key = "notification-settings-header") {
                    SectionHeader(title = stringResource(R.string.settings_notification_types))
                }
                item(key = "notification-settings-controls") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        SettingsToggleRow(
                            icon = PinBomIcons.Check,
                            title = stringResource(R.string.settings_notify_likes),
                            checked = notifyLikes,
                            onCheckedChange = { enabled -> notifyLikes = enabled; scope.launch { prefs.setNotificationType("like", enabled) } },
                        )
                        SettingsToggleRow(
                            icon = PinBomIcons.Check,
                            title = stringResource(R.string.settings_notify_comments),
                            checked = notifyComments,
                            onCheckedChange = { enabled -> notifyComments = enabled; scope.launch { prefs.setNotificationType("comment", enabled) } },
                        )
                        SettingsToggleRow(
                            icon = PinBomIcons.Check,
                            title = stringResource(R.string.settings_notify_follows),
                            checked = notifyFollows,
                            onCheckedChange = { enabled -> notifyFollows = enabled; scope.launch { prefs.setNotificationType("follow", enabled) } },
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            SettingsValueRow(
                                icon = PinBomIcons.Translate,
                                title = stringResource(R.string.settings_system_notifications),
                                value = stringResource(R.string.settings_open),
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                },
                            )
                        }
                    }
                }
                item(key = "account-header") {
                    SectionHeader(title = stringResource(R.string.settings_account))
                }
                item(key = "account-actions") {
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        SettingsValueRow(
                            icon = PinBomIcons.Translate,
                            title = stringResource(R.string.interests_edit_title),
                            value = stringResource(R.string.settings_open),
                            onClick = { nav.navigate(Routes.EDIT_INTERESTS) },
                        )
                        SettingsValueRow(
                            icon = PinBomIcons.Check,
                            title = stringResource(R.string.settings_active_sessions),
                            value = stringResource(R.string.settings_open),
                            onClick = { nav.navigate(Routes.ACTIVE_SESSIONS) },
                        )
                        SettingsValueRow(
                            icon = PinBomIcons.Notifications,
                            title = stringResource(R.string.push_title),
                            value = stringResource(R.string.settings_open),
                            onClick = { nav.navigate(Routes.PUSH_PREFS) },
                        )
                        SettingsValueRow(
                            icon = PinBomIcons.Lock,
                            title = stringResource(R.string.blocked_title),
                            value = stringResource(R.string.settings_open),
                            onClick = { nav.navigate(Routes.BLOCKED) },
                        )
                        Spacer(Modifier.height(Dimens.sm))
                        SecondaryPillButton(
                            text = stringResource(R.string.settings_logout),
                            onClick = onLogout,
                            leadingIcon = PinBomIcons.Logout,
                            fillWidth = true,
                        )
                        Spacer(Modifier.height(Dimens.sm))
                        SecondaryPillButton(
                            text = stringResource(R.string.settings_delete_account),
                            onClick = { confirmDelete = true },
                            leadingIcon = PinBomIcons.Delete,
                            fillWidth = true,
                        )
                        Spacer(Modifier.height(Dimens.huge))
                    }
                }
            }
        }

        ActionToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter),
            baseUrl = session.baseUrl,
        )
    }

    if (languageSheet) {
        PinBomBottomSheet(onDismissRequest = { languageSheet = false }) {
            SheetHeader(title = stringResource(R.string.settings_language))
            AppLanguage.entries.forEach { option ->
                LanguageRow(
                    language = option,
                    selected = option == language,
                    onClick = {
                        languageSheet = false
                        languageState?.select(option)
                    },
                )
            }
            Spacer(Modifier.height(Dimens.sm))
        }
    }

    if (confirmClearCache) {
        PinBomDialog(onDismissRequest = { confirmClearCache = false }) {
            PinBomDialogContent(
                title = stringResource(R.string.settings_clear_cache),
                body = stringResource(R.string.settings_clear_cache_confirm),
                confirmLabel = stringResource(R.string.settings_clear_cache),
                onConfirm = {
                    confirmClearCache = false
                    cacheVm.clear { toast.show(context.getString(R.string.settings_cache_cleared)) }
                },
                onDismiss = { confirmClearCache = false },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }

    if (confirmDelete) {
        PinBomDialog(onDismissRequest = { confirmDelete = false }) {
            PinBomDialogContent(
                title = stringResource(R.string.settings_delete_title),
                body = stringResource(R.string.settings_delete_body),
                confirmLabel = stringResource(R.string.settings_delete_account),
                onConfirm = {
                    confirmDelete = false
                    vm.delete(onLogout)
                },
                onDismiss = { confirmDelete = false },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }
}

/* ── Rows ─────────────────────────────────────────────────────────────────── */

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    val colors = PinBomTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Dimens.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PinBomText.body,
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = PinBomText.caption,
                    color = colors.textSecondary,
                )
            }
        }
        PinBomSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Row with a current value and a chevron that follows the reading direction. */
@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = PinBomTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pinbomClick(
                onClick = onClick,
                role = Role.Button,
                onClickLabel = title,
                pressedScale = 0.99f,
            )
            .padding(vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Dimens.md))
        Text(
            text = title,
            style = PinBomText.body,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = PinBomText.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.width(Dimens.xs))
        Icon(
            imageVector = PinBomIcons.ChevronForward,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * One language choice. Each name is written in its own language *and* its own
 * font, so “فارسی” never renders in Inter and “English” never in Vazirmatn —
 * whichever language the UI happens to be in.
 */
@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PinBomTheme.colors
    val styles = remember(language) { pinbomTextStyles(language) }
    val label = language.nativeName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pinbomClick(
                onClick = onClick,
                role = Role.RadioButton,
                onClickLabel = label,
                pressedScale = 0.99f,
            )
            .padding(vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val direction = if (language.isRtl) TextDirection.Rtl else TextDirection.Ltr
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = styles.body.copy(textAlign = TextAlign.Start, textDirection = direction),
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (selected) {
            Spacer(Modifier.width(Dimens.sm))
            Icon(
                imageVector = PinBomIcons.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
