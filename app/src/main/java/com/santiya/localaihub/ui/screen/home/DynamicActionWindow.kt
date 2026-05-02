package com.santiya.localaihub.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import com.santiya.localaihub.ui.theme.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.state.AppStateManager
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.global.Standards

// в”Ђв”Ђ Dynamic Action Window в”Ђв”Ђ

enum class DynamicWindowTab {
    STATUS,
    MODELS,
    SYSTEM
}

@Composable
fun DynamicActionWindow(
    chatViewModel: ChatViewModel,
    modelViewModel: LLMModelViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    val appState by AppStateManager.appState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(DynamicWindowTab.STATUS) }
    val installedModels by modelViewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentModelID by modelViewModel.currentModelID.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.content()),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0A0F).copy(alpha = 0.98f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(34.dp)
    ) {
        Column {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = @Composable {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                DynamicWindowTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    DynamicWindowTab.STATUS -> "Статус"
                                    DynamicWindowTab.MODELS -> "Модели"
                                    DynamicWindowTab.SYSTEM -> "Система"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedTab == tab) Color.White else Color.White.copy(alpha = 0.58f),
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(Motion.state()) togetherWith
                            fadeOut(Motion.state())
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    DynamicWindowTab.STATUS -> StatusTabContent(
                        appState = appState,
                        chatViewModel = chatViewModel,
                        loadedRagCount = loadedRagCount,
                        enabledToolCount = enabledToolCount,
                        isMemoryEnabled = isMemoryEnabled,
                        ttsModelLoaded = ttsModelLoaded
                    )
                    DynamicWindowTab.MODELS -> ModelsTabContent(
                        installedModels,
                        currentModelID,
                        modelViewModel,
                        chatViewModel
                    )
                    DynamicWindowTab.SYSTEM -> SystemTabContent(appState, modelViewModel, chatViewModel)
                }
            }
        }
    }
}
