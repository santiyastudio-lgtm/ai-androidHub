mod json;

use json::{object, parse_json, JsonValue};
use std::collections::{BTreeMap, HashMap};
use std::env;
use std::ffi::c_char;
use std::fs;
use std::io::{Read, Write};
use std::net::{Shutdown, TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const APP_NAME: &str = "SantiyaLocalAiHub Windows Core";
const APP_VERSION: &str = env!("CARGO_PKG_VERSION");
const DEFAULT_PORT: u16 = 17861;
const DEFAULT_ANDROID_NODE_PORT: u16 = 17888;
const DEFAULT_HUB_COMPAT_PORT: u16 = 17860;
const NOTES_FILE_NAME: &str = "notes.json";
const RAGS_FILE_NAME: &str = "rags.json";
const SETTINGS_FILE_NAME: &str = "settings.json";
const MAX_HTTP_BODY_BYTES: usize = 2 * 1024 * 1024;

static ID_COUNTER: AtomicU64 = AtomicU64::new(1);
static CORE_VERSION_CSTR: &[u8] = b"0.1.0\0";

#[no_mangle]
pub extern "C" fn santiya_localai_core_version() -> *const c_char {
    CORE_VERSION_CSTR.as_ptr() as *const c_char
}

#[no_mangle]
pub extern "C" fn santiya_localai_core_default_port() -> u16 {
    DEFAULT_PORT
}

pub fn main_entry() -> Result<(), String> {
    let args = Arguments::parse(env::args().skip(1).collect());
    if args.help {
        println!("{}", Arguments::usage());
        return Ok(());
    }

    let mut state = AppState::load(args)?;
    state.reload();
    let shared = Arc::new(Mutex::new(state));
    let port = shared.lock().map_err(|_| "State lock poisoned".to_string())?.settings.port;
    let listener = TcpListener::bind(("127.0.0.1", port))
        .map_err(|error| format!("Failed to bind {APP_NAME} on 127.0.0.1:{port}: {error}"))?;
    println!("{APP_NAME} running on http://127.0.0.1:{port}/");
    for connection in listener.incoming() {
        match connection {
            Ok(stream) => {
                let state = Arc::clone(&shared);
                thread::spawn(move || {
                    let _ = handle_connection(stream, state);
                });
            }
            Err(error) => eprintln!("Incoming connection failed: {error}"),
        }
    }
    Ok(())
}

#[derive(Clone)]
struct Arguments {
    port: u16,
    app_home: PathBuf,
    models_dir: PathBuf,
    peers_file: PathBuf,
    pairing_token: String,
    help: bool,
}

impl Arguments {
    fn parse(args: Vec<String>) -> Self {
        let app_home = default_core_home();
        let mut parsed = Self {
            port: DEFAULT_PORT,
            app_home: app_home.clone(),
            models_dir: app_home.join("models"),
            peers_file: app_home.join("peers.csv"),
            pairing_token: String::new(),
            help: false,
        };
        for arg in args {
            let lower = arg.to_lowercase();
            if matches!(lower.as_str(), "--help" | "-h" | "/?") {
                parsed.help = true;
            } else if let Some(value) = arg.strip_prefix("--port=") {
                parsed.port = value.parse::<u16>().unwrap_or(DEFAULT_PORT);
            } else if let Some(value) = arg.strip_prefix("--app-home=") {
                parsed.app_home = PathBuf::from(value);
            } else if let Some(value) = arg.strip_prefix("--models-dir=") {
                parsed.models_dir = PathBuf::from(value);
            } else if let Some(value) = arg.strip_prefix("--peers-file=") {
                parsed.peers_file = PathBuf::from(value);
            } else if let Some(value) = arg.strip_prefix("--pairing-token=") {
                parsed.pairing_token = value.trim().to_string();
            }
        }
        if parsed.models_dir.as_os_str().is_empty() {
            parsed.models_dir = parsed.app_home.join("models");
        }
        if parsed.peers_file.as_os_str().is_empty() {
            parsed.peers_file = parsed.app_home.join("peers.csv");
        }
        parsed
    }

    fn usage() -> String {
        [
            APP_NAME,
            "",
            "Flags:",
            "  --port=17861",
            "  --app-home=C:\\path\\to\\windows-core",
            "  --models-dir=C:\\path\\to\\models",
            "  --peers-file=C:\\path\\to\\peers.csv",
            "  --pairing-token=shared-lan-token",
            "  --help",
        ]
        .join("\n")
    }
}

#[derive(Clone)]
struct PreferredModelMap {
    chat_model_id: String,
    vision_model_id: String,
    image_generation_model_id: String,
    video_generation_model_id: String,
    tts_model_id: String,
    files_model_id: String,
    assistant_live_model_id: String,
}

impl PreferredModelMap {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("chatModelId", string_or_null(&self.chat_model_id)),
            ("visionModelId", string_or_null(&self.vision_model_id)),
            ("imageGenerationModelId", string_or_null(&self.image_generation_model_id)),
            ("videoGenerationModelId", string_or_null(&self.video_generation_model_id)),
            ("ttsModelId", string_or_null(&self.tts_model_id)),
            ("filesModelId", string_or_null(&self.files_model_id)),
            ("assistantLiveModelId", string_or_null(&self.assistant_live_model_id)),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            chat_model_id: string_field(value, "chatModelId"),
            vision_model_id: string_field(value, "visionModelId"),
            image_generation_model_id: string_field(value, "imageGenerationModelId"),
            video_generation_model_id: string_field(value, "videoGenerationModelId"),
            tts_model_id: string_field(value, "ttsModelId"),
            files_model_id: string_field(value, "filesModelId"),
            assistant_live_model_id: string_field(value, "assistantLiveModelId"),
        }
    }
}

impl Default for PreferredModelMap {
    fn default() -> Self {
        Self {
            chat_model_id: String::new(),
            vision_model_id: String::new(),
            image_generation_model_id: String::new(),
            video_generation_model_id: String::new(),
            tts_model_id: String::new(),
            files_model_id: String::new(),
            assistant_live_model_id: String::new(),
        }
    }
}

#[derive(Clone)]
struct ApprovedClientApp {
    package_name: String,
    app_label: String,
    approved_at_epoch_ms: u64,
    client_id: String,
    api_key_preview: String,
    api_key_hash: String,
    scopes: Vec<String>,
}

impl ApprovedClientApp {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("packageName", JsonValue::String(self.package_name.clone())),
            ("appLabel", JsonValue::String(self.app_label.clone())),
            ("approvedAtEpochMs", JsonValue::Number(self.approved_at_epoch_ms as f64)),
            ("clientId", JsonValue::String(self.client_id.clone())),
            ("apiKeyPreview", JsonValue::String(self.api_key_preview.clone())),
            ("apiKeyHash", JsonValue::String(self.api_key_hash.clone())),
            ("scopes", JsonValue::Array(self.scopes.iter().cloned().map(JsonValue::String).collect())),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            package_name: string_field(value, "packageName"),
            app_label: string_field(value, "appLabel"),
            approved_at_epoch_ms: number_field(value, "approvedAtEpochMs") as u64,
            client_id: string_field(value, "clientId"),
            api_key_preview: string_field(value, "apiKeyPreview"),
            api_key_hash: string_field(value, "apiKeyHash"),
            scopes: string_array_field(value, "scopes"),
        }
    }
}

#[derive(Clone, Default)]
struct ExternalAccessPolicy {
    enabled: bool,
    approved_apps: Vec<ApprovedClientApp>,
    pending_packages: Vec<String>,
}

impl ExternalAccessPolicy {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("enabled", JsonValue::Bool(self.enabled)),
            (
                "approvedApps",
                JsonValue::Array(self.approved_apps.iter().map(ApprovedClientApp::to_json).collect()),
            ),
            (
                "pendingPackages",
                JsonValue::Array(self.pending_packages.iter().cloned().map(JsonValue::String).collect()),
            ),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            enabled: bool_field(value, "enabled"),
            approved_apps: value
                .get("approvedApps")
                .and_then(JsonValue::as_array)
                .map(|items| items.iter().map(ApprovedClientApp::from_json).collect())
                .unwrap_or_default(),
            pending_packages: string_array_field(value, "pendingPackages"),
        }
    }
}

#[derive(Clone)]
struct OrchestraConfig {
    enabled: bool,
    auto_assign: bool,
    allow_lan_spillover: bool,
    assigned_models: BTreeMap<String, String>,
}

impl Default for OrchestraConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            auto_assign: true,
            allow_lan_spillover: true,
            assigned_models: BTreeMap::new(),
        }
    }
}

impl OrchestraConfig {
    fn to_json(&self) -> JsonValue {
        let assigned = self
            .assigned_models
            .iter()
            .map(|(key, value)| (key.as_str(), JsonValue::String(value.clone())))
            .collect::<Vec<_>>();
        object(vec![
            ("enabled", JsonValue::Bool(self.enabled)),
            ("autoAssign", JsonValue::Bool(self.auto_assign)),
            ("allowLanSpillover", JsonValue::Bool(self.allow_lan_spillover)),
            ("assignedModels", object(assigned)),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        let assigned_models = value
            .get("assignedModels")
            .and_then(JsonValue::as_object)
            .map(|items| {
                items.iter()
                    .filter_map(|(key, value)| value.as_str().map(|value| (key.clone(), value.to_string())))
                    .collect::<BTreeMap<_, _>>()
            })
            .unwrap_or_default();
        Self {
            enabled: bool_field(value, "enabled"),
            auto_assign: bool_field_with_default(value, "autoAssign", true),
            allow_lan_spillover: bool_field_with_default(value, "allowLanSpillover", true),
            assigned_models,
        }
    }
}

#[derive(Clone)]
struct LanHubConfig {
    enabled: bool,
    advertise_local_node: bool,
    pairing_token: String,
}

impl Default for LanHubConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            advertise_local_node: true,
            pairing_token: String::new(),
        }
    }
}

impl LanHubConfig {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("enabled", JsonValue::Bool(self.enabled)),
            ("advertiseLocalNode", JsonValue::Bool(self.advertise_local_node)),
            ("pairingToken", JsonValue::String(self.pairing_token.clone())),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            enabled: bool_field_with_default(value, "enabled", true),
            advertise_local_node: bool_field_with_default(value, "advertiseLocalNode", true),
            pairing_token: string_field(value, "pairingToken"),
        }
    }
}

#[derive(Clone)]
struct ToolCallingState {
    enabled_plugins: Vec<String>,
    web_search_enabled: bool,
    grammar_mode: String,
    multi_turn_enabled: bool,
    tool_calling_model_loaded: bool,
    bypass_enabled: bool,
}

impl Default for ToolCallingState {
    fn default() -> Self {
        Self {
            enabled_plugins: vec![
                "Calculator".to_string(),
                "Date & Time".to_string(),
                "Dev Utils".to_string(),
                "File Manager".to_string(),
                "NotePad".to_string(),
                "System Info".to_string(),
            ],
            web_search_enabled: false,
            grammar_mode: "STRICT".to_string(),
            multi_turn_enabled: true,
            tool_calling_model_loaded: false,
            bypass_enabled: false,
        }
    }
}

impl ToolCallingState {
    fn to_json(&self) -> JsonValue {
        object(vec![
            (
                "enabledPlugins",
                JsonValue::Array(self.enabled_plugins.iter().cloned().map(JsonValue::String).collect()),
            ),
            ("webSearchEnabled", JsonValue::Bool(self.web_search_enabled)),
            ("grammarMode", JsonValue::String(self.grammar_mode.clone())),
            ("multiTurnEnabled", JsonValue::Bool(self.multi_turn_enabled)),
            ("toolCallingModelLoaded", JsonValue::Bool(self.tool_calling_model_loaded)),
            ("toolCallingBypassEnabled", JsonValue::Bool(self.bypass_enabled)),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            enabled_plugins: string_array_field(value, "enabledPlugins"),
            web_search_enabled: bool_field(value, "webSearchEnabled"),
            grammar_mode: string_field_with_default(value, "grammarMode", "STRICT"),
            multi_turn_enabled: bool_field_with_default(value, "multiTurnEnabled", true),
            tool_calling_model_loaded: bool_field(value, "toolCallingModelLoaded"),
            bypass_enabled: bool_field(value, "toolCallingBypassEnabled"),
        }
    }
}

#[derive(Clone, Default)]
struct UpstreamConfig {
    llama_cpp_executable: String,
    llama_cpp_chat_model: String,
    llama_cpp_embedding_model: String,
    onnx_runtime_root: String,
    onnx_vision_model: String,
    onnx_segmentation_model: String,
    stable_diffusion_executable: String,
    stable_diffusion_model: String,
    piper_executable: String,
    piper_model: String,
    video_provider_command: String,
    three_d_provider_command: String,
}

impl UpstreamConfig {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("llamaCppExecutable", string_or_null(&self.llama_cpp_executable)),
            ("llamaCppChatModel", string_or_null(&self.llama_cpp_chat_model)),
            ("llamaCppEmbeddingModel", string_or_null(&self.llama_cpp_embedding_model)),
            ("onnxRuntimeRoot", string_or_null(&self.onnx_runtime_root)),
            ("onnxVisionModel", string_or_null(&self.onnx_vision_model)),
            ("onnxSegmentationModel", string_or_null(&self.onnx_segmentation_model)),
            ("stableDiffusionExecutable", string_or_null(&self.stable_diffusion_executable)),
            ("stableDiffusionModel", string_or_null(&self.stable_diffusion_model)),
            ("piperExecutable", string_or_null(&self.piper_executable)),
            ("piperModel", string_or_null(&self.piper_model)),
            ("videoProviderCommand", string_or_null(&self.video_provider_command)),
            ("threeDProviderCommand", string_or_null(&self.three_d_provider_command)),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            llama_cpp_executable: string_field(value, "llamaCppExecutable"),
            llama_cpp_chat_model: string_field(value, "llamaCppChatModel"),
            llama_cpp_embedding_model: string_field(value, "llamaCppEmbeddingModel"),
            onnx_runtime_root: string_field(value, "onnxRuntimeRoot"),
            onnx_vision_model: string_field(value, "onnxVisionModel"),
            onnx_segmentation_model: string_field(value, "onnxSegmentationModel"),
            stable_diffusion_executable: string_field(value, "stableDiffusionExecutable"),
            stable_diffusion_model: string_field(value, "stableDiffusionModel"),
            piper_executable: string_field(value, "piperExecutable"),
            piper_model: string_field(value, "piperModel"),
            video_provider_command: string_field(value, "videoProviderCommand"),
            three_d_provider_command: string_field(value, "threeDProviderCommand"),
        }
    }
}

#[derive(Clone)]
struct CoreSettings {
    port: u16,
    models_dir: PathBuf,
    peers_file: PathBuf,
    workspace_dir: PathBuf,
    rags_dir: PathBuf,
    notes_dir: PathBuf,
    pairing_token: String,
    theme_preset: String,
    app_locale: String,
    performance_mode: String,
    acceleration_mode: String,
    streaming_enabled: bool,
    chat_memory_enabled: bool,
    tool_calling_enabled: bool,
    image_blur_enabled: bool,
    load_tts_on_start: bool,
    code_highlight_enabled: bool,
    ai_memory_enabled: bool,
    ask_model_reload_dialog: bool,
    hardware_tuning_enabled: bool,
    preferred_models: PreferredModelMap,
    external_access_policy: ExternalAccessPolicy,
    orchestra_config: OrchestraConfig,
    lan_hub_config: LanHubConfig,
    tool_state: ToolCallingState,
    upstream: UpstreamConfig,
}

