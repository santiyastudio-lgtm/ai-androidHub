package com.santiya.localaihub.distributedruntime

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.arm.aichat.internal.InferenceEngineImpl
import com.santiya.localaihub.distributedruntime.internal.GgufShardTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

data class GgufShardRequest(
    val inputModelPath: String,
    val outputPath: String,
    val maxTensorsPerShard: Int = 128,
    val maxShardSizeBytes: Long? = null,
    val noTensorFirstSplit: Boolean = false,
    val dryRun: Boolean = false,
)

data class GgufShardFile(
    val path: String,
    val sizeBytes: Long,
    val ordinal: Int,
)

data class GgufShardManifest(
    val inputModelPath: String,
    val outputPath: String,
    val splitMode: String,
    val splitValue: String,
    val dryRun: Boolean,
    val exitCode: Int,
    val shardFiles: List<GgufShardFile>,
)

class SourceDistributedRuntime private constructor(
    private val engine: InferenceEngineImpl,
) {
    val state: StateFlow<InferenceEngine.State> = engine.state

    suspend fun loadModel(modelPath: String, systemPrompt: String? = null) {
        if (state.value.isModelLoaded) {
            engine.cleanUp()
        }
        engine.loadModel(modelPath)
        if (!systemPrompt.isNullOrBlank()) {
            engine.setSystemPrompt(systemPrompt)
        }
    }

    fun generate(prompt: String, predictLength: Int = InferenceEngine.DEFAULT_PREDICT_LENGTH): Flow<String> =
        engine.sendUserPrompt(prompt, predictLength)

    suspend fun getStateSizeBytes(): Long = engine.getStateSizeBytes()

    suspend fun saveStateToFile(path: String): Boolean = engine.saveStateFile(path)

    suspend fun loadStateFromFile(path: String): Boolean = engine.loadStateFile(path)

    suspend fun getModelInfoJson(): String = engine.modelInfoJson()

    fun unload() = engine.cleanUp()

    fun stopGeneration() = engine.cancelGeneration()

    fun destroy() = engine.destroy()

    suspend fun prepareShards(request: GgufShardRequest): GgufShardManifest = withContext(Dispatchers.IO) {
        val inputFile = File(request.inputModelPath)
        require(inputFile.exists()) { "GGUF file not found: ${request.inputModelPath}" }
        require(inputFile.isFile) { "GGUF source is not a file: ${request.inputModelPath}" }

        val outputFile = File(request.outputPath)
        outputFile.parentFile?.mkdirs()

        val maxSizeArg = request.maxShardSizeBytes?.let(::toSplitSizeArg)
        val exitCode = GgufShardTool.run(
            inputPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            maxTensors = request.maxTensorsPerShard,
            maxSizeArg = maxSizeArg,
            noTensorFirstSplit = request.noTensorFirstSplit,
            dryRun = request.dryRun,
        )
        check(exitCode == 0) { "gguf-split failed with exit code $exitCode" }

        val shardFiles = if (request.dryRun) {
            emptyList()
        } else {
            findShardFiles(outputFile).mapIndexed { index, file ->
                GgufShardFile(
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    ordinal = index,
                )
            }
        }

        GgufShardManifest(
            inputModelPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            splitMode = if (maxSizeArg != null) "size" else "tensor_count",
            splitValue = maxSizeArg ?: request.maxTensorsPerShard.toString(),
            dryRun = request.dryRun,
            exitCode = exitCode,
            shardFiles = shardFiles,
        )
    }

    companion object {
        fun create(context: Context): SourceDistributedRuntime =
            SourceDistributedRuntime(InferenceEngineImpl.getInstance(context.applicationContext))

        private fun findShardFiles(outputFile: File): List<File> {
            val parent = outputFile.parentFile ?: return emptyList()
            val extension = outputFile.extension.ifBlank { "gguf" }
            val baseName = outputFile.nameWithoutExtension
            val shardRegex = Regex("^${Regex.escape(baseName)}-\\d{5}-of-\\d{5}\\.${Regex.escape(extension)}$")
            return parent.listFiles()
                ?.filter { it.isFile && shardRegex.matches(it.name) }
                ?.sortedBy { it.name }
                .orEmpty()
        }

        private fun toSplitSizeArg(sizeBytes: Long): String {
            require(sizeBytes > 0) { "Shard size must be positive." }
            val inGigabytes = sizeBytes / 1_000_000_000L
            if (inGigabytes > 0 && inGigabytes * 1_000_000_000L == sizeBytes) {
                return "${inGigabytes}G"
            }
            val inMegabytes = ceil(sizeBytes / 1_000_000.0).toLong().coerceAtLeast(1L)
            return "${inMegabytes}M"
        }
    }
}
