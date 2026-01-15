package ax.nd.faceunlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ax.nd.faceunlock.service.LockscreenFaceAuthService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FaceAuthBoot", "Boot received: ${intent.action}")
        if (Intent.ACTION_BOOT_COMPLETED == intent.action || 
            Intent.ACTION_LOCKED_BOOT_COMPLETED == intent.action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {
            
            val serviceIntent = Intent(context, LockscreenFaceAuthService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}