# Distributed GGUF Module

## What is implemented now

- Heuristic GGUF model profiling for layer count, hidden size, KV heads and model footprint.
- Single-device sequential offload planning:
  - resident layer window size
  - estimated KV cache pressure
  - estimated flash traffic per token
  - suggested KV compression mode
- Multi-node partition planning:
  - contiguous layer span assignments
  - root/worker roles
  - hidden-state handoff estimate
  - preferred future transport = `libp2p` with Android NSD discovery
- Local node resource snapshot from Android hardware and memory state.
- NSD advertiser/discovery scaffold for local node announcements.
- Protocol contracts for:
  - session handshake
  - hidden-state envelope
  - pipeline step request/response
  - future libp2p protocol IDs and HTTP endpoints
- Extended Hub API:
  - `listLanNodesJson()`
  - `getDistributedGgufPlanJson(modelId)`

## Why transport is not fully live yet

The current JNI GGUF runtime is still monolithic. It can load a whole model and run generation, but it does not expose:

- execute layer range
- serialize hidden state
- deserialize hidden state
- load/evict layer window
- remote segment resume

Without those hooks, any claim of "real distributed GGUF execution" would be dishonest.

## Native hooks required next

For sequential offload on one device:

- `gguf_load_layer_window(startLayer, endLayer)`
- `gguf_evict_layer_window(startLayer, endLayer)`
- `gguf_execute_layer_range(startLayer, endLayer, hiddenStateHandle, kvCacheHandle)`

For LAN pipeline parallelism:

- `gguf_serialize_hidden_state()`
- `gguf_deserialize_hidden_state()`
- `gguf_reserve_kv_cache(compressionMode)`
- `remote_segment_execute(requestEnvelope)`
- `pipeline_session_resume(sessionId, stepId)`

## Reference takeaways used for this design

- `distributed-llama`: root/worker execution model and contiguous neural-network segment ownership.
- `petals`: private swarm thinking, block-span routing and server-to-server continuation.
- `jvm-libp2p`: JVM/Android portability path for the future transport layer.

## Recommended next execution pass

1. Add JNI layer-range hooks to the GGUF engine.
2. Start with local sequential offload only.
3. Add hidden-state serialization + LAN transport.
4. Wire Android NSD discovery to live peer inventory.
5. Add libp2p stream protocol for pipeline requests.

## Current honest status

- Planning layer: implemented
- Node snapshot layer: implemented
- NSD advertisement/discovery scaffold: implemented
- SDK/API contract for distributed plan: implemented
- Real JNI layer-range execution: not implemented
- Real hidden-state transport: not implemented
- Real LAN worker execution: not implemented
