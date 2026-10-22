package org.fossify.messages.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.fossify.messages.activities.SplashActivity
import org.fossify.messages.extensions.config

/**
 * Launcher icon switching, which is really alias switching: every icon colour is an activity-alias
 * in the manifest and exactly one of them is enabled at a time.
 *
 * The alias is declared against the code namespace, not against the installed app id, so its class
 * name has to come from [SplashActivity] itself. Building it from the context's package name
 * produces a component that does not exist, and setting that state fails silently, which leaves the
 * icon unchanged and makes [current] always report the default.
 *
 * Commons' own helper for this reads R.array.app_icon_colors, an array this app never defines, so
 * the switching is done here instead.
 */
class AppIconSwitcher(private val context: Context) {
    private val manager: PackageManager
        get() = context.packageManager

    /** The alias the launcher is currently using; falls back to the shipped one. */
    fun current(): AppIconVariant {
        return appIconVariants.firstOrNull { variant ->
            manager.getComponentEnabledSetting(component(variant)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: defaultAppIconVariant
    }

    /** The variant [color] belongs to, falling back to the nearest one for anything unpicked. */
    fun closest(color: Int): AppIconVariant {
        return appIconVariants.firstOrNull { it.color == color }
            ?: appIconVariants.minByOrNull { colorDistance(it.color, color) }
            ?: defaultAppIconVariant
    }

    /** Enables the chosen alias and disables the rest, which is what changes the launcher icon. */
    fun apply(variant: AppIconVariant) {
        appIconVariants.forEach { other ->
            val state = if (other == variant) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            manager.setComponentEnabledSetting(component(other), state, PackageManager.DONT_KILL_APP)
        }

        context.config.appIconColor = variant.color
    }

    /**
     * Cycles the enabled alias off and back on, which is a component state change the system
     * reacts to. Some ROMs keep the package icon they first read - SystemUI then draws that stale
     * bitmap in notifications until a reboot - and this is the only lever an app has to make it
     * look again.
     */
    fun refresh() {
        val variant = current()
        manager.setComponentEnabledSetting(
            component(variant),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        manager.setComponentEnabledSetting(
            component(variant),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /** The launcher icon for [variant], used as the preview in the app icon picker. */
    fun iconDrawable(variant: AppIconVariant = current()): Drawable? {
        val name = if (variant == defaultAppIconVariant) {
            "ic_launcher"
        } else {
            "ic_launcher_${variant.name.lowercase()}"
        }
        val iconRes = context.resources.getIdentifier(name, "mipmap", context.packageName)
        return if (iconRes == 0) null else context.getDrawable(iconRes)
    }

    private fun component(variant: AppIconVariant): ComponentName {
        return ComponentName(context.packageName, "${SplashActivity::class.java.name}.${variant.name}")
    }
}
