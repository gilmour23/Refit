package com.refit.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.refit.app.auth.AuthState
import com.refit.app.ui.auth.AuthViewModel
import com.refit.app.ui.auth.LoginScreen
import com.refit.app.ui.history.HistoryScreen
import com.refit.app.ui.home.HomeScreen
import com.refit.app.ui.session.SessionScreen
import com.refit.app.ui.session.SessionViewModel
import com.refit.app.ui.session.SessionViewModelFactory

private enum class AppPage {
    HOME,
    SESSION,
    HISTORY
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReFitRoot()
                }
            }
        }
    }
}

@Composable
private fun ReFitRoot() {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        AuthState.Unauthenticated -> {
            LoginScreen(
                state = authUiState,
                authError = null,
                onSignIn = authViewModel::signIn,
                onSignUp = authViewModel::signUp
            )
        }

        is AuthState.Error -> {
            LoginScreen(
                state = authUiState,
                authError = state.message,
                onSignIn = authViewModel::signIn,
                onSignUp = authViewModel::signUp
            )
        }

        is AuthState.Authenticated -> {
            key(state.userId) {
                AuthenticatedApp(
                    auth = state,
                    accountOwner = authViewModel.accountViewModelStoreOwner,
                    onSignOut = authViewModel::signOut
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(
    auth: AuthState.Authenticated,
    accountOwner: ViewModelStoreOwner,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application

    var pageName by rememberSaveable(auth.userId) {
        mutableStateOf(AppPage.HOME.name)
    }
    val page = AppPage.valueOf(pageName)

    val sessionViewModel: SessionViewModel = viewModel(
        viewModelStoreOwner = accountOwner,
        key = "session-${auth.userId}",
        factory = SessionViewModelFactory(
            application = application,
            userId = auth.userId
        )
    )
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        sessionViewModel.retrySync()
    }

    when (page) {
        AppPage.HOME -> {
            HomeScreen(
                email = auth.email,
                sessionRefreshPending = auth.sessionRefreshPending,
                onStartExercise = {
                    pageName = AppPage.SESSION.name
                },
                onOpenHistory = {
                    pageName = AppPage.HISTORY.name
                },
                onSignOut = onSignOut
            )
        }

        AppPage.SESSION -> {
            SessionScreen(
                viewModel = sessionViewModel,
                onBackToHome = {
                    pageName = AppPage.HOME.name
                }
            )
        }

        AppPage.HISTORY -> {
            HistoryScreen(
                sessions = sessionState.history,
                isSyncing = sessionState.isSyncing,
                syncMessage = sessionState.syncMessage,
                onRetrySync = sessionViewModel::retrySync,
                onBack = {
                    pageName = AppPage.HOME.name
                }
            )
        }
    }
}
