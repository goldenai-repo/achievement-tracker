package com.goldenai.achievements.features.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.features.api.AchievementApi
import com.goldenai.achievements.features.api.CatalogPlace
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The hierarchy currently represented by the Explore map. */
enum class ExploreLevel {
    WORLD,
    COUNTRY,
    ADMIN1,
}

enum class ExploreStep {
    COUNTRY_PICKER,
    REGION_PICKER,
}

/**
 * UI state and catalog requests for the Explore hierarchy.
 *
 * The map remains a visual surface; catalog search is deliberately kept as
 * the reliable primary navigation mechanism for the first drill-down slice.
 */
class ExploreViewModel(
    private val api: AchievementApi,
) : ViewModel() {
    private var latestMapViewport: MapViewport? = null

    var step by mutableStateOf(ExploreStep.COUNTRY_PICKER)
        private set

    var level by mutableStateOf(ExploreLevel.WORLD)
        private set

    var countryQuery by mutableStateOf("")
    var regionQuery by mutableStateOf("")

    var countries by mutableStateOf<List<CatalogPlace>>(emptyList())
        private set

    var regions by mutableStateOf<List<CatalogPlace>>(emptyList())
        private set

    var selectedCountry by mutableStateOf<CatalogPlace?>(null)
        private set

    var selectedRegion by mutableStateOf<CatalogPlace?>(null)
        private set

    var countryLoading by mutableStateOf(false)
        private set

    var regionLoading by mutableStateOf(false)
        private set

    var countryDropdownOpen by mutableStateOf(false)
        private set

    var regionDropdownOpen by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Stores the latest camera bounds for the upcoming viewport data loader. */
    fun recordMapViewport(viewport: MapViewport) {
        latestMapViewport = viewport
    }

    private var countrySearchJob: Job? = null
    private var regionSearchJob: Job? = null

    fun onCountryFocused() {
        countryDropdownOpen = true
        if (countries.isEmpty()) startCountrySearch(query = null)
    }

    fun updateCountryQuery(value: String) {
        countryQuery = value
        countryDropdownOpen = true
        countrySearchJob?.cancel()
        countrySearchJob = viewModelScope.launch {
            delay(250)
            requestCountries(value.takeIf { it.isNotBlank() })
        }
    }

    fun searchCountries() {
        startCountrySearch(countryQuery.takeIf { it.isNotBlank() })
    }

    private fun startCountrySearch(query: String?) {
        countrySearchJob?.cancel()
        countrySearchJob = viewModelScope.launch {
            requestCountries(query)
        }
    }

    private suspend fun requestCountries(query: String?) {
        countryLoading = true
        error = null
        try {
            countries = api.searchCatalog(
                kind = "country",
                query = query,
                limit = 500,
            )
        } catch (t: Throwable) {
            error = t.message ?: "Could not load countries."
        } finally {
            countryLoading = false
        }
    }

    fun chooseCountry(country: CatalogPlace) {
        selectedCountry = country
        selectedRegion = null
        step = ExploreStep.REGION_PICKER
        level = ExploreLevel.COUNTRY
        countryQuery = country.name
        countries = emptyList()
        regionQuery = ""
        countryDropdownOpen = false
        regionDropdownOpen = false
        error = null
        loadRegions(country)
    }

    fun onRegionFocused() {
        regionDropdownOpen = true
        if (regions.isEmpty()) startRegionSearch(query = null)
    }

    fun updateRegionQuery(value: String) {
        regionQuery = value
        regionDropdownOpen = true
        regionSearchJob?.cancel()
        regionSearchJob = viewModelScope.launch {
            delay(250)
            requestRegions(value.takeIf { it.isNotBlank() })
        }
    }

    fun searchRegions() {
        startRegionSearch(regionQuery.takeIf { it.isNotBlank() })
    }

    private fun startRegionSearch(query: String?) {
        val country = selectedCountry ?: run {
            error = "Choose a country before searching regions."
            return
        }
        regionSearchJob?.cancel()
        regionSearchJob = viewModelScope.launch {
            requestRegions(query)
        }
    }

    private suspend fun requestRegions(query: String?) {
        val country = selectedCountry ?: return
        regionLoading = true
        error = null
        try {
            regions = api.searchCatalog(
                kind = "admin1",
                query = query,
                parentId = country.id,
                limit = 500,
            )
        } catch (t: Throwable) {
            error = t.message ?: "Could not load regions."
        } finally {
            regionLoading = false
        }
    }

    fun chooseRegion(region: CatalogPlace) {
        selectedRegion = region
        level = ExploreLevel.ADMIN1
        regionQuery = region.name
        regionDropdownOpen = false
        error = null
    }

    /** Looks up a rendered boundary's catalog id in the already loaded region list. */
    fun regionById(id: String): CatalogPlace? = regions.firstOrNull { it.id == id }

    fun clearRegion() {
        selectedRegion = null
        level = if (selectedCountry == null) ExploreLevel.WORLD else ExploreLevel.COUNTRY
        regionQuery = ""
        regionDropdownOpen = false
        error = null
    }

    fun backToCountryPicker() {
        clearCountry()
    }

    /** Removes the temporary Explore search context before opening Check-in. */
    fun clearSearchContextForCheckIn() {
        clearCountry()
    }

    fun dismissDropdowns() {
        countryDropdownOpen = false
        regionDropdownOpen = false
    }

    fun clearCountry() {
        countrySearchJob?.cancel()
        regionSearchJob?.cancel()
        countrySearchJob = null
        regionSearchJob = null
        countryLoading = false
        regionLoading = false
        selectedCountry = null
        selectedRegion = null
        countries = emptyList()
        regions = emptyList()
        countryQuery = ""
        regionQuery = ""
        countryDropdownOpen = false
        regionDropdownOpen = false
        step = ExploreStep.COUNTRY_PICKER
        level = ExploreLevel.WORLD
        error = null
    }

    private fun loadRegions(country: CatalogPlace) {
        regionSearchJob?.cancel()
        regionSearchJob = viewModelScope.launch {
            requestRegions(query = null)
        }
    }
}
