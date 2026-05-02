package com.santiya.localaihub.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.santiya.localaihub.global.AppPaths
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkspaceFileEntry(
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val updatedAt: Long,
    val editable: Boolean,
)

@HiltViewModel
class FilesWorkspaceViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val workspaceDir: File = AppPaths.workspaceFiles(application)

    private val _files = MutableStateFlow<List<WorkspaceFileEntry>>(emptyList())
    val files: StateFlow<List<WorkspaceFileEntry>> = _files.asStateFlow()

    private val _selectedFile = MutableStateFlow<WorkspaceFileEntry?>(null)
    val selectedFile: StateFlow<WorkspaceFileEntry?> = _selectedFile.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _files.value = withContext(Dispatchers.IO) { listWorkspaceFiles() }
            val stillSelected = _selectedFile.value?.relativePath?.let { current ->
                _files.value.firstOrNull { it.relativePath == current }
            }
            _selectedFile.value = stillSelected
            if (stillSelected == null) {
                _editorText.value = ""
            }
        }
    }

    fun updateEditorText(value: String) {
        _editorText.value = value
    }

    fun dismissStatus() {
        _statusMessage.value = null
    }

    fun selectFile(relativePath: String) {
        viewModelScope.launch {
            val target = _files.value.firstOrNull { it.relativePath == relativePath }
            _selectedFile.value = target
            if (target == null) {
                _editorText.value = ""
                return@launch
            }

            if (!target.editable) {
                _editorText.value = ""
                _statusMessage.value = "Этот файл нельзя редактировать как текст."
                return@launch
            }

            _editorText.value = withContext(Dispatchers.IO) {
                resolveInWorkspace(target.relativePath).readText(Charsets.UTF_8)
            }
        }
    }

    fun createFile(rawName: String) {
        val cleaned = sanitizeFileName(rawName)
        if (cleaned.isBlank()) {
            _statusMessage.value = "Укажите имя файла."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            try {
                val created = withContext(Dispatchers.IO) {
                    val normalized = if ('.' in cleaned) cleaned else "$cleaned.txt"
                    val target = uniqueTarget(normalized)
                    target.parentFile?.mkdirs()
                    target.writeText("", Charsets.UTF_8)
                    target
                }
                refresh()
                selectFile(created.relativeTo(workspaceDir).invariantSeparatorsPath)
                _statusMessage.value = "Файл создан: ${created.name}"
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Не удалось создать файл."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveSelectedFile() {
        val selected = _selectedFile.value
        if (selected == null) {
            _statusMessage.value = "Сначала выберите файл."
            return
        }
        if (!selected.editable) {
            _statusMessage.value = "Этот файл нельзя сохранить как текст."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(Dispatchers.IO) {
                    resolveInWorkspace(selected.relativePath).writeText(_editorText.value, Charsets.UTF_8)
                }
                refresh()
                _statusMessage.value = "Файл сохранён."
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Не удалось сохранить файл."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteSelectedFile() {
        val selected = _selectedFile.value
        if (selected == null) {
            _statusMessage.value = "Сначала выберите файл."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(Dispatchers.IO) {
                    resolveInWorkspace(selected.relativePath).delete()
                }
                _selectedFile.value = null
                _editorText.value = ""
                refresh()
                _statusMessage.value = "Файл удалён."
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Не удалось удалить файл."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val displayName = queryDisplayName(uri) ?: "import-${System.currentTimeMillis()}.txt"
                    val safeName = sanitizeFileName(displayName).ifBlank {
                        "import-${System.currentTimeMillis()}.txt"
                    }
                    val target = uniqueTarget(safeName)
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw FileNotFoundException("Не удалось открыть выбранный файл.")
                    target
                }
                refresh()
                val relative = imported.relativeTo(workspaceDir).invariantSeparatorsPath
                if (isEditableText(imported.name)) {
                    selectFile(relative)
                }
                _statusMessage.value = "Файл импортирован: ${imported.name}"
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Не удалось импортировать файл."
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun listWorkspaceFiles(): List<WorkspaceFileEntry> {
        workspaceDir.mkdirs()
        return workspaceDir.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                WorkspaceFileEntry(
                    relativePath = file.relativeTo(workspaceDir).invariantSeparatorsPath,
                    displayName = file.name,
                    sizeBytes = file.length(),
                    updatedAt = file.lastModified(),
                    editable = isEditableText(file.name),
                )
            }
            .sortedByDescending { it.updatedAt }
            .toList()
    }

    private fun resolveInWorkspace(relativePath: String): File {
        val resolved = File(workspaceDir, relativePath).canonicalFile
        val root = workspaceDir.canonicalFile
        require(resolved.path.startsWith(root.path)) {
            "Доступ разрешён только внутри рабочего каталога приложения."
        }
        return resolved
    }

    private fun uniqueTarget(fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 0
        var candidate = File(workspaceDir, fileName)
        while (candidate.exists()) {
            index += 1
            val suffix = if (extension.isNotBlank()) ".$extension" else ""
            candidate = File(workspaceDir, "$baseName-$index$suffix")
        }
        return candidate
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun sanitizeFileName(rawName: String): String =
        rawName.trim()
            .replace('\\', '_')
            .replace('/', '_')
            .replace(':', '_')
            .replace('*', '_')
            .replace('?', '_')
            .replace('"', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('|', '_')

    private fun isEditableText(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("txt", "md", "json", "csv", "log", "xml", "yaml", "yml")
    }
}
