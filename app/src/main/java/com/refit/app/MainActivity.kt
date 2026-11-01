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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.ui.components.Page
import com.refit.app.ui.components.PrimaryAction
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.refit.app.auth.AuthState
import com.refit.app.ui.auth.AuthViewModel
import com.refit.app.ui.auth.LoginScreen
import com.refit.app.ui.history.HistoryScreen
import com.refit.app.ui.home.HomeScreen
import com.refit.app.ui.session.SessionScreen
import com.refit.app.ui.session.SessionViewModel
import com.refit.app.ui.session.SessionViewModelFactory
import com.refit.app.ui.session.GuideSettingsScreen
import com.refit.app.ui.components.ReFitTheme
import androidx.activity.compose.BackHandler
import com.refit.app.ui.fitting.FittingDemoScreen
import com.refit.app.ui.fitting.BraceSetupStore

private const val LOCAL_DEMO_OWNER = "__offline_demo_v1__"

private enum class AppPage {
    HOME,
    SESSION,
    HISTORY,
    FITTING
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.refit.app.data.remote.SupabaseClientProvider.initialize(applicationContext)

        setContent {
            ReFitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReFitRoot()
                }
            }
        }
    }
}

@Composable
private fun ReFitRoot() {
    // A local demonstration has its own owner and never creates an auth session.
    // With no backend configuration, the remote data source also has no client.
    if (!com.refit.app.data.remote.SupabaseClientProvider.isConfigured) {
        val demoOwner = requireNotNull(LocalViewModelStoreOwner.current)
        key(LOCAL_DEMO_OWNER) {
            CheckedAuthenticatedApp(
                auth = AuthState.Authenticated(LOCAL_DEMO_OWNER, null),
                accountOwner = demoOwner,
                onSignOut = null
            )
        }
        return
    }

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
                CheckedAuthenticatedApp(
                    auth = state,
                    accountOwner = authViewModel.accountViewModelStoreOwner,
                    onSignOut = authViewModel::signOut
                )
            }
        }
    }
}

@Composable
private fun CheckedAuthenticatedApp(auth: AuthState.Authenticated, accountOwner: ViewModelStoreOwner, onSignOut: (() -> Unit)?) {
    val context = LocalContext.current.applicationContext
    val storage by produceState<Result<Unit>?>(null, auth.userId) {
        value = withContext(Dispatchers.IO) {
            runCatching { ReFitDatabase.getInstance(context).openHelper.writableDatabase; Unit }
        }
    }
    when {
        storage == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        storage!!.isFailure -> Page("저장된 기록을 열 수 없어요") {
            Text("기록은 지우지 않았습니다. 앱을 삭제하지 말고 관리자에게 문의해 주세요.")
            onSignOut?.let { PrimaryAction("로그아웃", it) }
        }
        else -> AuthenticatedApp(auth, accountOwner, onSignOut)
    }
}

@Composable
private fun AuthenticatedApp(
    auth: AuthState.Authenticated,
    accountOwner: ViewModelStoreOwner,
    onSignOut: (() -> Unit)?
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val braceStore = remember(application) { BraceSetupStore(application) }
    var setupReload by remember(auth.userId) { mutableStateOf(0) }
    var registeredDemoSide by remember(auth.userId) { mutableStateOf<String?>(null) }
    val setupLoad by produceState<Result<String?>?>(null, auth.userId, setupReload) {
        value = null
        val loaded = withContext(Dispatchers.IO) { runCatching { braceStore.readDemoSide(auth.userId) } }
        registeredDemoSide = loaded.getOrNull()
        value = loaded
    }
    if (setupLoad == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (setupLoad!!.isFailure) {
        Page("보조기 등록 정보를 열 수 없어요") {
            Text("운동을 시작하기 전에 보조기 등록 정보를 확인해야 합니다.")
            PrimaryAction("다시 확인", { setupReload++ })
        }
        return
    }

    var pageName by rememberSaveable(auth.userId) {
        mutableStateOf(if (registeredDemoSide == null) AppPage.FITTING.name else AppPage.HOME.name)
    }
    // A restored HOME/SESSION route cannot bypass first-time brace setup.
    val page = if (registeredDemoSide == null) AppPage.FITTING else AppPage.valueOf(pageName)

    val sessionViewModel: SessionViewModel = viewModel(
        viewModelStoreOwner = accountOwner,
        key = "session-${auth.userId}",
        factory = SessionViewModelFactory(
            application = application,
            userId = auth.userId
        )
    )
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()

    var showGuideSettings by rememberSaveable(auth.userId) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { sessionViewModel.setPaused(true) }
    BackHandler(enabled = page == AppPage.HISTORY) { pageName = AppPage.HOME.name }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        sessionViewModel.retrySync()
    }
    if (showGuideSettings) {
        GuideSettingsScreen(sessionState.guideTiming, { showGuideSettings = false }, { a, r, n ->
            if (sessionViewModel.configureGuidance(a, r, n)) showGuideSettings = false
        })
        return
    }

    when (page) {
        AppPage.FITTING -> FittingDemoScreen(
            setupRequired = registeredDemoSide == null,
            onRegisterDemo = { side ->
                val saved = withContext(Dispatchers.IO) { runCatching { braceStore.registerDemo(auth.userId, side) } }
                if (saved.isSuccess) {
                    registeredDemoSide = side
                    pageName = AppPage.HOME.name
                }
                saved.isSuccess
            },
            onHome = { pageName = AppPage.HOME.name },
            onOpenHistory = { pageName = AppPage.HISTORY.name },
            onBack = { pageName = AppPage.HOME.name }
        )
        AppPage.HOME -> {
            HomeScreen(
                email = auth.email,
                sessions = sessionState.history,
                wristSide = registeredDemoSide ?: sessionState.wristSide,
                braceSide = registeredDemoSide,
                guideDescription = sessionState.guideTiming.description,
                onConfigure = { showGuideSettings = true },
                onOpenFitting = { pageName = AppPage.FITTING.name },
                sessionRefreshPending = auth.sessionRefreshPending,
                onStartExercise = {
                    registeredDemoSide?.let(sessionViewModel::configureWristSide)
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
                cloudSyncEnabled = onSignOut != null,
                onOpenFitting = { pageName = AppPage.FITTING.name },
                onBack = {
                    pageName = AppPage.HOME.name
                }
            )
        }
    }
}

