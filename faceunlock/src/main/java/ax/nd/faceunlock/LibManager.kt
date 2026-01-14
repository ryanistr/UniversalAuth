package ax.nd.faceunlock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

// Kept data class to avoid breaking other references, but it's largely unused now
data class RequiredLib(
    val name: String
)

data class LibraryState(
    val library: RequiredLib,
    val valid: Boolean
)

object LibManager {
    private val TAG = LibManager::class.simpleName

    // We only keep this if other parts of the app reference the list size/names
    val requiredLibraries = listOf(
        RequiredLib("libmegface.so"),
        RequiredLib("libFaceDetectCA.so"),
        RequiredLib("libMegviiUnlock.so"),
        RequiredLib("libMegviiUnlock-jni-1.2.so")
    )

    // Always report true/valid so the UI doesn't ask to download
    val librariesData = MutableStateFlow<List<LibraryState>>(
        requiredLibraries.map { LibraryState(it, true) }
    )
    
    val libsLoaded = AtomicBoolean(false)
    val libLoadError = MutableStateFlow<Throwable?>(null)

    fun init(context: Context) {
        if (libsLoaded.get()) return

        try {
            // Load libraries from the native library path (bundled in APK)
            // Order matters!
            System.loadLibrary("megface")
            System.loadLibrary("FaceDetectCA")
            System.loadLibrary("MegviiUnlock")
            System.loadLibrary("MegviiUnlock-jni-1.2")

            libsLoaded.set(true)
            Log.i(TAG, "Native libraries loaded successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load native libraries!", t)
            libLoadError.value = t
        }
    }
}