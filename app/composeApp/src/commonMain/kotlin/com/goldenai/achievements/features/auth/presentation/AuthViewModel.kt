package com.goldenai.achievements.features.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.achievements.domain.AchievementValidation
import kotlinx.coroutines.launch

class AuthViewModel(val register: Boolean) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun submit(onSuccess: () -> Unit) {
        if (loading) return
        error = AchievementValidation.emailError(email)
            ?: if (register) {
                AchievementValidation.passwordError(password)
                    ?: if (password != confirmPassword) "Passwords do not match" else null
            } else {
                null
            }
        if (error != null) return

        loading = true
        viewModelScope.launch {
            val result =
                if (register) AppGraph.auth.register(email, password)
                else AppGraph.auth.signIn(email, password)
            loading = false
            when (result) {
                is AppResult.Ok -> onSuccess()
                is AppResult.Err -> error = result.message
            }
        }
    }
}