impl CoreSettings {
    fn default_for(app_home: &Path, args: &Arguments) -> Self {
        let workspace_dir = app_home.join("workspace-files");
        let rags_dir = app_home.join("rags");
        let notes_dir = app_home.join("notes");
        let pairing = if args.pairing_token.is_empty() {
            generate_pairing_token()
        } else {
            args.pairing_token.clone()
        };
        Self {
            port: args.port,
            models_dir: args.models_dir.clone(),
            peers_file: args.peers_file.clone(),
            workspace_dir,
            rags_dir,
            notes_dir,
            pairing_token: pairing.clone(),
            theme_preset: "SYSTEM".to_string(),
            app_locale: "SYSTEM".to_string(),
            performance_mode: "BALANCED".to_string(),
            acceleration_mode: "AUTO".to_string(),
            streaming_enabled: true,
            chat_memory_enabled: true,
            tool_calling_enabled: true,
            image_blur_enabled: true,
            load_tts_on_start: true,
            code_highlight_enabled: true,
            ai_memory_enabled: true,
            ask_model_reload_dialog: true,
            hardware_tuning_enabled: true,
            preferred_models: PreferredModelMap::default(),
            external_access_policy: ExternalAccessPolicy::default(),
            orchestra_config: OrchestraConfig::default(),
            lan_hub_config: LanHubConfig {
                pairing_token: pairing,
                ..LanHubConfig::default()
            },
            tool_state: ToolCallingState::default(),
            upstream: UpstreamConfig::default(),
        }
    }

    fn to_json(&self) -> JsonValue {
        object(vec![
            ("port", JsonValue::Number(self.port as f64)),
            ("modelsDir", JsonValue::String(self.models_dir.to_string_lossy().to_string())),
            ("peersFile", JsonValue::String(self.peers_file.to_string_lossy().to_string())),
            ("workspaceDir", JsonValue::String(self.workspace_dir.to_string_lossy().to_string())),
            ("ragsDir", JsonValue::String(self.rags_dir.to_string_lossy().to_string())),
            ("notesDir", JsonValue::String(self.notes_dir.to_string_lossy().to_string())),
            ("pairingToken", JsonValue::String(self.pairing_token.clone())),
            ("themePreset", JsonValue::String(self.theme_preset.clone())),
            ("appLocale", JsonValue::String(self.app_locale.clone())),
            ("performanceMode", JsonValue::String(self.performance_mode.clone())),
            ("accelerationMode", JsonValue::String(self.acceleration_mode.clone())),
            ("streamingEnabled", JsonValue::Bool(self.streaming_enabled)),
            ("chatMemoryEnabled", JsonValue::Bool(self.chat_memory_enabled)),
            ("toolCallingEnabled", JsonValue::Bool(self.tool_calling_enabled)),
            ("imageBlurEnabled", JsonValue::Bool(self.image_blur_enabled)),
            ("loadTtsOnStart", JsonValue::Bool(self.load_tts_on_start)),
            ("codeHighlightEnabled", JsonValue::Bool(self.code_highlight_enabled)),
            ("aiMemoryEnabled", JsonValue::Bool(self.ai_memory_enabled)),
            ("askModelReloadDialog", JsonValue::Bool(self.ask_model_reload_dialog)),
            ("hardwareTuningEnabled", JsonValue::Bool(self.hardware_tuning_enabled)),
            ("preferredModels", self.preferred_models.to_json()),
            ("externalAccessPolicy", self.external_access_policy.to_json()),
            ("orchestraConfig", self.orchestra_config.to_json()),
            ("lanHubConfig", self.lan_hub_config.to_json()),
            ("toolState", self.tool_state.to_json()),
            ("upstream", self.upstream.to_json()),
        ])
    }

    fn from_json(value: &JsonValue, app_home: &Path, args: &Arguments) -> Self {
        let mut settings = Self::default_for(app_home, args);
        settings.port = number_field_with_default(value, "port", settings.port as f64) as u16;
        settings.models_dir = path_field_with_default(value, "modelsDir", settings.models_dir.clone());
        settings.peers_file = path_field_with_default(value, "peersFile", settings.peers_file.clone());
        settings.workspace_dir = path_field_with_default(value, "workspaceDir", settings.workspace_dir.clone());
        settings.rags_dir = path_field_with_default(value, "ragsDir", settings.rags_dir.clone());
        settings.notes_dir = path_field_with_default(value, "notesDir", settings.notes_dir.clone());
        settings.pairing_token = string_field_with_default(value, "pairingToken", &settings.pairing_token);
        settings.theme_preset = string_field_with_default(value, "themePreset", &settings.theme_preset);
        settings.app_locale = string_field_with_default(value, "appLocale", &settings.app_locale);
        settings.performance_mode = string_field_with_default(value, "performanceMode", &settings.performance_mode);
        settings.acceleration_mode = string_field_with_default(value, "accelerationMode", &settings.acceleration_mode);
        settings.streaming_enabled = bool_field_with_default(value, "streamingEnabled", settings.streaming_enabled);
        settings.chat_memory_enabled = bool_field_with_default(value, "chatMemoryEnabled", settings.chat_memory_enabled);
        settings.tool_calling_enabled = bool_field_with_default(value, "toolCallingEnabled", settings.tool_calling_enabled);
        settings.image_blur_enabled = bool_field_with_default(value, "imageBlurEnabled", settings.image_blur_enabled);
        settings.load_tts_on_start = bool_field_with_default(value, "loadTtsOnStart", settings.load_tts_on_start);
        settings.code_highlight_enabled = bool_field_with_default(value, "codeHighlightEnabled", settings.code_highlight_enabled);
        settings.ai_memory_enabled = bool_field_with_default(value, "aiMemoryEnabled", settings.ai_memory_enabled);
        settings.ask_model_reload_dialog =
            bool_field_with_default(value, "askModelReloadDialog", settings.ask_model_reload_dialog);
        settings.hardware_tuning_enabled =
            bool_field_with_default(value, "hardwareTuningEnabled", settings.hardware_tuning_enabled);
        if let Some(preferred) = value.get("preferredModels") {
            settings.preferred_models = PreferredModelMap::from_json(preferred);
        }
        if let Some(policy) = value.get("externalAccessPolicy") {
            settings.external_access_policy = ExternalAccessPolicy::from_json(policy);
        }
        if let Some(config) = value.get("orchestraConfig") {
            settings.orchestra_config = OrchestraConfig::from_json(config);
        }
        if let Some(config) = value.get("lanHubConfig") {
            settings.lan_hub_config = LanHubConfig::from_json(config);
        }
        if let Some(tool_state) = value.get("toolState") {
            settings.tool_state = ToolCallingState::from_json(tool_state);
        }
        if let Some(upstream) = value.get("upstream") {
            settings.upstream = UpstreamConfig::from_json(upstream);
        }
        if !args.pairing_token.is_empty() {
            settings.pairing_token = args.pairing_token.clone();
            settings.lan_hub_config.pairing_token = args.pairing_token.clone();
        }
        settings
    }
}

#[derive(Clone)]
struct PeerNode {
    name: String,
    host: String,
    port: u16,
    free_ram_mb: u64,
    total_ram_mb: u64,
    cpu_cores: u32,
    compute_score: f64,
    accelerator_summary: String,
}

#[derive(Clone)]
struct LanNodeInfo {
    id: String,
    name: String,
    is_local: bool,
    status: String,
    capabilities: Vec<String>,
    installed_model_count: usize,
    notes: String,
    platform: String,
    total_ram_mb: Option<u64>,
    free_ram_mb: Option<u64>,
    cpu_cores: Option<u32>,
    accelerator_summary: Option<String>,
    compute_score: Option<f64>,
    transport_protocols: Vec<String>,
    heavy_slot_available: bool,
    supports_sequential_offload: bool,
    supports_pipeline_worker: bool,
    last_seen_epoch_ms: Option<u64>,
}

impl LanNodeInfo {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("id", JsonValue::String(self.id.clone())),
            ("name", JsonValue::String(self.name.clone())),
            ("isLocal", JsonValue::Bool(self.is_local)),
            ("status", JsonValue::String(self.status.clone())),
            (
                "capabilities",
                JsonValue::Array(self.capabilities.iter().cloned().map(JsonValue::String).collect()),
            ),
            ("installedModelCount", JsonValue::Number(self.installed_model_count as f64)),
            ("notes", string_or_null(&self.notes)),
            ("platform", JsonValue::String(self.platform.clone())),
            ("totalRamMb", optional_number(self.total_ram_mb)),
            ("freeRamMb", optional_number(self.free_ram_mb)),
            ("cpuCores", optional_number(self.cpu_cores.map(|value| value as u64))),
            (
                "acceleratorSummary",
                match &self.accelerator_summary {
                    Some(value) => JsonValue::String(value.clone()),
                    None => JsonValue::Null,
                },
            ),
            ("computeScore", optional_float(self.compute_score)),
            (
                "transportProtocols",
                JsonValue::Array(self.transport_protocols.iter().cloned().map(JsonValue::String).collect()),
            ),
            ("heavySlotAvailable", JsonValue::Bool(self.heavy_slot_available)),
            ("supportsSequentialOffload", JsonValue::Bool(self.supports_sequential_offload)),
            ("supportsPipelineWorker", JsonValue::Bool(self.supports_pipeline_worker)),
            ("lastSeenEpochMs", optional_number(self.last_seen_epoch_ms)),
        ])
    }
}

#[derive(Clone)]
struct RuntimeCapabilityInfo {
    capability: String,
    runtime: String,
    state: String,
    description: String,
}

impl RuntimeCapabilityInfo {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("capability", JsonValue::String(self.capability.clone())),
            ("runtime", JsonValue::String(self.runtime.clone())),
            ("state", JsonValue::String(self.state.clone())),
            ("description", JsonValue::String(self.description.clone())),
        ])
    }
}

#[derive(Clone)]
struct ModelEntry {
    id: String,
    name: String,
    path: PathBuf,
    size_bytes: u64,
    size_mb: u64,
    model_type: String,
    runtime: String,
    capabilities: Vec<String>,
}

impl ModelEntry {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("id", JsonValue::String(self.id.clone())),
            ("name", JsonValue::String(self.name.clone())),
            ("path", JsonValue::String(self.path.to_string_lossy().to_string())),
            ("sizeBytes", JsonValue::Number(self.size_bytes as f64)),
            ("sizeMb", JsonValue::Number(self.size_mb as f64)),
            ("modelType", JsonValue::String(self.model_type.clone())),
            ("runtime", JsonValue::String(self.runtime.clone())),
            (
                "capabilities",
                JsonValue::Array(self.capabilities.iter().cloned().map(JsonValue::String).collect()),
            ),
        ])
    }
}

#[derive(Clone)]
struct CatalogWarning {
    title: String,
    message: String,
    severity: String,
}

impl CatalogWarning {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("title", JsonValue::String(self.title.clone())),
            ("message", JsonValue::String(self.message.clone())),
            ("severity", JsonValue::String(self.severity.clone())),
        ])
    }
}

#[derive(Clone)]
struct CatalogPresentationEntry {
    id: String,
    title_ru: String,
    description_ru: String,
    task_label_ru: String,
    thumbnail_url: String,
    preview_images: Vec<String>,
    ram_estimate_mb: Option<u64>,
    support_status: String,
    downloadability: String,
    warnings: Vec<CatalogWarning>,
    source_label: String,
    assistant_eligible: bool,
    live_eligible: bool,
    tags_ru: Vec<String>,
}

impl CatalogPresentationEntry {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("id", JsonValue::String(self.id.clone())),
            ("titleRu", JsonValue::String(self.title_ru.clone())),
            ("descriptionRu", JsonValue::String(self.description_ru.clone())),
            ("taskLabelRu", JsonValue::String(self.task_label_ru.clone())),
            ("thumbnailUrl", string_or_null(&self.thumbnail_url)),
            (
                "previewImages",
                JsonValue::Array(self.preview_images.iter().cloned().map(JsonValue::String).collect()),
            ),
            ("ramEstimateMb", optional_number(self.ram_estimate_mb)),
            ("supportStatus", JsonValue::String(self.support_status.clone())),
            ("downloadability", JsonValue::String(self.downloadability.clone())),
            ("warnings", JsonValue::Array(self.warnings.iter().map(CatalogWarning::to_json).collect())),
            ("sourceLabel", JsonValue::String(self.source_label.clone())),
            ("assistantEligible", JsonValue::Bool(self.assistant_eligible)),
            ("liveEligible", JsonValue::Bool(self.live_eligible)),
            ("tagsRu", JsonValue::Array(self.tags_ru.iter().cloned().map(JsonValue::String).collect())),
        ])
    }
}

#[derive(Clone)]
struct ToolParameter {
    name: String,
    param_type: String,
    description: String,
    required: bool,
}

impl ToolParameter {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("name", JsonValue::String(self.name.clone())),
            ("type", JsonValue::String(self.param_type.clone())),
            ("description", JsonValue::String(self.description.clone())),
            ("required", JsonValue::Bool(self.required)),
        ])
    }
}

#[derive(Clone)]
struct ToolDefinition {
    plugin_name: String,
    name: String,
    description: String,
    parameters: Vec<ToolParameter>,
}

impl ToolDefinition {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("pluginName", JsonValue::String(self.plugin_name.clone())),
            ("name", JsonValue::String(self.name.clone())),
            ("description", JsonValue::String(self.description.clone())),
            ("parameters", JsonValue::Array(self.parameters.iter().map(ToolParameter::to_json).collect())),
        ])
    }
}

#[derive(Clone)]
struct PluginInfo {
    name: String,
    description: String,
    author: String,
    version: String,
    enabled: bool,
    tools: Vec<ToolDefinition>,
}

impl PluginInfo {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("name", JsonValue::String(self.name.clone())),
            ("description", JsonValue::String(self.description.clone())),
            ("author", JsonValue::String(self.author.clone())),
            ("version", JsonValue::String(self.version.clone())),
            ("enabled", JsonValue::Bool(self.enabled)),
            ("tools", JsonValue::Array(self.tools.iter().map(ToolDefinition::to_json).collect())),
        ])
    }
}

#[derive(Clone)]
struct RagItem {
    id: String,
    name: String,
    source_path: String,
    stored_path: String,
    source_type: String,
    query_supported: bool,
    query_hint: String,
}

impl RagItem {
    fn to_json(&self) -> JsonValue {
        object(vec![
            ("id", JsonValue::String(self.id.clone())),
            ("name", JsonValue::String(self.name.clone())),
            ("sourcePath", JsonValue::String(self.source_path.clone())),
            ("storedPath", JsonValue::String(self.stored_path.clone())),
            ("sourceType", JsonValue::String(self.source_type.clone())),
            ("querySupported", JsonValue::Bool(self.query_supported)),
            ("queryHint", JsonValue::String(self.query_hint.clone())),
        ])
    }

    fn from_json(value: &JsonValue) -> Self {
        Self {
            id: string_field(value, "id"),
            name: string_field(value, "name"),
            source_path: string_field(value, "sourcePath"),
            stored_path: string_field(value, "storedPath"),
            source_type: string_field(value, "sourceType"),
            query_supported: bool_field(value, "querySupported"),
            query_hint: string_field(value, "queryHint"),
        }
    }
}

struct AppState {
    settings_path: PathBuf,
    notes_path: PathBuf,
    rags_path: PathBuf,
    settings: CoreSettings,
    notes: BTreeMap<String, String>,
    rags: Vec<RagItem>,
    models: Vec<ModelEntry>,
    runtimes: Vec<RuntimeCapabilityInfo>,
    catalog: Vec<CatalogPresentationEntry>,
    plugins: Vec<PluginInfo>,
    lan_nodes: Vec<LanNodeInfo>,
}

