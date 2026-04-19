use anyhow::{anyhow, Context, Result};
use chrono::Utc;
use futures::StreamExt;
use reqwest::{multipart, Client, StatusCode};
use rdkafka::config::ClientConfig;
use rdkafka::consumer::{CommitMode, Consumer, StreamConsumer};
use rdkafka::message::Message;
use rdkafka::producer::{FutureProducer, FutureRecord};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::f64::consts::PI;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::fs;
use tracing::{error, info, warn};
use uuid::Uuid;

const INTERNAL_SECRET_HEADER: &str = "X-Internal-Secret";
const GENERATED_FILE_KIND: &str = "DOCUMENT";

#[derive(Debug, Clone)]
struct WorkerConfig {
    kafka_bootstrap_servers: String,
    task_topic_requested: String,
    task_topic_status_updated: String,
    worker_max_file_size_bytes: u64,
    worker_input_root: PathBuf,
    file_service_base_url: String,
    file_internal_shared_secret: String,
}

impl WorkerConfig {
    fn from_env() -> Result<Self> {
        let kafka_bootstrap_servers = env_or_default("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        let task_topic_requested = env_or_default("TASK_TOPIC_REQUESTED", "task.requested.v1");
        let task_topic_status_updated =
            env_or_default("TASK_TOPIC_STATUS_UPDATED", "task.status-updated.v1");
        let worker_max_file_size_bytes = std::env::var("WORKER_MAX_FILE_SIZE_BYTES")
            .ok()
            .and_then(|v| v.parse::<u64>().ok())
            .unwrap_or(104_857_600);
        let worker_input_root = PathBuf::from(env_or_default("WORKER_INPUT_ROOT", "./data/input"));
        let file_service_base_url = env_or_default("FILE_SERVICE_BASE_URL", "http://localhost:8083")
            .trim_end_matches('/')
            .to_string();
        let file_internal_shared_secret = required_env("FILE_INTERNAL_SHARED_SECRET")?;

        Ok(Self {
            kafka_bootstrap_servers,
            task_topic_requested,
            task_topic_status_updated,
            worker_max_file_size_bytes,
            worker_input_root,
            file_service_base_url,
            file_internal_shared_secret,
        })
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TaskRequestedEvent {
    task_id: Uuid,
    user_id: Uuid,
    source_file_id: Option<Uuid>,
    source_file_path: Option<String>,
    ground_truth_path: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct TaskStatusUpdatedEvent {
    task_id: Uuid,
    status: String,
    progress_percent: Option<i32>,
    error_code: Option<String>,
    error_message: Option<String>,
    result_csv_path: Option<String>,
    result_json_path: Option<String>,
    result_csv_file_id: Option<Uuid>,
    result_json_file_id: Option<Uuid>,
    metrics_json: Option<String>,
    updated_at: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UploadedFileResponse {
    id: Uuid,
}

#[derive(Debug, Clone)]
struct ImuSample {
    timestamp_us: u64,
    imu_id: u8,
    accel: [f64; 3],
    gyro: [f64; 3],
}

#[derive(Debug, Clone)]
struct BaroSample {
    timestamp_us: u64,
    baro_id: u8,
    pressure_pa: f64,
    altitude_m: f64,
}

#[derive(Debug, Clone)]
struct VibeSample {
    timestamp_us: u64,
    vibration: f64,
}

#[derive(Debug, Clone)]
struct GpsSample {
    timestamp_ms: u64,
    x: f64,
    y: f64,
    z: f64,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct TrajectoryPoint {
    timestamp: u64,
    x: f64,
    y: f64,
    z: f64,
    speed: f64,
    roll: f64,
    pitch: f64,
    yaw: f64,
    vibration: f64,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct MetricSnapshot {
    timestamp: u64,
    accel_x: f64,
    accel_y: f64,
    accel_z: f64,
    gyro_x: f64,
    gyro_y: f64,
    gyro_z: f64,
    pressure: f64,
    altitude: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct MetricsPayload {
    schema_version: String,
    file_size_bytes: usize,
    point_count: usize,
    endpoint_error_m: f64,
    rms_crosstrack_error_m: f64,
    total_distance_m: f64,
    max_vibration: f64,
    peak_abs_accel: f64,
    peak_abs_gyro: f64,
    used_imu_count: usize,
    used_baro_count: usize,
    ground_truth_used: bool,
    trajectory: Vec<TrajectoryPoint>,
    metrics: Vec<MetricSnapshot>,
}

#[derive(Debug)]
struct ProcessedTaskArtifacts {
    csv_path: String,
    metrics_path: String,
    csv_file_id: Uuid,
    metrics_file_id: Uuid,
    metrics_json: String,
}

#[derive(Debug, Default)]
struct ParsedLog {
    imu_samples: Vec<ImuSample>,
    baro_samples: Vec<BaroSample>,
    vibe_samples: Vec<VibeSample>,
    gps_samples: Vec<GpsSample>,
}

#[derive(Debug, Clone)]
struct FmtDefinition {
    message_type: u8,
    message_len: usize,
    name: String,
    columns: Vec<String>,
    parsers: Vec<FieldParser>,
}

#[derive(Debug, Clone)]
enum FieldParser {
    I8,
    U8,
    I16,
    U16,
    I32,
    U32,
    I64,
    U64,
    F32,
    F64,
    Char(usize),
    ScaledI16(f64),
    ScaledU16(f64),
    ScaledI32(f64),
    ScaledU32(f64),
}

#[derive(Debug, Clone)]
enum FieldValue {
    Number(f64),
    Text(String),
}

#[tokio::main]
async fn main() -> Result<()> {
    init_logging();
    let cfg = WorkerConfig::from_env()?;
    let http_client = Client::builder()
        .timeout(Duration::from_secs(60))
        .build()
        .context("failed to create HTTP client")?;

    let consumer: StreamConsumer = ClientConfig::new()
        .set("bootstrap.servers", &cfg.kafka_bootstrap_servers)
        .set("group.id", "aeropulse-worker")
        .set("enable.auto.commit", "false")
        .set("auto.offset.reset", "earliest")
        .create()
        .context("failed to create Kafka consumer")?;

    consumer
        .subscribe(&[&cfg.task_topic_requested])
        .context("failed to subscribe to topic")?;

    let producer: FutureProducer = ClientConfig::new()
        .set("bootstrap.servers", &cfg.kafka_bootstrap_servers)
        .create()
        .context("failed to create Kafka producer")?;

    info!(
        "worker started; consuming topic={} file_service_base_url={}",
        cfg.task_topic_requested, cfg.file_service_base_url
    );

    let mut stream = consumer.stream();
    while let Some(result) = stream.next().await {
        match result {
            Ok(message) => {
                if let Err(err) = process_message(&cfg, &http_client, &producer, message.payload()).await {
                    error!("message processing failed: {err:#}");
                }
                if let Err(err) = consumer.commit_message(&message, CommitMode::Async) {
                    warn!("failed to commit Kafka offset: {err}");
                }
            }
            Err(err) => error!("kafka consumer error: {err}"),
        }
    }

    Ok(())
}

async fn process_message(
    cfg: &WorkerConfig,
    http_client: &Client,
    producer: &FutureProducer,
    payload: Option<&[u8]>,
) -> Result<()> {
    let payload = payload.ok_or_else(|| anyhow!("empty message payload"))?;
    let event: TaskRequestedEvent =
        serde_json::from_slice(payload).context("failed to deserialize TaskRequestedEvent")?;

    publish_status(
        cfg,
        producer,
        TaskStatusUpdatedEvent {
            task_id: event.task_id,
            status: "PROCESSING".to_string(),
            progress_percent: Some(10),
            error_code: None,
            error_message: None,
            result_csv_path: None,
            result_json_path: None,
            result_csv_file_id: None,
            result_json_file_id: None,
            metrics_json: None,
            updated_at: Utc::now().to_rfc3339(),
        },
    )
    .await?;

    match process_task(cfg, http_client, &event).await {
        Ok(artifacts) => {
            publish_status(
                cfg,
                producer,
                TaskStatusUpdatedEvent {
                    task_id: event.task_id,
                    status: "COMPLETED".to_string(),
                    progress_percent: Some(100),
                    error_code: None,
                    error_message: None,
                    result_csv_path: Some(artifacts.csv_path),
                    result_json_path: Some(artifacts.metrics_path),
                    result_csv_file_id: Some(artifacts.csv_file_id),
                    result_json_file_id: Some(artifacts.metrics_file_id),
                    metrics_json: Some(artifacts.metrics_json),
                    updated_at: Utc::now().to_rfc3339(),
                },
            )
            .await?;
        }
        Err(err) => {
            let error_message = truncate_for_event(&format!("{err:#}"), 1800);
            let error_code = classify_error_code(&error_message);
            publish_status(
                cfg,
                producer,
                TaskStatusUpdatedEvent {
                    task_id: event.task_id,
                    status: "FAILED".to_string(),
                    progress_percent: Some(100),
                    error_code: Some(error_code),
                    error_message: Some(error_message),
                    result_csv_path: None,
                    result_json_path: None,
                    result_csv_file_id: None,
                    result_json_file_id: None,
                    metrics_json: None,
                    updated_at: Utc::now().to_rfc3339(),
                },
            )
            .await?;
        }
    }

    Ok(())
}

async fn process_task(
    cfg: &WorkerConfig,
    http_client: &Client,
    event: &TaskRequestedEvent,
) -> Result<ProcessedTaskArtifacts> {
    let bytes = read_input_bytes(cfg, http_client, event).await?;
    if bytes.is_empty() {
        return Err(anyhow!("input file is empty"));
    }

    let parsed = parse_dataflash_log(&bytes)?;
    validate_sensor_coverage(&parsed)?;

    let ground_truth = if let Some(path) = &event.ground_truth_path {
        let resolved_gt = resolve_input_path(&cfg.worker_input_root, path);
        if resolved_gt.exists() {
            Some(load_ground_truth_csv(&resolved_gt).await?)
        } else {
            None
        }
    } else {
        None
    };

    let payload = run_inertial_pipeline(&parsed, bytes.len(), ground_truth.as_deref())?;

    let csv_text = render_trajectory_csv(&payload.trajectory);
    let metrics_json = serde_json::to_string(&payload).context("failed to serialize metrics payload")?;

    let csv_file = upload_generated_artifact(
        cfg,
        http_client,
        event.user_id,
        format!("{}-trajectory.csv", event.task_id),
        "text/csv",
        csv_text.into_bytes(),
    )
    .await?;

    let metrics_file = upload_generated_artifact(
        cfg,
        http_client,
        event.user_id,
        format!("{}-metrics.json", event.task_id),
        "application/json",
        metrics_json.as_bytes().to_vec(),
    )
    .await?;

    Ok(ProcessedTaskArtifacts {
        csv_path: download_path(csv_file.id),
        metrics_path: download_path(metrics_file.id),
        csv_file_id: csv_file.id,
        metrics_file_id: metrics_file.id,
        metrics_json,
    })
}

async fn read_input_bytes(
    cfg: &WorkerConfig,
    http_client: &Client,
    event: &TaskRequestedEvent,
) -> Result<Vec<u8>> {
    if let Some(file_id) = event.source_file_id {
        return download_input_from_file_service(cfg, http_client, file_id).await;
    }

    let source_file_path = event
        .source_file_path
        .as_ref()
        .ok_or_else(|| anyhow!("sourceFileId or sourceFilePath is required for worker file access"))?;

    read_local_input(cfg, source_file_path).await
}

async fn read_local_input(cfg: &WorkerConfig, source_file_path: &str) -> Result<Vec<u8>> {
    let resolved_input = resolve_input_path(&cfg.worker_input_root, source_file_path);
    validate_input_file(&resolved_input, cfg.worker_max_file_size_bytes).await?;

    let bytes = fs::read(&resolved_input)
        .await
        .with_context(|| format!("failed to read input file {}", resolved_input.display()))?;

    if bytes.is_empty() {
        return Err(anyhow!("input file is empty"));
    }

    Ok(bytes)
}

async fn download_input_from_file_service(
    cfg: &WorkerConfig,
    http_client: &Client,
    file_id: Uuid,
) -> Result<Vec<u8>> {
    let url = format!("{}/internal/files/{file_id}/download", cfg.file_service_base_url);
    let response = http_client
        .get(&url)
        .header(INTERNAL_SECRET_HEADER, &cfg.file_internal_shared_secret)
        .send()
        .await
        .with_context(|| format!("failed to download input file from file-service fileId={file_id}"))?;

    match response.status() {
        StatusCode::OK => {
            if let Some(content_length) = response.content_length() {
                if content_length == 0 {
                    return Err(anyhow!("input file is empty: fileId={file_id}"));
                }
                if content_length > cfg.worker_max_file_size_bytes {
                    return Err(anyhow!(
                        "input file is too large: {} bytes (max {})",
                        content_length,
                        cfg.worker_max_file_size_bytes
                    ));
                }
            }

            let bytes = response
                .bytes()
                .await
                .with_context(|| format!("failed reading downloaded bytes from file-service fileId={file_id}"))?
                .to_vec();

            if bytes.is_empty() {
                return Err(anyhow!("input file is empty: fileId={file_id}"));
            }
            if (bytes.len() as u64) > cfg.worker_max_file_size_bytes {
                return Err(anyhow!(
                    "input file is too large: {} bytes (max {})",
                    bytes.len(),
                    cfg.worker_max_file_size_bytes
                ));
            }

            Ok(bytes)
        }
        StatusCode::NOT_FOUND => Err(anyhow!("input file does not exist: fileId={file_id}")),
        StatusCode::UNAUTHORIZED => Err(anyhow!(
            "internal file-service authentication failed while downloading input file"
        )),
        StatusCode::CONFLICT => {
            let body = response.text().await.unwrap_or_default();
            if body.to_ascii_lowercase().contains("pending") {
                Err(anyhow!("input file is not ready for processing"))
            } else {
                Err(anyhow!(
                    "input file conflict from file-service: status={} body={}",
                    StatusCode::CONFLICT,
                    truncate_for_event(&body, 400)
                ))
            }
        }
        status => {
            let body = response.text().await.unwrap_or_default();
            Err(anyhow!(
                "failed to download input file from file-service fileId={}: status={} body={}",
                file_id,
                status,
                truncate_for_event(&body, 400)
            ))
        }
    }
}

async fn upload_generated_artifact(
    cfg: &WorkerConfig,
    http_client: &Client,
    owner_id: Uuid,
    file_name: String,
    content_type: &'static str,
    bytes: Vec<u8>,
) -> Result<UploadedFileResponse> {
    let url = format!("{}/internal/files/users/{owner_id}/upload", cfg.file_service_base_url);

    let part = multipart::Part::bytes(bytes)
        .file_name(file_name.clone())
        .mime_str(content_type)
        .with_context(|| format!("failed to prepare multipart upload for {}", file_name))?;
    let form = multipart::Form::new().part("file", part);

    let response = http_client
        .post(&url)
        .header(INTERNAL_SECRET_HEADER, &cfg.file_internal_shared_secret)
        .query(&[("fileKind", GENERATED_FILE_KIND)])
        .multipart(form)
        .send()
        .await
        .with_context(|| format!("failed to upload generated artifact {}", file_name))?;

    match response.status() {
        StatusCode::OK => response
            .json::<UploadedFileResponse>()
            .await
            .with_context(|| format!("failed to parse upload response for {}", file_name)),
        StatusCode::UNAUTHORIZED => Err(anyhow!(
            "internal file-service authentication failed while uploading generated artifact"
        )),
        status => {
            let body = response.text().await.unwrap_or_default();
            Err(anyhow!(
                "failed to upload generated artifact {}: status={} body={}",
                file_name,
                status,
                truncate_for_event(&body, 400)
            ))
        }
    }
}

fn resolve_input_path(input_root: &Path, source_file_path: &str) -> PathBuf {
    let provided = PathBuf::from(source_file_path);
    if provided.is_absolute() {
        provided
    } else {
        input_root.join(provided)
    }
}

async fn validate_input_file(path: &Path, max_size_bytes: u64) -> Result<()> {
    let metadata = fs::symlink_metadata(path)
        .await
        .with_context(|| format!("input file does not exist: {}", path.display()))?;

    if metadata.file_type().is_symlink() {
        return Err(anyhow!("input symlink is not allowed: {}", path.display()));
    }
    if !metadata.is_file() {
        return Err(anyhow!("input path is not a regular file: {}", path.display()));
    }
    if metadata.len() == 0 {
        return Err(anyhow!("input file is empty: {}", path.display()));
    }
    if metadata.len() > max_size_bytes {
        return Err(anyhow!(
            "input file is too large: {} bytes (max {})",
            metadata.len(),
            max_size_bytes
        ));
    }

    Ok(())
}

fn render_trajectory_csv(points: &[TrajectoryPoint]) -> String {
    let mut out = String::with_capacity(points.len().saturating_mul(36).max(64));
    out.push_str("timestamp,x,y,z\n");
    for p in points {
        out.push_str(&format!("{},{:.6},{:.6},{:.6}\n", p.timestamp, p.x, p.y, p.z));
    }
    out
}

async fn publish_status(
    cfg: &WorkerConfig,
    producer: &FutureProducer,
    event: TaskStatusUpdatedEvent,
) -> Result<()> {
    let payload = serde_json::to_string(&event).context("failed to serialize status event")?;
    producer
        .send(
            FutureRecord::to(&cfg.task_topic_status_updated)
                .payload(&payload)
                .key(&event.task_id.to_string()),
            Duration::from_secs(5),
        )
        .await
        .map_err(|(err, _)| anyhow!("kafka send failed: {err}"))?;
    Ok(())
}

fn classify_error_code(message: &str) -> String {
    let msg = message.to_lowercase();
    if msg.contains("does not exist") {
        "INPUT_NOT_FOUND".to_string()
    } else if msg.contains("too large") {
        "INPUT_TOO_LARGE".to_string()
    } else if msg.contains("symlink") {
        "INPUT_SYMLINK_DENIED".to_string()
    } else if msg.contains("not ready") {
        "INPUT_NOT_READY".to_string()
    } else if msg.contains("empty") {
        "INPUT_EMPTY".to_string()
    } else if msg.contains("dataflash") {
        "INPUT_DATAFLASH_INVALID".to_string()
    } else if msg.contains("imu") && msg.contains("count") {
        "INS_IMU_COVERAGE_INVALID".to_string()
    } else if msg.contains("baro") {
        "INS_BARO_COVERAGE_INVALID".to_string()
    } else if msg.contains("metricsjson") || msg.contains("trajectory") {
        "TASK_METRICS_PAYLOAD_INVALID".to_string()
    } else {
        "WORKER_PROCESSING_FAILED".to_string()
    }
}

fn truncate_for_event(value: &str, max_len: usize) -> String {
    if value.len() <= max_len {
        return value.to_string();
    }
    let mut truncated = value
        .chars()
        .take(max_len.saturating_sub(3))
        .collect::<String>();
    truncated.push_str("...");
    truncated
}

fn download_path(file_id: Uuid) -> String {
    format!("/api/files/{file_id}/download")
}

fn env_or_default(name: &str, default_value: &str) -> String {
    std::env::var(name)
        .ok()
        .filter(|v| !v.trim().is_empty())
        .unwrap_or_else(|| default_value.to_string())
}

fn required_env(name: &str) -> Result<String> {
    std::env::var(name)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .ok_or_else(|| anyhow!("environment variable {} is required", name))
}

fn init_logging() {
    let filter = std::env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string());
    tracing_subscriber::fmt()
        .with_env_filter(filter)
        .with_target(false)
        .compact()
        .init();
}

fn parse_dataflash_log(bytes: &[u8]) -> Result<ParsedLog> {
    if bytes.len() < 8 {
        return Err(anyhow!("dataflash payload is too small"));
    }

    let mut parsed = ParsedLog::default();
    let mut formats: HashMap<u8, FmtDefinition> = HashMap::new();
    let mut i = 0usize;

    while i + 3 < bytes.len() {
        if bytes[i] != 0xA3 || bytes[i + 1] != 0x95 {
            i += 1;
            continue;
        }

        let msg_type = bytes[i + 2];

        if msg_type == 0x80 {
            if i + 89 > bytes.len() {
                break;
            }
            let payload = &bytes[i + 3..i + 89];
            if let Ok(fmt) = parse_fmt(payload) {
                formats.insert(fmt.message_type, fmt);
                i += 89;
                continue;
            }
            i += 1;
            continue;
        }

        let Some(fmt) = formats.get(&msg_type) else {
            i += 1;
            continue;
        };

        if fmt.message_len < 3 {
            i += 1;
            continue;
        }

        if i + fmt.message_len > bytes.len() {
            break;
        }

        let payload = &bytes[i + 3..i + fmt.message_len];
        if let Ok(record) = parse_record(fmt, payload) {
            collect_record(&mut parsed, fmt, &record);
        }

        i += fmt.message_len;
    }

    if parsed.imu_samples.is_empty() {
        return Err(anyhow!("dataflash parsing produced no IMU samples"));
    }

    Ok(parsed)
}

fn parse_fmt(payload: &[u8]) -> Result<FmtDefinition> {
    if payload.len() < 86 {
        return Err(anyhow!("FMT payload is too short"));
    }

    let message_type = payload[0];
    let message_len = payload[1] as usize;
    let name = parse_cstr(&payload[2..6]);
    let format = parse_cstr(&payload[6..22]);
    let columns_raw = parse_cstr(&payload[22..86]);
    let columns = columns_raw
        .split(',')
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect::<Vec<_>>();

    let parsers = format
        .chars()
        .map(char_to_parser)
        .collect::<Result<Vec<_>>>()?;

    Ok(FmtDefinition {
        message_type,
        message_len,
        name,
        columns,
        parsers,
    })
}

fn parse_record(fmt: &FmtDefinition, payload: &[u8]) -> Result<HashMap<String, FieldValue>> {
    let mut result = HashMap::new();
    let mut offset = 0usize;

    for (idx, parser) in fmt.parsers.iter().enumerate() {
        let column = fmt
            .columns
            .get(idx)
            .cloned()
            .unwrap_or_else(|| format!("f{}", idx));
        let (value, consumed) = parse_field(parser, &payload[offset..])?;
        offset += consumed;
        result.insert(column, value);
    }

    Ok(result)
}

fn parse_field(parser: &FieldParser, payload: &[u8]) -> Result<(FieldValue, usize)> {
    match parser {
        FieldParser::I8 => Ok((FieldValue::Number(read_i8(payload)? as f64), 1)),
        FieldParser::U8 => Ok((FieldValue::Number(read_u8(payload)? as f64), 1)),
        FieldParser::I16 => Ok((FieldValue::Number(read_i16(payload)? as f64), 2)),
        FieldParser::U16 => Ok((FieldValue::Number(read_u16(payload)? as f64), 2)),
        FieldParser::I32 => Ok((FieldValue::Number(read_i32(payload)? as f64), 4)),
        FieldParser::U32 => Ok((FieldValue::Number(read_u32(payload)? as f64), 4)),
        FieldParser::I64 => Ok((FieldValue::Number(read_i64(payload)? as f64), 8)),
        FieldParser::U64 => Ok((FieldValue::Number(read_u64(payload)? as f64), 8)),
        FieldParser::F32 => Ok((FieldValue::Number(read_f32(payload)? as f64), 4)),
        FieldParser::F64 => Ok((FieldValue::Number(read_f64(payload)?), 8)),
        FieldParser::Char(n) => {
            if payload.len() < *n {
                return Err(anyhow!("char field out of bounds"));
            }
            Ok((FieldValue::Text(parse_cstr(&payload[..*n])), *n))
        }
        FieldParser::ScaledI16(scale) => Ok((FieldValue::Number(read_i16(payload)? as f64 * *scale), 2)),
        FieldParser::ScaledU16(scale) => Ok((FieldValue::Number(read_u16(payload)? as f64 * *scale), 2)),
        FieldParser::ScaledI32(scale) => Ok((FieldValue::Number(read_i32(payload)? as f64 * *scale), 4)),
        FieldParser::ScaledU32(scale) => Ok((FieldValue::Number(read_u32(payload)? as f64 * *scale), 4)),
    }
}

fn collect_record(parsed: &mut ParsedLog, fmt: &FmtDefinition, record: &HashMap<String, FieldValue>) {
    let name = fmt.name.as_str();

    if name.starts_with("IMU") {
        let t = get_u64(record, &["TimeUS", "TimeMS", "TS"]);
        let Some(timestamp_us) = t else {
            return;
        };

        let accel_x = get_f64(record, &["AccX", "AX", "XAcc"]);
        let accel_y = get_f64(record, &["AccY", "AY", "YAcc"]);
        let accel_z = get_f64(record, &["AccZ", "AZ", "ZAcc"]);
        let gyro_x = get_f64(record, &["GyrX", "GyroX", "GX"]);
        let gyro_y = get_f64(record, &["GyrY", "GyroY", "GY"]);
        let gyro_z = get_f64(record, &["GyrZ", "GyroZ", "GZ"]);

        let (Some(ax), Some(ay), Some(az), Some(gx), Some(gy), Some(gz)) =
            (accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z)
        else {
            return;
        };

        let imu_id = get_f64(record, &["I", "IMU", "Instance"])
            .map(|v| v as u8)
            .or_else(|| infer_suffix_id(name))
            .unwrap_or(0);

        parsed.imu_samples.push(ImuSample {
            timestamp_us,
            imu_id,
            accel: [ax, ay, az],
            gyro: [gx, gy, gz],
        });
        return;
    }

    if name.starts_with("BARO") {
        let t = get_u64(record, &["TimeUS", "TimeMS", "TS"]);
        let p = get_f64(record, &["Press", "Pressure"]);
        let z = get_f64(record, &["Alt", "AltAMSL", "Altitude"]);
        let (Some(timestamp_us), Some(pressure_pa), Some(altitude_m)) = (t, p, z) else {
            return;
        };
        let baro_id = get_f64(record, &["I", "Instance"]).map(|v| v as u8).unwrap_or(0);
        parsed.baro_samples.push(BaroSample {
            timestamp_us,
            baro_id,
            pressure_pa,
            altitude_m,
        });
        return;
    }

    if name.starts_with("VIBE") {
        let t = get_u64(record, &["TimeUS", "TimeMS", "TS"]);
        let vx = get_f64(record, &["VibeX"]).unwrap_or(0.0);
        let vy = get_f64(record, &["VibeY"]).unwrap_or(0.0);
        let vz = get_f64(record, &["VibeZ"]).unwrap_or(0.0);
        let Some(timestamp_us) = t else {
            return;
        };
        parsed.vibe_samples.push(VibeSample {
            timestamp_us,
            vibration: (vx * vx + vy * vy + vz * vz).sqrt(),
        });
        return;
    }

    if name.starts_with("GPS") {
        let t = get_u64(record, &["TimeUS", "TimeMS", "TS"]);
        let lat = get_f64(record, &["Lat", "Latitude"]);
        let lng = get_f64(record, &["Lng", "Lon", "Longitude"]);
        let alt = get_f64(record, &["Alt", "Altitude", "AltAMSL"]);

        let (Some(timestamp_us), Some(lat_raw), Some(lng_raw), Some(alt_m)) = (t, lat, lng, alt)
        else {
            return;
        };

        parsed.gps_samples.push(GpsSample {
            timestamp_ms: timestamp_us / 1000,
            x: lat_raw,
            y: lng_raw,
            z: alt_m,
        });
    }
}

fn validate_sensor_coverage(parsed: &ParsedLog) -> Result<()> {
    let imu_ids = parsed
        .imu_samples
        .iter()
        .map(|s| s.imu_id)
        .collect::<HashSet<_>>();
    if imu_ids.len() < 3 {
        return Err(anyhow!(
            "imu count is insufficient: expected 3 unique IMUs, got {}",
            imu_ids.len()
        ));
    }

    let baro_ids = parsed
        .baro_samples
        .iter()
        .map(|s| s.baro_id)
        .collect::<HashSet<_>>();
    if baro_ids.len() < 2 {
        return Err(anyhow!(
            "baro count is insufficient: expected 2 unique barometers, got {}",
            baro_ids.len()
        ));
    }

    Ok(())
}

fn run_inertial_pipeline(parsed: &ParsedLog, file_size_bytes: usize, gt: Option<&[GpsSample]>) -> Result<MetricsPayload> {
    let mut imu_by_step: BTreeMap<u64, Vec<&ImuSample>> = BTreeMap::new();
    for s in &parsed.imu_samples {
        let ts_ms = s.timestamp_us / 1000;
        let step = (ts_ms / 20) * 20;
        imu_by_step.entry(step).or_default().push(s);
    }

    let mut baro_by_step: BTreeMap<u64, Vec<&BaroSample>> = BTreeMap::new();
    for s in &parsed.baro_samples {
        let ts_ms = s.timestamp_us / 1000;
        let step = (ts_ms / 20) * 20;
        baro_by_step.entry(step).or_default().push(s);
    }

    let mut vibe_by_step: BTreeMap<u64, f64> = BTreeMap::new();
    for s in &parsed.vibe_samples {
        let ts_ms = s.timestamp_us / 1000;
        let step = (ts_ms / 20) * 20;
        let prev = vibe_by_step.get(&step).copied().unwrap_or(0.0);
        vibe_by_step.insert(step, prev.max(s.vibration));
    }

    if imu_by_step.is_empty() {
        return Err(anyhow!("no IMU steps available after binning"));
    }

    let mut trajectory = Vec::new();
    let mut metrics = Vec::new();
    let mut roll = 0.0f64;
    let mut pitch = 0.0f64;
    let mut yaw = 0.0f64;
    let mut vel = [0.0f64; 3];
    let mut pos = [0.0f64; 3];
    let mut prev_step = None::<u64>;
    let mut peak_abs_accel = 0.0f64;
    let mut peak_abs_gyro = 0.0f64;
    let mut max_vibration = 0.0f64;
    let mut total_distance = 0.0f64;
    let mut baro_reference: Option<f64> = None;

    for (step_ms, samples) in imu_by_step {
        let dt = if let Some(prev) = prev_step {
            ((step_ms.saturating_sub(prev)) as f64 / 1000.0).clamp(0.005, 0.1)
        } else {
            0.02
        };
        prev_step = Some(step_ms);

        let fused_accel = robust_average_vec3(samples.iter().map(|s| s.accel).collect());
        let fused_gyro = robust_average_vec3(samples.iter().map(|s| s.gyro).collect());

        let accel = normalize_accel_units(fused_accel);
        let gyro = normalize_gyro_units(fused_gyro);

        let accel_norm = norm3(accel);
        let gyro_norm = norm3(gyro);
        peak_abs_accel = peak_abs_accel.max(accel.iter().map(|v| v.abs()).fold(0.0, f64::max));
        peak_abs_gyro = peak_abs_gyro.max(gyro.iter().map(|v| v.abs()).fold(0.0, f64::max));

        roll += gyro[0] * dt;
        pitch += gyro[1] * dt;
        yaw += gyro[2] * dt;

        if (6.0..13.5).contains(&accel_norm) {
            let roll_acc = accel[1].atan2(accel[2]);
            let pitch_acc = (-accel[0]).atan2((accel[1] * accel[1] + accel[2] * accel[2]).sqrt());
            let blend = 0.03;
            roll = roll * (1.0 - blend) + roll_acc * blend;
            pitch = pitch * (1.0 - blend) + pitch_acc * blend;
        }

        let rot = rotation_matrix_rpy(roll, pitch, yaw);
        let world_acc = mat3_mul_vec(rot, accel);
        let net_acc = [world_acc[0], world_acc[1], world_acc[2] - 9.80665];

        vel[0] += net_acc[0] * dt;
        vel[1] += net_acc[1] * dt;
        vel[2] += net_acc[2] * dt;

        if (accel_norm - 9.80665).abs() < 0.2 && gyro_norm < (3.0 * PI / 180.0) {
            vel[0] *= 0.97;
            vel[1] *= 0.97;
            vel[2] *= 0.93;
        }

        let prev = pos;
        pos[0] += vel[0] * dt;
        pos[1] += vel[1] * dt;
        pos[2] += vel[2] * dt;

        if let Some(baro_samples) = baro_by_step.get(&step_ms) {
            let altitude = robust_average(baro_samples.iter().map(|s| s.altitude_m).collect());
            let altitude = if let Some(reference) = baro_reference {
                altitude - reference
            } else {
                baro_reference = Some(altitude);
                0.0
            };
            pos[2] = pos[2] * 0.96 + altitude * 0.04;
            vel[2] *= 0.985;
        }

        total_distance += distance3(prev, pos);

        let vibration = vibe_by_step.get(&step_ms).copied().unwrap_or(0.0);
        max_vibration = max_vibration.max(vibration);

        trajectory.push(TrajectoryPoint {
            timestamp: step_ms,
            x: pos[0],
            y: pos[1],
            z: pos[2],
            speed: norm3(vel),
            roll: roll.to_degrees(),
            pitch: pitch.to_degrees(),
            yaw: yaw.to_degrees(),
            vibration,
        });

        let pressure = baro_by_step
            .get(&step_ms)
            .map(|items| robust_average(items.iter().map(|s| s.pressure_pa).collect()))
            .unwrap_or(0.0);

        metrics.push(MetricSnapshot {
            timestamp: step_ms,
            accel_x: accel[0],
            accel_y: accel[1],
            accel_z: accel[2],
            gyro_x: gyro[0].to_degrees(),
            gyro_y: gyro[1].to_degrees(),
            gyro_z: gyro[2].to_degrees(),
            pressure,
            altitude: pos[2],
        });
    }

    if trajectory.is_empty() {
        return Err(anyhow!("trajectory generation produced zero points"));
    }

    let (endpoint_error_m, rms_crosstrack_error_m, gt_used) = if let Some(gt_points) = gt {
        if gt_points.is_empty() {
            (0.0, 0.0, false)
        } else {
            (
                endpoint_error(&trajectory, gt_points),
                rms_crosstrack_error(&trajectory, gt_points),
                true,
            )
        }
    } else {
        (0.0, 0.0, false)
    };

    let used_imu_count = parsed
        .imu_samples
        .iter()
        .map(|s| s.imu_id)
        .collect::<HashSet<_>>()
        .len();
    let used_baro_count = parsed
        .baro_samples
        .iter()
        .map(|s| s.baro_id)
        .collect::<HashSet<_>>()
        .len();

    Ok(MetricsPayload {
        schema_version: "2.0.0".to_string(),
        file_size_bytes,
        point_count: trajectory.len(),
        endpoint_error_m,
        rms_crosstrack_error_m,
        total_distance_m: total_distance,
        max_vibration,
        peak_abs_accel,
        peak_abs_gyro,
        used_imu_count,
        used_baro_count,
        ground_truth_used: gt_used,
        trajectory,
        metrics,
    })
}

async fn load_ground_truth_csv(path: &Path) -> Result<Vec<GpsSample>> {
    let text = fs::read_to_string(path)
        .await
        .with_context(|| format!("failed to read ground truth csv {}", path.display()))?;

    let mut lines = text.lines();
    let Some(header_line) = lines.next() else {
        return Ok(Vec::new());
    };

    let headers = header_line
        .split(',')
        .map(|h| h.trim().to_ascii_lowercase())
        .collect::<Vec<_>>();

    let idx_timestamp = find_header(&headers, &["timestamp", "time", "timeus", "timems"]);
    let idx_x = find_header(&headers, &["x"]);
    let idx_y = find_header(&headers, &["y"]);
    let idx_z = find_header(&headers, &["z"]);
    let idx_lat = find_header(&headers, &["lat", "latitude"]);
    let idx_lng = find_header(&headers, &["lng", "lon", "longitude"]);
    let idx_alt = find_header(&headers, &["alt", "altitude", "altamsl"]);

    let has_xyz = idx_x.is_some() && idx_y.is_some() && idx_z.is_some();
    let has_geo = idx_lat.is_some() && idx_lng.is_some() && idx_alt.is_some();

    if !has_xyz && !has_geo {
        return Err(anyhow!("ground truth csv does not contain x/y/z or lat/lng/alt columns"));
    }

    let mut out = Vec::new();
    let mut geo_ref: Option<(f64, f64, f64)> = None;

    for (line_idx, line) in lines.enumerate() {
        let cols = line.split(',').map(|c| c.trim()).collect::<Vec<_>>();
        if cols.len() < headers.len() {
            continue;
        }

        let timestamp = idx_timestamp
            .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
            .map(normalize_timestamp_to_ms)
            .unwrap_or((line_idx as u64) * 20);

        let (x, y, z) = if has_xyz {
            let x = idx_x.and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok())).unwrap_or(0.0);
            let y = idx_y.and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok())).unwrap_or(0.0);
            let z = idx_z.and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok())).unwrap_or(0.0);
            (x, y, z)
        } else {
            let lat = idx_lat
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .ok_or_else(|| anyhow!("invalid lat in ground truth"))?;
            let lng = idx_lng
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .ok_or_else(|| anyhow!("invalid lng in ground truth"))?;
            let alt = idx_alt
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .ok_or_else(|| anyhow!("invalid alt in ground truth"))?;

            let reference = *geo_ref.get_or_insert((lat, lng, alt));
            geodetic_to_enu(lat, lng, alt, reference)
        };

        out.push(GpsSample {
            timestamp_ms: timestamp,
            x,
            y,
            z,
        });
    }

    Ok(out)
}

fn find_header(headers: &[String], aliases: &[&str]) -> Option<usize> {
    headers.iter().position(|h| aliases.iter().any(|a| h == a))
}

fn normalize_timestamp_to_ms(raw: f64) -> u64 {
    if raw > 1_000_000_000_000.0 {
        (raw / 1_000_000.0) as u64
    } else if raw > 1_000_000.0 {
        (raw / 1000.0) as u64
    } else {
        raw as u64
    }
}

fn geodetic_to_enu(lat: f64, lon: f64, alt: f64, reference: (f64, f64, f64)) -> (f64, f64, f64) {
    let (lat0, lon0, alt0) = reference;
    let r = 6_378_137.0;
    let d_lat = (lat - lat0).to_radians();
    let d_lon = (lon - lon0).to_radians();
    let east = d_lon * r * lat0.to_radians().cos();
    let north = d_lat * r;
    let up = alt - alt0;
    (east, north, up)
}

fn endpoint_error(estimated: &[TrajectoryPoint], gt: &[GpsSample]) -> f64 {
    let Some(last) = estimated.last() else {
        return 0.0;
    };
    let gt_last = gt
        .iter()
        .min_by_key(|g| g.timestamp_ms.abs_diff(last.timestamp))
        .unwrap_or(&gt[gt.len() - 1]);
    distance3([last.x, last.y, last.z], [gt_last.x, gt_last.y, gt_last.z])
}

fn rms_crosstrack_error(estimated: &[TrajectoryPoint], gt: &[GpsSample]) -> f64 {
    if estimated.is_empty() || gt.is_empty() {
        return 0.0;
    }

    let mut sum_sq = 0.0;
    for e in estimated {
        let d = gt
            .iter()
            .map(|g| distance3([e.x, e.y, e.z], [g.x, g.y, g.z]))
            .fold(f64::INFINITY, f64::min);
        sum_sq += d * d;
    }
    (sum_sq / (estimated.len() as f64)).sqrt()
}

fn robust_average_vec3(values: Vec<[f64; 3]>) -> [f64; 3] {
    if values.is_empty() {
        return [0.0, 0.0, 0.0];
    }
    let xs = values.iter().map(|v| v[0]).collect::<Vec<_>>();
    let ys = values.iter().map(|v| v[1]).collect::<Vec<_>>();
    let zs = values.iter().map(|v| v[2]).collect::<Vec<_>>();
    [robust_average(xs), robust_average(ys), robust_average(zs)]
}

fn robust_average(mut values: Vec<f64>) -> f64 {
    if values.is_empty() {
        return 0.0;
    }
    values.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let median = values[values.len() / 2];
    let mut deviations = values.iter().map(|v| (v - median).abs()).collect::<Vec<_>>();
    deviations.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let mad = deviations[deviations.len() / 2].max(1e-6);
    let filtered = values
        .into_iter()
        .filter(|v| (v - median).abs() <= 3.5 * mad)
        .collect::<Vec<_>>();
    if filtered.is_empty() {
        median
    } else {
        filtered.iter().sum::<f64>() / (filtered.len() as f64)
    }
}

fn normalize_accel_units(mut accel: [f64; 3]) -> [f64; 3] {
    let n = norm3(accel);
    if (0.5..2.0).contains(&n) {
        accel = [accel[0] * 9.80665, accel[1] * 9.80665, accel[2] * 9.80665];
    }
    accel
}

fn normalize_gyro_units(mut gyro: [f64; 3]) -> [f64; 3] {
    let max_abs = gyro.iter().map(|g| g.abs()).fold(0.0, f64::max);
    if max_abs > 20.0 {
        gyro = [gyro[0].to_radians(), gyro[1].to_radians(), gyro[2].to_radians()];
    }
    gyro
}

fn rotation_matrix_rpy(roll: f64, pitch: f64, yaw: f64) -> [[f64; 3]; 3] {
    let (sr, cr) = roll.sin_cos();
    let (sp, cp) = pitch.sin_cos();
    let (sy, cy) = yaw.sin_cos();

    [
        [cy * cp, cy * sp * sr - sy * cr, cy * sp * cr + sy * sr],
        [sy * cp, sy * sp * sr + cy * cr, sy * sp * cr - cy * sr],
        [-sp, cp * sr, cp * cr],
    ]
}

fn mat3_mul_vec(m: [[f64; 3]; 3], v: [f64; 3]) -> [f64; 3] {
    [
        m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
        m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
        m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
    ]
}

fn norm3(v: [f64; 3]) -> f64 {
    (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt()
}

fn distance3(a: [f64; 3], b: [f64; 3]) -> f64 {
    let dx = a[0] - b[0];
    let dy = a[1] - b[1];
    let dz = a[2] - b[2];
    (dx * dx + dy * dy + dz * dz).sqrt()
}

fn get_f64(record: &HashMap<String, FieldValue>, keys: &[&str]) -> Option<f64> {
    for key in keys {
        if let Some(FieldValue::Number(v)) = record.get(*key) {
            if v.is_finite() {
                return Some(*v);
            }
        }
    }
    None
}

fn get_u64(record: &HashMap<String, FieldValue>, keys: &[&str]) -> Option<u64> {
    get_f64(record, keys).map(|v| if v < 0.0 { 0 } else { v as u64 })
}

fn infer_suffix_id(name: &str) -> Option<u8> {
    let suffix = name.chars().skip_while(|c| !c.is_ascii_digit()).collect::<String>();
    if suffix.is_empty() {
        None
    } else {
        suffix.parse::<u8>().ok()
    }
}

fn parse_cstr(raw: &[u8]) -> String {
    let zero = raw.iter().position(|b| *b == 0).unwrap_or(raw.len());
    String::from_utf8_lossy(&raw[..zero]).trim().to_string()
}

fn char_to_parser(ch: char) -> Result<FieldParser> {
    match ch {
        'b' => Ok(FieldParser::I8),
        'B' | 'M' => Ok(FieldParser::U8),
        'h' => Ok(FieldParser::I16),
        'H' => Ok(FieldParser::U16),
        'i' => Ok(FieldParser::I32),
        'I' => Ok(FieldParser::U32),
        'q' => Ok(FieldParser::I64),
        'Q' => Ok(FieldParser::U64),
        'f' => Ok(FieldParser::F32),
        'd' => Ok(FieldParser::F64),
        'n' => Ok(FieldParser::Char(4)),
        'N' => Ok(FieldParser::Char(16)),
        'Z' => Ok(FieldParser::Char(64)),
        'c' => Ok(FieldParser::ScaledI16(0.01)),
        'C' => Ok(FieldParser::ScaledU16(0.01)),
        'e' => Ok(FieldParser::ScaledI32(0.01)),
        'E' => Ok(FieldParser::ScaledU32(0.01)),
        'L' => Ok(FieldParser::ScaledI32(1e-7)),
        _ => Err(anyhow!("unsupported FMT parser token: {}", ch)),
    }
}

fn read_u8(data: &[u8]) -> Result<u8> {
    data.first().copied().ok_or_else(|| anyhow!("u8 out of bounds"))
}

fn read_i8(data: &[u8]) -> Result<i8> {
    Ok(read_u8(data)? as i8)
}

fn read_u16(data: &[u8]) -> Result<u16> {
    if data.len() < 2 {
        return Err(anyhow!("u16 out of bounds"));
    }
    Ok(u16::from_le_bytes([data[0], data[1]]))
}

fn read_i16(data: &[u8]) -> Result<i16> {
    Ok(read_u16(data)? as i16)
}

fn read_u32(data: &[u8]) -> Result<u32> {
    if data.len() < 4 {
        return Err(anyhow!("u32 out of bounds"));
    }
    Ok(u32::from_le_bytes([data[0], data[1], data[2], data[3]]))
}

fn read_i32(data: &[u8]) -> Result<i32> {
    Ok(read_u32(data)? as i32)
}

fn read_u64(data: &[u8]) -> Result<u64> {
    if data.len() < 8 {
        return Err(anyhow!("u64 out of bounds"));
    }
    Ok(u64::from_le_bytes([
        data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7],
    ]))
}

fn read_i64(data: &[u8]) -> Result<i64> {
    Ok(read_u64(data)? as i64)
}

fn read_f32(data: &[u8]) -> Result<f32> {
    if data.len() < 4 {
        return Err(anyhow!("f32 out of bounds"));
    }
    Ok(f32::from_le_bytes([data[0], data[1], data[2], data[3]]))
}

fn read_f64(data: &[u8]) -> Result<f64> {
    if data.len() < 8 {
        return Err(anyhow!("f64 out of bounds"));
    }
    Ok(f64::from_le_bytes([
        data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7],
    ]))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn truncate_adds_ellipsis() {
        let data = "x".repeat(20);
        let out = truncate_for_event(&data, 10);
        assert_eq!(out.len(), 10);
        assert!(out.ends_with("..."));
    }

    #[test]
    fn classify_error_code_works() {
        assert_eq!(classify_error_code("input file is too large"), "INPUT_TOO_LARGE");
        assert_eq!(classify_error_code("input file does not exist"), "INPUT_NOT_FOUND");
        assert_eq!(
            classify_error_code("input file is not ready for processing"),
            "INPUT_NOT_READY"
        );
    }

    #[test]
    fn download_path_uses_gateway_file_route() {
        let file_id = Uuid::parse_str("11111111-1111-1111-1111-111111111111").unwrap();
        assert_eq!(
            download_path(file_id),
            "/api/files/11111111-1111-1111-1111-111111111111/download"
        );
    }
}