This directory is reserved for pinned upstream vendor trees.

Current repository state:

- upstream runtime sources are not copied here yet;
- pinned source references live in `VENDOR_LOCK.json`;
- the current blocker is environmental, not architectural:
  - this machine can validate the Rust source graph with `cargo check --release`
  - it cannot finish native linking without MSVC CRT import libraries

Planned vendor population order:

1. `llama.cpp`
2. `onnxruntime`
3. `onnxruntime-extensions`
4. `stable-diffusion.cpp`
5. `piper`

Local source reused separately:

- `../..\\neuron-packet\\src\\main\\cpp`
