package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.api.CheckInSelection
import com.goldenai.achievements.features.api.AchievementApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val repo: AchievementRepository,
    initialType: String?,
) : ViewModel() {
    private val _selectedType = MutableStateFlow(initialType)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    var deletingId by mutableStateOf<String?>(null)
        private set
    var deletingBatch by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set
    var updatingId by mutableStateOf<String?>(null)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set

    val items: StateFlow<List<Achievement>> = _selectedType
        .flatMapLatest { filter ->
            repo.watchAll().map { achievements -> filterAchievements(achievements, filter) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupedItems: StateFlow<List<AchievementGroup>> = items
        .let { flow ->
            flow.map { achievements ->
                achievements.groupBy { it.entityId }.values.map { visits ->
                    AchievementGroup(visits.sortedByDescending { it.timestamp })
                }.sortedByDescending { it.lastVisitedAt }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val geographyDirectory: StateFlow<List<GeographyCountryGroup>> = items
        .map(::buildGeographyDirectory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.refresh() }
    }

    fun selectType(typeKey: String?) {
        _selectedType.value = typeKey
    }

    fun deleteVisit(id: String, password: String?, onSuccess: () -> Unit) {
        if (deletingId != null || deletingBatch) return
        deletingId = id
        deleteError = null
        viewModelScope.launch {
            try {
                if (AppGraph.auth.currentUser != null) {
                    when (val authResult = AppGraph.auth.reauthenticate(password.orEmpty())) {
                        is AppResult.Ok -> Unit
                        is AppResult.Err -> {
                            deleteError = authResult.message
                            return@launch
                        }
                    }
                }
                when (val result = repo.delete(id)) {
                    is AppResult.Ok -> onSuccess()
                    is AppResult.Err -> deleteError = result.message
                }
            } catch (t: Throwable) {
                deleteError = t.message ?: "Could not delete check-in."
            } finally {
                deletingId = null
            }
        }
    }

    /** Re-authenticates once, then removes all selected visits. */
    fun deleteVisits(ids: List<String>, password: String?, onSuccess: () -> Unit) {
        if (ids.isEmpty() || deletingId != null || deletingBatch) return
        deletingBatch = true
        deleteError = null
        viewModelScope.launch {
            try {
                if (AppGraph.auth.currentUser != null) {
                    when (val authResult = AppGraph.auth.reauthenticate(password.orEmpty())) {
                        is AppResult.Ok -> Unit
                        is AppResult.Err -> {
                            deleteError = authResult.message
                            return@launch
                        }
                    }
                }

                ids.forEach { id ->
                    when (val result = repo.delete(id)) {
                        is AppResult.Ok -> Unit
                        is AppResult.Err -> {
                            deleteError = result.message
                            return@launch
                        }
                    }
                }
                onSuccess()
            } catch (t: Throwable) {
                deleteError = t.message ?: "Could not delete selected check-ins."
            } finally {
                deletingBatch = false
            }
        }
    }

    fun updateVisit(id: String, timestamp: Long, notes: String?, onSuccess: () -> Unit) {
        if (updatingId != null || deletingId != null || deletingBatch) return
        updatingId = id
        updateError = null
        viewModelScope.launch {
            try {
                when (val result = repo.update(id, timestamp, notes)) {
                    is AppResult.Ok -> onSuccess()
                    is AppResult.Err -> updateError = result.message
                }
            } catch (t: Throwable) {
                updateError = t.message ?: "Could not update check-in."
            } finally {
                updatingId = null
            }
        }
    }
}

data class AchievementGroup(
    val visits: List<Achievement>,
) {
    val place: Achievement get() = visits.first()
    val visitCount: Int get() = visits.size
    val firstVisitedAt: Long get() = visits.minOf { it.timestamp }
    val lastVisitedAt: Long get() = visits.maxOf { it.timestamp }
}

/** A country-level node containing its direct visits and visited admin-1 children. */
data class GeographyCountryGroup(
    val countryId: String,
    val countryName: String,
    val countryVisits: AchievementGroup?,
    val regions: List<AchievementGroup>,
) {
    val countryCode: String
        get() = countryId
            .removePrefix("country:")
            .substringBefore('.')
            .substringBefore('-')
            .uppercase()

    val visitCount: Int
        get() = (countryVisits?.visitCount ?: 0) + regions.sumOf { it.visitCount }

    val lastVisitedAt: Long
        get() = listOfNotNull(countryVisits?.lastVisitedAt, regions.maxOfOrNull { it.lastVisitedAt })
            .maxOrNull() ?: 0L
}

private fun filterAchievements(achievements: List<Achievement>, filter: String?): List<Achievement> =
    when {
        filter == null -> achievements
        filter == "geography" -> achievements.filter { it.entityKind == "country" || it.entityKind == "admin1" }
        filter.contains('.') -> achievements.filter { it.type == filter }
        else -> achievements.filter { it.type.startsWith("$filter.") }
    }

private fun buildGeographyDirectory(achievements: List<Achievement>): List<GeographyCountryGroup> {
    val geography = achievements.filter { it.entityKind == "country" || it.entityKind == "admin1" }
    val groupedByEntity = geography.groupBy { it.entityId }
        .mapValues { (_, visits) -> AchievementGroup(visits.sortedByDescending { it.timestamp }) }

    return geography.groupBy { countryIdFor(it) }
        .map { (countryId, visits) ->
            val directCountry = visits.firstOrNull { it.entityKind == "country" }
            val regions = groupedByEntity.values
                .filter { it.place.entityKind == "admin1" && countryIdFor(it.place) == countryId }
                .sortedByDescending { it.lastVisitedAt }
            GeographyCountryGroup(
                countryId = countryId,
                countryName = directCountry?.locationName
                    ?: countryId.removePrefix("country:").uppercase(),
                countryVisits = directCountry?.let { groupedByEntity[it.entityId] },
                regions = regions,
            )
        }
        .sortedByDescending { it.lastVisitedAt }
}

private fun countryIdFor(achievement: Achievement): String =
    if (achievement.entityKind == "admin1") {
        achievement.parentId ?: "country:${achievement.entityCode.substringBefore('.')}"
    } else {
        achievement.entityId
    }

class CheckInFormViewModel(
    private val repo: AchievementRepository,
    private val api: AchievementApi,
    initialSelection: CheckInSelection? = null,
) : ViewModel() {
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
    var countryHasRegions by mutableStateOf(false)
        private set
    var countryDropdownOpen by mutableStateOf(false)
        private set
    var regionDropdownOpen by mutableStateOf(false)
        private set
    var countryLoading by mutableStateOf(false)
        private set
    var regionLoading by mutableStateOf(false)
        private set
    var timestamp by mutableStateOf(nowEpochMillis())
    var notes by mutableStateOf("")
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var countrySearchJob: Job? = null
    private var regionSearchJob: Job? = null

    init {
        initialSelection?.let { selection ->
            selectedCountry = selection.country
            countryQuery = selection.country.name
            if (selection.isCountrySelection) {
                // Country markers summarize visited regions, but the actual
                // check-in must still resolve to an admin-1 place.
                selectedPlace = null
                countryHasRegions = true
                startRegionSearch(null) {
                    countryHasRegions = regions.isNotEmpty()
                    if (!countryHasRegions) selectedPlace = selection.country
                }
            } else {
                selectedPlace = selection.place
                countryHasRegions = selection.place.kind == "admin1"
                regionQuery = selection.place.name
            }
        }
    }

    fun onCountryFocused() {
        countryDropdownOpen = true
        if (countries.isEmpty()) startCountrySearch(null)
    }

    fun updateCountryQuery(value: String) {
        countryQuery = value
        selectedCountry = null
        selectedPlace = null
        countryDropdownOpen = true
        startCountrySearch(value.takeIf { it.isNotBlank() }, debounce = true)
    }

    fun clearCountryQuery() {
        countryQuery = ""
        selectedCountry = null
        selectedPlace = null
        countries = emptyList()
        regions = emptyList()
        countryHasRegions = false
        countryDropdownOpen = true
        startCountrySearch(null)
    }

    fun chooseCountry(place: CatalogPlace) {
        selectedCountry = place
        selectedPlace = null
        countryQuery = place.name
        countries = emptyList()
        regions = emptyList()
        regionQuery = ""
        countryHasRegions = false
        countryDropdownOpen = false
        regionDropdownOpen = false
        error = null
        startRegionSearch(null) {
            countryHasRegions = regions.isNotEmpty()
            if (!countryHasRegions) selectedPlace = place
        }
    }

    fun onRegionFocused() {
        regionDropdownOpen = true
        if (regions.isEmpty()) startRegionSearch(null)
    }

    fun updateRegionQuery(value: String) {
        regionQuery = value
        selectedPlace = null
        regionDropdownOpen = true
        startRegionSearch(value.takeIf { it.isNotBlank() }, debounce = true)
    }

    fun clearRegionQuery() {
        regionQuery = ""
        selectedPlace = null
        regions = emptyList()
        regionDropdownOpen = true
        startRegionSearch(null)
    }

    fun dismissDropdowns() {
        countryDropdownOpen = false
        regionDropdownOpen = false
    }

    fun chooseRegion(place: CatalogPlace) {
        selectedPlace = place
        regionQuery = place.name
        regions = emptyList()
        regionDropdownOpen = false
        error = null
    }

    fun changeCountry() {
        countrySearchJob?.cancel()
        regionSearchJob?.cancel()
        selectedPlace = null
        selectedCountry = null
        countries = emptyList()
        regions = emptyList()
        countryHasRegions = false
        countryQuery = ""
        regionQuery = ""
        countryDropdownOpen = false
        regionDropdownOpen = false
        countryLoading = false
        regionLoading = false
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
                if (countryHasRegions && place.kind != "admin1") {
                    error = "Select a state or province for this country."
                    return@launch
                }
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

    private fun startCountrySearch(query: String?, debounce: Boolean = false) {
        countrySearchJob?.cancel()
        countrySearchJob = viewModelScope.launch {
            if (debounce) delay(250)
            countryLoading = true
            error = null
            try {
                countries = api.searchCatalog("country", query, limit = 500)
            } catch (t: Throwable) {
                error = t.message ?: "Could not load places."
            } finally {
                countryLoading = false
            }
        }
    }

    private fun startRegionSearch(
        query: String?,
        debounce: Boolean = false,
        onLoaded: (() -> Unit)? = null,
    ) {
        val parentId = selectedCountry?.id ?: return
        regionSearchJob?.cancel()
        regionSearchJob = viewModelScope.launch {
            if (debounce) delay(250)
            regionLoading = true
            error = null
            try {
                regions = api.searchCatalog("admin1", query, parentId = parentId, limit = 500)
                onLoaded?.invoke()
            } catch (t: Throwable) {
                error = t.message ?: "Could not load regions."
            } finally {
                regionLoading = false
            }
        }
    }
}
