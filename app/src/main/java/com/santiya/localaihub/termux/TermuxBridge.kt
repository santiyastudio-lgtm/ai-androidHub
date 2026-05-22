package com.santiya.localaihub.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

data class TermuxStatus(
    val installed: Boolean,
    val runCommandPermissionGranted: Boolean,
    val commandServiceVisible: Boolean,
    val ready: Boolean,
    val message: String,
)

data class TermuxCommandRequest(
    val commandPath: String,
    val arguments: List<String> = emptyList(),
    val stdin: String? = null,
    val workdir: String = "~/",
    val background: Boolean = true,
    val label: String = "SantiyaLocalAiHub",
    val description: String = "OpenClaw Local command",
    val timeoutMs: Long = 30_000L,
)

data class TermuxCommandResult(
    val commandPath: String,
    val workdir: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val termuxErrorCode: Int?,
    val termuxErrorMessage: String?,
    val durationMs: Long,
    val success: Boolean,
)

object TermuxBridge {

    private const val TAG = "TermuxBridge"

    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val TERMUX_RUN_ACTION = "com.termux.RUN_COMMAND"
    private const val TERMUX_RUN_SERVICE = "com.termux.app.RunCommandService"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_LABEL"
    private const val EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    private const val RESULT_BUNDLE_KEY = "com.termux.app.extra.PLUGIN_RESULT_BUNDLE"
    private const val RESULT_STDOUT = "stdout"
    private const val RESULT_STDERR = "stderr"
    private const val RESULT_EXIT_CODE = "exit_code"
    private const val RESULT_ERR = "errmsg"
    private const val RESULT_ERR_CODE = "err"

    private const val EXTRA_EXECUTION_ID = "com.santiya.localaihub.termux.EXECUTION_ID"

    private val executionIdCounter = AtomicInteger(1)
    private val pendingExecutions = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()
    private val startTimes = ConcurrentHashMap<Int, Long>()
    private val requestMetadata = ConcurrentHashMap<Int, Pair<String, String>>()

    fun getStatus(context: Context): TermuxStatus {
        val installed = isPackageInstalled(context, TERMUX_PACKAGE)
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            TERMUX_RUN_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
        val serviceVisible = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_SERVICE)
            action = TERMUX_RUN_ACTION
        }.let { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveService(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                ) != null
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveService(intent, 0) != null
            }
        }

        val message = when {
            !installed ->
                "Termux is not installed. Install Termux first."
            !permissionGranted ->
                "Termux is installed, but RUN_COMMAND permission is not granted to this app."
            !serviceVisible ->
                "Termux is installed, but the RUN_COMMAND service is not visible. Check package visibility or Termux version."
            else ->
                "Termux bridge is ready."
        }

        return TermuxStatus(
            installed = installed,
            runCommandPermissionGranted = permissionGranted,
            commandServiceVisible = serviceVisible,
            ready = installed && permissionGranted && serviceVisible,
            message = message,
        )
    }

    suspend fun runCommand(
        context: Context,
        request: TermuxCommandRequest,
    ): TermuxCommandResult {
        val status = getStatus(context)
        require(status.ready) { status.message }

        val executionId = executionIdCounter.getAndIncrement()
        val deferred = CompletableDeferred<TermuxCommandResult>()
        pendingExecutions[executionId] = deferred
        startTimes[executionId] = System.currentTimeMillis()
        requestMetadata[executionId] = request.commandPath to request.workdir

        val callbackIntent = Intent(context, TermuxCommandResultReceiver::class.java).apply {
            putExtra(EXTRA_EXECUTION_ID, executionId)
        }
        val callbackFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, executionId, callbackIntent, callbackFlags)

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_SERVICE)
            action = TERMUX_RUN_ACTION
            putExtra(EXTRA_COMMAND_PATH, request.commandPath)
            putExtra(EXTRA_ARGUMENTS, request.arguments.toTypedArray())
            putExtra(EXTRA_STDIN, request.stdin)
            putExtra(EXTRA_WORKDIR, request.workdir)
            putExtra(EXTRA_BACKGROUND, request.background)
            putExtra(EXTRA_LABEL, request.label)
            putExtra(EXTRA_DESCRIPTION, request.description)
            putExtra(EXTRA_PENDING_INTENT, callback)
        }

        try {
            context.startService(intent)
        } catch (t: Throwable) {
            cleanupExecution(executionId)
            throw IllegalStateException("Failed to start Termux command: ${t.message}", t)
        }

        return try {
            withTimeout(request.timeoutMs) { deferred.await() }
        } catch (t: Throwable) {
            cleanupExecution(executionId)
            throw t
        }
    }

    internal fun onResultIntent(intent: Intent?) {
        if (intent == null) return
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        if (executionId <= 0) return

        val startedAt = startTimes.remove(executionId) ?: System.currentTimeMillis()
        val metadata = requestMetadata.remove(executionId)
        val bundle = extractResultBundle(intent.extras)
        val result = TermuxCommandResult(
            commandPath = metadata?.first.orEmpty(),
            workdir = metadata?.second.orEmpty(),
            stdout = bundle?.getString(RESULT_STDOUT).orEmpty(),
            stderr = bundle?.getString(RESULT_STDERR).orEmpty(),
            exitCode = bundle?.takeIf { it.containsKey(RESULT_EXIT_CODE) }?.getInt(RESULT_EXIT_CODE),
            termuxErrorCode = bundle?.takeIf { it.containsKey(RESULT_ERR_CODE) }?.getInt(RESULT_ERR_CODE),
            termuxErrorMessage = bundle?.getString(RESULT_ERR),
            durationMs = System.currentTimeMillis() - startedAt,
            success = bundle != null &&
                !bundle.containsKey(RESULT_ERR_CODE) &&
                bundle.getInt(RESULT_EXIT_CODE, 0) == 0,
        )

        val deferred = pendingExecutions.remove(executionId)
        if (deferred == null) {
            Log.w(TAG, "Received Termux result for unknown executionId=$executionId")
            return
        }
        deferred.complete(result)
    }

    private fun extractResultBundle(extras: Bundle?): Bundle? {
        extras ?: return null
        val nested = extras.getBundle(RESULT_BUNDLE_KEY)
        return nested ?: extras
    }

    private fun cleanupExecution(executionId: Int) {
        pendingExecutions.remove(executionId)
        startTimes.remove(executionId)
        requestMetadata.remove(executionId)
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
