package net.kigawa.kalender.data.auth

import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MsalAuthManagerTest {

    @Test
    fun `when_acquireTokenSilent_called_then_uses_account_authority_not_default`() = runTest {
        val mockApp = mockk<IMultipleAccountPublicClientApplication>()
        val mockAccount = mockk<IAccount>()
        val mockResult = mockk<IAuthenticationResult>()

        val accountAuthority = "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad"
        val expectedToken = "test_access_token"

        every { mockAccount.authority } returns accountAuthority
        every { mockResult.accessToken } returns expectedToken

        val paramsSlot = slot<AcquireTokenSilentParameters>()
        every { mockApp.acquireTokenSilentAsync(capture(paramsSlot)) } answers {
            paramsSlot.captured.callback.onSuccess(mockResult)
        }

        val manager = MsalAuthManager(mockApp)
        val token = manager.acquireTokenSilent(mockAccount)

        assertEquals(expectedToken, token)
        assertEquals(accountAuthority, paramsSlot.captured.authority)
    }
}
