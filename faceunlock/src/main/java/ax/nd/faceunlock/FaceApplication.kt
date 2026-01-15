package ax.nd.faceunlock

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ax.nd.faceunlock.pref.Prefs
import ax.nd.faceunlock.service.FacePPPreloader
import ax.nd.faceunlock.util.NotificationUtils
import ax.nd.faceunlock.util.Util
import com.google.android.material.color.DynamicColors

class FaceApplication : Application() {
    private val mHandler = Handler(Looper.getMainLooper())
    
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (Util.DEBUG) {
                Log.d(TAG, "mReceiver Received intent with action = $action")
            }
            if (Intent.ACTION_USER_PRESENT == intent.action) {
                NotificationUtils.checkAndShowNotification(context)
            }
        }
    }

    var facePreloader: FacePPPreloader? = null
        private set
    var prefs: Prefs? = null
        private set

    override fun onCreate() {
        if (Util.DEBUG) {
            Log.d(TAG, "onCreate")
        }
        super.onCreate()
        mApp = this

        // Apply Material You Dynamic Colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_USER_PRESENT)
        intentFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        // Using Context.RECEIVER_EXPORTED if targetSdk >= 33, handled by system or library usually, 
        // strictly passing 2 (RECEIVER_EXPORTED) here as per original code logic intent
        registerReceiver(mReceiver, intentFilter, 2) 

        packageManager.setComponentEnabledSetting(
            ComponentName(this, SetupFaceIntroActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Util.setFaceUnlockAvailable(applicationContext)
        LibManager.init(this)
        prefs = Prefs(this)
        facePreloader = FacePPPreloader(this)
    }

    override fun onTerminate() {
        if (Util.DEBUG) {
            Log.d(TAG, "onTerminate")
        }
        super.onTerminate()
    }

    fun postRunnable(runnable: Runnable) {
        mHandler.post(runnable)
    }

    companion object {
        private val TAG = FaceApplication::class.java.simpleName
        @JvmStatic
        var mApp: FaceApplication? = null
            private set
        
        // Helper to match Java static getter style if needed by other Java classes
        @JvmStatic
        fun getApp(): FaceApplication? {
            return mApp
        }
    }
}
