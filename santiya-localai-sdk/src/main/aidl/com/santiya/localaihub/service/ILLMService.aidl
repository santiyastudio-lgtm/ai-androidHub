package com.santiya.localaihub.service;

import android.os.ParcelFileDescriptor;
import com.santiya.localaihub.service.IDiffusionGenerationCallback;
import com.santiya.localaihub.service.IGgufGenerationCallback;
import com.santiya.localaihub.service.IModelLoadCallback;

interface ILLMService {

    String getRuntimeCapabilitiesJson();
    String listModelsJson();
    String listModelsJsonForLocale(String locale);
    String searchCatalogJson(String requestJson);
    String registerClientJson(String requestJson);
    String getModelManifestSchemaJson();
    String importModelManifestJson(String manifestJson);
    String downloadModelJson(String requestJson);
    String cancelDownloadJson(String modelId);
    String getDownloadStatusJson(String modelId);
    String preparePreferredModelJson(String capability);
    String runVisionJson(String requestJson);
    String runImageJson(String requestJson);
    String runWithModeJson(String requestJson);
    String getHttpApiStateJson();
    String getPreferredModelsJson();
    String setPreferredModelJson(String capability, String modelId);
    String getExternalAccessPolicyJson();
    String approveClientJson(String packageName);
    String revokeClientJson(String packageName);
    String listLanNodesJson();
    String getDistributedGgufPlanJson(String modelId);
    String getOrchestraConfigJson();
    String setOrchestraConfigJson(String configJson);

    void loadGgufModel(String modelPath, String modelName, String loadingParams, String inferenceParams, IModelLoadCallback callback);
    void loadGgufModelFromFd(in ParcelFileDescriptor pfd, String modelName, String loadingParams, String inferenceParams, IModelLoadCallback callback);
    void generateGguf(String prompt, int maxTokens, IGgufGenerationCallback callback);
    void stopGenerationGguf();
    void unloadModelGguf();
    String getModelInfoGguf();
    boolean setToolsJsonGguf(String toolsJson);
    void clearToolsGguf();

    boolean enableToolCallingGguf(String toolsJson, int grammarMode, boolean useTypedGrammar);
    void generateGgufMultiTurn(String messagesJson, int maxTokens, IGgufGenerationCallback callback);
    void setGrammarModeGguf(int mode);
    void setTypedGrammarGguf(boolean enabled);
    boolean isToolCallingSupportedGguf();

    boolean updateSamplerParamsGguf(String paramsJson);
    boolean setLogitBiasGguf(String biasJson);
    boolean loadControlVectorsGguf(String vectorsJson);
    boolean clearControlVectorGguf();

    long getStateSizeGguf();
    boolean stateSaveToFileGguf(String path);
    boolean stateLoadFromFileGguf(String path);

    void setSpeculativeDecodingGguf(boolean enabled, int nDraft, int ngramSize);
    void setPromptCacheDirGguf(String path);
    boolean warmUpGguf();
    boolean supportsThinkingGguf();
    void setThinkingEnabledGguf(boolean enabled);
    float getContextUsageGguf();

    String getContextInfoGguf(String prompt);

    boolean setPersonalityGguf(String personalityJson);
    boolean setMoodGguf(int mood);
    boolean setCustomMoodGguf(float tempMod, float topPMod, float repPenaltyMod);
    String getCharacterContextGguf();
    String buildPromptGguf(String userPrompt);
    boolean setUncensoredGguf(boolean enabled);
    boolean isUncensoredGguf();

    void loadUpscaler(String modelPath, IModelLoadCallback callback);
    void releaseUpscaler();

    void loadDiffusionModel(
        String name,
        String modelDir,
        int height,
        int width,
        int textEmbeddingSize,
        boolean runOnCpu,
        boolean useCpuClip,
        boolean isPony,
        int httpPort,
        boolean safetyMode,
        IModelLoadCallback callback
    );

    void generateDiffusionImage(
        String prompt,
        String negativePrompt,
        int steps,
        float cfgScale,
        long seed,
        int width,
        int height,
        String scheduler,
        boolean useOpenCL,
        String inputImage,
        String mask,
        float denoiseStrength,
        boolean showDiffusionProcess,
        int showDiffusionStride,
        IDiffusionGenerationCallback callback
    );

    void stopGenerationDiffusion();
    void restartDiffusionBackend(IModelLoadCallback callback);
    void stopDiffusionBackend();
    String getDiffusionBackendState();
    String getCurrentDiffusionModel();
}
