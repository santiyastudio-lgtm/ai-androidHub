package com.santiya.localaihub.distributedruntime.internal

internal object GgufShardTool {
    init {
        System.loadLibrary("ai-chat")
    }

    private external fun splitModel(
        inputPath: String,
        outputPath: String,
        maxTensors: Int,
        maxSizeArg: String?,
        noTensorFirstSplit: Boolean,
        dryRun: Boolean,
    ): Int

    fun run(
        inputPath: String,
        outputPath: String,
        maxTensors: Int,
        maxSizeArg: String?,
        noTensorFirstSplit: Boolean,
        dryRun: Boolean,
    ): Int = splitModel(
        inputPath = inputPath,
        outputPath = outputPath,
        maxTensors = maxTensors,
        maxSizeArg = maxSizeArg,
        noTensorFirstSplit = noTensorFirstSplit,
        dryRun = dryRun,
    )
}
