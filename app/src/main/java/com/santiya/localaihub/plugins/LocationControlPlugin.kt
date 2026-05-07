package com.santiya.localaihub.plugins

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.plugins.PluginInfo
import com.santiya.localaihub.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class LocationControlResult(
    val action: String,
    val enabled: Boolean?,
    val success: Boolean,
    val message: String,
    val scriptPath: String? = null,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
)

class LocationControlPlugin(
    private val context: Context,
) : SuperPlugin {

    companion object {
        const val TOOL_GET_LOCATION_STATUS = "get_location_status"
        const val TOOL_SET_LOCATION_ENABLED = "set_location_enabled"
        const val TOOL_OPEN_LOCATION_SETTINGS = "open_location_settings"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Location Control",
            description = "Read Android location status, attempt scripted GPS toggle, or open location settings",
            author = "SantiyaLocalAiHub",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_GET_LOCATION_STATUS,
                    "Get whether Android location services are currently enabled"
                ),
                ToolDefinitionBuilder(
                    TOOL_SET_LOCATION_ENABLED,
                    "Attempt to enable or disable Android location services using a generated shell script. This may fail on non-privileged devices."
                )
                    .booleanParam("enabled", "True to enable location, false to disable it", required = true),
                ToolDefinitionBuilder(
                    TOOL_OPEN_LOCATION_SETTINGS,
                    "Open the Android location settings screen for manual GPS changes"
                )
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return when (toolCall.name) {
            TOOL_GET_LOCATION_STATUS -> Result.success(getLocationStatus())
            TOOL_SET_LOCATION_ENABLED -> setLocationEnabled(toolCall.getBoolean("enabled"))
            TOOL_OPEN_LOCATION_SETTINGS -> openLocationSettings()
            else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
        }
    }

    override fun serializeResult(data: Any): String {
        return (data as? LocationControlResult)?.let {
            JSONObject().apply {
                put("action", it.action)
                put("enabled", it.enabled)
                put("success", it.success)
                put("message", it.message)
                put("scriptPath", it.scriptPath)
                put("exitCode", it.exitCode)
                put("stdout", it.stdout)
                put("stderr", it.stderr)
            }.toString()
        } ?: data.toString()
    }

    @Composable
    override fun ToolCallUI() = Unit

    @Composable
    override fun CacheToolUI(data: JSONObject) = Unit

    private fun getLocationStatus(): LocationControlResult {
        val enabled = isLocationEnabled()
        return LocationControlResult(
            action = TOOL_GET_LOCATION_STATUS,
            enabled = enabled,
            success = true,
            message = if (enabled) {
                "Android location services are enabled."
            } else {
                "Android location services are disabled."
            }
        )
    }

    private suspend fun setLocationEnabled(enabled: Boolean): Result<Any> = withContext(Dispatchers.IO) {
        val workspace = AppPaths.workspaceFiles(context)
        val automationDir = File(workspace, "automation").also { it.mkdirs() }
        val scriptFile = File(automationDir, if (enabled) "toggle_location_on.sh" else "toggle_location_off.sh")
        val command = if (enabled) {
            "cmd location set-location-enabled true"
        } else {
            "cmd location set-location-enabled false"
        }
        scriptFile.writeText(
            """
            #!/system/bin/sh
            $command
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        val process = ProcessBuilder("/system/bin/sh", scriptFile.absolutePath)
            .directory(workspace)
            .redirectErrorStream(false)
            .start()

        val finished = process.waitFor(12, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext Result.success(
                LocationControlResult(
                    action = TOOL_SET_LOCATION_ENABLED,
                    enabled = isLocationEnabled(),
                    success = false,
                    message = "Location toggle script timed out. Android likely blocked the operation.",
                    scriptPath = scriptFile.absolutePath,
                    exitCode = -1,
                )
            )
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText() }.take(8_000)
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.take(8_000)
        val exitCode = process.exitValue()
        val actualEnabled = isLocationEnabled()
        val success = exitCode == 0 && actualEnabled == enabled
        val message = if (success) {
            if (enabled) "Android location services were enabled." else "Android location services were disabled."
        } else {
            buildString {
                append("Direct GPS toggle was blocked by Android permissions or device policy.")
                append(" Use the location settings screen for manual confirmation.")
            }
        }

        Result.success(
            LocationControlResult(
                action = TOOL_SET_LOCATION_ENABLED,
                enabled = actualEnabled,
                success = success,
                message = message,
                scriptPath = scriptFile.absolutePath,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
            )
        )
    }

    private fun openLocationSettings(): Result<Any> {
        return runCatching {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            LocationControlResult(
                action = TOOL_OPEN_LOCATION_SETTINGS,
                enabled = isLocationEnabled(),
                success = true,
                message = "Opened Android location settings."
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { manager.isLocationEnabled }.getOrDefault(false)
    }
}
