package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.MsAuthState

@Composable
fun LoginScreen(
    googleState: GoogleAuthManager.AuthState,
    msState: MsAuthState,
    onGoogleSignIn: () -> Unit,
    onMsSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Kalender", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(48.dp))

        GoogleSignInSection(state = googleState, onSignIn = onGoogleSignIn)

        Spacer(modifier = Modifier.height(24.dp))
        OrDivider()
        Spacer(modifier = Modifier.height(24.dp))

        MicrosoftSignInSection(state = msState, onSignIn = onMsSignIn)
    }
}

@Composable
private fun GoogleSignInSection(
    state: GoogleAuthManager.AuthState,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state is GoogleAuthManager.AuthState.Error) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when (state) {
            is GoogleAuthManager.AuthState.Loading -> CircularProgressIndicator()
            else -> Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text("Googleでサインイン")
            }
        }
    }
}

@Composable
private fun MicrosoftSignInSection(
    state: MsAuthState,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state is MsAuthState.Error) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TextButton(onClick = onSignIn) { Text("再試行") }
        } else {
            when (state) {
                is MsAuthState.SigningIn, MsAuthState.Initializing -> CircularProgressIndicator()
                else -> OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Microsoftでサインイン")
                }
            }
        }
    }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text("または", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    KalenderTheme {
        LoginScreen(
            googleState = GoogleAuthManager.AuthState.SignedOut,
            msState = MsAuthState.SignedOut,
            onGoogleSignIn = {},
            onMsSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    KalenderTheme {
        LoginScreen(
            googleState = GoogleAuthManager.AuthState.Error("サインインに失敗しました"),
            msState = MsAuthState.Error("MSAL初期化エラー"),
            onGoogleSignIn = {},
            onMsSignIn = {},
        )
    }
}
