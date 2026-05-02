# Model Compatibility

Runtime support in this build:

| Capability | Runtime | Status |
| --- | --- | --- |
| Chat | GGUF | Ready through existing AIDL generation callbacks |
| Image generation | Stable Diffusion | Ready through existing diffusion callbacks |
| TTS | Bundled TTS engine | Ready inside the app runtime |
| Object detection | ONNX Runtime | Adapter contract added, model-specific execution pending |
| Image segmentation | ONNX/GGUF | Adapter contract added, model-specific execution pending |
| Video generation | Experimental | Catalog and manifest only |
| 3D generation | Experimental | Catalog and manifest only |

Compatibility checks should consider:
- Android API level.
- ABI: `arm64-v8a` or `x86_64`.
- Available RAM and storage.
- Quantization and model size.
- License and gated model access.
- Thermal limits during long-running generation.

Recommended first external-app integration path:
1. Use AIDL to discover capabilities.
2. Let the user pick/import a model in `SantiyaLocalAiHub`.
3. Call existing streaming GGUF or diffusion APIs for ready runtimes.
4. Use manifest metadata for ONNX vision until the concrete adapter is completed.
