package com.santiya.localaihub.plugins

import androidx.compose.runtime.Composable
import com.santiya.localaihub.browser.BrowserCommand
import com.santiya.localaihub.browser.BrowserToolState
import com.santiya.localaihub.browser.BrowserUrlPolicy
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import org.json.JSONObject

class BrowserPlugin : SuperPlugin {

    companion object {
        private const val PLUGIN_NAME = "Browser"
        private const val TOOL_OPEN_URL = "browser_open_url"
        private const val TOOL_CURRENT_PAGE = "browser_current_page"
        private const val TOOL_BACK = "browser_back"
        private const val TOOL_FORWARD = "browser_forward"
        private const val TOOL_REFRESH = "browser_refresh"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = PLUGIN_NAME,
            description = "Open and control the embedded browser inside the app",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(TOOL_OPEN_URL, "Open a URL in the embedded browser")
                    .stringParam("url", "URL to open", required = true),
                ToolDefinitionBuilder(TOOL_CURRENT_PAGE, "Read the current page state from the embedded browser"),
                ToolDefinitionBuilder(TOOL_BACK, "Go back in the embedded browser history"),
                ToolDefinitionBuilder(TOOL_FORWARD, "Go forward in the embedded browser history"),
                ToolDefinitionBuilder(TOOL_REFRESH, "Refresh the embedded browser page"),
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return runCatching {
            when (toolCall.name) {
                TOOL_OPEN_URL -> {
                    val rawUrl = toolCall.getString("url").trim()
                    val normalized = BrowserUrlPolicy.normalize(rawUrl)
                    BrowserToolState.openUrl(normalized)
                    browserPayload(
                        action = "open",
                        ok = true,
                        message = "Opened $normalized in the embedded browser",
                    )
                }

                TOOL_CURRENT_PAGE -> {
                    val state = BrowserToolState.state.value
                    browserPayload(
                        action = "current_page",
                        ok = true,
                        message = if (state.currentUrl != null) {
                            "Current page: ${state.pageTitle ?: state.currentUrl}"
                        } else {
                            "Browser is idle"
                        },
                        state = state,
                    )
                }

                TOOL_BACK -> {
                    BrowserToolState.queueCommand(BrowserCommand.GO_BACK)
                    browserPayload("back", true, "Requested browser back navigation")
                }

                TOOL_FORWARD -> {
                    BrowserToolState.queueCommand(BrowserCommand.GO_FORWARD)
                    browserPayload("forward", true, "Requested browser forward navigation")
                }

                TOOL_REFRESH -> {
                    BrowserToolState.queueCommand(BrowserCommand.REFRESH)
                    browserPayload("refresh", true, "Requested browser refresh")
                }

                else -> error("Unknown browser tool: ${toolCall.name}")
            }
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? JSONObject)?.toString() ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private fun browserPayload(
        action: String,
        ok: Boolean,
        message: String,
        state: com.santiya.localaihub.browser.BrowserUiState? = null,
    ): JSONObject {
        return JSONObject().apply {
            put("plugin", PLUGIN_NAME)
            put("action", action)
            put("ok", ok)
            put("message", message)
            state?.let {
                put("currentUrl", it.currentUrl)
                put("pageTitle", it.pageTitle)
                put("canGoBack", it.canGoBack)
                put("canGoForward", it.canGoForward)
            }
        }
    }
}
