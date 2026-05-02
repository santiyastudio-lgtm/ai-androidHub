package com.santiya.localaihub.ui.screen.home

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.activity.ModelPickerActivity
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons

// в”Ђв”Ђ TopBar в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    showDynamicWindow: () -> Unit,
    onStoreButtonClicked: () -> Unit,
    currentModelName: String?,
    isGenerating: Boolean,
    isDynamicWindowVisible: Boolean
) {
    val context = LocalContext.current

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        title = {
        DynamicIslandPill(
            currentModelName = currentModelName,
            isGenerating = isGenerating,
            isExpanded = isDynamicWindowVisible,
            onClick = showDynamicWindow
        )
    }, navigationIcon = {
        ActionButton(
            onClickListener = onMenuClick,
            icon = TnIcons.Menu,
            modifier = Modifier.padding(start = 6.dp)
        )
    }, actions = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                onClickListener = onSettingsClick,
                icon = TnIcons.Settings,
                modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = onDeveloperClick,
                icon = TnIcons.Code,
                modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    onStoreButtonClicked()
                }, icon = TnIcons.Download, modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    // Open ModelPickerScreen with GGUF / VLM tabs
                    context.startActivity(Intent(context, ModelPickerActivity::class.java))
                }, icon = TnIcons.Upload, modifier = Modifier.padding(end = 6.dp)
            )
        }
    })
}
