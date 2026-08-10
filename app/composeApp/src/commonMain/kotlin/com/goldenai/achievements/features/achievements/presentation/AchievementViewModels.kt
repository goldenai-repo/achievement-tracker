package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.api.AchievementApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(repo: AchievementRepository) : ViewModel() {
    val summary = repo.summary
    val counts: StateFlow<Map<String, Long>> = repo.watchCountsByType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val recent: StateFlow<List<Achievement>> = repo.watchRecent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.refresh() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementListViewModel(
    repo: AchievementRepository,
    initialType: String?,
) : ViewModel() {
    private val _selectedType = MutableStateFlow(initialType)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    val items: StateFlow<List<Achievement>> = _selectedType
        .flatMapLatest { type -> if (type == null) repo.watchAll() else repo.watchByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.refresh() }
    }

    fun selectType(typeKey: String?) {
        _selectedType.value = typeKey
    }
}

class CheckInFormViewModel(
    private val repo: AchievementRepository,
    private val api: AchievementApi,
) : ViewModel() {
    var admin1Mode by mutableStateOf(false)
    var countryQuery by mutableStateOf("")
    var regionQuery by mutableStateOf("")
    var countries by mutableStateOf<List<CatalogPlace>>(emptyList())
        private set
    var regions by mutableStateOf<List<CatalogPlace>>(emptyList())
        private set
    var selectedCountry by mutableStateOf<CatalogPlace?>(null)
        private set
    var selectedPlace by mutableStateOf<CatalogPlace?>(null)
        private set
    var timestamp by mutableStateOf(nowEpochMillis())
    var notes by mutableStateOf("")
    var searching by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun searchCountries() {
        search { api.searchCatalog("country", countryQuery.takeIf { it.isNotBlank() }, limit = 25) }
    }

    fun chooseCountry(place: CatalogPlace) {
        selectedCountry = place
        selectedPlace = if (admin1Mode) null else place
        countries = emptyList()
        regions = emptyList()
        regionQuery = ""
    }

    fun searchRegions() {
        val parent = selectedCountry?.id ?: run {
            error = "Choose a country before searching regions."
            return
        }
        search { api.searchCatalog("admin1", regionQuery.takeIf { it.isNotBlank() }, parentId = parent, limit = 25) }
    }

    fun chooseRegion(place: CatalogPlace) {
        selectedPlace = place
        regions = emptyList()
    }

    fun setMode(admin1: Boolean) {
        admin1Mode = admin1
        selectedPlace = null
        selectedCountry = null
        countries = emptyList()
        regions = emptyList()
        error = null
    }

    fun submit(onSuccess: () -> Unit) {
        val place = selectedPlace ?: run {
            error = "Choose a place before checking in."
            return
        }
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                when (val result = repo.create(place, timestamp, notes)) {
                    is AppResult.Ok -> onSuccess()
                    is AppResult.Err -> error = result.message
                }
            } catch (t: Throwable) {
                error = t.message ?: "Could not create check-in."
            } finally {
                saving = false
            }
        }
    }

    private fun search(block: suspend () -> List<CatalogPlace>) {
        if (searching) return
        searching = true
        error = null
        viewModelScope.launch {
            try {
                val results = block()
                if (admin1Mode && selectedCountry != null) regions = results else countries = results
            } catch (t: Throwable) {
                error = t.message ?: "Could not load places."
            } finally {
                searching = false
            }
        }
    }
}
