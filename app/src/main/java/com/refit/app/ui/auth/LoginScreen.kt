package com.refit.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.refit.app.ui.components.*

@Composable
fun LoginScreen(state: AuthUiState, authError: String?, onSignIn: (String, String) -> Unit,
                onSignUp: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Page("손목 운동을\n함께 이어가요") {
        DemoNotice()
        Panel {
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("이메일") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.isSubmitting)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.isSubmitting)
            val message = authError ?: state.message
            if (!message.isNullOrBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            PrimaryAction(if (state.isSubmitting) "로그인 처리 중…" else "로그인", { onSignIn(email, password) }, !state.isSubmitting)
            SecondaryAction("회원가입", { onSignUp(email, password) }, !state.isSubmitting)
        }
    }
}
