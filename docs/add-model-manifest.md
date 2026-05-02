# Add A Model Manifest

`SantiyaLocalAiHub` uses model manifests to describe models that are not hard-coded into the app catalog.

Example:

```json
{
  "id": "onnx/ssd-mobilenet-v1-int8",
  "name": "SSD MobileNet V1 INT8 Object Detection",
  "source": {
    "type": "huggingface",
    "repo": "onnxmodelzoo/ssd_mobilenet_v1_12-int8"
  },
  "files": [
    {
      "path": "ssd_mobilenet_v1_12-int8.onnx",
      "sizeBytes": 9200000,
      "sha256": null,
      "role": "model"
    }
  ],
  "capabilities": ["object_detection"],
  "runtime": "onnxruntime",
  "license": "MIT",
  "minRamMb": 2048,
  "tags": ["mobile", "vision"],
  "experimental": false,
  "notes": "Mobile-friendly object detector."
}
```

Supported capability values:

```text
chat
embeddings
object_detection
image_segmentation
image_generation
upscale
tts
rag
video_generation
3d_generation
```

Do not mark `video_generation` or `3d_generation` as production-ready unless a concrete Android runtime adapter is installed and tested on target hardware.
