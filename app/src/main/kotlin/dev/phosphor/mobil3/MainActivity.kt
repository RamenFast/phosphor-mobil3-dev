package dev.phosphor.mobil3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// M0: prove the whole seam — Kotlin → JNI → phosphor engine crates — on the S25.
// The real UI arrives with M1 (scope surface) and M5 (UX build-out).
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engineInfo = runCatching { PhosphorNative.engineInfo() }
            .getOrElse { "engine load failed: ${it.message}" }
        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = "phosphor-mobil3",
                        style = TextStyle(
                            color = Color(0xFF9BE8A8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                        ),
                    )
                    BasicText(
                        text = "v${BuildConfig.VERSION_NAME} · api ${android.os.Build.VERSION.SDK_INT}",
                        style = TextStyle(
                            color = Color(0xFF6A6F6A),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    BasicText(
                        text = engineInfo,
                        style = TextStyle(
                            color = Color(0xFF6A6F6A),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier.padding(top = 16.dp).padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}
