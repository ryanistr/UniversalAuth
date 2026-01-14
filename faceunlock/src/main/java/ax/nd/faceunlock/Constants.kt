package ax.nd.faceunlock

object Constants {
    // Actions must match the BroadcastReceiver you added to SystemUI via Smali
    const val ACTION_UNLOCK_DEVICE = "ax.nd.universalauth.UNLOCK_DEVICE"
    const val ACTION_EARLY_UNLOCK = "ax.nd.universalauth.EARLY_UNLOCK"
    
    // Extras
    const val EXTRA_UNLOCK_MODE = "ax.nd.universalauth.extra.UNLOCK_MODE"
    const val EXTRA_BYPASS_KEYGUARD = "ax.nd.universalauth.extra.BYPASS_KEYGUARD"
    const val EXTRA_EARLY_UNLOCK_MODE = "ax.nd.universalauth.extra.EARLY_UNLOCK_MODE"

    // Permissions
    const val PERMISSION_UNLOCK_DEVICE = "ax.nd.universalauth.permission.UNLOCK_DEVICE"

    /**
     * Unlock Modes matching com.android.systemui.statusbar.phone.BiometricUnlockController
     */
    const val MODE_NONE = 0
    const val MODE_WAKE_AND_UNLOCK = 1 // No animation
    const val MODE_WAKE_AND_UNLOCK_PULSING = 2 // No animation
    const val MODE_UNLOCK_COLLAPSING = 5
    const val MODE_UNLOCK_FADING = 7 // Animated
    const val MODE_DISMISS_BOUNCER = 8
}