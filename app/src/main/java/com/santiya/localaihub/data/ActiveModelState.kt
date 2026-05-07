package com.santiya.localaihub.data

import kotlinx.serialization.Serializable

@Serializable
enum class ActiveModelInstallStage {
    NONE,
    IMPORTED,
    INDEXED,
    SELECTED,
    ACTIVATED,
}

@Serializable
enum class ActiveModelActivationState {
    IDLE,
    ACTIVE,
    BLOCKED,
}

@Serializable
data class ActiveModelState(
    val modelId: String? = null,
    val modelName: String? = null,
    val providerTypeName: String? = null,
    val installStage: ActiveModelInstallStage = ActiveModelInstallStage.NONE,
    val activationState: ActiveModelActivationState = ActiveModelActivationState.IDLE,
    val activationReason: String? = null,
) {
    val hasSelection: Boolean
        get() = !modelId.isNullOrBlank() && !modelName.isNullOrBlank()
}
