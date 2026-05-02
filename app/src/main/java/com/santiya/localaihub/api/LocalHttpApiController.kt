package com.santiya.localaihub.api

import com.santiya.localaihub.runtime.HttpApiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

class LocalHttpApiController {
    private val json = Json { encodeDefaults = true }
    private val random = SecureRandom()

    @Volatile
    private var token: String = newToken()

    @Volatile
    private var state = HttpApiState(enabled = false, port = null)

    fun stateJson(): String = json.encodeToString(state)

    fun currentTokenForDisplay(): String = token

    fun rotateToken(): String {
        token = newToken()
        return token
    }

    fun disabledReason(): String =
        "Local HTTP API is disabled by default. Enable it from the developer screen, bind only to 127.0.0.1, and require the generated token for every request."

    private fun newToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
