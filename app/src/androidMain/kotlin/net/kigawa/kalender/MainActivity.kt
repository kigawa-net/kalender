package net.kigawa.kalender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KalenderApplication
        setContent {
            val consentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                app.googleAuthController.handleConsentResult(this, result.data)
            }

            LaunchedEffect(Unit) {
                app.googleAuthController.pendingConsent.collect { intentSender ->
                    consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }

            KalenderRoot(container = app.container)
        }
    }
}
