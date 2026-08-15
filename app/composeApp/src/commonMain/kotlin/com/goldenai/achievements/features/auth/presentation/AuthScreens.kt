package com.goldenai.achievements.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SignInScreen(onDone: () -> Unit, onSwitchToRegister: () -> Unit, onBack: () -> Unit) {
    val vm: AuthViewModel = viewModel(key = "signin") { AuthViewModel(register = false) }
    AuthForm(
        title = "Sign in",
        submitLabel = "Sign in",
        switchLabel = "New here? Create an account",
        vm = vm,
        onDone = onDone,
        onSwitch = onSwitchToRegister,
        onBack = onBack,
    )
}

@Composable
fun RegisterScreen(onDone: () -> Unit, onSwitchToSignIn: () -> Unit, onBack: () -> Unit) {
    val vm: AuthViewModel = viewModel(key = "register") { AuthViewModel(register = true) }
    AuthForm(
        title = "Create account",
        submitLabel = "Create account",
        switchLabel = "Already have an account? Sign in",
        vm = vm,
        onDone = onDone,
        onSwitch = onSwitchToSignIn,
        onBack = onBack,
    )
}

@Composable
private fun AuthForm(
    title: String,
    submitLabel: String,
    switchLabel: String,
    vm: AuthViewModel,
    onDone: () -> Unit,
    onSwitch: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (vm.register) {
            Text(
                "Your achievements stay on this device and are uploaded to your new " +
                    "account after you sign up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (vm.register) {
            OutlinedTextField(
                value = vm.confirmPassword,
                onValueChange = { vm.confirmPassword = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { vm.submit(onSuccess = onDone) },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.loading) "Please wait…" else submitLabel)
        }
        TextButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth()) {
            Text(switchLabel)
        }
        Spacer(Modifier.weight(1f))
    }
}
