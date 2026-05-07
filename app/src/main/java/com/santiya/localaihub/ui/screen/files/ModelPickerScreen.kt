package com.santiya.localaihub.ui.screen.files

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.formatBytes
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.ui.components.ActionTextButton
import com.santiya.localaihub.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    onModelPicked: (Uri, ProviderType) -> Unit,
    onModelFilePicked: (String, ProviderType) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var downloadsCandidates by remember { mutableStateOf<List<DownloadCandidate>>(emptyList()) }

    val ggufPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onModelPicked(uri, ProviderType.GGUF)
        }
    }

    LaunchedEffect(Unit) {
        downloadsCandidates = findDownloadsCandidates(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(TnIcons.ArrowLeft, "Back")
                    }
                },
                title = {
                    Text(
                        "Model Picker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = Standards.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
        ) {
            PickerCard(
                icon = TnIcons.File,
                title = "GGUF Model",
                description = "Pick a .gguf model file for text generation. " +
                    "The file will be accessed via a secure file descriptor, without broad storage permission.",
                buttonLabel = "Pick Model File",
                buttonIcon = TnIcons.Upload,
                onClick = { ggufPickerLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
            )

            if (downloadsCandidates.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Standards.RadiusXxl),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "If the system picker is unstable on your device, import a GGUF file directly from Downloads.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        downloadsCandidates.forEach { candidate ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val path = candidate.filePath
                                        if (!path.isNullOrBlank()) {
                                            onModelFilePicked(path, ProviderType.GGUF)
                                        } else {
                                            onModelPicked(candidate.uri, ProviderType.GGUF)
                                        }
                                    },
                                shape = RoundedCornerShape(Standards.RadiusLg),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = candidate.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${formatBytes(candidate.size)} • ${candidate.locationLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusXxl),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Standards.RadiusLg))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ActionTextButton(
                onClickListener = onClick,
                modifier = Modifier.fillMaxWidth(),
                icon = buttonIcon,
                text = buttonLabel,
                contentDescription = buttonLabel
            )
        }
    }
}

private data class DownloadCandidate(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val locationLabel: String,
    val filePath: String? = null
)

private fun findDownloadsCandidates(context: android.content.Context): List<DownloadCandidate> {
    return runCatching {
        val resolver = context.contentResolver
        val allRows = mutableListOf<DownloadCandidate>()
        val manifests = mutableSetOf<String>()
        val downloadsRoot = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        Log.d("ModelPickerScreen", "Querying Downloads fallback via MediaStore")

        fun queryCandidates(
            contentUri: Uri,
            idColumn: String,
            relativePathColumn: String?,
            pathColumn: String?
        ) {
            resolver.query(
                contentUri,
                buildList {
                    add(idColumn)
                    add(OpenableColumns.DISPLAY_NAME)
                    add(OpenableColumns.SIZE)
                    relativePathColumn?.let(::add)
                    pathColumn?.let(::add)
                }.toTypedArray(),
                null,
                null,
                "${MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                val relativeIndex = relativePathColumn?.let { column ->
                    cursor.getColumnIndex(column).takeIf { it >= 0 }
                }
                val pathIndex = pathColumn?.let { column ->
                    cursor.getColumnIndex(column).takeIf { it >= 0 }
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val size = cursor.getLong(sizeIndex).takeIf { it > 0L } ?: 0L
                    val relativePath = relativeIndex?.let(cursor::getString).orEmpty()
                    val filePath = pathIndex?.let(cursor::getString)
                    Log.d(
                        "ModelPickerScreen",
                        "Downloads row id=$id name=$name size=$size relativePath=$relativePath filePath=$filePath"
                    )
                    if (name.endsWith(".santiya.json", ignoreCase = true)) {
                        manifests += name
                        continue
                    }
                    allRows += DownloadCandidate(
                        uri = Uri.withAppendedPath(contentUri, id.toString()),
                        displayName = name,
                        size = size,
                        locationLabel = relativePath.ifBlank { "Downloads" }.trimEnd('/'),
                        filePath = filePath
                    )
                }
            }
        }

        queryCandidates(
            contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            idColumn = MediaStore.Downloads._ID,
            relativePathColumn = MediaStore.Downloads.RELATIVE_PATH,
            pathColumn = null
        )
        queryCandidates(
            contentUri = MediaStore.Files.getContentUri("external"),
            idColumn = MediaColumns._ID,
            relativePathColumn = MediaColumns.RELATIVE_PATH,
            pathColumn = MediaColumns.DATA
        )

        val directDownloadFiles = downloadsRoot
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.filter { file ->
                file.name.endsWith(".gguf", ignoreCase = true) ||
                    looksLikeGgufFile(file) ||
                    manifests.contains("${file.name}.santiya.json") ||
                    manifests.contains("${file.name}.gguf.santiya.json")
            }
            ?.map { file ->
                val normalizedName = when {
                    file.name.endsWith(".gguf", ignoreCase = true) -> file.name
                    looksLikeGgufFile(file) -> "${file.name}.gguf"
                    else -> file.name
                }
                DownloadCandidate(
                    uri = Uri.fromFile(file),
                    displayName = normalizedName,
                    size = file.length(),
                    locationLabel = "Downloads",
                    filePath = file.absolutePath
                )
            }
            ?.toList()
            .orEmpty()

        val filtered = (allRows.asSequence()
            .filter { candidate ->
                val name = candidate.displayName
                val directFileLooksGguf = candidate.filePath
                    ?.let(::File)
                    ?.takeIf { it.exists() && it.isFile }
                    ?.let(::looksLikeGgufFile)
                    ?: false
                val downloadsFileLooksGguf = File(downloadsRoot, name)
                    .takeIf { it.exists() && it.isFile }
                    ?.let(::looksLikeGgufFile)
                    ?: false
                name.endsWith(".gguf", ignoreCase = true) ||
                    directFileLooksGguf ||
                    downloadsFileLooksGguf ||
                    manifests.contains("${name}.gguf.santiya.json") ||
                    manifests.contains("${name}.santiya.json") ||
                    candidate.locationLabel.contains("SantiyaLocalAiHub/models/gguf", ignoreCase = true)
            }
            .map { candidate ->
                val normalizedName = when {
                    candidate.displayName.endsWith(".gguf", ignoreCase = true) -> candidate.displayName
                    looksLikeGgufFile(File(downloadsRoot, candidate.displayName)) -> "${candidate.displayName}.gguf"
                    candidate.filePath?.let(::File)?.let(::looksLikeGgufFile) == true -> "${candidate.displayName}.gguf"
                    manifests.contains("${candidate.displayName}.gguf.santiya.json") -> "${candidate.displayName}.gguf"
                    else -> candidate.displayName
                }
                candidate.copy(displayName = normalizedName)
            }
            .distinctBy { it.filePath ?: it.uri.toString() }
            .plus(directDownloadFiles.asSequence())
            .distinctBy { it.filePath ?: it.uri.toString() }
            .sortedByDescending { it.size }
            .take(8)
            .toList())

        Log.d(
            "ModelPickerScreen",
            "Downloads fallback rows=${allRows.size}, directFiles=${directDownloadFiles.size}, filtered=${filtered.size}, manifests=${manifests.size}"
        )
        filtered
    }.onFailure { error ->
        Log.e("ModelPickerScreen", "Failed to query Downloads fallback", error)
    }.getOrElse { emptyList() }
}

private fun looksLikeGgufFile(file: File): Boolean {
    if (!file.exists() || !file.isFile || file.length() < 4L) return false
    return runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 && header.decodeToString() == "GGUF"
        }
    }.getOrDefault(false)
}
