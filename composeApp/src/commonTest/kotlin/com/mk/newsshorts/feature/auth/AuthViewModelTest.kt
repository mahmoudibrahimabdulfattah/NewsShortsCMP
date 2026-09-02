package com.mk.newsshorts.feature.auth

import com.mk.newsshorts.core.model.auth.AuthFailure
import com.mk.newsshorts.core.model.auth.AuthResult
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.core.domain.auth.DefaultAuthSession
import com.mk.newsshorts.core.data.local.PendingSignInEmailPersistence
import com.mk.newsshorts.core.domain.use_case.DeleteAccountUseCase
import com.mk.newsshorts.navigation.SignInLinkBus
import com.mk.newsshorts.core.model.sync.SyncDelete
import com.mk.newsshorts.testing.AuthCall
import com.mk.newsshorts.testing.FakeAuthClient
import com.mk.newsshorts.testing.FakeRemoteSyncClient
import com.mk.newsshorts.testing.RemoteSyncCall
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @Test
    fun `auth session changes reach auth state`() = runTest {
        val fixture = fixture()
        val reader = authUser("reader-1")

        fixture.authClient.setUser(reader)
        runCurrent()

        assertEquals(reader, fixture.viewModel.uiState.value.authUser)

        fixture.authClient.setUser(null)
        runCurrent()

        assertNull(fixture.viewModel.uiState.value.authUser)
    }

    @Test
    fun `google sign-in exposes the user and asks the shell to close`() = runTest {
        val fixture = fixture()

        fixture.viewModel.processEvent(AuthUiEvent.SignInWithGoogle)
        runCurrent()
        runCurrent()

        assertEquals(listOf<AuthCall>(AuthCall.SignInWithGoogle), fixture.authClient.calls)
        assertEquals(fixture.authClient.userAfterSignIn, fixture.viewModel.uiState.value.authUser)
        assertFalse(fixture.viewModel.uiState.value.authInProgress)
        assertNull(fixture.viewModel.uiState.value.authError)
        assertEquals(listOf(AuthUiEffect.CloseOverlay), fixture.viewModel.readEffects(1))
    }

    @Test
    fun `invalid email is rejected before sending a link`() = runTest {
        val fixture = fixture()

        fixture.viewModel.processEvent(AuthUiEvent.SendSignInLink("not-an-address"))

        assertEquals(AuthFailure.INVALID_EMAIL, fixture.viewModel.uiState.value.authError)
        assertEquals(emptyList<AuthCall>(), fixture.authClient.calls)
        assertNull(fixture.pendingEmailStore.email)
    }

    @Test
    fun `email link send stores the trimmed address and app language`() = runTest {
        val fixture = fixture(appLocaleCode = "ar")

        fixture.viewModel.processEvent(AuthUiEvent.SendSignInLink("  reader@example.com  "))
        runCurrent()

        assertEquals(
            listOf<AuthCall>(AuthCall.SendSignInLink("reader@example.com", "ar")),
            fixture.authClient.calls,
        )
        assertEquals("reader@example.com", fixture.pendingEmailStore.email)
        assertEquals("reader@example.com", fixture.viewModel.uiState.value.pendingSignInEmail)
        assertFalse(fixture.viewModel.uiState.value.authInProgress)
    }

    @Test
    fun `email link send failure stays on the sign-in screen`() = runTest {
        val fixture = fixture()
        fixture.authClient.sendSignInLinkResult = AuthResult.Error(AuthFailure.EMAIL_QUOTA_EXCEEDED)

        fixture.viewModel.processEvent(AuthUiEvent.SendSignInLink("reader@example.com"))
        runCurrent()

        assertEquals(AuthFailure.EMAIL_QUOTA_EXCEEDED, fixture.viewModel.uiState.value.authError)
        assertFalse(fixture.viewModel.uiState.value.authInProgress)
        assertNull(fixture.viewModel.uiState.value.pendingSignInEmail)
        assertNull(fixture.pendingEmailStore.email)
    }

    @Test
    fun `cold-start sign-in link with stored email signs in and clears the bus`() = runTest {
        val link = "https://example.com/sign-in?code=abc"
        val signInLinkBus = SignInLinkBus().apply { post(link) }
        val fixture = fixture(
            signInLinkBus = signInLinkBus,
            pendingEmail = "reader@example.com",
            configureAuthClient = { isSignInLinkResult = true },
        )

        runCurrent()
        runCurrent()

        assertEquals(
            listOf(
                AuthCall.IsSignInLink(link),
                AuthCall.CompleteSignInWithLink("reader@example.com", link),
            ),
            fixture.authClient.calls,
        )
        assertEquals(fixture.authClient.userAfterSignIn, fixture.viewModel.uiState.value.authUser)
        assertNull(fixture.pendingEmailStore.email)
        assertNull(fixture.signInLinkBus.pending.value)
        assertNull(fixture.viewModel.uiState.value.pendingSignInEmail)
        assertNull(fixture.viewModel.uiState.value.unclaimedSignInLink)
        assertEquals(listOf(AuthUiEffect.CloseOverlay), fixture.viewModel.readEffects(1))
    }

    @Test
    fun `posted link without stored email asks the shell to open sign-in`() = runTest {
        val link = "https://example.com/sign-in?code=abc"
        val fixture = fixture()
        fixture.authClient.isSignInLinkResult = true

        fixture.signInLinkBus.post(link)
        runCurrent()
        runCurrent()

        assertEquals(listOf<AuthCall>(AuthCall.IsSignInLink(link)), fixture.authClient.calls)
        assertEquals(link, fixture.viewModel.uiState.value.unclaimedSignInLink)
        assertNull(fixture.signInLinkBus.pending.value)
        assertEquals(listOf(AuthUiEffect.OpenSignInOverlay), fixture.viewModel.readEffects(1))
    }

    @Test
    fun `supplying the email for an unclaimed link completes sign-in`() = runTest {
        val link = "https://example.com/sign-in?code=abc"
        val fixture = fixture()
        fixture.authClient.isSignInLinkResult = true
        fixture.viewModel.processEvent(AuthUiEvent.SignInLinkOpened(link))
        runCurrent()

        fixture.viewModel.processEvent(AuthUiEvent.SupplyLinkEmail("  reader@example.com  "))
        runCurrent()
        runCurrent()

        assertEquals(
            listOf(
                AuthCall.IsSignInLink(link),
                AuthCall.CompleteSignInWithLink("reader@example.com", link),
            ),
            fixture.authClient.calls,
        )
        assertEquals(fixture.authClient.userAfterSignIn, fixture.viewModel.uiState.value.authUser)
        assertNull(fixture.viewModel.uiState.value.unclaimedSignInLink)
        assertEquals(
            listOf(AuthUiEffect.OpenSignInOverlay, AuthUiEffect.CloseOverlay),
            fixture.viewModel.readEffects(2),
        )
    }

    @Test
    fun `cancel pending sign-in clears the stored email link state and error`() = runTest {
        val fixture = fixture()
        fixture.authClient.isSignInLinkResult = true
        fixture.viewModel.processEvent(AuthUiEvent.SignInLinkOpened("https://example.com/link"))
        runCurrent()
        fixture.viewModel.processEvent(AuthUiEvent.SupplyLinkEmail("bad"))

        fixture.viewModel.processEvent(AuthUiEvent.CancelPendingSignInLink)

        assertNull(fixture.pendingEmailStore.email)
        assertNull(fixture.viewModel.uiState.value.pendingSignInEmail)
        assertNull(fixture.viewModel.uiState.value.unclaimedSignInLink)
        assertNull(fixture.viewModel.uiState.value.authError)
    }

    @Test
    fun `sign out clears the user without touching local reader data`() = runTest {
        val fixture = fixture()
        fixture.authClient.setUser(authUser("reader-1"))
        runCurrent()

        fixture.viewModel.processEvent(AuthUiEvent.SignOut)
        runCurrent()
        runCurrent()

        assertEquals(listOf<AuthCall>(AuthCall.SignOut), fixture.authClient.calls)
        assertNull(fixture.viewModel.uiState.value.authUser)
    }

    @Test
    fun `delete account uses the signed-in uid and closes on success`() = runTest {
        val fixture = fixture()
        fixture.authClient.setUser(authUser("reader-1"))
        runCurrent()

        fixture.viewModel.processEvent(AuthUiEvent.DeleteAccount)
        runCurrent()
        runCurrent()

        assertEquals(listOf<RemoteSyncCall>(RemoteSyncCall.DeleteUserData("reader-1")), fixture.remoteSync.calls)
        assertEquals(listOf<AuthCall>(AuthCall.DeleteAccount), fixture.authClient.calls)
        assertNull(fixture.viewModel.uiState.value.authUser)
        assertFalse(fixture.viewModel.uiState.value.authInProgress)
        assertEquals(listOf(AuthUiEffect.CloseOverlay), fixture.viewModel.readEffects(1))
    }

    @Test
    fun `delete account failure keeps the user and exposes the auth error`() = runTest {
        val fixture = fixture()
        val reader = authUser("reader-1")
        fixture.authClient.setUser(reader)
        runCurrent()
        fixture.remoteSync.deleteUserDataResult = SyncDelete.Failed

        fixture.viewModel.processEvent(AuthUiEvent.DeleteAccount)
        runCurrent()

        assertEquals(listOf<RemoteSyncCall>(RemoteSyncCall.DeleteUserData("reader-1")), fixture.remoteSync.calls)
        assertEquals(emptyList<AuthCall>(), fixture.authClient.calls)
        assertEquals(reader, fixture.viewModel.uiState.value.authUser)
        assertEquals(
            AuthFailure.ACCOUNT_DATA_DELETE_FAILED,
            fixture.viewModel.uiState.value.authError,
        )
        assertFalse(fixture.viewModel.uiState.value.authInProgress)
        assertNoBufferedEffect(fixture.viewModel)
    }

    @Test
    fun `unsuccessful auth helper only changes failure state`() {
        val errorState = AuthUiState(authInProgress = true)
            .afterUnsuccessfulAuth(AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED))
        val cancelledState = AuthUiState(authInProgress = true)
            .afterUnsuccessfulAuth(AuthResult.Cancelled)

        assertFalse(errorState.authInProgress)
        assertEquals(AuthFailure.ACCOUNT_DATA_DELETE_FAILED, errorState.authError)
        assertFalse(cancelledState.authInProgress)
        assertNull(cancelledState.authError)
    }

    private fun TestScope.fixture(
        appLocaleCode: String = "en",
        signInLinkBus: SignInLinkBus = SignInLinkBus(),
        pendingEmail: String? = null,
        configureAuthClient: FakeAuthClient.() -> Unit = {},
    ): Fixture {
        val authClient = FakeAuthClient().apply(configureAuthClient)
        val remoteSync = FakeRemoteSyncClient()
        val pendingEmailStore = RecordingPendingSignInEmailStore(pendingEmail)
        val viewModel = AuthViewModel(
            authClient = authClient,
            authSession = DefaultAuthSession(authClient),
            pendingSignInEmailStore = pendingEmailStore,
            signInLinkBus = signInLinkBus,
            deleteAccountUseCase = DeleteAccountUseCase(authClient, remoteSync),
            appLocaleCode = { appLocaleCode },
            scopeOverride = backgroundScope,
        )
        runCurrent()
        runCurrent()
        return Fixture(
            viewModel = viewModel,
            authClient = authClient,
            remoteSync = remoteSync,
            pendingEmailStore = pendingEmailStore,
            signInLinkBus = signInLinkBus,
        )
    }

    private suspend fun AuthViewModel.readEffects(count: Int): List<AuthUiEffect> =
        uiEffect.take(count).toList()

    private suspend fun TestScope.assertNoBufferedEffect(viewModel: AuthViewModel) {
        val nextEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        assertTrue(nextEffect.isActive)
        nextEffect.cancelAndJoin()
    }

    private data class Fixture(
        val viewModel: AuthViewModel,
        val authClient: FakeAuthClient,
        val remoteSync: FakeRemoteSyncClient,
        val pendingEmailStore: RecordingPendingSignInEmailStore,
        val signInLinkBus: SignInLinkBus,
    )

    private class RecordingPendingSignInEmailStore(
        initialEmail: String?,
    ) : PendingSignInEmailPersistence {
        var email: String? = initialEmail

        override fun save(email: String) {
            this.email = email.trim()
        }

        override fun load(): String? = email

        override fun clear() {
            email = null
        }
    }

    private fun authUser(uid: String): AuthUser = AuthUser(
        uid = uid,
        displayName = "Reader",
        email = "$uid@example.com",
        photoUrl = null,
    )
}