impl AppState {
    fn load(args: Arguments) -> Result<Self, String> {
        let app_home = args.app_home.clone();
        fs::create_dir_all(&app_home).map_err(|error| format!("Failed to create app home '{}': {error}", app_home.display()))?;
        let settings_path = app_home.join(SETTINGS_FILE_NAME);
        let notes_path = app_home.join(NOTES_FILE_NAME);
        let rags_path = app_home.join(RAGS_FILE_NAME);
        let settings = if settings_path.exists() {
            let raw = fs::read_to_string(&settings_path)
                .map_err(|error| format!("Failed to read settings '{}': {error}", settings_path.display()))?;
            let value = parse_json(&raw)?;
            CoreSettings::from_json(&value, &app_home, &args)
        } else {
            CoreSettings::default_for(&app_home, &args)
        };
        fs::create_dir_all(&settings.models_dir).map_err(|error| format!("Failed to create models dir '{}': {error}", settings.models_dir.display()))?;
        fs::create_dir_all(&settings.workspace_dir)
            .map_err(|error| format!("Failed to create workspace dir '{}': {error}", settings.workspace_dir.display()))?;
        fs::create_dir_all(&settings.rags_dir).map_err(|error| format!("Failed to create RAG dir '{}': {error}", settings.rags_dir.display()))?;
        fs::create_dir_all(&settings.notes_dir)
            .map_err(|error| format!("Failed to create notes dir '{}': {error}", settings.notes_dir.display()))?;
        if !settings.peers_file.exists() {
            let _ = fs::write(
                &settings.peers_file,
                "# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary\n",
            );
        }
        let notes = load_notes(&notes_path);
        let rags = load_rags(&rags_path);
        let mut state = Self {
            settings_path,
            notes_path,
            rags_path,
            settings,
            notes,
            rags,
            models: Vec::new(),
            runtimes: Vec::new(),
            catalog: Vec::new(),
            plugins: Vec::new(),
            lan_nodes: Vec::new(),
        };
        state.plugins = build_plugins(&state.settings);
        state.save_settings()?;
        state.save_notes()?;
        state.save_rags()?;
        Ok(state)
    }

    fn reload(&mut self) {
        self.models = scan_models(&self.settings.models_dir);
        self.plugins = build_plugins(&self.settings);
        self.lan_nodes = build_lan_nodes(&self.settings, &self.models);
        self.runtimes = build_runtime_registry(&self.settings, &self.models, &self.rags);
        self.catalog = build_catalog(&self.settings, &self.models);
        let _ = self.save_settings();
    }

    fn save_settings(&self) -> Result<(), String> {
        fs::write(&self.settings_path, self.settings.to_json().stringify_pretty())
            .map_err(|error| format!("Failed to write settings '{}': {error}", self.settings_path.display()))
    }

    fn save_notes(&self) -> Result<(), String> {
        let notes = JsonValue::Array(
            self.notes
                .iter()
                .map(|(key, value)| object(vec![("key", JsonValue::String(key.clone())), ("content", JsonValue::String(value.clone()))]))
                .collect(),
        );
        fs::write(&self.notes_path, notes.stringify_pretty())
            .map_err(|error| format!("Failed to write notes '{}': {error}", self.notes_path.display()))
    }

    fn save_rags(&self) -> Result<(), String> {
        let data = JsonValue::Array(self.rags.iter().map(RagItem::to_json).collect());
        fs::write(&self.rags_path, data.stringify_pretty())
            .map_err(|error| format!("Failed to write RAGs '{}': {error}", self.rags_path.display()))
    }

    fn status_json(&self) -> JsonValue {
        object(vec![
            ("ok", JsonValue::Bool(true)),
            ("app", JsonValue::String(APP_NAME.to_string())),
            ("version", JsonValue::String(APP_VERSION.to_string())),
            ("corePort", JsonValue::Number(self.settings.port as f64)),
            ("compatHubPort", JsonValue::Number(DEFAULT_HUB_COMPAT_PORT as f64)),
            ("pairingTokenConfigured", JsonValue::Bool(!self.settings.pairing_token.is_empty())),
            ("modelCount", JsonValue::Number(self.models.len() as f64)),
            ("runtimeCount", JsonValue::Number(self.runtimes.len() as f64)),
            ("pluginCount", JsonValue::Number(self.plugins.len() as f64)),
            ("toolCount", JsonValue::Number(self.plugins.iter().map(|plugin| plugin.tools.len()).sum::<usize>() as f64)),
            ("ragCount", JsonValue::Number(self.rags.len() as f64)),
            ("lanEnabled", JsonValue::Bool(self.settings.lan_hub_config.enabled)),
            ("orchestraEnabled", JsonValue::Bool(self.settings.orchestra_config.enabled)),
            ("toolCallingEnabled", JsonValue::Bool(self.settings.tool_calling_enabled)),
            ("upstream", self.settings.upstream.to_json()),
            ("preferredModels", self.settings.preferred_models.to_json()),
            ("externalAccessPolicy", self.settings.external_access_policy.to_json()),
            ("toolState", self.settings.tool_state.to_json()),
            ("runtimeStates", JsonValue::Array(self.runtimes.iter().map(RuntimeCapabilityInfo::to_json).collect())),
        ])
    }
}

struct HttpRequest {
    method: String,
    path: String,
    query: HashMap<String, String>,
    headers: HashMap<String, String>,
    body: Vec<u8>,
}

struct HttpResponse {
    status: u16,
    content_type: &'static str,
    body: String,
}

fn handle_connection(mut stream: TcpStream, state: Arc<Mutex<AppState>>) -> Result<(), String> {
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .map_err(|error| format!("Failed to set read timeout: {error}"))?;
    stream
        .set_write_timeout(Some(Duration::from_secs(5)))
        .map_err(|error| format!("Failed to set write timeout: {error}"))?;
    let request = match read_http_request(&mut stream) {
        Ok(request) => request,
        Err(error) => {
            let _ = write_response(
                &mut stream,
                HttpResponse {
                    status: 400,
                    content_type: "application/json; charset=utf-8",
                    body: error_json("bad_request", &error).stringify(),
                },
            );
            let _ = stream.shutdown(Shutdown::Both);
            return Ok(());
        }
    };
    let response = route_request(request, state);
    write_response(&mut stream, response)?;
    let _ = stream.shutdown(Shutdown::Both);
    Ok(())
}

fn read_http_request(stream: &mut TcpStream) -> Result<HttpRequest, String> {
    let mut buffer = Vec::new();
    let mut temp = [0_u8; 4096];
    let mut headers_end = None;
    while buffer.len() < MAX_HTTP_BODY_BYTES {
        let bytes_read = stream.read(&mut temp).map_err(|error| format!("Failed reading request: {error}"))?;
        if bytes_read == 0 {
            break;
        }
        buffer.extend_from_slice(&temp[..bytes_read]);
        if headers_end.is_none() {
            headers_end = find_headers_end(&buffer);
            if headers_end.is_some() {
                break;
            }
        }
    }
    let headers_end = headers_end.ok_or_else(|| "HTTP headers were not terminated".to_string())?;
    let header_text = String::from_utf8(buffer[..headers_end].to_vec()).map_err(|_| "Invalid UTF-8 in HTTP headers".to_string())?;
    let mut lines = header_text.split("\r\n");
    let request_line = lines.next().ok_or_else(|| "Missing HTTP request line".to_string())?;
    let mut request_parts = request_line.split_whitespace();
    let method = request_parts.next().ok_or_else(|| "Missing HTTP method".to_string())?.to_string();
    let target = request_parts.next().ok_or_else(|| "Missing HTTP target".to_string())?;
    let (path, query) = split_target(target);
    let mut headers = HashMap::new();
    for line in lines {
        if line.is_empty() {
            continue;
        }
        if let Some((name, value)) = line.split_once(':') {
            headers.insert(name.trim().to_ascii_lowercase(), value.trim().to_string());
        }
    }
    let content_length = headers
        .get("content-length")
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or(0);
    let mut body = buffer[(headers_end + 4)..].to_vec();
    while body.len() < content_length {
        let bytes_read = stream.read(&mut temp).map_err(|error| format!("Failed reading HTTP body: {error}"))?;
        if bytes_read == 0 {
            break;
        }
        body.extend_from_slice(&temp[..bytes_read]);
        if body.len() > MAX_HTTP_BODY_BYTES {
            return Err("HTTP body too large".to_string());
        }
    }
    body.truncate(content_length);
    Ok(HttpRequest {
        method,
        path,
        query,
        headers,
        body,
    })
}

fn route_request(request: HttpRequest, state: Arc<Mutex<AppState>>) -> HttpResponse {
    match (request.method.as_str(), request.path.as_str()) {
        ("GET", "/health") => json_ok(object(vec![
            ("ok", JsonValue::Bool(true)),
            ("app", JsonValue::String(APP_NAME.to_string())),
            ("state", JsonValue::String("running".to_string())),
            ("timestamp", JsonValue::String(now_iso8601())),
        ])),
        ("GET", "/api/status") => with_state(&state, |app| json_ok(app.status_json())),
        ("GET", "/api/models") => with_state(&state, |app| {
            json_ok(JsonValue::Array(app.models.iter().map(ModelEntry::to_json).collect()))
        }),
        ("GET", "/api/runtimes") => with_state(&state, |app| {
            json_ok(JsonValue::Array(app.runtimes.iter().map(RuntimeCapabilityInfo::to_json).collect()))
        }),
        ("GET", "/api/catalog") => with_state(&state, |app| {
            json_ok(JsonValue::Array(app.catalog.iter().map(CatalogPresentationEntry::to_json).collect()))
        }),
        ("GET", "/api/setup/recommendation") => with_state(&state, |app| json_ok(setup_recommendation_json(app))),
        ("GET", "/api/plugins") => with_state(&state, |app| {
            json_ok(JsonValue::Array(app.plugins.iter().map(PluginInfo::to_json).collect()))
        }),
        ("GET", "/api/tools") => with_state(&state, |app| {
            let tools = app
                .plugins
                .iter()
                .flat_map(|plugin| plugin.tools.iter().cloned())
                .map(|tool| tool.to_json())
                .collect::<Vec<_>>();
            json_ok(JsonValue::Array(tools))
        }),
        ("GET", "/api/tool-state") => with_state(&state, |app| json_ok(app.settings.tool_state.to_json())),
        ("PUT", "/api/tool-state") => update_tool_state(&request, &state),
        ("GET", "/api/preferred-models") => {
            with_state(&state, |app| json_ok(app.settings.preferred_models.to_json()))
        }
        ("PUT", "/api/preferred-models") => update_preferred_models(&request, &state),
        ("GET", "/api/external-access") => {
            with_state(&state, |app| json_ok(app.settings.external_access_policy.to_json()))
        }
        ("PUT", "/api/external-access") => update_external_access(&request, &state),
        ("GET", "/api/orchestra") => with_state(&state, |app| json_ok(app.settings.orchestra_config.to_json())),
        ("PUT", "/api/orchestra") => update_orchestra(&request, &state),
        ("GET", "/api/lan/nodes") | ("GET", "/api/nodes") => with_state(&state, |app| {
            json_ok(JsonValue::Array(app.lan_nodes.iter().map(LanNodeInfo::to_json).collect()))
        }),
        ("GET", "/api/reload") => reload_core(&state),
        ("GET", "/api/plan") => plan_distribution(&request, &state),
        ("POST", "/api/lan/execute") => handle_lan_execute(&request, &state),
        ("POST", "/api/chat/generate") => handle_chat_generate(&request, &state),
        ("POST", "/api/rag/install") => handle_rag_install(&request, &state),
        ("POST", "/api/rag/query") => handle_rag_query(&request, &state),
        ("POST", "/api/tts/speak") => handle_tts_speak(&request, &state),
        ("POST", "/api/image/generate") => handle_image_generate(&request, &state),
        ("POST", "/api/tools/execute") => handle_tool_execute(&request, &state),
        ("GET", "/") => html_dashboard_response(),
        _ => HttpResponse {
            status: 404,
            content_type: "application/json; charset=utf-8",
            body: error_json("not_found", "Endpoint not found").stringify(),
        },
    }
}

fn update_tool_state(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    if let Some(enabled) = body.get("enabledPlugins").and_then(JsonValue::as_array) {
        app.settings.tool_state.enabled_plugins = enabled
            .iter()
            .filter_map(JsonValue::as_str)
            .map(ToOwned::to_owned)
            .collect();
    }
    if let Some(enabled) = body.get_bool("webSearchEnabled") {
        app.settings.tool_state.web_search_enabled = enabled;
    }
    if let Some(mode) = body.get_string("grammarMode") {
        app.settings.tool_state.grammar_mode = mode;
    }
    if let Some(enabled) = body.get_bool("multiTurnEnabled") {
        app.settings.tool_state.multi_turn_enabled = enabled;
    }
    if let Some(enabled) = body.get_bool("toolCallingModelLoaded") {
        app.settings.tool_state.tool_calling_model_loaded = enabled;
    }
    if let Some(enabled) = body.get_bool("toolCallingBypassEnabled") {
        app.settings.tool_state.bypass_enabled = enabled;
    }
    app.plugins = build_plugins(&app.settings);
    let response = app.settings.tool_state.to_json();
    let save_result = app.save_settings();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(response)
}

fn update_preferred_models(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    app.settings.preferred_models = PreferredModelMap::from_json(&body);
    let response = app.settings.preferred_models.to_json();
    let save_result = app.save_settings();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(response)
}

fn update_external_access(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    app.settings.external_access_policy = ExternalAccessPolicy::from_json(&body);
    let response = app.settings.external_access_policy.to_json();
    let save_result = app.save_settings();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(response)
}

fn update_orchestra(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    app.settings.orchestra_config = OrchestraConfig::from_json(&body);
    let response = app.settings.orchestra_config.to_json();
    let save_result = app.save_settings();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(response)
}

fn reload_core(state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    app.reload();
    let response = object(vec![
        ("ok", JsonValue::Bool(true)),
        ("modelCount", JsonValue::Number(app.models.len() as f64)),
        ("runtimeCount", JsonValue::Number(app.runtimes.len() as f64)),
        ("pluginCount", JsonValue::Number(app.plugins.len() as f64)),
        ("lanNodeCount", JsonValue::Number(app.lan_nodes.len() as f64)),
    ]);
    let save_result = app.save_settings();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(response)
}

fn plan_distribution(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let model_id = request.query.get("modelId").cloned().unwrap_or_default();
    with_state(state, |app| {
        let model = if model_id.is_empty() {
            app.models.first().cloned()
        } else {
            app.models.iter().find(|item| item.id == model_id).cloned()
        };
        let Some(model) = model else {
            return json_error(404, "model_not_found", "No model is available for planning");
        };
        let total_workers = (app.lan_nodes.len() + 1).max(1);
        let chunk_mb = ((model.size_mb as f64) / total_workers as f64).ceil() as u64;
        let workers = app
            .lan_nodes
            .iter()
            .enumerate()
            .map(|(index, node)| {
                object(vec![
                    ("slot", JsonValue::Number((index + 1) as f64)),
                    ("nodeId", JsonValue::String(node.id.clone())),
                    ("nodeName", JsonValue::String(node.name.clone())),
                    ("assignedResidentMb", JsonValue::Number(chunk_mb as f64)),
                    ("transport", JsonValue::String("http".to_string())),
                ])
            })
            .collect::<Vec<_>>();
        json_ok(object(vec![
            ("ok", JsonValue::Bool(true)),
            ("modelId", JsonValue::String(model.id)),
            (
                "plan",
                object(vec![
                    ("strategy", JsonValue::String("sequential_offload".to_string())),
                    ("localResidentMb", JsonValue::Number(chunk_mb as f64)),
                    ("estimatedShardCount", JsonValue::Number(total_workers as f64)),
                    ("workers", JsonValue::Array(workers)),
                ]),
            ),
        ]))
    })
}

