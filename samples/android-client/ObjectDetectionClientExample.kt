package com.example.santiyaclient

import android.content.Context
import com.santiya.localaihub.sdk.SantiyaLocalAiClient

class ObjectDetectionClientExample(private val context: Context) {
    suspend fun inspectHub(): String {
        SantiyaLocalAiClient.bind(context).use { client ->
            return client.getRuntimeCapabilitiesJson()
        }
    }

    suspend fun runObjectDetectionRequest(imageUri: String): String {
        val requestJson = """
            {
              "modelId": "onnx/ssd-mobilenet-v1-int8",
              "imageUri": "$imageUri",
              "maxDetections": 20,
              "scoreThreshold": 0.35
            }
        """.trimIndent()

        SantiyaLocalAiClient.bind(context).use { client ->
            return client.runVisionJson(requestJson)
        }
    }
}
