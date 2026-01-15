package ax.nd.faceunlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import ax.nd.faceunlock.service.LockscreenFaceAuthService
import ax.nd.faceunlock.service.RemoveFaceController
import ax.nd.faceunlock.service.RemoveFaceControllerCallbacks
import ax.nd.faceunlock.util.SharedUtil
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), RemoveFaceControllerCallbacks {
    private var removeFaceController: RemoveFaceController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Libs
        LibManager.init(this)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFF6750A4), 
                    onPrimary = Color.White
                )
            ) {
                MainScreen()
            }
        }

        checkAndAskForPermissions()
        monitorLibLoadErrors()
    }

    private fun monitorLibLoadErrors() {
        lifecycleScope.launch {
            var curErrorDialog: MaterialDialog? = null
            LibManager.libLoadError.flowWithLifecycle(lifecycle).collect { error ->
                if (error != null) {
                    curErrorDialog?.cancel()
                    curErrorDialog = MaterialDialog(this@MainActivity).show {
                        title(text = "Fatal error")
                        message(text = "Failed to load bundled libraries.\n\nError: ${error.message}")
                        positiveButton(text = "Ok") {}
                        cancelable(false)
                        cancelOnTouchOutside(false)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var faceEnrolled by remember { mutableStateOf(false) }

        // State initialization
        LaunchedEffect(Unit) {
            updateEnrollmentState { faceEnrolled = it }
        }

        // OnResume effect to refresh state
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.currentStateFlow.collect {
                if (it == Lifecycle.State.RESUMED) {
                    updateEnrollmentState { enrolled -> faceEnrolled = enrolled }
                }
            }
        }

        // Animation States
        val headerAlpha = remember { Animatable(0f) }
        val headerOffset = remember { Animatable(50f) }
        val cardAlpha = remember { Animatable(0f) }
        val cardOffset = remember { Animatable(100f) }

        LaunchedEffect(Unit) {
            launch {
                headerAlpha.animateTo(1f, animationSpec = tween(600, delayMillis = 100))
            }
            launch {
                headerOffset.animateTo(0f, animationSpec = tween(600, delayMillis = 100))
            }
            launch {
                cardAlpha.animateTo(1f, animationSpec = tween(700, delayMillis = 200))
            }
            launch {
                cardOffset.animateTo(0f, animationSpec = tween(700, delayMillis = 200))
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Liquid Background with Blur
                LiquidBackground()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Section
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = headerAlpha.value
                                translationY = headerOffset.value
                            }
                            .padding(top = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_face_auth),
                            contentDescription = "Face Icon",
                            modifier = Modifier.size(72.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Face Unlock",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Secure your device with your face",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Controls Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = cardAlpha.value
                                translationY = cardOffset.value
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { startActivity(Intent(context, SetupFaceIntroActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = !faceEnrolled,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(if (faceEnrolled) "Face Enrolled" else "Set up Face Unlock")
                            }

                            Button(
                                onClick = { startActivity(Intent(context, FaceAuthActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = faceEnrolled,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("Test Authentication")
                            }

                            if (faceEnrolled) {
                                Button(
                                    onClick = {
                                        val faceId = SharedUtil(context).getIntValueByKey(AppConstants.SHARED_KEY_FACE_ID)
                                        removeFace(faceId)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Remove Face Model")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LiquidBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(
                                50f, 50f, Shader.TileMode.MIRROR
                            )
                            .asComposeRenderEffect()
                    }
            ) {
                // Place your abstract shapes or background image here to be blurred
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .offset(x = (-50).dp, y = (-50).dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 50.dp, y = 50.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape)
                )
            }
        } else {
            // Fallback for older APIs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }

    private fun updateEnrollmentState(onResult: (Boolean) -> Unit) {
        val faceId = SharedUtil(this).getIntValueByKey(AppConstants.SHARED_KEY_FACE_ID)
        onResult(faceId > -1)
    }

    private fun checkAndAskForPermissions() {
        if (ContextCompat.checkSelfPermission(this, Constants.PERMISSION_UNLOCK_DEVICE) != PackageManager.PERMISSION_GRANTED) {
             // Permission logic
        }

        if (!isAccessServiceEnabled(this, LockscreenFaceAuthService::class.java)) {
            MaterialDialog(this).show {
                title(text = "Accessibility service required")
                message(text = "Face unlock needs accessibility access to interact with your lock screen.")
                positiveButton(text = "Enable") {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }

    private fun isAccessServiceEnabled(context: Context, accessibilityServiceClass: Class<*>): Boolean {
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val serviceName = accessibilityServiceClass.canonicalName
        return prefString?.split(":")?.filter { it.isNotBlank() }?.any {
            val slashIndex = it.indexOf('/')
            val possibleServiceName = it.substring(slashIndex + 1)
            possibleServiceName == serviceName || (it.substring(0, slashIndex) + possibleServiceName) == serviceName
        } == true
    }

    private fun removeFace(faceId: Int) {
        releaseRemoveFaceController()
        removeFaceController = RemoveFaceController(this, faceId, this).apply {
            start()
        }
    }

    private fun releaseRemoveFaceController() {
        removeFaceController?.stop()
        removeFaceController = null
    }

    override fun onStop() {
        super.onStop()
        releaseRemoveFaceController()
    }

    override fun onRemove() {
        runOnUiThread {
            Toast.makeText(this, "Face model removed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onError(errId: Int, message: String) {
        runOnUiThread {
            Toast.makeText(this, "Error removing face: $message", Toast.LENGTH_LONG).show()
            releaseRemoveFaceController()
        }
    }
}
