package com.santiya.localaihub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.santiya.localaihub.desktop.state.DesktopAppStore
import com.santiya.localaihub.desktop.state.UtilityWindowType
import com.santiya.localaihub.desktop.ui.DesktopApp
import com.santiya.localaihub.desktop.ui.UtilityWindowContent
import java.awt.Image
import java.awt.Toolkit

fun main() = application {
    val store = remember { DesktopAppStore() }
    val primaryWindowState = rememberWindowState(
        width = 1460.dp,
        height = 980.dp,
        position = WindowPosition.PlatformDefault
    )

    LaunchedEffect(Unit) {
        store.initialize()
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = primaryWindowState,
        title = "SantiyaLocalAiHub",
        undecorated = false
    ) {
        rememberWindowIcon()?.let { window.iconImage = it }
        DesktopApp(store = store)
    }

    UtilityWindowType.entries.forEach { type ->
        if (store.isUtilityWindowOpen(type)) {
            Window(
                onCloseRequest = { store.closeUtility(type) },
                title = store.utilityTitle(type),
                state = rememberWindowState(
                    width = if (type == UtilityWindowType.LOGS) 980.dp else 860.dp,
                    height = 760.dp
                )
            ) {
                rememberWindowIcon()?.let { window.iconImage = it }
                UtilityWindowContent(type = type, store = store)
            }
        }
    }
}

private fun rememberWindowIcon(): Image? {
    val url = Thread.currentThread().contextClassLoader.getResource("images/app_icon.png") ?: return null
    return Toolkit.getDefaultToolkit().getImage(url)
}
