package com.santiya.localaihub.distributed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpPipelineClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) : TensorTransport {

    override val protocol: DistributedTransportProtocol
        get() = DistributedTransportProtocol.NSD_HTTP

    override fun isAvailable(): Boolean = true

    fun handshake(baseUrl: String, request: DistributedSessionHandshake, bearerToken: String? = null): String {
        return postJson(baseUrl + DistributedProtocolIds.HTTP_HANDSHAKE, json.encodeToString(request), bearerToken)
    }

    fun pipelineStep(baseUrl: String, request: PipelineStepRequest, bearerToken: String? = null): String {
        return postJson(baseUrl + DistributedProtocolIds.HTTP_PIPELINE_STEP, json.encodeToString(request), bearerToken)
    }

    private fun postJson(url: String, body: String, bearerToken: String?): String {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))

        if (!bearerToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $bearerToken")
        }

        client.newCall(builder.build()).execute().use { response ->
            return response.body?.string().orEmpty()
        }
    }
}
