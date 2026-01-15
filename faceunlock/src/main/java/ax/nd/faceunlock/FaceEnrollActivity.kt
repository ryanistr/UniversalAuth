package ax.nd.faceunlock

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

    private val TAG = FaceEnrollActivity::class.java.simpleName
    private var mToken: ByteArray? = null
    private var mEnrollmentCancel = CancellationSignal()
    private var mCameraEnrollService: CameraFaceEnrollController? = null
    private var mIsActivityPaused = false
    private var enrollCalled = false

    // State for Compose
    private var enrollmentProgress by mutableStateOf(0f)
    private var enrollmentMessage by mutableStateOf("")
    private var enrollmentError by mutableStateOf<String?>(null)
    private var isFaceDetected by mutableStateOf(false)
    private var isEnrollmentComplete by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initCameraAndService()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mToken = intent.getByteArrayExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN)
        if (savedInstanceState != null && mToken == null) {
            mToken = savedInstanceState.getByteArray(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN)
        }

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White
                )
            ) {
                EnrollScreen()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            initCameraAndService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
    }

    private fun initCameraAndService() {
        if (mCameraEnrollService == null) {
            mCameraEnrollService = CameraFaceEnrollController.getInstance()
        }
        
        bindService(Intent(this, FaceAuthService::class.java), faceAuthConn, Context.BIND_AUTO_CREATE)
    }

    @Composable
    fun EnrollScreen() {
        val animatedProgress by animateFloatAsState(targetValue = enrollmentProgress, label = "Progress")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Enrolling Face",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Keep your face inside the ring",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(300.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    mCameraEnrollService?.setSurfaceHolder(holder)
                                    startCameraPreview()
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    mCameraEnrollService?.setSurfaceHolder(null)
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                )

                Canvas(modifier = Modifier.size(280.dp)) {
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                    
                    drawArc(
                        color = Color(0xFF6750A4),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 3.6f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = enrollmentMessage,
                style = MaterialTheme.typography.titleMedium,
                color = if (enrollmentError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))

            if (isEnrollmentComplete) {
                Button(
                    onClick = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Done")
                }
            }
        }

        LaunchedEffect(isFaceDetected, enrollmentProgress) {
             if (isFaceDetected && enrollmentProgress < 60f) {
                 kotlinx.coroutines.delay(500)
                 enrollmentProgress += 5f
             }
        }
    }

    private fun startCameraPreview() {
        mCameraEnrollService?.start(mCameraCallback, 15000)
    }

    private val mCameraCallback = object : CameraFaceEnrollController.CameraCallback {
        override fun handleSaveFeature(bArr: ByteArray?, i: Int, i2: Int, i3: Int): Int = -1
        override fun setDetectArea(size: Camera.Size?) {}

        override fun handleSaveFeatureResult(error: Int) {
            runOnUiThread {
                var msg = ""
                when (error) {
                    MG_UNLOCK_FACE_SCALE_TOO_SMALL -> msg = getString(R.string.unlock_failed_face_small)
                    MG_UNLOCK_FACE_SCALE_TOO_LARGE -> msg = getString(R.string.unlock_failed_face_large)
                    MG_UNLOCK_FACE_OFFSET_LEFT, MG_UNLOCK_FACE_OFFSET_RIGHT, 
                    MG_UNLOCK_FACE_ROTATED_LEFT, MG_UNLOCK_FACE_ROTATED_RIGHT -> {
                         enrollmentProgress += 10f
                    }
                    MG_UNLOCK_KEEP -> {
                        enrollmentProgress += 10f
                        isFaceDetected = true
                    }
                    MG_UNLOCK_FACE_MULTI -> msg = getString(R.string.unlock_failed_face_multi)
                    MG_UNLOCK_FACE_BLUR -> msg = getString(R.string.unlock_failed_face_blur)
                    MG_UNLOCK_FACE_NOT_COMPLETE -> msg = getString(R.string.unlock_failed_face_not_complete)
                    MG_UNLOCK_DARKLIGHT -> msg = getString(R.string.attr_light_dark)
                    MG_UNLOCK_HIGHLIGHT -> msg = getString(R.string.attr_light_high)
                    MG_UNLOCK_HALF_SHADOW -> msg = getString(R.string.attr_light_shadow)
                }

                if (enrollmentProgress < 100f) {
                    if (isFaceDetected && enrollmentProgress >= 60f) {
                         if (enrollmentProgress > 60f && !isEnrollmentComplete) enrollmentProgress = 60f
                    }
                    if (msg.isNotEmpty()) {
                        enrollmentMessage = msg
                        enrollmentError = msg
                    } else {
                        enrollmentError = null
                    }
                }
            }
        }

        override fun onTimeout() {
            if (!isFinishing && !isDestroyed) {
                if (Settings.isFaceUnlockAvailable(this@FaceEnrollActivity)) {
                    finishEnrollment(true)
                } else {
                    if (!mIsActivityPaused) {
                        startActivity(Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                             putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                        })
                    }
                    finish()
                }
            }
        }

        override fun onCameraError() {
             if (!isFinishing && !isDestroyed) {
                 if (!mIsActivityPaused) {
                     startActivity(Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                         putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                     })
                 }
                 finish()
             }
        }

        override fun onFaceDetected() {
            isFaceDetected = true
        }
    }

    private val faceAuthConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val authBinder = IFaceService.Stub.asInterface(service)
            val authCallback = object : IFaceServiceReceiver.Stub() {
                override fun onEnrollResult(faceId: Int, userId: Int, remaining: Int) {
                     if (remaining == 0) {
                         runOnUiThread {
                             enrollmentProgress = 100f
                             finishEnrollment(true)
                         }
                     }
                }

                override fun onAuthenticated(faceId: Int, userId: Int, token: ByteArray?) {}
                
                override fun onError(error: Int, vendorCode: Int) {
                    val message = FaceManager.getErrorString(this@FaceEnrollActivity, error, vendorCode)
                    runOnUiThread {
                         enrollmentMessage = message.toString()
                         enrollmentError = message.toString()
                         
                         if (!mIsActivityPaused && error != MG_UNLOCK_FAILED) {
                             startActivity(Intent(this@FaceEnrollActivity, FaceTryAgain::class.java).apply {
                                 putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                             })
                             finish()
                         } else if (error == MG_UNLOCK_FAILED) {
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
                    authBinder.enroll(byteArrayOf(0), 75, intArrayOf(1))
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Service error", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun finishEnrollment(success: Boolean) {
        if (success) {
            isEnrollmentComplete = true
            enrollmentMessage = "Enrollment Complete"
            enrollmentError = null
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, FaceFinish::class.java)
                intent.putExtra(SetupFaceIntroActivity.EXTRA_ENROLL_SUCCESS, true)
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

    override fun onPause() {
        super.onPause()
        mIsActivityPaused = true
        mCameraEnrollService?.let {
            it.setSurfaceHolder(null)
            it.stop(mCameraCallback)
        }
        mCameraEnrollService = null
        mEnrollmentCancel.cancel()
        try {
            unbindService(faceAuthConn)
        } catch (e: Exception) {
            // Ignore if not bound
        }
    }

    override fun onResume() {
        super.onResume()
        mIsActivityPaused = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
             initCameraAndService()
        }
    }
}
