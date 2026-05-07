package com.santiya.localaihub.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

enum class BrowserCommand {
    NONE,
    GO_BACK,
    GO_FORWARD,
    REFRESH
}

data class BrowserUiState(
    val currentUrl: String? = null,
    val pageTitle: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommandToken: Long = 0L,
    val pendingCommand: BrowserCommand = BrowserCommand.NONE,
)

@Serializable
data class BrowserSessionSnapshot(
    val currentUrl: String? = null,
    val pageTitle: String? = null,
)

object BrowserToolState {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    fun openUrl(url: String, title: String? = null) {
        _state.update {
            it.copy(
                currentUrl = url,
                pageTitle = title ?: it.pageTitle,
                pendingCommand = BrowserCommand.NONE
            )
        }
    }

    fun updatePageState(
        url: String?,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
        _state.update {
            it.copy(
                currentUrl = url ?: it.currentUrl,
                pageTitle = title ?: it.pageTitle,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
            )
        }
    }

    fun queueCommand(command: BrowserCommand) {
        _state.update {
            it.copy(
                pendingCommand = command,
                pendingCommandToken = it.pendingCommandToken + 1L
            )
        }
    }

    fun clearPendingCommand() {
        _state.update { it.copy(pendingCommand = BrowserCommand.NONE) }
    }

    fun snapshot(): BrowserSessionSnapshot {
        val state = _state.value
        return BrowserSessionSnapshot(
            currentUrl = state.currentUrl,
            pageTitle = state.pageTitle
        )
    }

    fun restore(snapshot: BrowserSessionSnapshot?) {
        if (snapshot == null) return
        _state.update {
            it.copy(
                currentUrl = snapshot.currentUrl,
                pageTitle = snapshot.pageTitle,
                pendingCommand = BrowserCommand.NONE
            )
        }
    }
}
