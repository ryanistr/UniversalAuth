package ax.nd.faceunlock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class FaceTryAgain : ComponentActivity() {

    private var mToken: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Retrieve token
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
                    onSurface = Color(0xFFE6E1E5),
                    error = Color(0xFFF2B8B5)
                )
                else -> lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color(0xFF1C1B1F),
                    error = Color(0xFFB3261E)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                TryAgainScreen()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
    }

    override fun onPause() {
        super.onPause()
        // Ensure we finish if backgrounded, similar to original behavior
        if (!isChangingConfigurations) {
            finish()
        }
    }

    @Composable
    fun TryAgainScreen() {
        val context = LocalContext.current

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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(100.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Enrollment Failed",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "We couldn't recognize your face. Please ensure you are in a well-lit area and try again.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Only show Try Again if we have the token
                        if (mToken != null) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, FaceEnrollActivity::class.java)
                                    intent.putExtra(AppConstants.EXTRA_KEY_CHALLENGE_TOKEN, mToken)
                                    // Forward standard flags if necessary
                                    startActivity(intent)
                                    finish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Try Again")
                            }
                        }

                        TextButton(
                            onClick = { finish() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
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
}