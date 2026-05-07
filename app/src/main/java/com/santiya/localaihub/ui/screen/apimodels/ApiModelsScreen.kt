package com.santiya.localaihub.ui.screen.apimodels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private enum class ApiProvider(
    val label: String,
    val defaultModel: String,
) {
    OPENAI("OpenAI", "gpt-4.1-mini"),
    OPENROUTER("OpenRouter", "openai/gpt-4.1-mini"),
    DEEPSEEK("DeepSeek", "deepseek-chat"),
    ANTHROPIC("Claude", "claude-sonnet-4-5"),
    GEMINI("Gemini", "gemini-2.5-flash")
}

private val apiClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiModelsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by rememberSaveable { mutableStateOf(ApiProvider.OPENAI) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(ApiProvider.OPENAI.defaultModel) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var response by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val titleText = localizedText("API-модели", "API Models")
    val backText = localizedText("Назад", "Back")
    val introText = localizedText(
        "Облачные модели через API. Ключ хранится только в состоянии этого экрана и не записывается в постоянное хранилище.",
        "Cloud models over API. The key stays only in this screen state and is not persisted."
    )
    val modelText = localizedText("Модель", "Model")
    val promptText = localizedText("Запрос", "Prompt")
    val failedResponseText = localizedText(
        context,
        "Не удалось получить ответ от API.",
        "Failed to get a response from the API."
    )
    val sendingText = localizedText("Отправляю...", "Sending...")
    val sendRequestText = localizedText("Отправить запрос", "Send request")
    val responseTitleText = localizedText("Ответ модели", "Model response")
    val emptyResponseText = localizedText(
        "Здесь появится ответ после отправки запроса.",
        "The answer will appear here after you send a request."
    )

    fun switchProvider(next: ApiProvider) {
        provider = next
        model = next.defaultModel
        response = ""
        error = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = backText,
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = introText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ApiProvider.entries.forEach { item ->
                            FilterChip(
                                selected = provider == item,
                                onClick = { switchProvider(item) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(localizedText("API key", "API key")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(modelText) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        label = { Text(promptText) },
                        minLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    Button(
                        onClick = {
                            if (apiKey.isBlank() || model.isBlank() || prompt.isBlank() || isSending) {
                                return@Button
                            }
                            isSending = true
                            response = ""
                            error = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        sendCloudPrompt(
                                            provider = provider,
                                            apiKey = apiKey.trim(),
                                            model = model.trim(),
                                            prompt = prompt.trim()
                                        )
                                    }
                                }.onSuccess {
                                    response = it
                                }.onFailure {
                                    error = it.message ?: failedResponseText
                                }
                                isSending = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSending
                    ) {
                        Text(if (isSending) sendingText else sendRequestText)
                    }
                }
            }

            error?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = responseTitleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = response.ifBlank { emptyResponseText },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun sendCloudPrompt(
    provider: ApiProvider,
    apiKey: String,
    model: String,
    prompt: String,
): String {
    return when (provider) {
        ApiProvider.OPENAI ->
            requestOpenAiCompatible(
                url = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
            )

        ApiProvider.OPENROUTER ->
            requestOpenAiCompatible(
                url = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://santiya.local",
                    "X-Title" to "SantiyaLocalAiHub"
                )
            )

        ApiProvider.DEEPSEEK ->
            requestOpenAiCompatible(
                url = "https://api.deepseek.com/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
            )

        ApiProvider.ANTHROPIC ->
            requestAnthropic(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
            )

        ApiProvider.GEMINI ->
            requestGemini(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
            )
    }
}

private fun requestOpenAiCompatible(
    url: String,
    apiKey: String,
    model: String,
    prompt: String,
    extraHeaders: Map<String, String> = emptyMap(),
): String {
    val body = JSONObject()
        .put("model", model)
        .put("stream", false)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", prompt)
            )
        )
        .toString()

    val builder = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
    extraHeaders.forEach { (name, value) -> builder.addHeader(name, value) }

    val request = builder.post(body.toRequestBody("application/json".toMediaType())).build()
    apiClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${extractErrorText(raw)}")
        }
        val json = JSONObject(raw)
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty API response")
    }
}

private fun requestAnthropic(
    apiKey: String,
    model: String,
    prompt: String,
): String {
    val body = JSONObject()
        .put("model", model)
        .put("max_tokens", 1024)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", prompt)
                        )
                    )
            )
        )
        .toString()

    val request = Request.Builder()
        .url("https://api.anthropic.com/v1/messages")
        .addHeader("x-api-key", apiKey)
        .addHeader("anthropic-version", "2023-06-01")
        .addHeader("content-type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

    apiClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${extractErrorText(raw)}")
        }
        val json = JSONObject(raw)
        return json.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty API response")
    }
}

private fun requestGemini(
    apiKey: String,
    model: String,
    prompt: String,
): String {
    val body = JSONObject()
        .put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        )
        .toString()

    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

    apiClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${extractErrorText(raw)}")
        }
        val json = JSONObject(raw)
        return json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty API response")
    }
}

private fun extractErrorText(raw: String): String {
    if (raw.isBlank()) return "Empty error body"
    return runCatching {
        val json = JSONObject(raw)
        when {
            json.has("error") && json.opt("error") is JSONObject ->
                json.getJSONObject("error").optString("message", raw)

            json.has("message") -> json.optString("message", raw)
            else -> raw
        }
    }.getOrDefault(raw)
}
