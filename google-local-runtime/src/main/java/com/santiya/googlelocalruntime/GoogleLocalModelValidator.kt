package com.santiya.googlelocalruntime

import java.io.File

object GoogleLocalModelValidator {
    private const val MIN_LITERT_MODEL_BYTES = 64 * 1024L

    fun validateLiteRtModel(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "LiteRT-LM model file was not found." }
        require(file.isFile) { "LiteRT-LM model path is not a file." }
        require(file.length() >= MIN_LITERT_MODEL_BYTES) {
            "LiteRT-LM model file looks incomplete or invalid."
        }
    }
}
