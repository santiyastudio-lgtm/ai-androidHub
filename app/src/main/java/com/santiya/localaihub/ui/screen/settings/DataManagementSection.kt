package com.santiya.localaihub.ui.screen.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.santiya.localaihub.ui.theme.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.formatBackupTimestamp
import com.santiya.localaihub.global.formatBytes
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.support.SupportReportBundle
import com.santiya.localaihub.ui.components.PasswordTextField
import com.santiya.localaihub.ui.components.SwitchRow
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.SupportReportState
import com.santiya.localaihub.viewmodel.SettingsViewModel
import com.santiya.localaihub.worker.SystemBackupManager
import java.io.File

// в”Ђв”Ђ Data Management Section в”Ђв”Ђ

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SupportQuickSection() {
    val context = LocalContext.current
    Surface(
        onClick = { openSupportBot(context, "support") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.CardCornerRadius),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Icon(
                TnIcons.Sparkles, null,
                modifier = Modifier.size(Standards.IconLg),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    localizedText("Поддержать проект", "Support the project"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    localizedText(
                        "Открывает @SantiyaSupportBot для доната и реквизитов поддержки.",
                        "Opens @SantiyaSupportBot for donations and support details."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DataManagementSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val backupOptions by viewModel.backupOptions.collectAsStateWithLifecycle()
    val backupSizeEstimate by viewModel.backupSizeEstimate.collectAsStateWithLifecycle()
    val supportReportState by viewModel.supportReportState.collectAsStateWithLifecycle()

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var deleteConfirmText by remember { mutableStateOf("") }
    var supportComment by remember { mutableStateOf("") }

    // SAF launchers
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && backupPassword.isNotEmpty()) {
            viewModel.createBackup(uri, backupPassword)
            backupPassword = ""
            backupPasswordConfirm = ""
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && restorePassword.isNotEmpty()) {
            viewModel.restoreBackup(uri, restorePassword)
            restorePassword = ""
        }
    }

    // Auto-dismiss progress after completion, or restart after restore
    LaunchedEffect(backupProgress) {
        if (backupProgress is SystemBackupManager.BackupProgress.Complete) {
            if (showRestoreDialog) {
                // Restart process вЂ” Hilt singletons hold stale DB/DAO refs
                kotlinx.coroutines.delay(500)
                showRestoreDialog = false
                val activity = context as? Activity
                activity?.let {
                    val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    it.finishAffinity()
                    if (intent != null) it.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            } else {
                kotlinx.coroutines.delay(2000)
                viewModel.clearBackupProgress()
            }
        }
    }

    LaunchedEffect(supportReportState) {
        val state = supportReportState
        if (state is SupportReportState.Ready) {
            launchTelegramSupport(context, state.bundle)
            kotlinx.coroutines.delay(400)
            viewModel.clearSupportReportState()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
        // Progress indicator
        AnimatedVisibility(
            visible = backupProgress != null && backupProgress !is SystemBackupManager.BackupProgress.Complete
                    && backupProgress !is SystemBackupManager.BackupProgress.Error,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Row(
                    modifier = Modifier.padding(Standards.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = when (val p = backupProgress) {
                            is SystemBackupManager.BackupProgress.Starting -> "Starting..."
                            is SystemBackupManager.BackupProgress.Collecting -> p.component
                            is SystemBackupManager.BackupProgress.Processing -> {
                                val stage = if (p.stage.isNotEmpty()) "${p.stage} " else ""
                                "${stage}${(p.progress * 100).toInt()}%"
                            }
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Status messages
        val progressStatus = backupProgress
        if (progressStatus is SystemBackupManager.BackupProgress.Complete) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    "Operation completed successfully",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }
        if (progressStatus is SystemBackupManager.BackupProgress.Error) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    progressStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }

        when (val state = supportReportState) {
            SupportReportState.Idle,
            is SupportReportState.Ready -> Unit

            SupportReportState.Preparing -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Standards.CardCornerRadius)
                ) {
                    Row(
                        modifier = Modifier.padding(Standards.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = localizedText("Подготавливаю архив логов для поддержки...", "Preparing support archive..."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            is SupportReportState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Standards.CardCornerRadius)
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Standards.CardPadding)
                    )
                }
            }
        }

        // --- Green Backup Card ---
        Surface(
            onClick = { showBackupDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudUpload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Create encrypted backup of all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Restore Card ---
        Surface(
            onClick = { showRestoreDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudDownload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Restore from Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Restore from encrypted backup file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            onClick = { showSupportDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.Send, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedText("Отправить логи в поддержку", "Send logs to support"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        localizedText(
                            "Подготовит архив, откроет @SantiyaSupportBot и даст отправить логи с комментарием.",
                            "Prepares an archive, opens @SantiyaSupportBot, and lets you send logs with a comment."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            onClick = { openSupportBot(context, "donate") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.Sparkles, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedText("Поддержать проект", "Support the project"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        localizedText(
                            "Открывает @SantiyaSupportBot для доната и реквизитов поддержки.",
                            "Opens @SantiyaSupportBot for donations and support details."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Red Delete All Card ---
        Surface(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.TrashX, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Delete All Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Permanently delete all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // в”Ђв”Ђ Dialogs в”Ђв”Ђ

    if (showBackupDialog) {
        BackupDialog(
            backupPassword = backupPassword,
            onPasswordChange = { backupPassword = it },
            backupPasswordConfirm = backupPasswordConfirm,
            onPasswordConfirmChange = { backupPasswordConfirm = it },
            backupOptions = backupOptions,
            onOptionsChange = { viewModel.updateBackupOptions(it) },
            backupSizeEstimate = backupSizeEstimate,
            onEstimateSize = { viewModel.estimateBackupSize() },
            onConfirm = {
                showBackupDialog = false
                val timestamp = formatBackupTimestamp()
                backupLauncher.launch("toolneuron_backup_$timestamp.tnbackup")
            },
            onDismiss = {
                showBackupDialog = false
                backupPassword = ""
                backupPasswordConfirm = ""
            }
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            restorePassword = restorePassword,
            onPasswordChange = { restorePassword = it },
            onConfirm = {
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onDismiss = {
                showRestoreDialog = false
                restorePassword = ""
            }
        )
    }

    if (showDeleteDialog) {
        DeleteAllDataDialog(
            deleteConfirmText = deleteConfirmText,
            onConfirmTextChange = { deleteConfirmText = it },
            onConfirm = {
                showDeleteDialog = false
                deleteConfirmText = ""
                viewModel.deleteAllData()
            },
            onDismiss = {
                showDeleteDialog = false
                deleteConfirmText = ""
            }
        )
    }

    if (showSupportDialog) {
        SupportLogsDialog(
            comment = supportComment,
            onCommentChange = { supportComment = it },
            isBusy = supportReportState is SupportReportState.Preparing,
            onConfirm = {
                showSupportDialog = false
                viewModel.prepareSupportReport(supportComment)
                supportComment = ""
            },
            onDismiss = {
                showSupportDialog = false
                supportComment = ""
            }
        )
    }
}

// в”Ђв”Ђ Backup Dialog в”Ђв”Ђ

@Composable
private fun BackupDialog(
    backupPassword: String,
    onPasswordChange: (String) -> Unit,
    backupPasswordConfirm: String,
    onPasswordConfirmChange: (String) -> Unit,
    backupOptions: SystemBackupManager.BackupOptions,
    onOptionsChange: (SystemBackupManager.BackupOptions) -> Unit,
    backupSizeEstimate: SystemBackupManager.BackupSizeEstimate?,
    onEstimateSize: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onEstimateSize() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudUpload, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = {
            Text("Create Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "Set a password to encrypt your backup. You'll need this password to restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordTextField(
                    value = backupPassword,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
                PasswordTextField(
                    value = backupPasswordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = "Confirm Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false,
                    isError = backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm
                )

                Spacer(modifier = Modifier.height(Standards.SpacingXs))

                SwitchRow(
                    title = "Include RAG files",
                    checked = backupOptions.includeRagFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeRagFiles = checked))
                    }
                )

                SwitchRow(
                    title = "Include AI Models",
                    checked = backupOptions.includeModelFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeModelFiles = checked))
                    }
                )

                if (backupOptions.includeModelFiles && backupSizeEstimate != null) {
                    val models = backupSizeEstimate.modelBreakdown
                    if (models.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = Standards.SpacingSm),
                            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXxs)
                        ) {
                            models.forEach { model ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        model.modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (model.canBackup) formatBytes(model.sizeBytes)
                                        else model.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                backupSizeEstimate?.let { estimate ->
                    Text(
                        "Estimated size: ${formatBytes(estimate.totalSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = backupPassword.length >= 4 && backupPassword == backupPasswordConfirm
            ) {
                Text("Create Backup", color = MaterialTheme.colorScheme.tertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

// в”Ђв”Ђ Restore Dialog в”Ђв”Ђ

@Composable
private fun RestoreDialog(
    restorePassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text("Restore from Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will replace all current data with the backup. The app will restart after restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                PasswordTextField(
                    value = restorePassword,
                    onValueChange = onPasswordChange,
                    label = "Backup Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = restorePassword.length >= 4
            ) {
                Text("Select Backup File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

// в”Ђв”Ђ Delete All Data Dialog в”Ђв”Ђ

@Composable
private fun DeleteAllDataDialog(
    deleteConfirmText: String,
    onConfirmTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.TrashX, null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text("Delete All Data", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will permanently delete all chats, memories, personas, RAG data, and settings. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Type DELETE to confirm",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = deleteConfirmText,
                    onValueChange = onConfirmTextChange,
                    label = { Text("Type DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = deleteConfirmText.isNotEmpty() && deleteConfirmText != "DELETE"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = deleteConfirmText == "DELETE"
            ) {
                Text("Delete Everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

@Composable
private fun SupportLogsDialog(
    comment: String,
    onCommentChange: (String) -> Unit,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.Send, null, tint = MaterialTheme.colorScheme.secondary) },
        title = {
            Text(localizedText("Отправить логи в поддержку", "Send logs to support"), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    localizedText(
                        "Приложение соберёт диагностический архив, откроет Telegram-бота и подготовит файл к отправке.",
                        "The app will build a diagnostics archive, open the Telegram bot, and prepare the file for sending."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = onCommentChange,
                    label = { Text(localizedText("Комментарий для поддержки", "Comment for support")) },
                    placeholder = { Text(localizedText("Опишите проблему или шаги, как её повторить", "Describe the issue or reproduction steps")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(localizedText("Открыть Telegram", "Open Telegram"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(localizedText("Отмена", "Cancel"))
            }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}

private fun openSupportBot(
    context: android.content.Context,
    startPayload: String? = null,
) {
    val packageManager = context.packageManager
    val botPath = startPayload?.takeIf { it.isNotBlank() }?.let { "?start=$it" }.orEmpty()
    val botIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=SantiyaSupportBot$botPath")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val webFallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SantiyaSupportBot$botPath")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (botIntent.resolveActivity(packageManager) != null) {
        context.startActivity(botIntent)
    } else if (webFallbackIntent.resolveActivity(packageManager) != null) {
        context.startActivity(webFallbackIntent)
    }
}

private suspend fun launchTelegramSupport(
    context: android.content.Context,
    bundle: SupportReportBundle
) {
    val reportFile = File(bundle.archiveFile.absolutePath)
    if (!reportFile.exists()) return

    val packageManager = context.packageManager
    val archiveUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        reportFile
    )

    openSupportBot(context, bundle.telegramStartPayload)
    kotlinx.coroutines.delay(350)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, archiveUri)
        putExtra(Intent.EXTRA_TEXT, bundle.shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val launchShareIntent = if (packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
        shareIntent.setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        Intent.createChooser(
            shareIntent,
            localizedText(context, "Отправить архив логов", "Send support archive")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    if (launchShareIntent.resolveActivity(packageManager) != null) {
        context.startActivity(launchShareIntent)
    }
}
