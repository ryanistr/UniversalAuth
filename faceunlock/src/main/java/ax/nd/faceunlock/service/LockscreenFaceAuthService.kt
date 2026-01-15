package ax.nd.faceunlock.service

import android.animation.Animator
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import android.widget.TextView
import ax.nd.faceunlock.Constants
import ax.nd.faceunlock.FaceApplication
import ax.nd.faceunlock.LibManager
import ax.nd.faceunlock.R
import ax.nd.faceunlock.pref.Prefs
import ax.nd.faceunlock.util.Util
import ax.nd.faceunlock.util.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LockscreenFaceAuthService : Service(), FaceAuthServiceCallbacks {
    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    
    // Use TYPE_APPLICATION_OVERLAY for Android O+
    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        layoutType,
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // Must start foreground immediately to avoid "Starting FGS without a type" crash
        startForegroundSelf()

        // Coroutine env
        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

        // Assert non-null because App and Prefs must exist when Service is created
        prefs = FaceApplication.getApp()!!.prefs!!

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
        
        // Initial check in case we were started while locked
        doubleCheckLockscreenState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we are in foreground to prevent kill
        startForegroundSelf()
        doubleCheckLockscreenState()
        return START_STICKY
    }

    private fun startForegroundSelf() {
        val channelId = "face_unlock_service"
        val channelName = "Face Unlock Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN)
            chan.lockscreenVisibility = Notification.VISIBILITY_SECRET
            chan.setShowBadge(false)
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setSmallIcon(R.mipmap.ic_launcher_face_unlock) 
            .setContentTitle("Face Unlock Active")
            .setContentText("Listening for screen events")
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()

        // SDK 34 (Android 14) requires specifying the type
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Good practice to set it if supported, though typically not strictly enforced as crash before 34
            // 0 is default "none" in older SDKs but Q accepts bitmask. 
            // We'll stick to standard call for < 34 to match previous behavior or simple start.
             startForeground(1, notification)
        } else {
            startForeground(1, notification)
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

    override fun onAuthed() {
        Log.d("MeasureFaceUnlock", "Total time: " + (System.currentTimeMillis() - startTime))
        
        val shouldBypass = prefs.bypassKeyguard.get()

        val unlockAnimation = if (shouldBypass) {
            when(FaceApplication.getApp()?.prefs?.unlockAnimation?.get()) {
                "mode_wake_and_unlock" -> Constants.MODE_WAKE_AND_UNLOCK
                "mode_wake_and_unlock_pulsing" -> Constants.MODE_WAKE_AND_UNLOCK_PULSING
                else -> Constants.MODE_UNLOCK_FADING
            }
        } else {
            Constants.MODE_NONE
        }
        
        Log.d(TAG, "onAuthed: Preparing broadcast. Mode: $unlockAnimation, Action: ${Constants.ACTION_UNLOCK_DEVICE}, Bypass: $shouldBypass")

        val intent = Intent(Constants.ACTION_UNLOCK_DEVICE).apply {
            putExtra(Constants.EXTRA_UNLOCK_MODE, unlockAnimation)
            putExtra(Constants.EXTRA_BYPASS_KEYGUARD, shouldBypass)
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
        const val RECEIVER_EXPORTED = 2 
    }
}