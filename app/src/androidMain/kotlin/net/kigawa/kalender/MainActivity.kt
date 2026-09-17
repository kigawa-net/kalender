package net.kigawa.kalender

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KalenderApplication
        intent?.let { app.authController.handleRedirectIntent(it) }
        setContent {
            KalenderRoot(container = app.container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as KalenderApplication
        app.authController.handleRedirectIntent(intent)
    }
}
