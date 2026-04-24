package com.cocode.claudeemailapp.app

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Reads the system animator-scale flag (`Settings.Global.ANIMATOR_DURATION_SCALE`)
 * to decide whether to skip animations. The same signal "Developer options → Animator
 * duration scale → off" and the standard accessibility preset disable here, so this
 * gives us a motion-reduce fallback without needing a new app-level toggle.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Settings.SettingNotFoundException) {
            1f
        }
        scale == 0f
    }
}
