package com.santiya.localaihub.ui.screen.files

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.FilesWorkspaceViewModel
import com.santiya.localaihub.viewmodel.WorkspaceFileEntry
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesWorkspaceScreen(
    onNavigateBack: () -> Unit,
    viewModel: FilesWorkspaceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importDocument(uri)
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            // keep the inline banner visible until the next user action
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Файлы") },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Назад",
                    )
                },
                actions = {
                    ActionButton(
                        onClickListener = { importLauncher.launch(arrayOf("*/*")) },
                        icon = TnIcons.Upload,
                        contentDescription = "Импортировать файл",
                    )
                    ActionButton(
                        onClickListener = {
                            newFileName = ""
                            showCreateDialog = true
                        },
                        icon = TnIcons.Plus,
                        contentDescription = "Создать файл",
                    )
                    ActionButton(
                        onClickListener = { viewModel.saveSelectedFile() },
                        icon = TnIcons.DeviceFloppy,
                        contentDescription = "Сохранить файл",
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
        ) {
            WorkspaceInfoCard()

            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(Standards.RadiusLg),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.dismissStatus() }) {
                            Text("Ок")
                        }
                    }
                }
            }

            if (files.isEmpty()) {
                EmptyWorkspaceState(
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onCreate = {
                        newFileName = ""
                        showCreateDialog = true
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                    Text(
                        text = "Рабочие файлы",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
                    ) {
                        items(files, key = { it.relativePath }) { file ->
                            FilterChip(
                                selected = selectedFile?.relativePath == file.relativePath,
                                onClick = { viewModel.selectFile(file.relativePath) },
                                label = {
                                    Text(
                                        text = file.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (file.editable) TnIcons.FileText else TnIcons.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                SelectedFilePanel(
                    file = selectedFile,
                    editorText = editorText,
                    isBusy = isBusy,
                    onEditorChange = viewModel::updateEditorText,
                    onSave = viewModel::saveSelectedFile,
                    onDelete = viewModel::deleteSelectedFile,
                    formatFileSize = { bytes -> Formatter.formatShortFileSize(context, bytes) },
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFile(newFileName)
                        showCreateDialog = false
                    },
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            },
            title = { Text("Новый файл") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    singleLine = true,
                    label = { Text("Имя файла") },
                    placeholder = { Text("notes.txt") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun WorkspaceInfoCard() {
    Surface(
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
        ) {
            Text(
                text = "Файлы для локального AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Здесь лежат копии документов, доступные локальным AI-инструментам и файловым workflow внутри приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyWorkspaceState(
    onImport: () -> Unit,
    onCreate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f), RoundedCornerShape(Standards.RadiusXl))
            .padding(Standards.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
        ) {
            Icon(
                imageVector = TnIcons.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "Рабочее пространство пусто",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Импортируйте документ или создайте новый текстовый файл.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                TextButton(onClick = onImport) { Text("Импорт") }
                TextButton(onClick = onCreate) { Text("Новый файл") }
            }
        }
    }
}

@Composable
private fun SelectedFilePanel(
    file: WorkspaceFileEntry?,
    editorText: String,
    isBusy: Boolean,
    onEditorChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    formatFileSize: (Long) -> String,
) {
    if (file == null) {
        Surface(
            shape = RoundedCornerShape(Standards.RadiusXl),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.CardPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Выберите файл для просмотра или редактирования.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatFileSize(file.sizeBytes)} • ${DateFormat.getDateTimeInstance().format(Date(file.updatedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (!file.editable) {
                Text(
                    text = "Этот тип файла пока доступен только для хранения и передачи в AI-инструменты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            OutlinedTextField(
                value = editorText,
                onValueChange = onEditorChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                label = { Text("Содержимое файла") },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                TextButton(onClick = onSave) {
                    Icon(TnIcons.DeviceFloppy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сохранить")
                }
                TextButton(onClick = onDelete) {
                    Icon(TnIcons.Trash, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Удалить")
                }
            }
        }
    }
}
