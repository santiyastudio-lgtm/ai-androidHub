package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.tts.TTSSettings
import com.santiya.localaihub.ui.components.ActionToggleGroup
import com.santiya.localaihub.ui.components.CaptionText
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.components.SwitchRow
import com.santiya.localaihub.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

internal val DEFAULT_VOICES = listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
internal val SUPPORTED_LANGUAGES = listOf("en" to "EN", "ko" to "KO", "es" to "ES", "pt" to "PT", "fr" to "FR")

internal fun LazyListScope.ttsSettingsSection(
    language: AppLanguage,
    hasTtsModel: Boolean,
    ttsDownloadState: ModelDownloadService.DownloadState?,
    ttsModelLoaded: Boolean,
    loadTTSOnStart: Boolean,
    ttsSettings: TTSSettings,
    voices: List<String>,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "Озвучка текста", "Text-to-Speech")) }

    if (!hasTtsModel) {
        item {
            ModelDownloadCard(
                title = localizedText(language, "Скачать TTS", "Download TTS"),
                description = "Supertonic v2 • ~263 MB",
                downloadState = ttsDownloadState,
                onDownload = { viewModel.downloadTts() },
                successText = localizedText(language, "Скачано — загружаю модель...", "Downloaded — loading model...")
            )
        }
    }

    item {
        SwitchRow(
            title = localizedText(language, "Загружать TTS при запуске", "Load TTS on app start"),
            description = localizedText(
                language,
                "Автоматически загружать модель озвучки при старте приложения",
                "Auto-load the TTS model when the app launches"
            ),
            checked = loadTTSOnStart,
            onCheckedChange = { viewModel.setLoadTTSOnStart(it) }
        )
    }

    item {
        StandardCard(title = localizedText(language, "Голос", "Voice")) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                val femaleVoices = voices.filter { it.startsWith("F") }
                val maleVoices = voices.filter { it.startsWith("M") }

                if (femaleVoices.isNotEmpty()) {
                    CaptionText(text = localizedText(language, "Женские", "Female"))
                    ActionToggleGroup(
                        items = femaleVoices,
                        selectedItem = ttsSettings.voice,
                        onItemSelected = { viewModel.updateVoice(it) },
                        itemLabel = { it },
                        enabled = ttsModelLoaded
                    )
                }
                if (maleVoices.isNotEmpty()) {
                    CaptionText(text = localizedText(language, "Мужские", "Male"))
                    ActionToggleGroup(
                        items = maleVoices,
                        selectedItem = ttsSettings.voice,
                        onItemSelected = { viewModel.updateVoice(it) },
                        itemLabel = { it },
                        enabled = ttsModelLoaded
                    )
                }
            }
        }
    }

    item {
        StandardCard(title = localizedText(language, "Язык", "Language")) {
            ActionToggleGroup(
                items = SUPPORTED_LANGUAGES.map { it.first },
                selectedItem = ttsSettings.language,
                onItemSelected = { viewModel.updateLanguage(it) },
                itemLabel = { code -> SUPPORTED_LANGUAGES.first { it.first == code }.second },
                enabled = ttsModelLoaded
            )
        }
    }

    item {
        StandardCard(title = localizedText(language, "Скорость", "Speed")) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptionText(text = localizedText(language, "Скорость воспроизведения", "Playback speed"))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(Standards.SpacingXs)
                    ) {
                        Text(
                            text = "${"%.2f".format(ttsSettings.speed)}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXxs)
                        )
                    }
                }

                Slider(
                    value = ttsSettings.speed,
                    onValueChange = { viewModel.updateSpeed((it * 20).roundToInt() / 20f) },
                    valueRange = 0.5f..2.0f,
                    enabled = ttsModelLoaded,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CaptionText(text = "0.5x")
                    CaptionText(text = "1.0x")
                    CaptionText(text = "2.0x")
                }
            }
        }
    }

    item {
        StandardCard(title = localizedText(language, "Шаги денойзинга", "Denoising steps")) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptionText(text = localizedText(language, "Больше — качественнее, но медленнее", "Higher = better quality, slower"))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(Standards.SpacingXs)
                    ) {
                        Text(
                            text = "${ttsSettings.steps}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXxs)
                        )
                    }
                }

                Slider(
                    value = ttsSettings.steps.toFloat(),
                    onValueChange = { viewModel.updateSteps(it.roundToInt()) },
                    valueRange = 1f..8f,
                    steps = 6,
                    enabled = ttsModelLoaded,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }
    }

    item {
        SwitchRow(
            title = localizedText(language, "Автоозвучка", "Auto-speak"),
            description = localizedText(language, "Автоматически озвучивать ответы ассистента", "Automatically speak assistant responses"),
            checked = ttsSettings.autoSpeak,
            onCheckedChange = { viewModel.updateAutoSpeak(it) },
            enabled = ttsModelLoaded
        )
    }

    item {
        SwitchRow(
            title = localizedText(language, "Использовать NNAPI", "Use NNAPI"),
            description = localizedText(language, "Аппаратное ускорение, которое может работать не на всех устройствах", "Hardware acceleration that may not work on all devices"),
            checked = ttsSettings.useNNAPI,
            onCheckedChange = { viewModel.updateUseNNAPI(it) },
            enabled = ttsModelLoaded
        )
    }
}
