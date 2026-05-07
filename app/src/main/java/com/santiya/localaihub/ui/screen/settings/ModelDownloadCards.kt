package com.santiya.localaihub.ui.screen.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.ui.components.CaptionText
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.ui.theme.Motion

@Composable
internal fun ModelDownloadCard(
    title: String,
    description: String,
    downloadState: ModelDownloadService.DownloadState?,
    onDownload: () -> Unit,
    successText: String = "Downloaded"
) {
    val context = LocalContext.current
    StandardCard(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(Motion.content()),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            description.split("\n").forEach { line ->
                CaptionText(text = line)
            }

            when (downloadState) {
                is ModelDownloadService.DownloadState.Downloading -> {
                    val progress = downloadState.progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.width(Standards.SpacingMd))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ModelDownloadService.DownloadState.Extracting,
                is ModelDownloadService.DownloadState.Processing -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                is ModelDownloadService.DownloadState.Success -> {
                    CaptionText(text = successText)
                }

                is ModelDownloadService.DownloadState.Error -> {
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(onClick = onDownload) {
                        Text(localizedText(context, "Повторить", "Retry"))
                    }
                }

                else -> {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            TnIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Standards.SpacingSm))
                        Text(localizedText(context, "Скачать", "Download"))
                    }
                }
            }
        }
    }
}
