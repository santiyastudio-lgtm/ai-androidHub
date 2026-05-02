# AIDL Integration

`SantiyaLocalAiHub` exposes its local runtime through `com.santiya.localaihub.service.LLMService`.

Client apps must request:

```xml
<uses-permission android:name="com.santiya.localaihub.permission.BIND_LLM_SERVICE" />
```

Bind with the SDK wrapper:

```kotlin
val client = SantiyaLocalAiClient.bind(context)
val catalogJson = client.listModelsJson()
val capabilitiesJson = client.getRuntimeCapabilitiesJson()
```

For chat generation, use the raw AIDL service while the stable SDK streaming wrapper is expanded:

```kotlin
client.rawService().generateGguf("Explain local AI on Android", 256, callback)
```

Use the JSON endpoints for stable cross-version discovery:

```kotlin
client.getModelManifestSchemaJson()
client.importModelManifestJson(manifestJson)
client.runVisionJson(requestJson)
client.getHttpApiStateJson()
```

Current execution status:
- GGUF chat: use existing streaming AIDL callbacks.
- Stable Diffusion image generation: use existing diffusion AIDL callbacks.
- ONNX vision: manifest/API contract exists; concrete execution adapter must be completed per model family.
- Video/3D: catalog and experimental adapter contract only.
