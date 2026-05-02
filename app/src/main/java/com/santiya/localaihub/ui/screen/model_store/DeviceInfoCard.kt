package com.santiya.localaihub.ui.screen.model_store

import androidx.compose.animation.AnimatedVisibility
import com.santiya.localaihub.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.icons.TnIcons
import java.util.Locale

// в”Ђв”Ђ DeviceInfoCard в”Ђв”Ђ

@Composable
internal fun DeviceInfoCard(deviceInfo: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    val entries = deviceInfo.entries.toList()
    val previewEntries = entries.take(3)
    val remainingEntries = entries.drop(3)

    StandardCard(
        title = "Device Information",
        icon = TnIcons.Prompt,
        trailing = {
            if (remainingEntries.isNotEmpty()) {
                ActionButton(
                    onClickListener = { expanded = !expanded },
                    icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
            previewEntries.forEach { (key, value) ->
                DeviceInfoRow(
                    label = key.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    },
                    value = value
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                    remainingEntries.forEach { (key, value) ->
                        DeviceInfoRow(
                            label = key.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                            },
                            value = value
                        )
                    }
                }
            }
        }
    }
}

// в”Ђв”Ђ DeviceInfoRow в”Ђв”Ђ

@Composable
internal fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
