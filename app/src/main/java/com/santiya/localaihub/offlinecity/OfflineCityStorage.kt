package com.santiya.localaihub.offlinecity

import android.content.Context
import com.santiya.localaihub.global.AppPaths
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineCityStorage(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val datasetFile: File
        get() = AppPaths.offlineCityDataset(context)

    fun loadDataset(): OfflineCityDataset? {
        val file = datasetFile
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<OfflineCityDataset>(file.readText())
        }.getOrNull()
    }

    fun saveDataset(dataset: OfflineCityDataset) {
        val file = datasetFile
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(dataset))
    }

    fun clearDataset() {
        val root = AppPaths.offlineCityRoot(context)
        if (root.exists()) {
            root.deleteRecursively()
        }
    }

    fun importCopyTarget(fileName: String): File {
        val safeName = fileName.ifBlank { "offline-city-import.bin" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(AppPaths.offlineCityImports(context), safeName)
    }
}
