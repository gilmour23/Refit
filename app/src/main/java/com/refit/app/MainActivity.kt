package com.refit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.refit.app.ui.session.SessionScreen
import com.refit.app.ui.session.SessionViewModel
import com.refit.app.ui.theme.ReFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReFitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val sessionViewModel: SessionViewModel = viewModel()
                    SessionScreen(viewModel = sessionViewModel)
                }
            }
        }
    }
}
