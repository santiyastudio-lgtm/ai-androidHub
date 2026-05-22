package com.santiya.localaihub.openclaw

import java.util.Locale

object OfficialOpenClawGatewayTermux {
    const val DEFAULT_PORT = 18789
    const val TERMUX_SHELL = "/data/data/com.termux/files/usr/bin/sh"

    fun bootstrapScript(
        port: Int = DEFAULT_PORT,
        token: String = "",
    ): String {
        val safePort = port.coerceIn(1024, 65535)
        return """
            #!/data/data/com.termux/files/usr/bin/sh
            set -eu
            export OPENCLAW_GATEWAY_PORT="${safePort}"
            ${tokenExport(token)}

            if ! command -v pkg >/dev/null 2>&1; then
              echo "This bootstrap must run inside Termux."
              exit 10
            fi

            pkg update -y
            pkg install -y nodejs-lts git

            if ! command -v openclaw >/dev/null 2>&1; then
              npm install -g openclaw@latest
            fi

            mkdir -p "${'$'}HOME/.openclaw"
            CONFIG="${'$'}HOME/.openclaw/openclaw.json"
            if [ ! -s "${'$'}CONFIG" ]; then
              printf '%s\n' '{"gateway":{"mode":"local","bind":"loopback"}}' > "${'$'}CONFIG"
            fi

            openclaw --version || true
            echo "Official OpenClaw Gateway bootstrap complete."
            echo "Next: run openclaw gateway --bind loopback --port ${safePort} --allow-unconfigured"
        """.trimIndent() + "\n"
    }

    fun startScript(
        port: Int = DEFAULT_PORT,
        token: String = "",
    ): String {
        val safePort = port.coerceIn(1024, 65535)
        return """
            #!/data/data/com.termux/files/usr/bin/sh
            set -eu
            export OPENCLAW_GATEWAY_PORT="${safePort}"
            ${tokenExport(token)}

            if ! command -v openclaw >/dev/null 2>&1; then
              echo "openclaw CLI is not installed. Run the bootstrap first."
              exit 20
            fi

            mkdir -p "${'$'}HOME/.openclaw/logs"
            PID_FILE="${'$'}HOME/.openclaw/gateway-${safePort}.pid"
            LOG_FILE="${'$'}HOME/.openclaw/logs/gateway-${safePort}.log"

            if [ -s "${'$'}PID_FILE" ] && kill -0 "$(cat "${'$'}PID_FILE")" >/dev/null 2>&1; then
              echo "Official OpenClaw Gateway already running. pid=$(cat "${'$'}PID_FILE")"
              echo "Endpoint: http://127.0.0.1:${safePort}"
              exit 0
            fi

            if [ -n "${'$'}{OPENCLAW_GATEWAY_TOKEN:-}" ]; then
              nohup openclaw gateway --bind loopback --port "${safePort}" --auth token --token "${'$'}OPENCLAW_GATEWAY_TOKEN" --allow-unconfigured --verbose > "${'$'}LOG_FILE" 2>&1 &
            else
              nohup openclaw gateway --bind loopback --port "${safePort}" --allow-unconfigured --verbose > "${'$'}LOG_FILE" 2>&1 &
            fi

            PID="${'$'}!"
            echo "${'$'}PID" > "${'$'}PID_FILE"
            sleep 2
            if kill -0 "${'$'}PID" >/dev/null 2>&1; then
              echo "Official OpenClaw Gateway started locally in Termux."
              echo "Endpoint: http://127.0.0.1:${safePort}"
              echo "WebSocket: ws://127.0.0.1:${safePort}"
              echo "PID: ${'$'}PID"
              echo "Log: ${'$'}LOG_FILE"
              tail -n 20 "${'$'}LOG_FILE" 2>/dev/null || true
            else
              echo "Official OpenClaw Gateway exited during startup."
              tail -n 80 "${'$'}LOG_FILE" 2>/dev/null || true
              exit 21
            fi
        """.trimIndent() + "\n"
    }

    fun statusScript(
        port: Int = DEFAULT_PORT,
        token: String = "",
    ): String {
        val safePort = port.coerceIn(1024, 65535)
        val tokenArg = if (token.isBlank()) "" else " --token ${shellSingleQuote(token)}"
        return """
            #!/data/data/com.termux/files/usr/bin/sh
            set -eu
            if ! command -v openclaw >/dev/null 2>&1; then
              echo "openclaw CLI is not installed."
              exit 20
            fi
            openclaw gateway status --url "ws://127.0.0.1:${safePort}"${tokenArg} --require-rpc --json
        """.trimIndent() + "\n"
    }

    fun gatewayCallScript(
        method: String,
        paramsJson: String,
        port: Int = DEFAULT_PORT,
        token: String = "",
    ): String {
        val safeMethod = method.trim()
        require(safeMethod.matches(Regex("[A-Za-z0-9_.:-]{1,120}"))) {
            "OpenClaw Gateway RPC method contains unsupported characters."
        }
        val safeParams = paramsJson.trim().ifBlank { "{}" }.take(16_000)
        val safePort = port.coerceIn(1024, 65535)
        val tokenArg = if (token.isBlank()) "" else " --token ${shellSingleQuote(token)}"
        val marker = "OPENCLAW_PARAMS_${safeMethod.hashCode().toString(16).uppercase(Locale.US)}"
        return """
            #!/data/data/com.termux/files/usr/bin/sh
            set -eu
            if ! command -v openclaw >/dev/null 2>&1; then
              echo "openclaw CLI is not installed."
              exit 20
            fi
            PARAMS_FILE="${'$'}TMPDIR/openclaw-gateway-params.json"
            cat > "${'$'}PARAMS_FILE" <<'${marker}'
            ${safeParams}
            ${marker}
            openclaw gateway call ${shellSingleQuote(safeMethod)} --url "ws://127.0.0.1:${safePort}"${tokenArg} --params "$(cat "${'$'}PARAMS_FILE")" --expect-final --json
        """.trimIndent() + "\n"
    }

    private fun tokenExport(token: String): String =
        if (token.isBlank()) {
            "export OPENCLAW_GATEWAY_TOKEN=\"\${OPENCLAW_GATEWAY_TOKEN:-}\""
        } else {
            "export OPENCLAW_GATEWAY_TOKEN=${shellSingleQuote(token)}"
        }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
