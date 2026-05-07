package com.santiya.localaihub.worker

import android.content.Context
import android.net.Uri
import com.santiya.localaihub.global.AppPaths
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.storage.SharedModelLibrary
import java.io.File

object ImportedModelInstaller {

    suspend fun installFromUri(
        context: Context,
        sourceUri: Uri,
        model: Model,
        analysis: ImportAnalysisResult,
    ): InstalledImportResult {
        val staged = ContentUriIO.stageToTempFile(
            context = context,
            uri = sourceUri,
            preferredName = model.modelName,
            sourceKind = ImportSourceKind.SAF_CONTENT_URI,
        )
        return installFromFile(
            context = context,
            sourceFile = staged.stagedFile,
            model = model.copy(
                modelName = staged.displayName.removeSuffix(".gguf").ifBlank { model.modelName },
                fileSize = staged.fileSize.takeIf { it > 0L } ?: model.fileSize
            ),
            analysis = analysis,
            sourceKind = staged.sourceKind,
            seedSession = staged.session,
            sourceContentUri = sourceUri.toString(),
        )
    }

    suspend fun installFromFile(
        context: Context,
        sourceFile: File,
        model: Model,
        analysis: ImportAnalysisResult,
        sourceKind: ImportSourceKind = ImportSourceKind.DIRECT_FILE,
        seedSession: ImportSessionResult? = null,
        sourceContentUri: String? = seedSession?.sourceContentUri,
    ): InstalledImportResult {
        if (analysis.providerType == com.santiya.localaihub.models.enums.ProviderType.GGUF) {
            val installed = SharedModelLibrary.installManagedGgufFromFile(
                context = context,
                sourceFile = sourceFile,
                model = model,
                sourceContentUri = sourceContentUri,
            )
            return InstalledImportResult(
                model = installed,
                session = (seedSession ?: ImportSessionResult(sourceKind = sourceKind)).copy(
                    sourceKind = sourceKind,
                    sourceContentUri = sourceContentUri,
                    finalInstalledPath = installed.modelPath,
                    detectedFormat = analysis.detectedFormat,
                    compatibility = analysis.compatibility,
                    conversionSuggestion = analysis.conversionSuggestion,
                    readAttempts = (seedSession?.readAttempts ?: emptyList()) + ImportReadAttempt(
                        method = ImportReadMethod.DIRECT_FILE_COPY,
                        success = true,
                        detail = "Installed GGUF from ${sourceFile.absolutePath}"
                    )
                )
            )
        }
        val target = targetPath(context, model, analysis)
        target.parentFile?.mkdirs()
        if (sourceFile.isDirectory) {
            sourceFile.copyRecursively(target, overwrite = true)
        } else {
            sourceFile.copyTo(target, overwrite = true)
        }
        val installed = model.copy(
            modelPath = target.absolutePath,
            pathType = if (target.isDirectory) PathType.DIRECTORY else PathType.FILE,
            providerType = analysis.providerType,
        )
        return InstalledImportResult(
            model = installed,
            session = (seedSession ?: ImportSessionResult(sourceKind = sourceKind)).copy(
                sourceKind = sourceKind,
                finalInstalledPath = installed.modelPath,
                detectedFormat = analysis.detectedFormat,
                compatibility = analysis.compatibility,
                conversionSuggestion = analysis.conversionSuggestion,
                readAttempts = (seedSession?.readAttempts ?: emptyList()) + ImportReadAttempt(
                    method = ImportReadMethod.DIRECT_FILE_COPY,
                    success = true,
                    detail = "Installed asset from ${sourceFile.absolutePath}"
                )
            )
        )
    }

    private fun targetPath(context: Context, model: Model, analysis: ImportAnalysisResult): File {
        return when (analysis.providerType) {
            com.santiya.localaihub.models.enums.ProviderType.GGUF ->
                File(AppPaths.models(context).also { it.mkdirs() }, model.modelName)
            com.santiya.localaihub.models.enums.ProviderType.DIFFUSION ->
                AppPaths.modelDir(context, model.id)
            com.santiya.localaihub.models.enums.ProviderType.TTS ->
                AppPaths.ttsModel(context)
            com.santiya.localaihub.models.enums.ProviderType.TTS_PIPER ->
                AppPaths.ttsVoicePack(context, model.id)
            com.santiya.localaihub.models.enums.ProviderType.ONNX ->
                File(AppPaths.rawAssetDir(context, model.id), model.modelName)
            com.santiya.localaihub.models.enums.ProviderType.RAW_ASSET ->
                File(AppPaths.rawAssetDir(context, model.id), model.modelName)
            else ->
                File(AppPaths.rawAssetDir(context, model.id), model.modelName)
        }
    }
}
