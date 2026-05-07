package com.santiya.localaihub.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.santiya.localaihub.global.AppLanguageManager
import com.santiya.localaihub.offlinecity.OfflineCityAnswer
import com.santiya.localaihub.offlinecity.OfflineCityAssistant
import com.santiya.localaihub.offlinecity.OfflineCityCatalog
import com.santiya.localaihub.offlinecity.OfflineCityCatalogEntry
import com.santiya.localaihub.offlinecity.OfflineCityDataset
import com.santiya.localaihub.offlinecity.OfflineCityImporter
import com.santiya.localaihub.offlinecity.OfflineCityLocationProvider
import com.santiya.localaihub.offlinecity.OfflineCityLocationSnapshot
import com.santiya.localaihub.offlinecity.OfflineCityStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OfflineCityImportState {
    data object Idle : OfflineCityImportState
    data object Importing : OfflineCityImportState
    data class Success(val message: String) : OfflineCityImportState
    data class Error(val message: String) : OfflineCityImportState
}

class OfflineCityViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = OfflineCityStorage(application)
    private val importer = OfflineCityImporter(application)
    private val catalog = OfflineCityCatalog(application)
    private val assistant = OfflineCityAssistant()
    private val locationProvider = OfflineCityLocationProvider(application)

    private val _dataset = MutableStateFlow(storage.loadDataset())
    val dataset: StateFlow<OfflineCityDataset?> = _dataset.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _answer = MutableStateFlow<OfflineCityAnswer?>(null)
    val answer: StateFlow<OfflineCityAnswer?> = _answer.asStateFlow()

    private val _location = MutableStateFlow<OfflineCityLocationSnapshot?>(null)
    val location: StateFlow<OfflineCityLocationSnapshot?> = _location.asStateFlow()

    private val _importState = MutableStateFlow<OfflineCityImportState>(OfflineCityImportState.Idle)
    val importState: StateFlow<OfflineCityImportState> = _importState.asStateFlow()

    private val _catalogQuery = MutableStateFlow("")
    val catalogQuery: StateFlow<String> = _catalogQuery.asStateFlow()

    private val _catalogResults = MutableStateFlow(catalog.search(""))
    val catalogResults: StateFlow<List<OfflineCityCatalogEntry>> = _catalogResults.asStateFlow()

    init {
        refreshLocation()
    }

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun updateCatalogQuery(value: String) {
        _catalogQuery.value = value
        _catalogResults.value = catalog.search(value)
    }

    fun importDataset(uri: Uri) {
        viewModelScope.launch {
            _importState.value = OfflineCityImportState.Importing
            runCatching {
                withContext(Dispatchers.IO) { importer.importUri(uri) }
            }.onSuccess { result ->
                _dataset.value = result.dataset
                _importState.value = OfflineCityImportState.Success(
                    if (result.merged) {
                        "Офлайн-пакет города обновлён: ${result.importedFileName}"
                    } else {
                        "Офлайн-пакет города импортирован: ${result.importedFileName}"
                    }
                )
                if (_query.value.isNotBlank()) ask(_query.value)
            }.onFailure { error ->
                _importState.value = OfflineCityImportState.Error(
                    error.message ?: "Не удалось импортировать офлайн-пакет города"
                )
            }
        }
    }

    fun installCatalogEntry(entry: OfflineCityCatalogEntry) {
        viewModelScope.launch {
            _importState.value = OfflineCityImportState.Importing
            runCatching {
                withContext(Dispatchers.IO) { importer.importCatalogEntry(entry) }
            }.onSuccess { result ->
                _dataset.value = result.dataset
                _importState.value = OfflineCityImportState.Success(
                    "Офлайн-пакет города установлен: ${entry.cityName} (${entry.regionCountry})"
                )
                if (_query.value.isNotBlank()) ask(_query.value)
            }.onFailure { error ->
                _importState.value = OfflineCityImportState.Error(
                    error.message ?: "Не удалось установить офлайн-пакет города"
                )
            }
        }
    }

    fun clearImportedDataset() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearDataset()
            _dataset.value = null
            _answer.value = null
            _importState.value = OfflineCityImportState.Idle
        }
    }

    fun refreshLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _location.value = locationProvider.readLastKnownLocation()
        }
    }

    fun canUseLocation(): Boolean = locationProvider.canUseLocation()

    fun ask(query: String = _query.value) {
        val language = AppLanguageManager.readPersistedLanguage(getApplication())
        _answer.value = assistant.answer(_dataset.value, query, language, _location.value)
    }

    fun dismissImportMessage() {
        _importState.value = OfflineCityImportState.Idle
    }
}
