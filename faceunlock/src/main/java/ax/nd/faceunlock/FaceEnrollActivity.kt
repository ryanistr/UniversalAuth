package ax.nd.faceunlock

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ax.nd.faceunlock.FaceConstants.*
import ax.nd.faceunlock.camera.CameraFaceEnrollController
import ax.nd.faceunlock.service.FaceAuthService
import ax.nd.faceunlock.stub.face.FaceManager
import ax.nd.faceunlock.util.Settings
import com.android.internal.util.custom.faceunlock.IFaceService
import com.android.internal.util.custom.faceunlock.IFaceServiceReceiver

class FaceEnrollActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENROLL_SUCCESS = "extra_enroll_success"
        private val TAG = "FaceEnrollActivity"
    }

    private var mToken: ByteArray? = null
    private var mEnrollmentCancel = CancellationSignal()
    private var mCameraEnrollService: CameraFaceEnrollController? = null
    private var enrollCalled = false
    private var isServiceBound = false
    
    // Hold reference to surface to attach camera later if permission is granted delayed
    private var mSurfaceHolder: SurfaceHolder? = null

    // State for Compose to observe
    private var enrollmentProgressState by mutableFloatStateOf(0f)
    private var enrollmentMessageState by mutableStateOf("Position your face in the circle")
    private var isErrorState by mutableStateOf(false)
    private var isEnrollmentCompleteState by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            initCameraAndService()
        } else {
            Log.d(TAG, "Camera permission denied")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()

        mToken = intent.getByteArrayExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN)
        if (savedInstanceState != null && mToken == null) {
            mToken = savedInstanceState.getByteArray(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN)
        }

        setContent {
            val context = LocalContext.current
            val isDark = isSystemInDarkTheme()
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    secondary = Color(0xFFCCC2DC),
                    onSecondary = Color(0xFF332D41),
                    secondaryContainer = Color(0xFF4A4458),
                    onSecondaryContainer = Color(0xFFE8DEF8),
                    tertiary = Color(0xFFEFB8C8),
                    onTertiary = Color(0xFF492532),
                    tertiaryContainer = Color(0xFF633B48),
                    onTertiaryContainer = Color(0xFFFFD8E4),
                    background = Color(0xFF1C1B1F),
                    surface = Color(0xFF1C1B1F),
                    onSurface = Color(0xFFE6E1E5)
                )
                else -> lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color(0xFF1C1B1F)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                EnrollScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            initCameraAndService()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        releaseResources()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
    }

    private fun initCameraAndService() {
        Log.d(TAG, "initCameraAndService")
        if (mCameraEnrollService == null) {
            Log.d(TAG, "Getting new CameraFaceEnrollController instance")
            mCameraEnrollService = CameraFaceEnrollController.getInstance()
        }

        // Fix: If surface was already created (but permission was missing), 
        // we attach it now that we have permission.
        if (mSurfaceHolder != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted late - attaching existing SurfaceHolder")
            try {
                mCameraEnrollService?.setSurfaceHolder(mSurfaceHolder)
                mCameraEnrollService?.start(mCameraCallback, 15000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera in init", e)
            }
        }

        if (!isServiceBound) {
            try {
                Log.d(TAG, "Binding FaceAuthService")
                bindService(Intent(this, FaceAuthService::class.java), faceAuthConn, Context.BIND_AUTO_CREATE)
                isServiceBound = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind FaceAuthService", e)
            }
        }
    }

    private fun releaseResources() {
        Log.d(TAG, "releaseResources")
        val cameraService = mCameraEnrollService
        if (cameraService != null) {
            Log.d(TAG, "Stopping camera service and clearing surface holder")
            cameraService.stop(mCameraCallback)
            cameraService.setSurfaceHolder(null)
            mCameraEnrollService = null
        }
        
        if (isServiceBound) {
            try {
                Log.d(TAG, "Unbinding FaceAuthService")
                unbindService(faceAuthConn)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isServiceBound = false
        }
        mEnrollmentCancel.cancel()
    }

    @Composable
    fun EnrollScreen() {
        val animatedProgress by animateFloatAsState(
            targetValue = enrollmentProgressState,
            animationSpec = tween(durationMillis = 500),
            label = "Progress"
        )
        
        // Monet / Material You Theme Colors for Progress Bar
        val progressColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                MaterialYouBackground()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = "Enrolling Face",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Keep your face inside the ring",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(60.dp))

                    // Camera Preview with Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(300.dp)
                    ) {
                        // The Camera Preview Container (Mask)
                        // The parent Box clips the overflow
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(CircleShape)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            // The Camera Preview Surface
                            // We use requiredSize to FORCE the size to be 347dp tall (3:4 aspect ratio)
                            // regardless of the parent's 260dp constraint. This ensures center-crop.
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT, 
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        holder.addCallback(object : SurfaceHolder.Callback {
                                            override fun surfaceCreated(holder: SurfaceHolder) {
                                                Log.d(TAG, "surfaceCreated")
                                                mSurfaceHolder = holder // Store the holder

                                                if (mCameraEnrollService == null) {
                                                    mCameraEnrollService = CameraFaceEnrollController.getInstance()
                                                }
                                                
                                                // Only start camera if permission is already granted
                                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    mCameraEnrollService?.setSurfaceHolder(holder)
                                                    mCameraEnrollService?.start(mCameraCallback, 15000)
                                                } else {
                                                    Log.d(TAG, "surfaceCreated: Camera permission missing, waiting for callback")
                                                }
                                            }

                                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                                Log.d(TAG, "surfaceChanged: $width x $height")
                                            }
                                            
                                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                                Log.d(TAG, "surfaceDestroyed")
                                                mSurfaceHolder = null
                                            }
                                        })
                                    }
                                },
                                modifier = Modifier.requiredSize(width = 260.dp, height = 347.dp)
                            )
                        }

                        // Progress Ring Overlay
                        Canvas(modifier = Modifier.size(290.dp)) {
                            drawCircle(
                                color = trackColor,
                                style = Stroke(width = 12.dp.toPx())
                            )
                            
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = (animatedProgress / 100f) * 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Status Message
                    Text(
                        text = enrollmentMessageState,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isErrorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    fun MaterialYouBackground() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    }

    private val mCameraCallback = object : CameraFaceEnrollController.CameraCallback {
        override fun handleSaveFeature(bArr: ByteArray?, i: Int, i2: Int, i3: Int): Int = -1
        override fun setDetectArea(size: Camera.Size?) {}

        override fun handleSaveFeatureResult(error: Int) {
            runOnUiThread {
                var msg = ""
                var isError = false
                
                when (error) {
                    MG_UNLOCK_FACE_SCALE_TOO_SMALL -> { msg = getString(R.string.unlock_failed_face_small); isError = true }
                    MG_UNLOCK_FACE_SCALE_TOO_LARGE -> { msg = getString(R.string.unlock_failed_face_large); isError = true }
                    MG_UNLOCK_FACE_OFFSET_LEFT, MG_UNLOCK_FACE_OFFSET_RIGHT, 
                    MG_UNLOCK_FACE_ROTATED_LEFT, MG_UNLOCK_FACE_ROTATED_RIGHT -> {
                         msg = "Aligning face..."
                         if (enrollmentProgressState < 20) enrollmentProgressState += 1f
                    }
                    MG_UNLOCK_KEEP -> {
                        msg = "Hold still..."
                        if (enrollmentProgressState < 60) enrollmentProgressState += 2f
                    }
                    MG_UNLOCK_FACE_MULTI -> { msg = getString(R.string.unlock_failed_face_multi); isError = true }
                    MG_UNLOCK_FACE_BLUR -> { msg = getString(R.string.unlock_failed_face_blur); isError = true }
                    MG_UNLOCK_FACE_NOT_COMPLETE -> { msg = getString(R.string.unlock_failed_face_not_complete); isError = true }
                    MG_UNLOCK_DARKLIGHT -> { msg = getString(R.string.attr_light_dark); isError = true }
                    MG_UNLOCK_HIGHLIGHT -> { msg = getString(R.string.attr_light_high); isError = true }
                    MG_UNLOCK_HALF_SHADOW -> { msg = getString(R.string.attr_light_shadow); isError = true }
                }

                if (!isEnrollmentCompleteState) {
                    if (msg.isNotEmpty()) {
                        enrollmentMessageState = msg
                        isErrorState = isError
                    }
                }
            }
        }

        override fun onTimeout() {
            Log.d(TAG, "onTimeout")
            if (!isFinishing && !isDestroyed) {
                if (Settings.isFaceUnlockAvailable(this@FaceEnrollActivity)) {
                    finishEnrollment(true)
                } else {
                    val intent = Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                        putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                    }
                    releaseResources()
                    startActivity(intent)
                    finish()
                }
            }
        }

        override fun onCameraError() {
             Log.e(TAG, "onCameraError")
             if (!isFinishing && !isDestroyed) {
                 val intent = Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                     putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                 }
                 releaseResources()
                 startActivity(intent)
                 finish()
             }
        }

        override fun onFaceDetected() {}
    }

    private val faceAuthConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val authBinder = IFaceService.Stub.asInterface(service)
            val authCallback = object : IFaceServiceReceiver.Stub() {
                override fun onEnrollResult(faceId: Int, userId: Int, remaining: Int) {
                      Log.d(TAG, "onEnrollResult: remaining=$remaining")
                      if (remaining == 0) {
                          runOnUiThread {
                              enrollmentProgressState = 100f
                              finishEnrollment(true)
                          }
                      } else {
                          val progress = 100f - (remaining * 10f).coerceAtMost(40f) 
                          if (progress > enrollmentProgressState) {
                              enrollmentProgressState = progress
                          }
                      }
                }

                override fun onAuthenticated(faceId: Int, userId: Int, token: ByteArray?) {}
                
                override fun onError(error: Int, vendorCode: Int) {
                    val message = FaceManager.getErrorString(this@FaceEnrollActivity, error, vendorCode)
                    runOnUiThread {
                         enrollmentMessageState = message.toString()
                         isErrorState = true
                         
                         if (error != MG_UNLOCK_FAILED) {
                             Handler(Looper.getMainLooper()).postDelayed({
                                 val intent = Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                                     putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                                 }
                                 releaseResources()
                                 startActivity(intent)
                                 finish()
                             }, 1500)
                         } else {
                             setResult(RESULT_CANCELED)
                             finish()
                         }
                    }
                }
                
                override fun onRemoved(faceIds: IntArray?, userId: Int) {}
                override fun onEnumerate(faceIds: IntArray?, userId: Int) {}
            }

            try {
                authBinder.setCallback(authCallback)
                if (!enrollCalled) {
                    enrollCalled = true
                    authBinder.enroll(mToken ?: byteArrayOf(0), 20, intArrayOf())
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Service error", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            isServiceBound = false
        }
    }

    private fun finishEnrollment(success: Boolean) {
        Log.d(TAG, "finishEnrollment success=$success")
        if (success && !isEnrollmentCompleteState) {
            isEnrollmentCompleteState = true
            enrollmentMessageState = "Enrollment Complete!"
            isErrorState = false
            
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, FaceFinish::class.java)
                intent.putExtra(EXTRA_ENROLL_SUCCESS, true)
                startActivityForResult(intent, 1)
            }, 500)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            setResult(resultCode)
            finish()
        }
    }
}