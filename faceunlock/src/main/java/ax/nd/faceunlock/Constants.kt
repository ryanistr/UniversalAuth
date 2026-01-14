package ax.nd.faceunlock

object Constants {
    // FIXED: Updated to match the lowercase/hyphenated strings in FaceUnlockReceiver.smali
    const val ACTION_UNLOCK_DEVICE = "ax.nd.universalauth.unlock-device"
    
    // Extras
    const val EXTRA_UNLOCK_MODE = "ax.nd.universalauth.unlock-device.unlock-mode"
    const val EXTRA_BYPASS_KEYGUARD = "ax.nd.universalauth.unlock-device.bypass-keyguard"

    // Early Unlock (Optional, keeping naming convention consistent if you use it)
    const val ACTION_EARLY_UNLOCK = "ax.nd.universalauth.EARLY_UNLOCK"
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