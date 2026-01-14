package ax.nd.faceunlock.service

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import ax.nd.faceunlock.Constants
import ax.nd.faceunlock.FaceApplication
import ax.nd.faceunlock.LibManager
import ax.nd.faceunlock.pref.Prefs
import ax.nd.faceunlock.util.Util
import ax.nd.faceunlock.util.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LockscreenFaceAuthService : AccessibilityService(), FaceAuthServiceCallbacks {
    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP
    }

    private var active: Boolean = false
    private var lockStateReceiver: BroadcastReceiver? = null
    private var unlockReceiver: BroadcastReceiver? = null
    private var controller: FaceAuthServiceController? = null
    private var keyguardManager: KeyguardManager? = null
    private var displayManager: DisplayManager? = null
    private var handler: Handler? = null
    private var startTime: Long = 0
    private var textViewAnimator: ViewPropertyAnimator? = null

    private lateinit var serviceJob: Job
    private lateinit var serviceScope: CoroutineScope
    private lateinit var prefs: Prefs

    private var showStatusText = true
    private var booted = false

    override fun onCreate() {
        super.onCreate()

        // Coroutine env
        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

        prefs = FaceApplication.getApp().prefs

        controller = FaceAuthServiceController(this, prefs, this)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        handler = Handler(Looper.getMainLooper())

        textView = TextView(this)
        textView?.setTextColor(getColor(android.R.color.white))
        textView?.setShadowLayer(10f, 0f, 0f, Color.BLACK)
        textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        textView?.setPadding(0, 16.dpToPx.toInt(), 0, 0)
        textView?.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

        booted = !prefs.requirePinOnBoot.get()
        if(booted) {
            reconfigureUnlockHook()
        }
        setupUnlockReceiver()

        serviceScope.launch {
            prefs.showStatusText.asFlow().collect { showStatusText ->
                this@LockscreenFaceAuthService.showStatusText = showStatusText
            }
        }
    }

    private fun setupUnlockReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                if(!booted) {
                    booted = true
                    reconfigureUnlockHook()
                }
                prefs.failedUnlockAttempts.set(0)
            }
        }
        registerReceiver(unlockReceiver, intentFilter, RECEIVER_EXPORTED)
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            unregisterReceiver(it)
            unlockReceiver = null
        }
    }

    private fun reconfigureUnlockHook() {
        serviceScope.launch {
            prefs.earlyUnlockHook.asFlow().collect { earlyUnlock ->
                unregisterLockStateReceiver()
                if(earlyUnlock) {
                    registerEarlyUnlockReceiver()
                } else {
                    registerNormalReceiver()
                }
            }
        }
    }

    private fun registerNormalReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        lockStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                doubleCheckLockscreenState()
            }
        }
        registerReceiver(lockStateReceiver, intentFilter, RECEIVER_EXPORTED)
    }

    private fun registerEarlyUnlockReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(Constants.ACTION_EARLY_UNLOCK)
        }
        lockStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent) {
                val mode = p1.getBooleanExtra(Constants.EXTRA_EARLY_UNLOCK_MODE, false)
                if(mode) {
                    show()
                } else {
                    hide()
                }
            }
        }
        registerReceiver(lockStateReceiver, intentFilter, RECEIVER_EXPORTED)
    }

    private fun unregisterLockStateReceiver() {
        lockStateReceiver?.let {
            unregisterReceiver(it)
            lockStateReceiver = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        if(!LibManager.libsLoaded.get()) {
            Log.w(TAG, "Libs not loaded, disabling self...")
            disableSelf()
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterLockStateReceiver()
        unregisterUnlockReceiver()
        hide()
        serviceJob.cancel()
    }

    private fun doubleCheckLockscreenState() {
        val mainDisplayOn = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON

        if(mainDisplayOn && keyguardManager?.isKeyguardLocked == true) {
            show()
        } else {
            hide()
        }
    }

    private fun show() {
        if(!active) {
            if(Util.isFaceUnlockEnrolled(this)) {
                active = true
                if(showStatusText) {
                    textViewAnimator?.cancel()
                    textView?.text = "Looking for face..."
                    try {
                        windowManager?.addView(textView, params)
                    } catch (e: Exception) {
                        // Handle view already added or similar race conditions
                    }
                }
                startTime = System.currentTimeMillis()
                controller?.start()
            }
        }
    }

    private fun hide(delay: Int = 0) {
        if(active) {
            active = false
            if(showStatusText) {
                if (delay > 0) {
                    textViewAnimator = textView?.animate()
                        ?.alpha(0f)
                        ?.setListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(p0: Animator) {}
                            override fun onAnimationEnd(p0: Animator) = onTextViewAnimationEnd()
                            override fun onAnimationCancel(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}
                        })
                        ?.setStartDelay(delay.toLong())
                        ?.setDuration(300)
                    textViewAnimator?.start()
                } else {
                    removeTextViewFromWindowManager()
                }
            }
            controller?.stop()
        } else {
            if(showStatusText) {
                if (delay == 0) {
                    textViewAnimator?.cancel()
                }
            }
        }
    }

    private fun onTextViewAnimationEnd() {
        removeTextViewFromWindowManager()
        textViewAnimator?.cancel()
        textViewAnimator = null
        textView?.alpha = 1f
    }

    private fun removeTextViewFromWindowManager() {
        try {
            windowManager?.removeView(textView)
        } catch (e: Exception) {
            // View might already be removed
        }
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onAuthed() {
        Log.d("MeasureFaceUnlock", "Total time: " + (System.currentTimeMillis() - startTime))
        
        // --- UPDATED LOGIC ---
        val unlockAnimation = when(FaceApplication.getApp().prefs.unlockAnimation.get()) {
            "mode_wake_and_unlock" -> Constants.MODE_WAKE_AND_UNLOCK
            "mode_wake_and_unlock_pulsing" -> Constants.MODE_WAKE_AND_UNLOCK_PULSING
            else -> Constants.MODE_UNLOCK_FADING
        }
        
        Log.d(TAG, "onAuthed: Preparing broadcast. Mode: $unlockAnimation, Action: ${Constants.ACTION_UNLOCK_DEVICE}")

        val intent = Intent(Constants.ACTION_UNLOCK_DEVICE).apply {
            putExtra(Constants.EXTRA_UNLOCK_MODE, unlockAnimation)
            putExtra(Constants.EXTRA_BYPASS_KEYGUARD, prefs.bypassKeyguard.get())
            // Ideally target the SystemUI package explicitly if possible, 
            // but standard broadcast is standard for this mod.
            // setPackage("com.android.systemui") 
        }
        
        sendBroadcast(intent)
        Log.d(TAG, "onAuthed: Broadcast sent!")

        handler?.post {
            textView?.text = "Welcome!"
            hide(delay = 1000)
        }
    }

    override fun onError(errId: Int, message: String) {
        handler?.post {
            textView?.text = message
            hide(delay = 2000)
        }
    }

    companion object {
        private val TAG = LockscreenFaceAuthService::class.simpleName
        // Defined locally just in case compileSdk is old, though newer SDKs have it in Context.
        const val RECEIVER_EXPORTED = 2 
    }
}