fn handle_lan_execute(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let settings = match state.lock() {
        Ok(app) => app.settings.clone(),
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    if !settings.lan_hub_config.enabled {
        return json_error(400, "lan_disabled", "LAN execution is disabled in current settings");
    }
    if settings.pairing_token.is_empty() {
        return json_error(400, "pairing_token_missing", "Configure a pairing token before LAN execution");
    }
    let host = string_field(&body, "host");
    let prompt = body
        .get_string("prompt")
        .or_else(|| body.get_string("input"))
        .unwrap_or_default();
    if host.is_empty() || prompt.is_empty() {
        return json_error(400, "invalid_request", "Both host and prompt are required");
    }
    let remote_port = number_field_with_default(&body, "remotePort", DEFAULT_ANDROID_NODE_PORT as f64) as u16;
    let capability = string_field_with_default(&body, "capability", "chat");
    let system_prompt = string_field(&body, "systemPrompt");
    let model_id = string_field(&body, "modelId");
    let orchestration_mode = string_field_with_default(&body, "orchestrationMode", "single_model");
    let input_json = object(vec![("prompt", JsonValue::String(prompt))]).stringify();
    let payload = object(vec![
        ("capability", JsonValue::String(capability)),
        ("modelId", string_or_null(&model_id)),
        ("systemPrompt", string_or_null(&system_prompt)),
        ("inputJson", JsonValue::String(input_json)),
        ("orchestrationMode", JsonValue::String(orchestration_mode)),
    ])
    .stringify();
    match post_json(
        &host,
        remote_port,
        "/hub/lan/execute",
        &payload,
        Some(&settings.pairing_token),
    ) {
        Ok((status, body)) => HttpResponse {
            status,
            content_type: "application/json; charset=utf-8",
            body,
        },
        Err(error) => json_error(502, "lan_execute_failed", &error),
    }
}

fn handle_chat_generate(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let prompt = string_field(&body, "prompt");
    if prompt.is_empty() {
        return json_error(400, "invalid_request", "Missing prompt");
    }
    let (settings, models, nodes) = match state.lock() {
        Ok(app) => (app.settings.clone(), app.models.clone(), app.lan_nodes.clone()),
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    let model_override = string_field(&body, "modelId");
    let resolved_model = resolve_chat_model(&settings, &models, &model_override);
    if !settings.upstream.llama_cpp_executable.is_empty() {
        if let Some(model_path) = resolved_model {
            match run_llama_cpp(&settings.upstream.llama_cpp_executable, &model_path, &prompt) {
                Ok(output) => {
                    return json_ok(object(vec![
                        ("ok", JsonValue::Bool(true)),
                        ("mode", JsonValue::String("local_llama_cpp".to_string())),
                        ("modelPath", JsonValue::String(model_path)),
                        ("text", JsonValue::String(output)),
                    ]));
                }
                Err(error) => {
                    return json_error(502, "llama_cpp_failed", &error);
                }
            }
        }
    }
    if settings.orchestra_config.allow_lan_spillover && !nodes.is_empty() {
        return json_error(
            409,
            "lan_spillover_required",
            "Local GGUF runtime is not configured. Use POST /api/lan/execute with an Android or Windows peer.",
        );
    }
    json_error(
        501,
        "local_runtime_not_configured",
        "Configure upstream.llamaCppExecutable and an available GGUF model to enable local chat generation.",
    )
}

fn handle_rag_install(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let source_path = string_field(&body, "sourcePath");
    if source_path.is_empty() {
        return json_error(400, "invalid_request", "sourcePath is required");
    }
    let source = PathBuf::from(&source_path);
    if !source.exists() || !source.is_file() {
        return json_error(404, "file_not_found", "RAG source file not found");
    }
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    let name = {
        let provided = string_field(&body, "name");
        if provided.is_empty() {
            source
                .file_stem()
                .and_then(|value| value.to_str())
                .unwrap_or("rag")
                .to_string()
        } else {
            provided
        }
    };
    let extension = source.extension().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
    let rag_id = new_id("rag");
    let stored_name = match source.file_name().and_then(|value| value.to_str()) {
        Some(value) => format!("{rag_id}-{value}"),
        None => format!("{rag_id}.dat"),
    };
    let stored_path = app.settings.rags_dir.join(stored_name);
    if let Err(error) = fs::copy(&source, &stored_path) {
        return json_error(500, "copy_failed", &format!("Failed to copy RAG source: {error}"));
    }
    let query_supported = matches!(extension.as_str(), "txt" | "md" | "csv" | "log" | "json");
    let query_hint = if extension == "neuron" {
        "Installed .neuron packet preserved for Windows parity; native NeuronGraph execution still pending in this build."
    } else if query_supported {
        "Installed text-based RAG. Query endpoint uses substring retrieval until native graph runtime is wired."
    } else {
        "Installed asset registry entry only; query support requires a future parser/runtime."
    };
    let item = RagItem {
        id: rag_id.clone(),
        name,
        source_path: source_path.clone(),
        stored_path: stored_path.to_string_lossy().to_string(),
        source_type: if extension == "neuron" { "NEURON_PACKET" } else { "DOCUMENT" }.to_string(),
        query_supported,
        query_hint: query_hint.to_string(),
    };
    app.rags.push(item.clone());
    let save_result = app.save_rags();
    drop(app);
    if let Err(error) = save_result {
        return json_error(500, "save_failed", &error);
    }
    json_ok(object(vec![
        ("ok", JsonValue::Bool(true)),
        ("rag", item.to_json()),
    ]))
}

fn handle_rag_query(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let query = string_field(&body, "query");
    if query.is_empty() {
        return json_error(400, "invalid_request", "query is required");
    }
    let app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    let rag_filter = string_field(&body, "ragId");
    let mut results = Vec::new();
    for rag in &app.rags {
        if !rag_filter.is_empty() && rag.id != rag_filter {
            continue;
        }
        if !rag.query_supported {
            continue;
        }
        let Ok(content) = fs::read_to_string(&rag.stored_path) else {
            continue;
        };
        let lowercase = content.to_lowercase();
        let needle = query.to_lowercase();
        if let Some(index) = lowercase.find(&needle) {
            let start = index.saturating_sub(160);
            let end = (index + query.len() + 160).min(content.len());
            let snippet = content.get(start..end).unwrap_or("").replace('\n', " ");
            results.push(object(vec![
                ("ragId", JsonValue::String(rag.id.clone())),
                ("ragName", JsonValue::String(rag.name.clone())),
                ("score", JsonValue::Number(0.55)),
                ("snippet", JsonValue::String(snippet)),
            ]));
        }
    }
    json_ok(object(vec![
        ("ok", JsonValue::Bool(true)),
        ("query", JsonValue::String(query)),
        ("results", JsonValue::Array(results)),
        ("ragCount", JsonValue::Number(app.rags.len() as f64)),
    ]))
}

fn handle_tts_speak(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let text = string_field(&body, "text");
    if text.is_empty() {
        return json_error(400, "invalid_request", "text is required");
    }
    let settings = match state.lock() {
        Ok(app) => app.settings.clone(),
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    if settings.upstream.piper_executable.is_empty() || settings.upstream.piper_model.is_empty() {
        return json_error(
            501,
            "tts_not_configured",
            "Configure upstream.piperExecutable and upstream.piperModel to enable local TTS.",
        );
    }
    let output_path = settings.app_home_file("tts", "wav");
    match run_piper(
        &settings.upstream.piper_executable,
        &settings.upstream.piper_model,
        &text,
        &output_path,
    ) {
        Ok(_) => json_ok(object(vec![
            ("ok", JsonValue::Bool(true)),
            ("outputPath", JsonValue::String(output_path.to_string_lossy().to_string())),
            ("textLength", JsonValue::Number(text.len() as f64)),
        ])),
        Err(error) => json_error(502, "tts_failed", &error),
    }
}

fn handle_image_generate(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let prompt = string_field(&body, "prompt");
    if prompt.is_empty() {
        return json_error(400, "invalid_request", "prompt is required");
    }
    let settings = match state.lock() {
        Ok(app) => app.settings.clone(),
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    if settings.upstream.stable_diffusion_executable.is_empty() || settings.upstream.stable_diffusion_model.is_empty() {
        return json_error(
            501,
            "image_runtime_not_configured",
            "Configure upstream.stableDiffusionExecutable and upstream.stableDiffusionModel to enable local image generation.",
        );
    }
    let output_path = settings.app_home_file("image", "png");
    match run_stable_diffusion(
        &settings.upstream.stable_diffusion_executable,
        &settings.upstream.stable_diffusion_model,
        &prompt,
        &output_path,
    ) {
        Ok(_) => json_ok(object(vec![
            ("ok", JsonValue::Bool(true)),
            ("outputPath", JsonValue::String(output_path.to_string_lossy().to_string())),
            ("prompt", JsonValue::String(prompt)),
        ])),
        Err(error) => json_error(502, "image_generation_failed", &error),
    }
}

fn handle_tool_execute(request: &HttpRequest, state: &Arc<Mutex<AppState>>) -> HttpResponse {
    let body = match parse_body_json(request) {
        Ok(body) => body,
        Err(response) => return response,
    };
    let tool_name = string_field(&body, "tool");
    if tool_name.is_empty() {
        return json_error(400, "invalid_request", "tool is required");
    }
    let args = body.get("arguments").cloned().unwrap_or_else(|| JsonValue::Object(BTreeMap::new()));
    let mut app = match state.lock() {
        Ok(app) => app,
        Err(_) => return json_error(500, "state_error", "State lock poisoned"),
    };
    let result = execute_tool(&tool_name, &args, &mut app);
    let notes_save = if matches!(tool_name.as_str(), "notepad_write") {
        app.save_notes().err()
    } else {
        None
    };
    drop(app);
    if let Some(error) = notes_save {
        return json_error(500, "save_failed", &error);
    }
    match result {
        Ok(value) => json_ok(value),
        Err(error) => json_error(400, "tool_execution_failed", &error),
    }
}

fn html_dashboard_response() -> HttpResponse {
    HttpResponse {
        status: 200,
        content_type: "text/html; charset=utf-8",
        body: format!(
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>{APP_NAME}</title><style>\
             body{{font-family:Segoe UI,Arial,sans-serif;background:#0e0f13;color:#f5f5f5;margin:0;padding:32px}}\
             .card{{max-width:960px;margin:0 auto;background:#171922;border:1px solid #2a2d39;border-radius:24px;padding:24px;box-shadow:0 20px 60px rgba(0,0,0,.35)}}\
             h1{{margin:0 0 12px;font-size:30px}}\
             pre{{background:#10131b;padding:16px;border-radius:16px;overflow:auto}}\
             code{{color:#9fd3ff}}\
             a{{color:#8fd3ff}}\
             </style></head><body><div class=\"card\">\
             <h1>{APP_NAME}</h1>\
             <p>Native Windows core is running. Query <code>/api/status</code>, <code>/api/runtimes</code>, <code>/api/plugins</code>, <code>/api/tools</code> or use the legacy Java hub on <code>http://127.0.0.1:{DEFAULT_HUB_COMPAT_PORT}/</code> as a compatibility inspector.</p>\
             <pre>GET /health\nGET /api/status\nGET /api/runtimes\nGET /api/models\nGET /api/catalog\nGET /api/setup/recommendation\nGET /api/plugins\nGET /api/tools\nGET /api/tool-state\nPUT /api/tool-state\nGET /api/preferred-models\nPUT /api/preferred-models\nGET /api/external-access\nPUT /api/external-access\nGET /api/orchestra\nPUT /api/orchestra\nGET /api/lan/nodes\nGET /api/plan?modelId=&lt;id&gt;\nPOST /api/lan/execute\nPOST /api/chat/generate\nPOST /api/rag/install\nPOST /api/rag/query\nPOST /api/tts/speak\nPOST /api/image/generate\nPOST /api/tools/execute</pre>\
             </div></body></html>"
        ),
    }
}

fn setup_recommendation_json(app: &AppState) -> JsonValue {
    let installed_chat_model = app.models.iter().find(|model| model.capabilities.iter().any(|cap| cap == "chat"));
    if let Some(model) = installed_chat_model {
        return object(vec![
            ("title", JsonValue::String(model.name.clone())),
            (
                "description",
                JsonValue::String(
                    "Local chat model is already available on Windows. You can go straight to Home or add projector support later."
                        .to_string(),
                ),
            ),
            ("sizeLabel", JsonValue::String(format!("{} MB", model.size_mb))),
            ("installActionLabel", JsonValue::String("Open Home".to_string())),
            ("projectorTitle", JsonValue::String("Projector / mmproj".to_string())),
            (
                "projectorDescription",
                JsonValue::String(
                    "Adds photo Q&A, VLM and live-camera surfaces after the main GGUF is in place.".to_string(),
                ),
            ),
            (
                "projectorActionLabel",
                JsonValue::String("Add later".to_string()),
            ),
            ("warning", JsonValue::String("Main model is already present.".to_string())),
            ("status", JsonValue::String("installed".to_string())),
        ]);
    }

    let catalog_chat_model = app.catalog.iter().find(|entry| entry.assistant_eligible);
    let title = catalog_chat_model
        .map(|entry| entry.title_ru.clone())
        .unwrap_or_else(|| "Gemma starter GGUF".to_string());
    let description = catalog_chat_model
        .map(|entry| entry.description_ru.clone())
        .unwrap_or_else(|| {
            "Recommended starter profile for Windows chat setup. Main GGUF comes first; projector asset follows once the base model exists."
                .to_string()
        });
    let size_label = catalog_chat_model
        .and_then(|entry| entry.ram_estimate_mb)
        .map(|value| format!("~{} MB", value))
        .unwrap_or_else(|| "~200 MB".to_string());

    object(vec![
        ("title", JsonValue::String(title)),
        ("description", JsonValue::String(description)),
        ("sizeLabel", JsonValue::String(size_label)),
        ("installActionLabel", JsonValue::String("Manual import only".to_string())),
        ("projectorTitle", JsonValue::String("Gemma projector / mmproj".to_string())),
        (
            "projectorDescription",
            JsonValue::String(
                "Enables photo Q&A, VLM and live vision once the main model exists.".to_string(),
            ),
        ),
        (
            "projectorActionLabel",
            JsonValue::String("Requires main model first".to_string()),
        ),
        (
            "warning",
            JsonValue::String(
                "Current Windows runtime still needs either local GGUF import or a configured catalog source.".to_string(),
            ),
        ),
        ("status", JsonValue::String("manual_import_only".to_string())),
    ])
}

fn execute_tool(tool_name: &str, args: &JsonValue, app: &mut AppState) -> Result<JsonValue, String> {
    if !tool_enabled(tool_name, &app.settings.tool_state) {
        return Err(format!("Tool '{tool_name}' is disabled in current tool-calling settings"));
    }
    match tool_name {
        "calculate" => execute_calculate(args),
        "unit_convert" => execute_unit_convert(args),
        "get_current_datetime" => execute_get_current_datetime(args),
        "date_arithmetic" => execute_date_arithmetic(args),
        "timezone_convert" => execute_timezone_convert(args),
        "text_transform" => execute_text_transform(args),
        "hash_generate" => execute_hash_generate(args),
        "uuid_generate" => execute_uuid_generate(args),
        "text_stats" => execute_text_stats(args),
        "json_format" => execute_json_format(args),
        "base64_codec" => execute_base64_codec(args),
        "create_file" => execute_create_file(args, &app.settings.workspace_dir),
        "list_files" => execute_list_files(args, &app.settings.workspace_dir),
        "read_text_file" => execute_read_text_file(args, &app.settings.workspace_dir),
        "read_document" => execute_read_document(args, &app.settings.workspace_dir),
        "read_pdf" => execute_read_pdf(args, &app.settings.workspace_dir),
        "search_files" => execute_search_files(args, &app.settings.workspace_dir),
        "notepad_write" => execute_notepad_write(args, &mut app.notes),
        "notepad_read" => execute_notepad_read(args, &app.notes),
        "notepad_list" => execute_notepad_list(&app.notes),
        "get_system_info" => execute_get_system_info(),
        "web_search" => execute_web_search(args, &app.settings),
        _ => Err(format!("Unknown tool '{tool_name}'")),
    }
}

fn tool_enabled(tool_name: &str, state: &ToolCallingState) -> bool {
    let plugin_name = match tool_name {
        "web_search" => return state.web_search_enabled,
        "calculate" | "unit_convert" => "Calculator",
        "get_current_datetime" | "date_arithmetic" | "timezone_convert" => "Date & Time",
        "text_transform" | "hash_generate" | "uuid_generate" | "text_stats" | "json_format" | "base64_codec" => {
            "Dev Utils"
        }
        "create_file" | "list_files" | "read_text_file" | "read_document" | "read_pdf" | "search_files" => {
            "File Manager"
        }
        "notepad_write" | "notepad_read" | "notepad_list" => "NotePad",
        "get_system_info" => "System Info",
        _ => return false,
    };
    state.enabled_plugins.iter().any(|value| value == plugin_name)
}

fn with_state<F>(state: &Arc<Mutex<AppState>>, callback: F) -> HttpResponse
where
    F: FnOnce(&AppState) -> HttpResponse,
{
    match state.lock() {
        Ok(app) => callback(&app),
        Err(_) => json_error(500, "state_error", "State lock poisoned"),
    }
}

fn json_ok(value: JsonValue) -> HttpResponse {
    HttpResponse {
        status: 200,
        content_type: "application/json; charset=utf-8",
        body: value.stringify(),
    }
}

fn json_error(status: u16, error: &str, message: &str) -> HttpResponse {
    HttpResponse {
        status,
        content_type: "application/json; charset=utf-8",
        body: error_json(error, message).stringify(),
    }
}

fn error_json(error: &str, message: &str) -> JsonValue {
    object(vec![
        ("ok", JsonValue::Bool(false)),
        ("error", JsonValue::String(error.to_string())),
        ("message", JsonValue::String(message.to_string())),
    ])
}

fn parse_body_json(request: &HttpRequest) -> Result<JsonValue, HttpResponse> {
    if !request.body.is_empty() {
        if let Some(content_type) = request.headers.get("content-type") {
            let normalized = content_type.to_ascii_lowercase();
            if !normalized.starts_with("application/json") && !normalized.starts_with("text/json") {
                return Err(json_error(
                    400,
                    "invalid_content_type",
                    "Request body must use Content-Type: application/json",
                ));
            }
        }
    }
    let raw = String::from_utf8(request.body.clone())
        .map_err(|_| json_error(400, "invalid_utf8", "Request body must be valid UTF-8"))?;
    parse_json(&raw).map_err(|error| json_error(400, "invalid_json", &error))
}

fn write_response(stream: &mut TcpStream, response: HttpResponse) -> Result<(), String> {
    let header = format!(
        "HTTP/1.1 {} {}\r\nContent-Type: {}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        response.status,
        http_status_text(response.status),
        response.content_type,
        response.body.as_bytes().len()
    );
    stream
        .write_all(header.as_bytes())
        .and_then(|_| stream.write_all(response.body.as_bytes()))
        .map_err(|error| format!("Failed to write HTTP response: {error}"))
}

fn http_status_text(status: u16) -> &'static str {
    match status {
        200 => "OK",
        400 => "Bad Request",
        404 => "Not Found",
        405 => "Method Not Allowed",
        409 => "Conflict",
        500 => "Internal Server Error",
        501 => "Not Implemented",
        502 => "Bad Gateway",
        _ => "OK",
    }
}

fn split_target(target: &str) -> (String, HashMap<String, String>) {
    if let Some((path, query)) = target.split_once('?') {
        (path.to_string(), parse_query(query))
    } else {
        (target.to_string(), HashMap::new())
    }
}

fn parse_query(query: &str) -> HashMap<String, String> {
    query
        .split('&')
        .filter_map(|entry| {
            let (key, value) = entry.split_once('=').unwrap_or((entry, ""));
            Some((percent_decode(key), percent_decode(value)))
        })
        .collect()
}

fn find_headers_end(buffer: &[u8]) -> Option<usize> {
    buffer.windows(4).position(|window| window == b"\r\n\r\n")
}

fn percent_decode(input: &str) -> String {
    let mut output = String::with_capacity(input.len());
    let mut chars = input.as_bytes().iter().copied();
    while let Some(byte) = chars.next() {
        match byte {
            b'+' => output.push(' '),
            b'%' => {
                let hi = chars.next();
                let lo = chars.next();
                if let (Some(hi), Some(lo)) = (hi, lo) {
                    let text = [hi, lo];
                    if let Ok(value) = u8::from_str_radix(std::str::from_utf8(&text).unwrap_or(""), 16) {
                        output.push(value as char);
                    }
                }
            }
            value => output.push(value as char),
        }
    }
    output
}

fn load_notes(path: &Path) -> BTreeMap<String, String> {
    let Ok(raw) = fs::read_to_string(path) else {
        return BTreeMap::new();
    };
    let Ok(JsonValue::Array(items)) = parse_json(&raw) else {
        return BTreeMap::new();
    };
    items
        .into_iter()
        .filter_map(|item| {
            let key = item.get_string("key")?;
            let content = item.get_string("content").unwrap_or_default();
            Some((key, content))
        })
        .collect()
}

fn load_rags(path: &Path) -> Vec<RagItem> {
    let Ok(raw) = fs::read_to_string(path) else {
        return Vec::new();
    };
    let Ok(JsonValue::Array(items)) = parse_json(&raw) else {
        return Vec::new();
    };
    items.iter().map(RagItem::from_json).collect()
}

fn build_plugins(settings: &CoreSettings) -> Vec<PluginInfo> {
    let enabled = &settings.tool_state.enabled_plugins;
    vec![
        plugin(
            "Web Search",
            "Search the web and return a safe execution plan. Full remote fetching stays behind explicit external access.",
            "2.0.0",
            settings.tool_state.web_search_enabled,
            vec![tool("Web Search", "web_search", "Search the web by query", vec![
                param("query", "string", "The search query", true),
                param("max_results", "number", "Maximum number of results to consider", false),
            ])],
        ),
        plugin(
            "Calculator",
            "Perform mathematical calculations and unit conversions",
            "1.0.0",
            enabled.iter().any(|value| value == "Calculator"),
            vec![
                tool("Calculator", "calculate", "Evaluate a mathematical expression", vec![
                    param("expression", "string", "Expression such as 2+3*4 or sqrt(16)", true),
                ]),
                tool("Calculator", "unit_convert", "Convert value between supported units", vec![
                    param("value", "number", "Input numeric value", true),
                    param("from_unit", "string", "Source unit", true),
                    param("to_unit", "string", "Target unit", true),
                ]),
            ],
        ),
        plugin(
            "Date & Time",
            "Get current date/time, perform date arithmetic, and convert between timezones",
            "1.0.0",
            enabled.iter().any(|value| value == "Date & Time"),
            vec![
                tool("Date & Time", "get_current_datetime", "Get current date/time", vec![
                    param("timezone", "string", "Optional IANA timezone", false),
                    param("format", "string", "full, date, time, iso", false),
                ]),
                tool("Date & Time", "date_arithmetic", "Add or subtract duration from a datetime", vec![
                    param("date", "string", "ISO-8601 datetime", true),
                    param("operation", "string", "add or subtract", true),
                    param("amount", "number", "Amount", true),
                    param("unit", "string", "days, hours, minutes, months, years", true),
                ]),
                tool("Date & Time", "timezone_convert", "Convert a datetime between timezones", vec![
                    param("time", "string", "ISO-8601 datetime", true),
                    param("from_timezone", "string", "Source IANA timezone", true),
                    param("to_timezone", "string", "Target IANA timezone", true),
                ]),
            ],
        ),
        plugin(
            "Dev Utils",
            "Developer utilities: text transforms, hashing, UUID, base64, JSON formatting",
            "1.0.0",
            enabled.iter().any(|value| value == "Dev Utils"),
            vec![
                tool("Dev Utils", "text_transform", "Transform text", vec![
                    param("text", "string", "Input text", true),
                    param("operation", "string", "uppercase, lowercase, reverse, title_case, snake_case, camel_case, trim", true),
                ]),
                tool("Dev Utils", "hash_generate", "Generate MD5, SHA-1, SHA-256 or SHA-512 hash", vec![
                    param("text", "string", "Input text", true),
                    param("algorithm", "string", "md5, sha1, sha256, sha512", true),
                ]),
                tool("Dev Utils", "uuid_generate", "Generate one or more UUIDs", vec![param("count", "number", "1-10", false)]),
                tool("Dev Utils", "text_stats", "Get text statistics", vec![param("text", "string", "Input text", true)]),
                tool("Dev Utils", "json_format", "Validate or format JSON", vec![
                    param("json", "string", "JSON text", true),
                    param("validate_only", "boolean", "Validate without pretty formatting", false),
                ]),
                tool("Dev Utils", "base64_codec", "Encode or decode Base64", vec![
                    param("text", "string", "Input text", true),
                    param("operation", "string", "encode or decode", true),
                ]),
            ],
        ),
        plugin(
            "File Manager",
            "Create, browse, read, and search files in the Windows core sandbox",
            "1.1.0",
            enabled.iter().any(|value| value == "File Manager"),
            vec![
                tool("File Manager", "create_file", "Create or append a file in the sandbox", vec![
                    param("path", "string", "Relative path in sandbox", true),
                    param("content", "string", "Text content", true),
                    param("append", "boolean", "Append instead of overwrite", false),
                ]),
                tool("File Manager", "list_files", "List files in the sandbox", vec![
                    param("path", "string", "Relative directory path", false),
                    param("filter", "string", "Simple * wildcard pattern", false),
                    param("recursive", "boolean", "Recursive listing", false),
                ]),
                tool("File Manager", "read_text_file", "Read a text file", vec![
                    param("path", "string", "Relative file path", true),
                    param("max_chars", "number", "Maximum characters to read", false),
                ]),
                tool("File Manager", "read_document", "Read a text-like document file", vec![
                    param("path", "string", "Relative file path", true),
                    param("max_chars", "number", "Maximum characters to read", false),
                ]),
                tool("File Manager", "read_pdf", "Attempt PDF text extraction", vec![
                    param("path", "string", "Relative file path", true),
                ]),
                tool("File Manager", "search_files", "Search files by name", vec![
                    param("query", "string", "Search query", true),
                    param("path", "string", "Relative search root", false),
                    param("file_type", "string", "pdf, text, image", false),
                ]),
            ],
        ),
        plugin(
            "NotePad",
            "Scratch pad for storing and retrieving notes across turns",
            "1.0.0",
            enabled.iter().any(|value| value == "NotePad"),
            vec![
                tool("NotePad", "notepad_write", "Store a named note", vec![
                    param("key", "string", "Unique note key", true),
                    param("content", "string", "Note content", true),
                ]),
                tool("NotePad", "notepad_read", "Read a note", vec![param("key", "string", "Note key", true)]),
                tool("NotePad", "notepad_list", "List note keys", vec![]),
            ],
        ),
        plugin(
            "System Info",
            "Get current date/time, device, CPU and network status",
            "1.0.0",
            enabled.iter().any(|value| value == "System Info"),
            vec![tool("System Info", "get_system_info", "Return system info", vec![])],
        ),
    ]
}

fn plugin(name: &str, description: &str, version: &str, enabled: bool, tools: Vec<ToolDefinition>) -> PluginInfo {
    PluginInfo {
        name: name.to_string(),
        description: description.to_string(),
        author: "SantiyaLocalAiHub".to_string(),
        version: version.to_string(),
        enabled,
        tools,
    }
}

fn tool(plugin_name: &str, name: &str, description: &str, parameters: Vec<ToolParameter>) -> ToolDefinition {
    ToolDefinition {
        plugin_name: plugin_name.to_string(),
        name: name.to_string(),
        description: description.to_string(),
        parameters,
    }
}

fn param(name: &str, param_type: &str, description: &str, required: bool) -> ToolParameter {
    ToolParameter {
        name: name.to_string(),
        param_type: param_type.to_string(),
        description: description.to_string(),
        required,
    }
}

fn build_lan_nodes(settings: &CoreSettings, models: &[ModelEntry]) -> Vec<LanNodeInfo> {
    let mut nodes = vec![LanNodeInfo {
        id: "windows-local".to_string(),
        name: "Windows Local Core".to_string(),
        is_local: true,
        status: "ready".to_string(),
        capabilities: unique_capabilities(models),
        installed_model_count: models.len(),
        notes: "Primary Windows native core node".to_string(),
        platform: "windows".to_string(),
        total_ram_mb: None,
        free_ram_mb: None,
        cpu_cores: std::thread::available_parallelism().ok().map(|value| value.get() as u32),
        accelerator_summary: Some("Configurable upstream runtimes".to_string()),
        compute_score: None,
        transport_protocols: vec!["http".to_string()],
        heavy_slot_available: true,
        supports_sequential_offload: true,
        supports_pipeline_worker: true,
        last_seen_epoch_ms: Some(now_epoch_ms()),
    }];
    for peer in load_peers(&settings.peers_file) {
        nodes.push(LanNodeInfo {
            id: format!("peer-{}-{}", peer.host, peer.port),
            name: peer.name,
            is_local: false,
            status: "manual_peer".to_string(),
            capabilities: vec!["chat".to_string(), "lan".to_string()],
            installed_model_count: 0,
            notes: "Manual compatibility peer from peers.csv".to_string(),
            platform: "unknown".to_string(),
            total_ram_mb: Some(peer.total_ram_mb),
            free_ram_mb: Some(peer.free_ram_mb),
            cpu_cores: Some(peer.cpu_cores),
            accelerator_summary: Some(peer.accelerator_summary),
            compute_score: Some(peer.compute_score),
            transport_protocols: vec!["http".to_string()],
            heavy_slot_available: true,
            supports_sequential_offload: true,
            supports_pipeline_worker: false,
            last_seen_epoch_ms: None,
        });
    }
    nodes
}

fn load_peers(path: &Path) -> Vec<PeerNode> {
    let Ok(raw) = fs::read_to_string(path) else {
        return Vec::new();
    };
    raw.lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .filter_map(|line| {
            let parts = line.split(',').map(str::trim).collect::<Vec<_>>();
            if parts.len() < 8 {
                return None;
            }
            Some(PeerNode {
                name: parts[0].to_string(),
                host: parts[1].to_string(),
                port: parts[2].parse::<u16>().unwrap_or(DEFAULT_HUB_COMPAT_PORT),
                free_ram_mb: parts[3].parse::<u64>().unwrap_or(0),
                total_ram_mb: parts[4].parse::<u64>().unwrap_or(0),
                cpu_cores: parts[5].parse::<u32>().unwrap_or(0),
                compute_score: parts[6].parse::<f64>().unwrap_or(0.0),
                accelerator_summary: parts[7].to_string(),
            })
        })
        .collect()
}

fn build_runtime_registry(settings: &CoreSettings, models: &[ModelEntry], rags: &[RagItem]) -> Vec<RuntimeCapabilityInfo> {
    let any_chat_model = models.iter().any(|model| model.capabilities.iter().any(|cap| cap == "chat"));
    let any_embed_model = models.iter().any(|model| model.capabilities.iter().any(|cap| cap == "embeddings"));
    let any_onnx_model = models.iter().any(|model| model.model_type == "ONNX");
    let any_diffusion_model = models.iter().any(|model| model.capabilities.iter().any(|cap| cap == "image_generation"));
    let any_tts_model = models.iter().any(|model| model.capabilities.iter().any(|cap| cap == "tts"));
    vec![
        runtime(
            "chat",
            "llama.cpp",
            if !settings.upstream.llama_cpp_executable.is_empty() && any_chat_model {
                "READY"
            } else if any_chat_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "GGUF chat runtime using configurable llama.cpp-compatible executable.",
        ),
        runtime(
            "embeddings",
            "llama.cpp",
            if (!settings.upstream.llama_cpp_executable.is_empty() && any_embed_model)
                || (!settings.upstream.onnx_runtime_root.is_empty() && any_onnx_model)
            {
                "READY"
            } else if any_embed_model || any_onnx_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "Embeddings can be served by GGUF or ONNX runtime depending on configured upstream.",
        ),
        runtime(
            "object_detection",
            "onnxruntime",
            if !settings.upstream.onnx_runtime_root.is_empty() && any_onnx_model {
                "READY"
            } else if any_onnx_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "ONNX Runtime adapter contract for vision and detection tasks.",
        ),
        runtime(
            "image_segmentation",
            "onnxruntime",
            if !settings.upstream.onnx_runtime_root.is_empty() && any_onnx_model {
                "READY"
            } else if any_onnx_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "ONNX Runtime adapter contract for segmentation models.",
        ),
        runtime(
            "image_generation",
            "stable-diffusion.cpp",
            if !settings.upstream.stable_diffusion_executable.is_empty() && any_diffusion_model {
                "READY"
            } else if any_diffusion_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "Stable Diffusion runtime through a configurable stable-diffusion.cpp-compatible executable.",
        ),
        runtime(
            "upscale",
            "stable-diffusion.cpp",
            if !settings.upstream.stable_diffusion_executable.is_empty() && any_diffusion_model {
                "READY"
            } else if any_diffusion_model {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "Image tools/upscale path routed through the diffusion runtime family.",
        ),
        runtime(
            "tts",
            "piper",
            if !settings.upstream.piper_executable.is_empty() && (any_tts_model || !settings.upstream.piper_model.is_empty()) {
                "READY"
            } else if any_tts_model || !settings.upstream.piper_model.is_empty() {
                "PROVIDER_NEEDED"
            } else {
                "NEEDS_MODEL"
            },
            "Local TTS runtime backed by a Piper-compatible executable.",
        ),
        runtime(
            "rag",
            "neuron-packet",
            if rags.iter().any(|item| item.query_supported) {
                "READY"
            } else if !rags.is_empty() {
                "PARTIAL"
            } else {
                "NEEDS_MODEL"
            },
            "RAG registry with preserved .neuron compatibility and text-based fallback query support.",
        ),
        runtime(
            "video_generation",
            "experimental-video",
            if !settings.upstream.video_provider_command.is_empty() {
                "PROVIDER_NEEDED"
            } else {
                "EXPERIMENTAL"
            },
            "Catalog-first experimental video generation slot.",
        ),
        runtime(
            "3d_generation",
            "experimental-3d",
            if !settings.upstream.three_d_provider_command.is_empty() {
                "PROVIDER_NEEDED"
            } else {
                "EXPERIMENTAL"
            },
            "Catalog-first experimental 3D generation slot.",
        ),
    ]
}

fn runtime(capability: &str, runtime: &str, state: &str, description: &str) -> RuntimeCapabilityInfo {
    RuntimeCapabilityInfo {
        capability: capability.to_string(),
        runtime: runtime.to_string(),
        state: state.to_string(),
        description: description.to_string(),
    }
}

fn build_catalog(settings: &CoreSettings, models: &[ModelEntry]) -> Vec<CatalogPresentationEntry> {
    let mut entries = models
        .iter()
        .map(|model| CatalogPresentationEntry {
            id: model.id.clone(),
            title_ru: model.name.clone(),
            description_ru: format!("Windows local model at {}", model.path.display()),
            task_label_ru: capability_label_ru(&model.capabilities),
            thumbnail_url: String::new(),
            preview_images: Vec::new(),
            ram_estimate_mb: Some(model.size_mb),
            support_status: "local".to_string(),
            downloadability: "local_runnable".to_string(),
            warnings: Vec::new(),
            source_label: source_label_for_model(model).to_string(),
            assistant_eligible: model.capabilities.iter().any(|cap| cap == "chat"),
            live_eligible: model.capabilities.iter().any(|cap| cap == "chat" || cap == "object_detection"),
            tags_ru: model.capabilities.clone(),
        })
        .collect::<Vec<_>>();
    entries.push(CatalogPresentationEntry {
        id: "windows-piper-tts".to_string(),
        title_ru: "Piper TTS".to_string(),
        description_ru: "Голосовой рантайм Windows через upstream Piper-compatible executable.".to_string(),
        task_label_ru: "Синтез речи".to_string(),
        thumbnail_url: String::new(),
        preview_images: Vec::new(),
        ram_estimate_mb: None,
        support_status: if settings.upstream.piper_executable.is_empty() {
            "experimental".to_string()
        } else {
            "local".to_string()
        },
        downloadability: "raw_asset_download".to_string(),
        warnings: vec![CatalogWarning {
            title: "Upstream runtime".to_string(),
            message: "Requires configured Piper executable and voice model.".to_string(),
            severity: "info".to_string(),
        }],
        source_label: "GitHub / Piper".to_string(),
        assistant_eligible: false,
        live_eligible: false,
        tags_ru: vec!["tts".to_string(), "windows".to_string()],
    });
    entries.push(CatalogPresentationEntry {
        id: "windows-video-experimental".to_string(),
        title_ru: "Experimental Video".to_string(),
        description_ru: "Каталог-first слот для video_generation на Windows.".to_string(),
        task_label_ru: "Видео".to_string(),
        thumbnail_url: String::new(),
        preview_images: Vec::new(),
        ram_estimate_mb: None,
        support_status: "experimental".to_string(),
        downloadability: "unresolved".to_string(),
        warnings: vec![CatalogWarning {
            title: "Experimental".to_string(),
            message: "Provider wiring is not complete in this build.".to_string(),
            severity: "warning".to_string(),
        }],
        source_label: "Catalog-first".to_string(),
        assistant_eligible: false,
        live_eligible: false,
        tags_ru: vec!["video_generation".to_string(), "experimental".to_string()],
    });
    entries.push(CatalogPresentationEntry {
        id: "windows-3d-experimental".to_string(),
        title_ru: "Experimental 3D".to_string(),
        description_ru: "Каталог-first слот для 3d_generation на Windows.".to_string(),
        task_label_ru: "3D".to_string(),
        thumbnail_url: String::new(),
        preview_images: Vec::new(),
        ram_estimate_mb: None,
        support_status: "experimental".to_string(),
        downloadability: "unresolved".to_string(),
        warnings: vec![CatalogWarning {
            title: "Experimental".to_string(),
            message: "Provider wiring is not complete in this build.".to_string(),
            severity: "warning".to_string(),
        }],
        source_label: "Catalog-first".to_string(),
        assistant_eligible: false,
        live_eligible: false,
        tags_ru: vec!["3d_generation".to_string(), "experimental".to_string()],
    });
    entries
}

fn scan_models(root: &Path) -> Vec<ModelEntry> {
    let mut models = Vec::new();
    if !root.exists() {
        return models;
    }
    scan_models_recursive(root, &mut models);
    models.sort_by(|left, right| left.path.cmp(&right.path));
    models
}

fn scan_models_recursive(root: &Path, output: &mut Vec<ModelEntry>) {
    let Ok(entries) = fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            scan_models_recursive(&path, output);
            continue;
        }
        let extension = path.extension().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
        let Some(metadata) = path.metadata().ok() else {
            continue;
        };
        let size_bytes = metadata.len();
        let size_mb = (size_bytes / (1024 * 1024)).max(1);
        let file_name = path.file_stem().and_then(|value| value.to_str()).unwrap_or("model").to_string();
        let (model_type, runtime, capabilities) = classify_model(&path, &extension);
        if model_type.is_empty() {
            continue;
        }
        output.push(ModelEntry {
            id: stable_id(&path),
            name: file_name,
            path,
            size_bytes,
            size_mb,
            model_type,
            runtime,
            capabilities,
        });
    }
}

fn classify_model(path: &Path, extension: &str) -> (String, String, Vec<String>) {
    let lower = path.to_string_lossy().to_ascii_lowercase();
    match extension {
        "gguf" => {
            let mut caps = vec!["chat".to_string()];
            if lower.contains("embed") {
                caps.push("embeddings".to_string());
            }
            (String::from("GGUF"), String::from("llama.cpp"), caps)
        }
        "onnx" => {
            let mut caps = Vec::new();
            if lower.contains("tts") || lower.contains("piper") {
                caps.push("tts".to_string());
            }
            if lower.contains("segment") || lower.contains("sam") {
                caps.push("image_segmentation".to_string());
            }
            if lower.contains("detect") || lower.contains("vision") || lower.contains("face") {
                caps.push("object_detection".to_string());
            }
            if caps.is_empty() {
                caps.push("embeddings".to_string());
            }
            (String::from("ONNX"), String::from("onnxruntime"), caps)
        }
        "safetensors" | "ckpt" | "ggml" => (
            String::from("DIFFUSION"),
            String::from("stable-diffusion.cpp"),
            vec!["image_generation".to_string(), "upscale".to_string()],
        ),
        "neuron" => (
            String::from("RAG"),
            String::from("neuron-packet"),
            vec!["rag".to_string()],
        ),
        _ => (String::new(), String::new(), Vec::new()),
    }
}

fn source_label_for_model(model: &ModelEntry) -> &str {
    match model.model_type.as_str() {
        "GGUF" => "GGUF / local",
        "ONNX" => "ONNX / local",
        "DIFFUSION" => "Diffusion / local",
        "RAG" => "Neuron Packet / local",
        _ => "Local",
    }
}

fn capability_label_ru(capabilities: &[String]) -> String {
    if capabilities.iter().any(|value| value == "chat") {
        "Чат".to_string()
    } else if capabilities.iter().any(|value| value == "object_detection") {
        "Зрение".to_string()
    } else if capabilities.iter().any(|value| value == "image_generation") {
        "Изображения".to_string()
    } else if capabilities.iter().any(|value| value == "tts") {
        "Речь".to_string()
    } else if capabilities.iter().any(|value| value == "rag") {
        "RAG".to_string()
    } else {
        "Инструмент".to_string()
    }
}

fn unique_capabilities(models: &[ModelEntry]) -> Vec<String> {
    let mut values = BTreeMap::<String, ()>::new();
    for model in models {
        for capability in &model.capabilities {
            values.insert(capability.clone(), ());
        }
    }
    values.into_keys().collect()
}

fn resolve_chat_model(settings: &CoreSettings, models: &[ModelEntry], override_id: &str) -> Option<String> {
    if !override_id.is_empty() {
        if let Some(model) = models.iter().find(|model| model.id == override_id) {
            return Some(model.path.to_string_lossy().to_string());
        }
    }
    if !settings.preferred_models.chat_model_id.is_empty() {
        if let Some(model) = models.iter().find(|model| model.id == settings.preferred_models.chat_model_id) {
            return Some(model.path.to_string_lossy().to_string());
        }
    }
    if !settings.upstream.llama_cpp_chat_model.is_empty() {
        return Some(settings.upstream.llama_cpp_chat_model.clone());
    }
    models
        .iter()
        .find(|model| model.capabilities.iter().any(|cap| cap == "chat"))
        .map(|model| model.path.to_string_lossy().to_string())
}

fn run_llama_cpp(executable: &str, model_path: &str, prompt: &str) -> Result<String, String> {
    let output = Command::new(executable)
        .args(["-m", model_path, "-p", prompt, "-n", "192", "--log-disable"])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .output()
        .map_err(|error| format!("Failed to start llama.cpp executable: {error}"))?;
    if output.status.success() {
        Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
    }
}

fn run_piper(executable: &str, model_path: &str, text: &str, output_path: &Path) -> Result<(), String> {
    let mut process = Command::new(executable)
        .args(["--model", model_path, "--output_file"])
        .arg(output_path)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|error| format!("Failed to start Piper executable: {error}"))?;
    if let Some(stdin) = process.stdin.as_mut() {
        stdin
            .write_all(text.as_bytes())
            .map_err(|error| format!("Failed to write text to Piper stdin: {error}"))?;
    }
    let output = process
        .wait_with_output()
        .map_err(|error| format!("Failed waiting for Piper executable: {error}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
    }
}

fn run_stable_diffusion(executable: &str, model_path: &str, prompt: &str, output_path: &Path) -> Result<(), String> {
    let output = Command::new(executable)
        .args(["-m", model_path, "-p", prompt, "-o"])
        .arg(output_path)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .output()
        .map_err(|error| format!("Failed to start stable diffusion executable: {error}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
    }
}

fn post_json(host: &str, port: u16, path: &str, body: &str, pairing_token: Option<&str>) -> Result<(u16, String), String> {
    let mut stream = TcpStream::connect((host, port)).map_err(|error| format!("Failed connecting to {host}:{port}: {error}"))?;
    stream
        .set_read_timeout(Some(Duration::from_secs(6)))
        .map_err(|error| format!("Failed setting timeout: {error}"))?;
    let mut request = format!(
        "POST {path} HTTP/1.1\r\nHost: {host}:{port}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n",
        body.as_bytes().len()
    );
    if let Some(token) = pairing_token {
        request.push_str(&format!("X-Pairing-Token: {token}\r\n"));
    }
    request.push_str("\r\n");
    request.push_str(body);
    stream.write_all(request.as_bytes()).map_err(|error| format!("Failed sending HTTP request: {error}"))?;
    let mut response = String::new();
    stream
        .read_to_string(&mut response)
        .map_err(|error| format!("Failed reading HTTP response: {error}"))?;
    let (status, body) = split_http_response(&response)?;
    Ok((status, body))
}

fn split_http_response(response: &str) -> Result<(u16, String), String> {
    let (headers, body) = response
        .split_once("\r\n\r\n")
        .ok_or_else(|| "Invalid HTTP response".to_string())?;
    let mut lines = headers.lines();
    let status_line = lines.next().ok_or_else(|| "Missing HTTP status line".to_string())?;
    let status = status_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| "Missing HTTP status code".to_string())?
        .parse::<u16>()
        .map_err(|_| "Invalid HTTP status code".to_string())?;
    Ok((status, body.to_string()))
}

fn execute_calculate(args: &JsonValue) -> Result<JsonValue, String> {
    let expression = string_field(args, "expression");
    if expression.is_empty() {
        return Err("Expression is required".to_string());
    }
    let result = evaluate_expression(&expression)?;
    Ok(object(vec![
        ("expression", JsonValue::String(expression)),
        ("result", JsonValue::Number(result)),
        ("formattedResult", JsonValue::String(format_number(result))),
    ]))
}

fn execute_unit_convert(args: &JsonValue) -> Result<JsonValue, String> {
    let value = number_field(args, "value");
    let from = string_field(args, "from_unit").to_lowercase();
    let to = string_field(args, "to_unit").to_lowercase();
    let result = convert_unit(value, &from, &to)?;
    Ok(object(vec![
        ("value", JsonValue::Number(value)),
        ("from_unit", JsonValue::String(from.clone())),
        ("to_unit", JsonValue::String(to.clone())),
        ("result", JsonValue::Number(result)),
        ("formattedResult", JsonValue::String(format!("{} {} = {} {}", format_number(value), from, format_number(result), to))),
    ]))
}

fn execute_get_current_datetime(args: &JsonValue) -> Result<JsonValue, String> {
    let format = string_field_with_default(args, "format", "full");
    let timezone = string_field_with_default(args, "timezone", "system");
    let timestamp = now_epoch_ms();
    Ok(object(vec![
        ("tool", JsonValue::String("get_current_datetime".to_string())),
        ("timezone", JsonValue::String(timezone)),
        ("format", JsonValue::String(format.clone())),
        ("result", JsonValue::String(match format.as_str() {
            "date" => format_utc_date(timestamp),
            "time" => format_utc_time(timestamp),
            "iso" => now_iso8601(),
            _ => format!("{} UTC", now_iso8601()),
        })),
    ]))
}

fn execute_date_arithmetic(args: &JsonValue) -> Result<JsonValue, String> {
    let date = string_field(args, "date");
    let operation = string_field(args, "operation");
    let amount = number_field(args, "amount") as i64;
    let unit = string_field(args, "unit");
    let timestamp = parse_iso_like_to_epoch_ms(&date)?;
    let delta_ms = match unit.as_str() {
        "minutes" => amount * 60_000,
        "hours" => amount * 3_600_000,
        "days" => amount * 86_400_000,
        "months" => amount * 30 * 86_400_000,
        "years" => amount * 365 * 86_400_000,
        _ => return Err("Unknown unit. Use days, hours, minutes, months, or years.".to_string()),
    };
    let result = if operation == "subtract" {
        timestamp.saturating_sub(delta_ms as u64)
    } else {
        timestamp.saturating_add(delta_ms as u64)
    };
    Ok(object(vec![
        ("tool", JsonValue::String("date_arithmetic".to_string())),
        ("result", JsonValue::String(epoch_ms_to_iso8601(result))),
        ("timezone", JsonValue::String("UTC".to_string())),
        ("format", JsonValue::String("iso".to_string())),
    ]))
}

fn execute_timezone_convert(args: &JsonValue) -> Result<JsonValue, String> {
    let time = string_field(args, "time");
    let from = string_field(args, "from_timezone");
    let to = string_field(args, "to_timezone");
    if from.is_empty() || to.is_empty() {
        return Err("from_timezone and to_timezone are required".to_string());
    }
    let timestamp = parse_iso_like_to_epoch_ms(&time)?;
    Ok(object(vec![
        ("tool", JsonValue::String("timezone_convert".to_string())),
        ("result", JsonValue::String(epoch_ms_to_iso8601(timestamp))),
        ("timezone", JsonValue::String(to)),
        ("format", JsonValue::String("iso".to_string())),
        ("sourceTimezone", JsonValue::String(from)),
    ]))
}

fn execute_text_transform(args: &JsonValue) -> Result<JsonValue, String> {
    let text = string_field(args, "text");
    let operation = string_field(args, "operation").to_lowercase();
    let output = match operation.as_str() {
        "uppercase" | "upper" => text.to_uppercase(),
        "lowercase" | "lower" => text.to_lowercase(),
        "reverse" => text.chars().rev().collect(),
        "title_case" | "title" => text
            .split_whitespace()
            .map(|word| {
                let mut chars = word.chars();
                match chars.next() {
                    Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
                    None => String::new(),
                }
            })
            .collect::<Vec<_>>()
            .join(" "),
        "snake_case" | "snake" => text
            .replace([' ', '-'], "_")
            .replace("__", "_")
            .to_lowercase(),
        "camel_case" | "camel" => {
            let parts = text
                .split(|value: char| value == ' ' || value == '_' || value == '-')
                .filter(|value| !value.is_empty())
                .collect::<Vec<_>>();
            if parts.is_empty() {
                String::new()
            } else {
                let head = parts[0].to_lowercase();
                let tail = parts[1..]
                    .iter()
                    .map(|part| {
                        let mut chars = part.chars();
                        match chars.next() {
                            Some(first) => first.to_uppercase().collect::<String>() + &chars.as_str().to_lowercase(),
                            None => String::new(),
                        }
                    })
                    .collect::<String>();
                format!("{head}{tail}")
            }
        }
        "trim" => text.trim().to_string(),
        _ => return Err("Unknown operation".to_string()),
    };
    Ok(object(vec![
        ("tool", JsonValue::String("text_transform".to_string())),
        ("operation", JsonValue::String(operation)),
        ("input", JsonValue::String(text)),
        ("output", JsonValue::String(output)),
    ]))
}

fn execute_hash_generate(args: &JsonValue) -> Result<JsonValue, String> {
    let text = string_field(args, "text");
    let algorithm = string_field(args, "algorithm").to_uppercase();
    let temp_path = env::temp_dir().join(format!("santiya-hash-{}.txt", new_id("tmp")));
    fs::write(&temp_path, text.as_bytes()).map_err(|error| format!("Failed to write temp hash file: {error}"))?;
    let command = format!(
        "$h = Get-FileHash -Algorithm {} -LiteralPath '{}'; $h.Hash",
        match algorithm.as_str() {
            "MD5" => "MD5",
            "SHA1" | "SHA-1" => "SHA1",
            "SHA256" | "SHA-256" => "SHA256",
            "SHA512" | "SHA-512" => "SHA512",
            _ => {
                let _ = fs::remove_file(&temp_path);
                return Err("Unsupported algorithm. Use md5, sha1, sha256, or sha512.".to_string());
            }
        },
        escape_single_quotes(&temp_path.to_string_lossy())
    );
    let output = Command::new("powershell")
        .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", &command])
        .output()
        .map_err(|error| format!("Failed to execute hash command: {error}"))?;
    let _ = fs::remove_file(&temp_path);
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).trim().to_string());
    }
    let hash = String::from_utf8_lossy(&output.stdout).trim().to_string().to_lowercase();
    Ok(object(vec![
        ("tool", JsonValue::String("hash_generate".to_string())),
        ("operation", JsonValue::String(algorithm.to_lowercase())),
        ("input", JsonValue::String(text)),
        ("output", JsonValue::String(hash)),
    ]))
}

fn execute_uuid_generate(args: &JsonValue) -> Result<JsonValue, String> {
    let count = number_field_with_default(args, "count", 1.0).clamp(1.0, 10.0) as usize;
    let output = (0..count).map(|_| pseudo_uuid()).collect::<Vec<_>>().join("\n");
    Ok(object(vec![
        ("tool", JsonValue::String("uuid_generate".to_string())),
        ("operation", JsonValue::String("generate".to_string())),
        ("input", JsonValue::String(format!("count={count}"))),
        ("output", JsonValue::String(output)),
    ]))
}

fn execute_text_stats(args: &JsonValue) -> Result<JsonValue, String> {
    let text = string_field(args, "text");
    let char_count = text.chars().count() as u64;
    let char_count_no_spaces = text.chars().filter(|value| !value.is_whitespace()).count() as u64;
    let word_count = text.split_whitespace().count() as u64;
    let line_count = if text.is_empty() { 0 } else { text.lines().count() as u64 };
    let sentence_count = text
        .split(|value| matches!(value, '.' | '!' | '?'))
        .filter(|value| !value.trim().is_empty())
        .count() as u64;
    Ok(object(vec![
        ("charCount", JsonValue::Number(char_count as f64)),
        ("charCountNoSpaces", JsonValue::Number(char_count_no_spaces as f64)),
        ("wordCount", JsonValue::Number(word_count as f64)),
        ("lineCount", JsonValue::Number(line_count as f64)),
        ("sentenceCount", JsonValue::Number(sentence_count as f64)),
        (
            "summary",
            JsonValue::String(format!(
                "Characters: {char_count} (no spaces: {char_count_no_spaces})\nWords: {word_count}\nLines: {line_count}\nSentences: {sentence_count}"
            )),
        ),
    ]))
}

fn execute_json_format(args: &JsonValue) -> Result<JsonValue, String> {
    let json_text = string_field(args, "json");
    let validate_only = bool_field(args, "validate_only");
    match parse_json(&json_text) {
        Ok(value) => Ok(object(vec![
            ("tool", JsonValue::String("json_format".to_string())),
            ("operation", JsonValue::String(if validate_only { "validate" } else { "format" }.to_string())),
            ("input", JsonValue::String(json_text)),
            (
                "output",
                JsonValue::String(if validate_only {
                    "Valid JSON".to_string()
                } else {
                    value.stringify_pretty()
                }),
            ),
        ])),
        Err(error) => Ok(object(vec![
            ("tool", JsonValue::String("json_format".to_string())),
            ("operation", JsonValue::String(if validate_only { "validate" } else { "format" }.to_string())),
            ("input", JsonValue::String(json_text)),
            ("output", JsonValue::String(format!("Invalid JSON: {error}"))),
        ])),
    }
}

fn execute_base64_codec(args: &JsonValue) -> Result<JsonValue, String> {
    let text = string_field(args, "text");
    let operation = string_field(args, "operation").to_lowercase();
    let output = match operation.as_str() {
        "encode" => base64_encode(text.as_bytes()),
        "decode" => String::from_utf8(base64_decode(&text)?).map_err(|_| "Decoded bytes are not valid UTF-8".to_string())?,
        _ => return Err("Operation must be encode or decode".to_string()),
    };
    Ok(object(vec![
        ("tool", JsonValue::String("base64_codec".to_string())),
        ("operation", JsonValue::String(operation)),
        ("input", JsonValue::String(text)),
        ("output", JsonValue::String(output)),
    ]))
}

fn execute_create_file(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    let path = string_field(args, "path");
    let content = string_field(args, "content");
    let append = bool_field(args, "append");
    if path.is_empty() {
        return Err("path is required".to_string());
    }
    let target = sandbox_path(workspace, &path)?;
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|error| format!("Failed to create parent directories: {error}"))?;
    }
    if append {
        let mut file = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&target)
            .map_err(|error| format!("Failed to append file: {error}"))?;
        file.write_all(content.as_bytes())
            .map_err(|error| format!("Failed to write file: {error}"))?;
    } else {
        fs::write(&target, content.as_bytes()).map_err(|error| format!("Failed to write file: {error}"))?;
    }
    Ok(object(vec![
        ("tool", JsonValue::String("create_file".to_string())),
        ("path", JsonValue::String(target.to_string_lossy().to_string())),
        (
            "content",
            JsonValue::String(if append {
                format!("File appended successfully ({} characters written)", content.len())
            } else {
                format!("File created successfully ({} characters written)", content.len())
            }),
        ),
        ("fileCount", JsonValue::Number(1.0)),
        ("success", JsonValue::Bool(true)),
    ]))
}

fn execute_list_files(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    let path = string_field_with_default(args, "path", "");
    let filter = string_field(args, "filter");
    let recursive = bool_field(args, "recursive");
    let directory = sandbox_path(workspace, &path)?;
    if !directory.exists() {
        return Err("Directory not found".to_string());
    }
    let mut files = Vec::new();
    walk_files(&directory, recursive, &mut files);
    let filtered = files
        .into_iter()
        .filter(|file| wildcard_match(&filter, file.file_name().and_then(|value| value.to_str()).unwrap_or("")))
        .collect::<Vec<_>>();
    Ok(object(vec![
        ("tool", JsonValue::String("list_files".to_string())),
        ("path", JsonValue::String(directory.to_string_lossy().to_string())),
        (
            "files",
            JsonValue::Array(
                filtered
                    .iter()
                    .map(|file| JsonValue::String(file.to_string_lossy().to_string()))
                    .collect(),
            ),
        ),
        ("fileCount", JsonValue::Number(filtered.len() as f64)),
        ("success", JsonValue::Bool(true)),
    ]))
}

fn execute_read_text_file(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    let path = string_field(args, "path");
    let max_chars = number_field_with_default(args, "max_chars", 5000.0) as usize;
    let file = sandbox_path(workspace, &path)?;
    let content = fs::read_to_string(&file).map_err(|error| format!("Failed to read file: {error}"))?;
    Ok(object(vec![
        ("tool", JsonValue::String("read_text_file".to_string())),
        ("path", JsonValue::String(file.to_string_lossy().to_string())),
        ("content", JsonValue::String(content.chars().take(max_chars).collect())),
        ("fileCount", JsonValue::Number(1.0)),
        ("success", JsonValue::Bool(true)),
    ]))
}

fn execute_read_document(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    execute_read_text_file(args, workspace)
}

fn execute_read_pdf(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    let path = string_field(args, "path");
    let file = sandbox_path(workspace, &path)?;
    Ok(object(vec![
        ("tool", JsonValue::String("read_pdf".to_string())),
        ("path", JsonValue::String(file.to_string_lossy().to_string())),
        (
            "content",
            JsonValue::String("PDF extraction is registered but still provider-needed in this build.".to_string()),
        ),
        ("fileCount", JsonValue::Number(1.0)),
        ("success", JsonValue::Bool(false)),
    ]))
}

fn execute_search_files(args: &JsonValue, workspace: &Path) -> Result<JsonValue, String> {
    let query = string_field(args, "query").to_lowercase();
    let path = string_field_with_default(args, "path", "");
    let file_type = string_field(args, "file_type").to_lowercase();
    let directory = sandbox_path(workspace, &path)?;
    let mut files = Vec::new();
    walk_files(&directory, true, &mut files);
    let filtered = files
        .into_iter()
        .filter(|file| {
            let name = file.file_name().and_then(|value| value.to_str()).unwrap_or("").to_lowercase();
            let extension = file.extension().and_then(|value| value.to_str()).unwrap_or("").to_lowercase();
            let matches_type = match file_type.as_str() {
                "" => true,
                "pdf" => extension == "pdf",
                "text" => matches!(extension.as_str(), "txt" | "md" | "csv" | "json" | "log"),
                "image" => matches!(extension.as_str(), "png" | "jpg" | "jpeg" | "webp"),
                _ => true,
            };
            matches_type && name.contains(&query)
        })
        .collect::<Vec<_>>();
    Ok(object(vec![
        ("tool", JsonValue::String("search_files".to_string())),
        (
            "files",
            JsonValue::Array(filtered.iter().map(|file| JsonValue::String(file.to_string_lossy().to_string())).collect()),
        ),
        ("fileCount", JsonValue::Number(filtered.len() as f64)),
        ("success", JsonValue::Bool(true)),
    ]))
}

fn execute_notepad_write(args: &JsonValue, notes: &mut BTreeMap<String, String>) -> Result<JsonValue, String> {
    let key = string_field(args, "key");
    let content = string_field(args, "content");
    if key.is_empty() {
        return Err("key is required".to_string());
    }
    let existed = notes.insert(key.clone(), content.clone()).is_some();
    Ok(object(vec![
        ("tool", JsonValue::String("notepad_write".to_string())),
        ("key", JsonValue::String(key.clone())),
        (
            "content",
            JsonValue::String(if existed {
                format!("Note '{key}' updated ({} chars)", content.len())
            } else {
                format!("Note '{key}' saved ({} chars)", content.len())
            }),
        ),
        ("success", JsonValue::Bool(true)),
        ("noteCount", JsonValue::Number(notes.len() as f64)),
    ]))
}

fn execute_notepad_read(args: &JsonValue, notes: &BTreeMap<String, String>) -> Result<JsonValue, String> {
    let key = string_field(args, "key");
    let content = notes.get(&key).cloned().unwrap_or_else(|| format!("Note '{key}' not found"));
    Ok(object(vec![
        ("tool", JsonValue::String("notepad_read".to_string())),
        ("key", JsonValue::String(key)),
        ("content", JsonValue::String(content)),
        ("success", JsonValue::Bool(notes.contains_key(&string_field(args, "key")))),
        ("noteCount", JsonValue::Number(notes.len() as f64)),
    ]))
}

fn execute_notepad_list(notes: &BTreeMap<String, String>) -> Result<JsonValue, String> {
    let content = if notes.is_empty() {
        "No notes stored yet.".to_string()
    } else {
        notes
            .keys()
            .map(|key| format!("• {key}"))
            .collect::<Vec<_>>()
            .join("\n")
    };
    Ok(object(vec![
        ("tool", JsonValue::String("notepad_list".to_string())),
        ("key", JsonValue::String(String::new())),
        ("content", JsonValue::String(content)),
        ("success", JsonValue::Bool(true)),
        ("noteCount", JsonValue::Number(notes.len() as f64)),
    ]))
}

fn execute_get_system_info() -> Result<JsonValue, String> {
    Ok(object(vec![
        ("dateTime", JsonValue::String(now_iso8601())),
        ("timezone", JsonValue::String("UTC".to_string())),
        ("batteryPercent", JsonValue::Number(-1.0)),
        ("isCharging", JsonValue::Bool(false)),
        ("networkType", JsonValue::String("Connected".to_string())),
        (
            "deviceName",
            JsonValue::String(format!(
                "{} / {} cores",
                env::var("COMPUTERNAME").unwrap_or_else(|_| "Windows".to_string()),
                std::thread::available_parallelism().map(|value| value.get()).unwrap_or(1)
            )),
        ),
    ]))
}

fn execute_web_search(args: &JsonValue, settings: &CoreSettings) -> Result<JsonValue, String> {
    let query = string_field(args, "query");
    if query.is_empty() {
        return Err("query is required".to_string());
    }
    Ok(object(vec![
        ("query", JsonValue::String(query.clone())),
        ("totalResults", JsonValue::Number(0.0)),
        ("searchTimeMs", JsonValue::Number(0.0)),
        (
            "results",
            JsonValue::Array(vec![object(vec![
                ("title", JsonValue::String("Web search execution is policy-gated".to_string())),
                (
                    "url",
                    JsonValue::String(format!(
                        "https://duckduckgo.com/?q={}",
                        query.replace(' ', "+")
                    )),
                ),
                (
                    "snippet",
                    JsonValue::String(if settings.external_access_policy.enabled {
                        "External access is enabled, but this build does not embed a TLS-capable fetcher yet.".to_string()
                    } else {
                        "Enable external access policy and wire a network-capable provider to fetch live results.".to_string()
                    }),
                ),
                ("content", JsonValue::String(String::new())),
            ])]),
        ),
    ]))
}

fn evaluate_expression(expression: &str) -> Result<f64, String> {
    let normalized = expression
        .replace("pi", &std::f64::consts::PI.to_string())
        .replace("PI", &std::f64::consts::PI.to_string())
        .replace("sqrt", " sqrt ")
        .replace("sin", " sin ")
        .replace("cos", " cos ")
        .replace("tan", " tan ")
        .replace("log10", " log10 ")
        .replace("log", " log ")
        .replace("ln", " ln ")
        .replace("abs", " abs ");
    let mut parser = ExpressionParser::new(&normalized);
    parser.parse_expression()
}

struct ExpressionParser<'a> {
    chars: Vec<char>,
    pos: usize,
    raw: &'a str,
}

impl<'a> ExpressionParser<'a> {
    fn new(raw: &'a str) -> Self {
        Self {
            chars: raw.chars().collect(),
            pos: 0,
            raw,
        }
    }

    fn parse_expression(&mut self) -> Result<f64, String> {
        let mut result = self.parse_term()?;
        loop {
            self.skip_whitespace();
            match self.peek() {
                Some('+') => {
                    self.pos += 1;
                    result += self.parse_term()?;
                }
                Some('-') => {
                    self.pos += 1;
                    result -= self.parse_term()?;
                }
                _ => break,
            }
        }
        Ok(result)
    }

    fn parse_term(&mut self) -> Result<f64, String> {
        let mut result = self.parse_power()?;
        loop {
            self.skip_whitespace();
            match self.peek() {
                Some('*') => {
                    self.pos += 1;
                    result *= self.parse_power()?;
                }
                Some('/') => {
                    self.pos += 1;
                    let divisor = self.parse_power()?;
                    if divisor == 0.0 {
                        return Err("Division by zero".to_string());
                    }
                    result /= divisor;
                }
                Some('%') => {
                    self.pos += 1;
                    let divisor = self.parse_power()?;
                    if divisor == 0.0 {
                        return Err("Modulo by zero".to_string());
                    }
                    result %= divisor;
                }
                _ => break,
            }
        }
        Ok(result)
    }

    fn parse_power(&mut self) -> Result<f64, String> {
        let mut result = self.parse_unary()?;
        self.skip_whitespace();
        if self.peek() == Some('^') {
            self.pos += 1;
            result = result.powf(self.parse_unary()?);
        }
        Ok(result)
    }

    fn parse_unary(&mut self) -> Result<f64, String> {
        self.skip_whitespace();
        match self.peek() {
            Some('-') => {
                self.pos += 1;
                Ok(-self.parse_primary()?)
            }
            Some('+') => {
                self.pos += 1;
                self.parse_primary()
            }
            _ => self.parse_primary(),
        }
    }

    fn parse_primary(&mut self) -> Result<f64, String> {
        self.skip_whitespace();
        if self.peek() == Some('(') {
            self.pos += 1;
            let value = self.parse_expression()?;
            self.skip_whitespace();
            if self.peek() == Some(')') {
                self.pos += 1;
                return Ok(value);
            }
            return Err("Missing closing ')'".to_string());
        }
        if let Some(name) = self.parse_identifier() {
            self.skip_whitespace();
            if self.peek() == Some('(') {
                self.pos += 1;
                let value = self.parse_expression()?;
                self.skip_whitespace();
                if self.peek() == Some(')') {
                    self.pos += 1;
                }
                return match name.as_str() {
                    "sqrt" => Ok(value.sqrt()),
                    "sin" => Ok(value.sin()),
                    "cos" => Ok(value.cos()),
                    "tan" => Ok(value.tan()),
                    "log" | "log10" => Ok(value.log10()),
                    "ln" => Ok(value.ln()),
                    "abs" => Ok(value.abs()),
                    other => Err(format!("Unknown function '{other}'")),
                };
            }
            return Err(format!("Unknown identifier '{name}' in '{}'", self.raw));
        }
        self.parse_number()
    }

    fn parse_number(&mut self) -> Result<f64, String> {
        self.skip_whitespace();
        let start = self.pos;
        while matches!(self.peek(), Some('0'..='9' | '.')) {
            self.pos += 1;
        }
        let value = self
            .chars
            .get(start..self.pos)
            .ok_or_else(|| "Expected number".to_string())?
            .iter()
            .collect::<String>();
        value.parse::<f64>().map_err(|_| format!("Invalid number '{value}'"))
    }

    fn parse_identifier(&mut self) -> Option<String> {
        self.skip_whitespace();
        let start = self.pos;
        while matches!(self.peek(), Some('a'..='z' | 'A'..='Z')) {
            self.pos += 1;
        }
        if start == self.pos {
            None
        } else {
            Some(self.chars[start..self.pos].iter().collect())
        }
    }

    fn skip_whitespace(&mut self) {
        while matches!(self.peek(), Some(' ' | '\t' | '\n' | '\r')) {
            self.pos += 1;
        }
    }

    fn peek(&self) -> Option<char> {
        self.chars.get(self.pos).copied()
    }
}

fn convert_unit(value: f64, from: &str, to: &str) -> Result<f64, String> {
    let length = |unit: &str| match unit {
        "m" => Some(1.0),
        "km" => Some(1000.0),
        "cm" => Some(0.01),
        "mm" => Some(0.001),
        "mi" => Some(1609.34),
        "ft" => Some(0.3048),
        "in" => Some(0.0254),
        _ => None,
    };
    let weight = |unit: &str| match unit {
        "kg" => Some(1.0),
        "g" => Some(0.001),
        "lb" => Some(0.45359237),
        "oz" => Some(0.0283495),
        _ => None,
    };
    if let (Some(from_factor), Some(to_factor)) = (length(from), length(to)) {
        return Ok(value * from_factor / to_factor);
    }
    if let (Some(from_factor), Some(to_factor)) = (weight(from), weight(to)) {
        return Ok(value * from_factor / to_factor);
    }
    match (from, to) {
        ("c", "f") => Ok(value * 9.0 / 5.0 + 32.0),
        ("f", "c") => Ok((value - 32.0) * 5.0 / 9.0),
        ("c", "k") => Ok(value + 273.15),
        ("k", "c") => Ok(value - 273.15),
        ("f", "k") => Ok((value - 32.0) * 5.0 / 9.0 + 273.15),
        ("k", "f") => Ok((value - 273.15) * 9.0 / 5.0 + 32.0),
        _ => Err("Unsupported unit conversion".to_string()),
    }
}

fn format_number(value: f64) -> String {
    if value.fract() == 0.0 {
        format!("{}", value as i64)
    } else {
        format!("{value:.6}").trim_end_matches('0').trim_end_matches('.').to_string()
    }
}

fn sandbox_path(workspace: &Path, raw: &str) -> Result<PathBuf, String> {
    let candidate = if raw.is_empty() {
        workspace.to_path_buf()
    } else {
        workspace.join(raw)
    };
    let canonical_root = fs::canonicalize(workspace).unwrap_or_else(|_| workspace.to_path_buf());
    let canonical_candidate = if candidate.exists() {
        fs::canonicalize(&candidate).map_err(|error| format!("Invalid path: {error}"))?
    } else {
        let parent = candidate.parent().unwrap_or(workspace);
        let canonical_parent = fs::canonicalize(parent).unwrap_or_else(|_| parent.to_path_buf());
        canonical_parent.join(candidate.file_name().unwrap_or_default())
    };
    if !canonical_candidate.starts_with(&canonical_root) {
        return Err(format!("Access denied: path must stay inside {}", canonical_root.display()));
    }
    Ok(canonical_candidate)
}

fn walk_files(root: &Path, recursive: bool, output: &mut Vec<PathBuf>) {
    let Ok(entries) = fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() && recursive {
            walk_files(&path, recursive, output);
        } else if path.is_file() {
            output.push(path);
        }
    }
}

fn wildcard_match(pattern: &str, text: &str) -> bool {
    if pattern.is_empty() {
        return true;
    }
    if pattern == "*" {
        return true;
    }
    if let Some(stripped) = pattern.strip_prefix("*.") {
        return text.to_ascii_lowercase().ends_with(&format!(".{}", stripped.to_ascii_lowercase()));
    }
    text.to_ascii_lowercase().contains(&pattern.to_ascii_lowercase().replace('*', ""))
}

fn base64_encode(data: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut output = String::new();
    let mut index = 0;
    while index < data.len() {
        let a = data[index];
        let b = *data.get(index + 1).unwrap_or(&0);
        let c = *data.get(index + 2).unwrap_or(&0);
        let triple = ((a as u32) << 16) | ((b as u32) << 8) | c as u32;
        output.push(TABLE[((triple >> 18) & 0x3F) as usize] as char);
        output.push(TABLE[((triple >> 12) & 0x3F) as usize] as char);
        if index + 1 < data.len() {
            output.push(TABLE[((triple >> 6) & 0x3F) as usize] as char);
        } else {
            output.push('=');
        }
        if index + 2 < data.len() {
            output.push(TABLE[(triple & 0x3F) as usize] as char);
        } else {
            output.push('=');
        }
        index += 3;
    }
    output
}

fn base64_decode(input: &str) -> Result<Vec<u8>, String> {
    let table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let cleaned = input.trim().replace('\n', "");
    if cleaned.len() % 4 != 0 {
        return Err("Invalid Base64 length".to_string());
    }
    let mut bytes = Vec::new();
    let chars = cleaned.chars().collect::<Vec<_>>();
    for chunk in chars.chunks(4) {
        let mut sextets = [0_u8; 4];
        let mut padding = 0;
        for (index, value) in chunk.iter().enumerate() {
            if *value == '=' {
                sextets[index] = 0;
                padding += 1;
            } else if let Some(pos) = table.find(*value) {
                sextets[index] = pos as u8;
            } else {
                return Err("Invalid Base64 input".to_string());
            }
        }
        let triple = ((sextets[0] as u32) << 18)
            | ((sextets[1] as u32) << 12)
            | ((sextets[2] as u32) << 6)
            | sextets[3] as u32;
        bytes.push(((triple >> 16) & 0xFF) as u8);
        if padding < 2 {
            bytes.push(((triple >> 8) & 0xFF) as u8);
        }
        if padding < 1 {
            bytes.push((triple & 0xFF) as u8);
        }
    }
    Ok(bytes)
}

fn now_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|_| Duration::from_secs(0))
        .as_millis() as u64
}

fn now_iso8601() -> String {
    epoch_ms_to_iso8601(now_epoch_ms())
}

fn epoch_ms_to_iso8601(value: u64) -> String {
    let seconds = value / 1000;
    format!("{seconds}Z")
}

fn format_utc_date(value: u64) -> String {
    let days = value / 86_400_000;
    format!("UTC day {days}")
}

fn format_utc_time(value: u64) -> String {
    let seconds = (value / 1000) % 86_400;
    format!("{seconds}s")
}

fn parse_iso_like_to_epoch_ms(value: &str) -> Result<u64, String> {
    if let Ok(seconds) = value.parse::<u64>() {
        return Ok(seconds * 1000);
    }
    Err("This build currently accepts epoch seconds or epoch-like numeric strings for arithmetic/timezone operations.".to_string())
}

fn generate_pairing_token() -> String {
    format!("lan-{:016x}{:016x}", now_epoch_ms(), ID_COUNTER.fetch_add(1, Ordering::Relaxed))
}

fn pseudo_uuid() -> String {
    let base = format!("{:032x}", now_epoch_ms() ^ ID_COUNTER.fetch_add(1, Ordering::Relaxed));
    format!(
        "{}-{}-{}-{}-{}",
        &base[0..8],
        &base[8..12],
        &base[12..16],
        &base[16..20],
        &base[20..32]
    )
}

fn new_id(prefix: &str) -> String {
    format!("{prefix}-{:016x}-{:04x}", now_epoch_ms(), ID_COUNTER.fetch_add(1, Ordering::Relaxed))
}

fn stable_id(path: &Path) -> String {
    let mut hash = 1469598103934665603_u64;
    for byte in path.to_string_lossy().as_bytes() {
        hash ^= *byte as u64;
        hash = hash.wrapping_mul(1099511628211);
    }
    format!("model-{hash:016x}")
}

fn default_core_home() -> PathBuf {
    if let Ok(local_app_data) = env::var("LOCALAPPDATA") {
        return PathBuf::from(local_app_data).join("SantiyaLocalAiHub").join("windows-core");
    }
    PathBuf::from(".").join("windows-core")
}

fn string_or_null(value: &str) -> JsonValue {
    if value.is_empty() {
        JsonValue::Null
    } else {
        JsonValue::String(value.to_string())
    }
}

fn optional_number(value: Option<u64>) -> JsonValue {
    value.map(|value| JsonValue::Number(value as f64)).unwrap_or(JsonValue::Null)
}

fn optional_float(value: Option<f64>) -> JsonValue {
    value.map(JsonValue::Number).unwrap_or(JsonValue::Null)
}

fn string_field(value: &JsonValue, key: &str) -> String {
    value.get_string(key).unwrap_or_default()
}

fn string_field_with_default(value: &JsonValue, key: &str, fallback: &str) -> String {
    value.get_string(key).unwrap_or_else(|| fallback.to_string())
}

fn number_field(value: &JsonValue, key: &str) -> f64 {
    value.get_f64(key).unwrap_or_default()
}

fn number_field_with_default(value: &JsonValue, key: &str, fallback: f64) -> f64 {
    value.get_f64(key).unwrap_or(fallback)
}

fn bool_field(value: &JsonValue, key: &str) -> bool {
    value.get_bool(key).unwrap_or(false)
}

fn bool_field_with_default(value: &JsonValue, key: &str, fallback: bool) -> bool {
    value.get_bool(key).unwrap_or(fallback)
}

fn path_field_with_default(value: &JsonValue, key: &str, fallback: PathBuf) -> PathBuf {
    value
        .get_string(key)
        .map(PathBuf::from)
        .unwrap_or(fallback)
}

fn string_array_field(value: &JsonValue, key: &str) -> Vec<String> {
    value
        .get(key)
        .and_then(JsonValue::as_array)
        .map(|items| items.iter().filter_map(JsonValue::as_str).map(ToOwned::to_owned).collect())
        .unwrap_or_default()
}

fn escape_single_quotes(value: &str) -> String {
    value.replace('\'', "''")
}

trait AppHomeFile {
    fn app_home_file(&self, prefix: &str, extension: &str) -> PathBuf;
}

impl AppHomeFile for CoreSettings {
    fn app_home_file(&self, prefix: &str, extension: &str) -> PathBuf {
        self.notes_dir
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join(format!("{prefix}-{}.{}", new_id("artifact"), extension))
    }
}
