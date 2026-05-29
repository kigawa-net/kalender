package net.kigawa.kalender.data.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.kigawa.kalender.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SignInCancelledException : Exception("サインインがキャンセルされました")

class MsalAuthManager private constructor(
    private val app: IMultipleAccountPublicClientApplication,
) {
    companion object {
        suspend fun create(context: Context): MsalAuthManager =
            withTimeout(5000) { // 5秒タイムアウト
                suspendCancellableCoroutine { cont ->
                    PublicClientApplication.createMultipleAccountPublicClientApplication(
                        context.applicationContext,
                        R.raw.msal_config,
                        object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                            override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                                if (cont.isActive) cont.resume(MsalAuthManager(application))
                            }

                            override fun onError(exception: MsalException) {
                                if (cont.isActive) cont.resumeWithException(exception)
                            }
                        }
                    )
                }
            }
    }

    suspend fun acquireToken(activity: Activity): String =
        suspendCancellableCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(listOf("Calendars.Read"))
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        if (cont.isActive) cont.resume(result.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        if (cont.isActive) cont.resumeWithException(SignInCancelledException())
                    }
                })
                .build()
            app.acquireToken(params)
        }

    suspend fun acquireTokenSilent(account: IAccount): String =
        suspendCancellableCoroutine { cont ->
            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(listOf("Calendars.Read"))
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        if (cont.isActive) cont.resume(result.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()
            app.acquireTokenSilentAsync(params)
        }

    suspend fun getAccounts(): List<IAccount> =
        suspendCancellableCoroutine { cont ->
            app.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
                override fun onTaskCompleted(result: List<IAccount>) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onError(exception: MsalException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
        }

    suspend fun removeAccount(account: IAccount): Unit =
        suspendCancellableCoroutine { cont ->
            app.removeAccount(
                account,
                object : IMultipleAccountPublicClientApplication.RemoveAccountCallback {
                    override fun onRemoved() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(exception: MsalException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                }
            )
        }
}
