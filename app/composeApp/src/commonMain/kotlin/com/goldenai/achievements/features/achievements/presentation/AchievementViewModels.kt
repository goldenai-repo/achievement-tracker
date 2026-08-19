package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.model.AchievementType
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.achievements.domain.AchievementValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(repo: AchievementRepository) : ViewModel() {
    val counts: StateFlow<Map<String, Long>> = repo.watchCountsByType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val recent: StateFlow<List<Achievement>> = repo.watchRecent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementListViewModel(
    repo: AchievementRepository,
    private val category: String?,
) : ViewModel() {
    val filterTypes: List<AchievementType> =
        if (category == null) AchievementType.entries
        else AchievementType.typesForCategory(category)

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    val items: StateFlow<List<Achievement>> = _selectedType
        .flatMapLatest { type ->
            when {
                type != null -> repo.watchByType(type)
                category != null -> repo.watchByTypes(filterTypes.map { it.key })
                else -> repo.watchAll()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectType(typeKey: String?) {
        _selectedType.value = typeKey
    }
}

class AchievementFormViewModel(
    private val repo: AchievementRepository,
    private val editId: String?,
    initialCategory: String? = null,
) : ViewModel() {

    var typeKey by mutableStateOf(
        if (editId == null) AchievementType.keysForCategory(initialCategory ?: "").firstOrNull() else null,
    )
    var content by mutableStateOf("")
    var locationName by mutableStateOf("")
    var notes by mutableStateOf("")
    var timestamp by mutableStateOf(nowEpochMillis())
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(editId == null)
        private set

    val isEdit: Boolean get() = editId != null
    val isValid: Boolean get() = AchievementValidation.isValid(typeKey, content)

    private var existing: Achievement? = null

    init {
        if (editId != null) {
            viewModelScope.launch {
                repo.get(editId)?.let { a ->
                    existing = a
                    typeKey = a.type
                    content = a.content
                    locationName = a.locationName.orEmpty()
                    notes = a.notes.orEmpty()
                    timestamp = a.timestamp
                }
                loaded = true
            }
        }
    }

    fun save(onSuccess: (listCategory: String?) -> Unit) {
        val type = typeKey ?: return
        if (!isValid || saving) return
        saving = true
        error = null
        viewModelScope.launch {
            val result = existing?.let { a ->
                repo.update(
                    a.copy(
                        type = type,
                        content = content,
                        locationName = locationName,
                        notes = notes,
                        timestamp = timestamp,
                    )
                )
            } ?: repo.create(
                type = type,
                content = content,
                timestamp = timestamp,
                locationName = locationName,
                notes = notes,
            )
            saving = false
            when (result) {
                is AppResult.Ok -> onSuccess(AchievementType.listCategoryFor(type))
                is AppResult.Err -> error = result.message
            }
        }
    }

    fun delete(onSuccess: () -> Unit) {
        val id = editId ?: return
        viewModelScope.launch {
            when (val result = repo.delete(id)) {
                is AppResult.Ok -> onSuccess()
                is AppResult.Err -> error = result.message
            }
        }
    }
}
