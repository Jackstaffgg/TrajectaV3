use anyhow::{anyhow, Context, Result};
use futures_util::StreamExt;
use lapin::{
    options::{
        BasicAckOptions, BasicConsumeOptions, BasicPublishOptions, BasicQosOptions,
        ExchangeDeclareOptions, QueueBindOptions, QueueDeclareOptions,
    },
    types::FieldTable,
    BasicProperties, Channel, Connection, ConnectionProperties, ExchangeKind,
};
use reqwest::{Client, StatusCode};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::f64::consts::PI;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::fs;
use tracing::{error, info, warn};

#[derive(Debug, Clone)]
struct WorkerConfig {
    rabbitmq_url: String,
    request_exchange: String,
    request_routing_key: String,
    request_queue: String,
    results_exchange: String,
    results_routing_key: String,
    results_queue: String,
    api_base_url: String,
    internal_raw_path_template: String,
    worker_token_header: String,
    internal_worker_token: String,
    http_timeout_seconds: u64,
    max_raw_download_bytes: u64,
    download_chunk_size_bytes: usize,
    timeline_dt_seconds: f64,
    ground_truth_path: Option<PathBuf>,
}

impl WorkerConfig {
    fn from_env() -> Result<Self> {
        let rabbitmq_url = env_or_default("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/%2F");
        let request_exchange = env_or_default("REQUEST_EXCHANGE", "telemetry.exchange");
        let request_routing_key = env_or_default("REQUEST_ROUTING_KEY", "telemetry.parse");
        let request_queue = env_or_default("REQUEST_QUEUE", "telemetry.parse");
        let results_exchange = env_or_default("RESULTS_EXCHANGE", "telemetry.exchange");
        let results_routing_key = env_or_default("RESULTS_ROUTING_KEY", "telemetry.results");
        let results_queue = env_or_default("RESULTS_QUEUE", "telemetry.results");
        let api_base_url = env_or_default("API_BASE_URL", "http://localhost:8080")
            .trim_end_matches('/')
            .to_string();
        let internal_raw_path_template =
            env_or_default("INTERNAL_RAW_PATH_TEMPLATE", "/api/internal/v1/tasks/{taskId}/raw");
        let worker_token_header = env_or_default("WORKER_TOKEN_HEADER", "X-Worker-Token");
        let internal_worker_token = required_env("INTERNAL_WORKER_TOKEN")?;
        let http_timeout_seconds = env_u64_or_default("HTTP_TIMEOUT_SECONDS", 30);
        let max_raw_download_bytes = env_u64_or_default("MAX_RAW_DOWNLOAD_BYTES", 200 * 1024 * 1024);
        let download_chunk_size_bytes = env_u64_or_default("DOWNLOAD_CHUNK_SIZE_BYTES", 1024 * 1024) as usize;
        let timeline_dt_seconds = env_f64_or_default("TIMELINE_DT", 0.1).max(0.02);
        let ground_truth_path = std::env::var("GROUND_TRUTH_PATH")
            .ok()
            .map(|v| v.trim().to_string())
            .filter(|v| !v.is_empty())
            .map(PathBuf::from);

        Ok(Self {
            rabbitmq_url,
            request_exchange,
            request_routing_key,
            request_queue,
            results_exchange,
            results_routing_key,
            results_queue,
            api_base_url,
            internal_raw_path_template,
            worker_token_header,
            internal_worker_token,
            http_timeout_seconds,
            max_raw_download_bytes,
            download_chunk_size_bytes,
            timeline_dt_seconds,
            ground_truth_path,
        })
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AnalysisRequest {
    task_id: i64,
    timeline_dt_seconds: Option<f64>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AnalysisResult {
    task_id: i64,
    status: String,
    trajectory_object_key: Option<String>,
    trajectory_json: Option<String>,
    metrics: Option<AnalysisMetrics>,
    error_message: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AnalysisMetrics {
    max_altitude: Option<f64>,
    max_speed: Option<f64>,
    flight_duration: Option<f64>,
    distance: Option<f64>,
    max_vertical_speed: Option<f64>,
    endpoint_error_m: Option<f64>,
    rms_crosstrack_error_m: Option<f64>,
    ate_m: Option<f64>,
    p95_crosstrack_error_m: Option<f64>,
    point_count: Option<i32>,
    ground_truth_used: Option<bool>,
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
struct FlightStageSample {
    timestamp_us: u64,
    stage: i32,
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
    baro_altitude: Option<f64>,
}

#[derive(Debug)]
struct InertialMetricsPayload {
    point_count: usize,
    endpoint_error_m: f64,
    rms_crosstrack_error_m: f64,
    ate_m: f64,
    p95_crosstrack_error_m: f64,
    horizontal_distance_m: f64,
    total_distance_3d_m: f64,
    max_vibration: f64,
    peak_abs_accel: f64,
    peak_abs_gyro: f64,
    used_imu_count: usize,
    used_baro_count: usize,
    ground_truth_used: bool,
    comparison_reference_track: Vec<GpsSample>,
    gps_reference_track: Vec<GpsSample>,
    trajectory: Vec<TrajectoryPoint>,
    metrics: Vec<MetricSnapshot>,
}

#[derive(Debug, Default)]
struct ParsedLog {
    imu_samples: Vec<ImuSample>,
    baro_samples: Vec<BaroSample>,
    vibe_samples: Vec<VibeSample>,
    gps_samples: Vec<GpsSample>,
    stage_samples: Vec<FlightStageSample>,
    params: BTreeMap<String, serde_json::Value>,
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

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerTrajectoryPayload {
    meta: TrajectoryMeta,
    frames: Vec<WorkerFrame>,
    events: Vec<serde_json::Value>,
    params: BTreeMap<String, serde_json::Value>,
    metrics: WorkerTrajectoryMetrics,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct TrajectoryMeta {
    start_time: f64,
    duration: f64,
    dt: f64,
    parsing: ParsingMeta,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ParsingMeta {
    messages: ParsingMessages,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ParsingMessages {
    imu: ParsingImu,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ParsingImu {
    sampling_hz: Option<f64>,
    sampling_period_ms: Option<f64>,
    resampled_step_ms: Option<f64>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerFrame {
    t: f64,
    pos: WorkerPosition,
    gps: Option<WorkerPosition>,
    position_error_m: Option<f64>,
    att: WorkerAtt,
    vel: f64,
    climb_rate: f64,
    battery: Option<f64>,
    vibration: Option<f64>,
    pressure: Option<f64>,
    imu: WorkerImu,
    mode: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerPosition {
    lat: f64,
    lon: f64,
    alt: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerAtt {
    roll: f64,
    pitch: f64,
    yaw: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerImu {
    accel: [f64; 3],
    gyro: [f64; 3],
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkerTrajectoryMetrics {
    max_altitude: Option<f64>,
    max_speed: Option<f64>,
    flight_duration: f64,
    distance: f64,
    horizontal_distance_m: f64,
    total_distance_3d_m: f64,
    max_horizontal_speed: Option<f64>,
    max_vertical_speed: Option<f64>,
    max_acceleration: Option<f64>,
    max_climb_rate: Option<f64>,
    total_flight_duration: f64,
    total_distance: f64,
    endpoint_error_m: f64,
    rms_crosstrack_error_m: f64,
    ate_m: f64,
    p95_crosstrack_error_m: f64,
    point_count: usize,
    used_imu_count: usize,
    used_baro_count: usize,
    max_vibration: f64,
    peak_abs_gyro: f64,
    ground_truth_used: bool,
    max_sensor_gps_error_m: Option<f64>,
    mean_sensor_gps_error_m: Option<f64>,
}

#[tokio::main]
async fn main() -> Result<()> {
    init_logging();

    let cfg = WorkerConfig::from_env()?;
    let http_client = Client::builder()
        .timeout(Duration::from_secs(cfg.http_timeout_seconds))
        .build()
        .context("failed to create HTTP client")?;

    let connection = Connection::connect(&cfg.rabbitmq_url, ConnectionProperties::default())
        .await
        .context("failed to connect to RabbitMQ")?;
    let channel = connection
        .create_channel()
        .await
        .context("failed to create RabbitMQ channel")?;

    declare_topology(&channel, &cfg).await?;

    channel
        .basic_qos(1, BasicQosOptions::default())
        .await
        .context("failed to set prefetch")?;

    let mut consumer = channel
        .basic_consume(
            &cfg.request_queue,
            "trajecta-rust-worker",
            BasicConsumeOptions::default(),
            FieldTable::default(),
        )
        .await
        .context("failed to start consumer")?;

    info!(
        "worker started queue={} exchange={} routing_key={}",
        cfg.request_queue, cfg.results_exchange, cfg.results_routing_key
    );

    while let Some(delivery_result) = consumer.next().await {
        match delivery_result {
            Ok(delivery) => {
                let result = handle_delivery(&cfg, &http_client, &delivery.data).await;
                if let Err(err) = publish_result(&channel, &cfg, &result).await {
                    error!("failed to publish result for task {}: {err:#}", result.task_id);
                }
                if let Err(err) = delivery.ack(BasicAckOptions::default()).await {
                    warn!("failed to ack delivery: {err}");
                }
            }
            Err(err) => error!("consumer error: {err}"),
        }
    }

    Ok(())
}

async fn declare_topology(channel: &Channel, cfg: &WorkerConfig) -> Result<()> {
    channel
        .exchange_declare(
            &cfg.request_exchange,
            ExchangeKind::Direct,
            ExchangeDeclareOptions {
                durable: true,
                ..Default::default()
            },
            FieldTable::default(),
        )
        .await
        .context("failed to declare request exchange")?;

    channel
        .exchange_declare(
            &cfg.results_exchange,
            ExchangeKind::Direct,
            ExchangeDeclareOptions {
                durable: true,
                ..Default::default()
            },
            FieldTable::default(),
        )
        .await
        .context("failed to declare results exchange")?;

    channel
        .queue_declare(
            &cfg.request_queue,
            QueueDeclareOptions {
                durable: true,
                ..Default::default()
            },
            FieldTable::default(),
        )
        .await
        .context("failed to declare request queue")?;

    channel
        .queue_declare(
            &cfg.results_queue,
            QueueDeclareOptions {
                durable: true,
                ..Default::default()
            },
            FieldTable::default(),
        )
        .await
        .context("failed to declare results queue")?;

    channel
        .queue_bind(
            &cfg.request_queue,
            &cfg.request_exchange,
            &cfg.request_routing_key,
            QueueBindOptions::default(),
            FieldTable::default(),
        )
        .await
        .context("failed to bind request queue")?;

    channel
        .queue_bind(
            &cfg.results_queue,
            &cfg.results_exchange,
            &cfg.results_routing_key,
            QueueBindOptions::default(),
            FieldTable::default(),
        )
        .await
        .context("failed to bind results queue")?;

    Ok(())
}

async fn publish_result(channel: &Channel, cfg: &WorkerConfig, result: &AnalysisResult) -> Result<()> {
    let payload = serde_json::to_vec(result).context("failed to serialize result payload")?;
    channel
        .basic_publish(
            &cfg.results_exchange,
            &cfg.results_routing_key,
            BasicPublishOptions::default(),
            &payload,
            BasicProperties::default()
                .with_content_type("application/json".into())
                .with_delivery_mode(2),
        )
        .await
        .context("failed to publish result")?
        .await
        .context("result publish not confirmed")?;

    Ok(())
}

async fn handle_delivery(cfg: &WorkerConfig, http_client: &Client, payload: &[u8]) -> AnalysisResult {
    let parsed: Result<AnalysisRequest> = serde_json::from_slice(payload).context("invalid request payload");

    let request = match parsed {
        Ok(req) => req,
        Err(err) => {
            return AnalysisResult {
                task_id: -1,
                status: "FAILED".to_string(),
                trajectory_object_key: None,
                trajectory_json: None,
                metrics: None,
                error_message: Some(truncate_for_event(&format!("{err:#}"), 1000)),
            }
        }
    };

    let request_dt = request.timeline_dt_seconds.filter(|v| v.is_finite() && *v > 0.0);
    match process_task(cfg, http_client, request.task_id, request_dt).await {
        Ok(ok) => ok,
        Err(err) => AnalysisResult {
            task_id: request.task_id,
            status: "FAILED".to_string(),
            trajectory_object_key: None,
            trajectory_json: None,
            metrics: None,
            error_message: Some(truncate_for_event(&format!("{err:#}"), 1800)),
        },
    }
}

async fn process_task(
    cfg: &WorkerConfig,
    http_client: &Client,
    task_id: i64,
    request_timeline_dt_seconds: Option<f64>,
) -> Result<AnalysisResult> {
    let bytes = download_raw_for_task(cfg, http_client, task_id).await?;
    if bytes.is_empty() {
        return Err(anyhow!("input file is empty"));
    }

    let mut parsed = parse_dataflash_log(&bytes)?;
    sanitize_parsed_log(&mut parsed);
    validate_sensor_coverage(&parsed)?;

    let ground_truth = match &cfg.ground_truth_path {
        Some(path) if path.exists() => Some(load_ground_truth_csv(path).await?),
        _ => None,
    };

    // Keep units consistent when external GT is absent: convert onboard GPS to local ENU meters.
    let gps_fallback_track = if ground_truth.is_none() {
        gps_samples_to_enu_track(&parsed.gps_samples)
    } else {
        None
    };
    let comparison_points = ground_truth
        .as_deref()
        .or_else(|| gps_fallback_track.as_deref());
    let comparison_is_ground_truth = ground_truth.is_some();

    let inertial = run_inertial_pipeline(&parsed, comparison_points, comparison_is_ground_truth)?;
    let effective_dt = request_timeline_dt_seconds.unwrap_or(cfg.timeline_dt_seconds).max(0.02);
    let trajectory_payload = build_worker_trajectory_payload(&parsed, &inertial, effective_dt)?;
    let trajectory_json = serde_json::to_string(&trajectory_payload)
        .context("failed to serialize worker trajectory payload")?;

    Ok(AnalysisResult {
        task_id,
        status: "COMPLETED".to_string(),
        trajectory_object_key: None,
        trajectory_json: Some(trajectory_json),
        metrics: Some(AnalysisMetrics {
            max_altitude: trajectory_payload.metrics.max_altitude,
            max_speed: trajectory_payload.metrics.max_speed,
            flight_duration: Some(trajectory_payload.metrics.flight_duration),
            distance: Some(trajectory_payload.metrics.distance),
            max_vertical_speed: trajectory_payload.metrics.max_vertical_speed,
            endpoint_error_m: Some(trajectory_payload.metrics.endpoint_error_m),
            rms_crosstrack_error_m: Some(trajectory_payload.metrics.rms_crosstrack_error_m),
            ate_m: Some(trajectory_payload.metrics.ate_m),
            p95_crosstrack_error_m: Some(trajectory_payload.metrics.p95_crosstrack_error_m),
            point_count: Some(trajectory_payload.metrics.point_count as i32),
            ground_truth_used: Some(trajectory_payload.metrics.ground_truth_used),
        }),
        error_message: None,
    })
}

async fn download_raw_for_task(cfg: &WorkerConfig, http_client: &Client, task_id: i64) -> Result<Vec<u8>> {
    let path = cfg
        .internal_raw_path_template
        .replace("{taskId}", &task_id.to_string());
    let url = format!("{}{}", cfg.api_base_url, path);

    let response = http_client
        .get(&url)
        .header(&cfg.worker_token_header, &cfg.internal_worker_token)
        .send()
        .await
        .with_context(|| format!("failed to download raw file for task {task_id}"))?;

    match response.status() {
        StatusCode::OK => {
            if let Some(content_length) = response.content_length() {
                if content_length == 0 {
                    return Err(anyhow!("raw file is empty"));
                }
                if content_length > cfg.max_raw_download_bytes {
                    return Err(anyhow!(
                        "raw file is too large: {} bytes (max {})",
                        content_length,
                        cfg.max_raw_download_bytes
                    ));
                }
            }

            let bytes = response.bytes().await.context("failed to read raw bytes")?.to_vec();
            if bytes.is_empty() {
                return Err(anyhow!("raw file is empty"));
            }
            if (bytes.len() as u64) > cfg.max_raw_download_bytes {
                return Err(anyhow!(
                    "raw file is too large: {} bytes (max {})",
                    bytes.len(),
                    cfg.max_raw_download_bytes
                ));
            }

            // Keep old worker behavior: cap memory exposure by validating chunk-size config at runtime.
            if cfg.download_chunk_size_bytes == 0 {
                return Err(anyhow!("DOWNLOAD_CHUNK_SIZE_BYTES must be greater than 0"));
            }

            Ok(bytes)
        }
        StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN => {
            Err(anyhow!("internal worker token is invalid or missing"))
        }
        StatusCode::NOT_FOUND => Err(anyhow!("task raw file not found")),
        status => {
            let body = response.text().await.unwrap_or_default();
            Err(anyhow!(
                "failed to download task raw file: status={} body={}",
                status,
                truncate_for_event(&body, 400)
            ))
        }
    }
}

fn build_worker_trajectory_payload(
    parsed: &ParsedLog,
    inertial: &InertialMetricsPayload,
    dt_seconds: f64,
) -> Result<WorkerTrajectoryPayload> {
    if inertial.trajectory.is_empty() {
        return Err(anyhow!("inertial trajectory is empty"));
    }

    let imu_rate_hz = estimate_sampling_hz(parsed.imu_samples.iter().map(|s| s.timestamp_us as f64 / 1e6));
    let imu_sampling_period_ms = imu_rate_hz
        .filter(|hz| *hz > 1e-6)
        .map(|hz| 1000.0 / hz);

    let reference_geo = parsed.gps_samples.iter().find_map(|g| {
        let lat = normalize_coordinate(g.x, 90.0)?;
        let lon = normalize_coordinate(g.y, 180.0)?;
        let alt = g.z;
        Some((lat, lon, alt))
    });

    let t0_ms = inertial.trajectory.first().map(|p| p.timestamp).unwrap_or(0);

    let metrics_by_ts = inertial
        .metrics
        .iter()
        .map(|m| (m.timestamp, m))
        .collect::<HashMap<_, _>>();

    let mut frames = Vec::with_capacity(inertial.trajectory.len());
    let mut prev_alt = None::<f64>;
    let mut prev_t = None::<f64>;
    let mut max_altitude = f64::NEG_INFINITY;
    let mut max_speed = 0.0f64;
    let mut max_vertical_speed = 0.0f64;
    let mut max_sensor_gps_error_m = 0.0f64;
    let mut sum_sensor_gps_error_m = 0.0f64;
    let mut sensor_gps_error_count = 0usize;

    for point in &inertial.trajectory {
        let t = (point.timestamp.saturating_sub(t0_ms) as f64) / 1000.0;
        let effective_up = metrics_by_ts
            .get(&point.timestamp)
            .and_then(|m| m.baro_altitude)
            .unwrap_or(point.z);

        let (lat, lon, alt) = if let Some(reference) = reference_geo {
            enu_to_geodetic(point.x, point.y, effective_up, reference)
        } else {
            // Fallback keeps payload valid for frontend consumers even when GPS is absent.
            enu_to_geodetic(point.x, point.y, effective_up, (0.0, 0.0, 0.0))
        };

        let climb_rate = match (prev_t, prev_alt) {
            (Some(prev_t), Some(prev_alt)) => {
                let dt = (t - prev_t).max(1e-3);
                (alt - prev_alt) / dt
            }
            _ => 0.0,
        };

        prev_t = Some(t);
        prev_alt = Some(alt);

        max_altitude = max_altitude.max(alt);
        max_speed = max_speed.max(point.speed.abs());
        max_vertical_speed = max_vertical_speed.max(climb_rate.abs());

        let reference_match_enu = sample_track_point(&inertial.comparison_reference_track, point.timestamp);
        let display_match_enu = sample_track_point(&inertial.gps_reference_track, point.timestamp)
            .or(reference_match_enu);
        let error_reference_enu = reference_match_enu.or(display_match_enu);
        let gps_match = display_match_enu.and_then(|gps_xyz| {
            reference_geo.map(|reference| {
                let (gps_lat, gps_lon, gps_alt) = enu_to_geodetic(gps_xyz[0], gps_xyz[1], gps_xyz[2], reference);
                (gps_lat, gps_lon, gps_alt)
            })
        });
        let position_error_m = error_reference_enu
            .map(|reference_xyz| distance3([point.x, point.y, effective_up], reference_xyz));

        if let Some(error) = position_error_m {
            max_sensor_gps_error_m = max_sensor_gps_error_m.max(error);
            sum_sensor_gps_error_m += error;
            sensor_gps_error_count += 1;
        }

        let imu = metrics_by_ts
            .get(&point.timestamp)
            .map(|m| WorkerImu {
                accel: [m.accel_x, m.accel_y, m.accel_z],
                gyro: [m.gyro_x, m.gyro_y, m.gyro_z],
            })
            .unwrap_or(WorkerImu {
                accel: [0.0, 0.0, 0.0],
                gyro: [0.0, 0.0, 0.0],
            });

        frames.push(WorkerFrame {
            t,
            pos: WorkerPosition { lat, lon, alt },
            gps: gps_match.map(|(gps_lat, gps_lon, gps_alt)| WorkerPosition {
                lat: gps_lat,
                lon: gps_lon,
                alt: gps_alt,
            }),
            position_error_m,
            att: WorkerAtt {
                roll: point.roll,
                pitch: point.pitch,
                yaw: point.yaw,
            },
            vel: point.speed,
            climb_rate,
            battery: None,
            vibration: Some(point.vibration),
            pressure: metrics_by_ts.get(&point.timestamp).map(|m| m.pressure),
            imu,
            mode: "INS".to_string(),
        });
    }

    let start_time = frames.first().map(|f| f.t).unwrap_or(0.0);
    let end_time = frames.last().map(|f| f.t).unwrap_or(start_time);
    let duration = (end_time - start_time).max(0.0);

    let metrics = WorkerTrajectoryMetrics {
        max_altitude: if max_altitude.is_finite() { Some(max_altitude) } else { None },
        max_speed: Some(max_speed),
        flight_duration: duration,
        distance: inertial.horizontal_distance_m,
        horizontal_distance_m: inertial.horizontal_distance_m,
        total_distance_3d_m: inertial.total_distance_3d_m,
        max_horizontal_speed: Some(max_speed),
        max_vertical_speed: Some(max_vertical_speed),
        max_acceleration: Some(inertial.peak_abs_accel),
        max_climb_rate: Some(max_vertical_speed),
        total_flight_duration: duration,
        total_distance: inertial.total_distance_3d_m,
        endpoint_error_m: inertial.endpoint_error_m,
        rms_crosstrack_error_m: inertial.rms_crosstrack_error_m,
        ate_m: inertial.ate_m,
        p95_crosstrack_error_m: inertial.p95_crosstrack_error_m,
        point_count: inertial.point_count,
        used_imu_count: inertial.used_imu_count,
        used_baro_count: inertial.used_baro_count,
        max_vibration: inertial.max_vibration,
        peak_abs_gyro: inertial.peak_abs_gyro,
        ground_truth_used: inertial.ground_truth_used,
        max_sensor_gps_error_m: if sensor_gps_error_count > 0 {
            Some(max_sensor_gps_error_m)
        } else {
            None
        },
        mean_sensor_gps_error_m: if sensor_gps_error_count > 0 {
            Some(sum_sensor_gps_error_m / (sensor_gps_error_count as f64))
        } else {
            None
        },
    };

    Ok(WorkerTrajectoryPayload {
        meta: TrajectoryMeta {
            start_time,
            duration,
            dt: dt_seconds,
            parsing: ParsingMeta {
                messages: ParsingMessages {
                    imu: ParsingImu {
                        sampling_hz: imu_rate_hz,
                        sampling_period_ms: imu_sampling_period_ms,
                        resampled_step_ms: Some(20.0),
                    },
                },
            },
        },
        frames,
        events: Vec::new(),
        params: parsed.params.clone(),
        metrics,
    })
}

fn normalize_gps_sample(sample: &GpsSample) -> Option<(f64, f64, f64)> {
    let lat = normalize_coordinate(sample.x, 90.0)?;
    let lon = normalize_coordinate(sample.y, 180.0)?;
    let alt = sample.z;
    if !lat.is_finite() || !lon.is_finite() || !alt.is_finite() {
        return None;
    }
    Some((lat, lon, alt))
}

fn gps_samples_to_enu_track(samples: &[GpsSample]) -> Option<Vec<GpsSample>> {
    let mut reference_geo: Option<(f64, f64, f64)> = None;
    let mut out = Vec::with_capacity(samples.len());

    for sample in samples {
        let Some((lat, lon, alt)) = normalize_gps_sample(sample) else {
            continue;
        };

        let reference = *reference_geo.get_or_insert((lat, lon, alt));
        let (x, y, z) = geodetic_to_enu(lat, lon, alt, reference);

        out.push(GpsSample {
            timestamp_ms: sample.timestamp_ms,
            x,
            y,
            z,
        });
    }

    if out.is_empty() {
        None
    } else {
        Some(out)
    }
}

fn estimate_sampling_hz<I>(timestamps_sec: I) -> Option<f64>
where
    I: Iterator<Item = f64>,
{
    let mut values = timestamps_sec.collect::<Vec<_>>();
    if values.len() < 2 {
        return None;
    }
    values.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let mut diffs = Vec::with_capacity(values.len().saturating_sub(1));
    for pair in values.windows(2) {
        let dt = pair[1] - pair[0];
        if dt > 0.0 {
            diffs.push(dt);
        }
    }
    if diffs.is_empty() {
        return None;
    }
    diffs.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let median_dt = diffs[diffs.len() / 2];
    if median_dt <= 0.0 {
        return None;
    }
    Some(1.0 / median_dt)
}

fn normalize_coordinate(raw: f64, max_abs: f64) -> Option<f64> {
    let normalized = if raw.abs() > max_abs { raw / 1e7 } else { raw };
    if normalized.abs() <= max_abs {
        Some(normalized)
    } else {
        None
    }
}

fn normalize_altitude_meters(raw: f64) -> Option<f64> {
    if !raw.is_finite() {
        return None;
    }

    let mut alt = raw;
    if alt.abs() > 200_000.0 {
        alt /= 1000.0;
    } else if alt.abs() > 10_000.0 {
        alt /= 100.0;
    }

    if alt.abs() <= 20_000.0 {
        Some(alt)
    } else {
        None
    }
}

fn trajectory_distances(trajectory: &[TrajectoryPoint]) -> (f64, f64) {
    if trajectory.len() < 2 {
        return (0.0, 0.0);
    }

    let mut horizontal_total = 0.0;
    let mut distance3d_total = 0.0;

    for pair in trajectory.windows(2) {
        let a = &pair[0];
        let b = &pair[1];
        let dt = (b.timestamp.saturating_sub(a.timestamp) as f64 / 1000.0).max(1e-3);

        let dx = b.x - a.x;
        let dy = b.y - a.y;
        let dz = b.z - a.z;

        let horizontal = (dx * dx + dy * dy).sqrt();
        let distance3d = (horizontal * horizontal + dz * dz).sqrt();

        let horizontal_speed = horizontal / dt;
        let vertical_rate = dz.abs() / dt;
        let speed3d = distance3d / dt;

        // Reject physically implausible jumps to avoid distance inflation from spikes.
        if horizontal_speed > 140.0 || vertical_rate > 70.0 || speed3d > 170.0 {
            continue;
        }

        horizontal_total += horizontal;
        distance3d_total += distance3d;
    }

    (horizontal_total, distance3d_total)
}

fn sanitize_trajectory(mut trajectory: Vec<TrajectoryPoint>) -> Vec<TrajectoryPoint> {
    trajectory.retain(|p| {
        p.timestamp > 0
            && p.x.is_finite()
            && p.y.is_finite()
            && p.z.is_finite()
            && p.roll.is_finite()
            && p.pitch.is_finite()
            && p.yaw.is_finite()
    });
    trajectory.sort_by_key(|p| p.timestamp);
    trajectory.dedup_by_key(|p| p.timestamp);

    if trajectory.len() < 2 {
        return trajectory;
    }

    let mut filtered = Vec::with_capacity(trajectory.len());
    filtered.push(trajectory[0].clone());

    for point in trajectory.into_iter().skip(1) {
        let prev = filtered.last().expect("filtered always has at least one point");
        let dt = (point.timestamp.saturating_sub(prev.timestamp) as f64 / 1000.0).max(1e-3);
        let dx = point.x - prev.x;
        let dy = point.y - prev.y;
        let dz = point.z - prev.z;
        let horizontal = (dx * dx + dy * dy).sqrt();
        let vertical = dz.abs();
        let speed3d = (horizontal * horizontal + vertical * vertical).sqrt() / dt;
        let vertical_rate = vertical / dt;

        if speed3d > 180.0 || vertical_rate > 80.0 {
            continue;
        }

        // Ignore near-duplicate jitter points that do not add physical movement.
        if horizontal < 1e-3 && vertical < 1e-3 {
            continue;
        }

        filtered.push(point);
    }

    filtered
}

fn align_trajectory_to_reference(estimated: &[TrajectoryPoint], reference: &[GpsSample]) -> Vec<TrajectoryPoint> {
    if estimated.len() < 3 || reference.len() < 3 {
        return estimated.to_vec();
    }

    let step = (estimated.len() / 400).max(1);
    let mut src = Vec::<[f64; 3]>::new();
    let mut dst = Vec::<[f64; 3]>::new();

    for point in estimated.iter().step_by(step) {
        if let Some(ref_xyz) = sample_track_point(reference, point.timestamp) {
            src.push([point.x, point.y, point.z]);
            dst.push(ref_xyz);
        }
    }

    if src.len() < 3 {
        return estimated.to_vec();
    }

    let n = src.len() as f64;
    let src_cx = src.iter().map(|p| p[0]).sum::<f64>() / n;
    let src_cy = src.iter().map(|p| p[1]).sum::<f64>() / n;
    let src_cz = src.iter().map(|p| p[2]).sum::<f64>() / n;
    let dst_cx = dst.iter().map(|p| p[0]).sum::<f64>() / n;
    let dst_cy = dst.iter().map(|p| p[1]).sum::<f64>() / n;
    let dst_cz = dst.iter().map(|p| p[2]).sum::<f64>() / n;

    let mut num = 0.0;
    let mut den = 0.0;
    for (s, d) in src.iter().zip(dst.iter()) {
        let sx = s[0] - src_cx;
        let sy = s[1] - src_cy;
        let dx = d[0] - dst_cx;
        let dy = d[1] - dst_cy;
        num += sx * dy - sy * dx;
        den += sx * dx + sy * dy;
    }

    let yaw = num.atan2(den);
    let c = yaw.cos();
    let s = yaw.sin();
    let tx = dst_cx - (c * src_cx - s * src_cy);
    let ty = dst_cy - (s * src_cx + c * src_cy);
    let tz = dst_cz - src_cz;

    let mut out = Vec::with_capacity(estimated.len());
    for point in estimated {
        let x = c * point.x - s * point.y + tx;
        let y = s * point.x + c * point.y + ty;
        let z = point.z + tz;
        out.push(TrajectoryPoint {
            timestamp: point.timestamp,
            x,
            y,
            z,
            speed: point.speed,
            roll: point.roll,
            pitch: point.pitch,
            yaw: wrap_angle_deg(point.yaw + yaw.to_degrees()),
            vibration: point.vibration,
        });
    }

    for i in 1..out.len() {
        let dt = ((out[i].timestamp.saturating_sub(out[i - 1].timestamp)) as f64 / 1000.0).max(1e-3);
        out[i].speed = distance3([out[i - 1].x, out[i - 1].y, out[i - 1].z], [out[i].x, out[i].y, out[i].z]) / dt;
    }
    if out.len() > 1 {
        out[0].speed = out[1].speed;
    }

    out
}

fn wrap_angle_deg(angle: f64) -> f64 {
    let mut a = angle;
    while a > 180.0 {
        a -= 360.0;
    }
    while a < -180.0 {
        a += 360.0;
    }
    a
}

fn enu_to_geodetic(east: f64, north: f64, up: f64, reference: (f64, f64, f64)) -> (f64, f64, f64) {
    let (lat0, lon0, alt0) = reference;
    let r = 6_378_137.0;
    let lat = lat0 + (north / r).to_degrees();
    let lon = lon0 + (east / (r * lat0.to_radians().cos().max(1e-6))).to_degrees();
    let alt = alt0 + up;
    (lat, lon, alt)
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

    let parsers = format.chars().map(char_to_parser).collect::<Result<Vec<_>>>()?;

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

    if name.starts_with("PARM") {

        let Some(param_name) = get_text(record, &["Name", "Param", "Parm", "Key"]) else {
            return;
        };
        let Some(param_value) = get_f64(record, &["Value", "Val", "Current"]) else {
            return;
        };
        if let Some(json_value) = json_value_from_f64(param_value) {
            parsed.params.insert(param_name.to_string(), json_value);
        }
        return;
    }

    if name.starts_with("IMU") {
        let t = get_timestamp_us(record);
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
        let t = get_timestamp_us(record);
        let p = get_f64(record, &["Press", "Pressure"]);
        let z = get_f64(record, &["Alt", "AltAMSL", "Altitude"]).and_then(normalize_altitude_meters);
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
        let t = get_timestamp_us(record);
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
        let t = get_timestamp_us(record);
        let lat = get_f64(record, &["Lat", "Latitude"]);
        let lng = get_f64(record, &["Lng", "Lon", "Longitude"]);
        let alt = get_f64(record, &["Alt", "Altitude", "AltAMSL"]).and_then(normalize_altitude_meters);

        // Mirror task challenge plotting defaults: GPS instance 0 with at least basic valid status.
        if let Some(instance) = get_f64(record, &["I", "Instance"]) {
            if (instance as i32) != 0 {
                return;
            }
        }
        if let Some(status) = get_f64(record, &["Status", "FixType"]) {
            if status < 1.0 {
                return;
            }
        }

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
        return;
    }

    if name.starts_with("FSTG") {
        let Some(timestamp_us) = get_timestamp_us(record) else {
            return;
        };
        let Some(stage) = get_f64(record, &["Stg", "Stage", "S"]) else {
            return;
        };
        parsed.stage_samples.push(FlightStageSample {
            timestamp_us,
            stage: stage as i32,
        });
    }
}

const STAGE_PAD_IDLE: i32 = 1;
const STAGE_DESCENT: i32 = 5;
const STAGE_LANDED: i32 = 6;

fn detect_stage_window(stage_samples: &[FlightStageSample]) -> Option<(u64, u64)> {
    if stage_samples.is_empty() {
        return None;
    }

    let mut start_time = None;
    for sample in stage_samples {
        if sample.stage == STAGE_PAD_IDLE {
            start_time = Some(sample.timestamp_us);
            break;
        }
    }
    let start_time = start_time?;

    let end_landed = stage_samples
        .iter()
        .find(|s| s.timestamp_us >= start_time && s.stage == STAGE_LANDED)
        .map(|s| s.timestamp_us);
    let end_descent = stage_samples
        .iter()
        .find(|s| s.timestamp_us >= start_time && s.stage == STAGE_DESCENT)
        .map(|s| s.timestamp_us);

    let end_time = end_landed.or(end_descent)?;
    if end_time <= start_time {
        return None;
    }
    Some((start_time, end_time))
}

fn validate_sensor_coverage(parsed: &ParsedLog) -> Result<()> {
    let imu_ids = parsed
        .imu_samples
        .iter()
        .map(|s| s.imu_id)
        .collect::<HashSet<_>>();
    if imu_ids.is_empty() {
        return Err(anyhow!("imu data is missing after sanitization"));
    }
    if imu_ids.len() < 3 {
        warn!(
            "reduced IMU coverage: expected 3 unique IMUs, got {}",
            imu_ids.len()
        );
    }

    let baro_ids = parsed
        .baro_samples
        .iter()
        .map(|s| s.baro_id)
        .collect::<HashSet<_>>();
    if baro_ids.is_empty() {
        warn!("BARO data is missing after sanitization; vertical channel will degrade");
    } else if baro_ids.len() < 2 {
        warn!(
            "reduced BARO coverage: expected 2 unique barometers, got {}",
            baro_ids.len()
        );
    }

    Ok(())
}

fn sanitize_parsed_log(parsed: &mut ParsedLog) {
    parsed.imu_samples.retain(|s| {
        s.accel.iter().all(|v| v.is_finite())
            && s.gyro.iter().all(|v| v.is_finite())
            && s.accel.iter().all(|v| v.abs() < 2_000.0)
            && s.gyro.iter().all(|v| v.abs() < 10_000.0)
    });
    parsed.imu_samples.sort_by_key(|s| s.timestamp_us);

    parsed.baro_samples.retain(|s| {
        s.pressure_pa.is_finite()
            && s.altitude_m.is_finite()
            && (500.0..=200_000.0).contains(&s.pressure_pa)
            && s.altitude_m.abs() <= 20_000.0
    });
    parsed.baro_samples.sort_by_key(|s| s.timestamp_us);

    parsed.vibe_samples.retain(|s| s.vibration.is_finite() && (0.0..=1_000.0).contains(&s.vibration));
    parsed.vibe_samples.sort_by_key(|s| s.timestamp_us);

    parsed.gps_samples.retain(|s| {
        normalize_coordinate(s.x, 90.0).is_some()
            && normalize_coordinate(s.y, 180.0).is_some()
            && s.z.is_finite()
            && s.z.abs() <= 20_000.0
    });
    parsed.gps_samples.sort_by_key(|s| s.timestamp_ms);
    parsed.gps_samples.dedup_by_key(|s| s.timestamp_ms);

    parsed.stage_samples.sort_by_key(|s| s.timestamp_us);
    parsed.stage_samples.dedup_by_key(|s| s.timestamp_us);

    if let Some((start_us, end_us)) = detect_stage_window(&parsed.stage_samples) {
        parsed
            .imu_samples
            .retain(|s| s.timestamp_us >= start_us && s.timestamp_us <= end_us);
        parsed
            .baro_samples
            .retain(|s| s.timestamp_us >= start_us && s.timestamp_us <= end_us);
        parsed
            .vibe_samples
            .retain(|s| s.timestamp_us >= start_us && s.timestamp_us <= end_us);
        parsed.gps_samples.retain(|s| {
            let ts_us = s.timestamp_ms.saturating_mul(1000);
            ts_us >= start_us && ts_us <= end_us
        });
    }
}

#[derive(Debug, Clone)]
struct AxisKalman {
    x: [f64; 3],
    p: [[f64; 3]; 3],
}

impl AxisKalman {
    fn new(position_var: f64, velocity_var: f64, bias_var: f64) -> Self {
        Self {
            x: [0.0, 0.0, 0.0],
            p: [
                [position_var.max(1e-6), 0.0, 0.0],
                [0.0, velocity_var.max(1e-6), 0.0],
                [0.0, 0.0, bias_var.max(1e-9)],
            ],
        }
    }

    fn predict(&mut self, dt: f64, measured_accel: f64, accel_noise_sigma: f64, bias_rw_sigma: f64) {
        let dt = dt.max(1e-4);
        let dt2 = dt * dt;
        let accel = measured_accel - self.x[2];

        self.x[0] += self.x[1] * dt + 0.5 * accel * dt2;
        self.x[1] += accel * dt;

        let f = [[1.0, dt, -0.5 * dt2], [0.0, 1.0, -dt], [0.0, 0.0, 1.0]];
        let fp = mat3_mul(f, self.p);
        let fpf_t = mat3_mul(fp, mat3_transpose(f));

        let q_acc = accel_noise_sigma.max(1e-4).powi(2);
        let q_bias = bias_rw_sigma.max(1e-6).powi(2) * dt;
        let q = [
            [0.25 * dt2 * dt2 * q_acc, 0.5 * dt2 * dt * q_acc, 0.0],
            [0.5 * dt2 * dt * q_acc, dt2 * q_acc, 0.0],
            [0.0, 0.0, q_bias],
        ];

        self.p = mat3_add(fpf_t, q);
        self.force_covariance_floor();
    }

    fn update_position(&mut self, z_pos: f64, r_var: f64) {
        self.update_scalar([1.0, 0.0, 0.0], z_pos, r_var);
    }

    fn update_velocity(&mut self, z_vel: f64, r_var: f64) {
        self.update_scalar([0.0, 1.0, 0.0], z_vel, r_var);
    }

    fn update_scalar(&mut self, h: [f64; 3], z: f64, r_var: f64) {
        let r = r_var.max(1e-6);
        let hx = self.x[0] * h[0] + self.x[1] * h[1] + self.x[2] * h[2];
        let innovation = z - hx;

        let ph_t = [
            self.p[0][0] * h[0] + self.p[0][1] * h[1] + self.p[0][2] * h[2],
            self.p[1][0] * h[0] + self.p[1][1] * h[1] + self.p[1][2] * h[2],
            self.p[2][0] * h[0] + self.p[2][1] * h[1] + self.p[2][2] * h[2],
        ];
        let s = h[0] * ph_t[0] + h[1] * ph_t[1] + h[2] * ph_t[2] + r;
        if s <= 1e-12 {
            return;
        }

        let k = [ph_t[0] / s, ph_t[1] / s, ph_t[2] / s];
        self.x[0] += k[0] * innovation;
        self.x[1] += k[1] * innovation;
        self.x[2] += k[2] * innovation;

        // Joseph form keeps covariance positive semi-definite under finite precision.
        let mut a = mat3_identity();
        for i in 0..3 {
            for j in 0..3 {
                a[i][j] -= k[i] * h[j];
            }
        }
        let apa_t = mat3_mul(mat3_mul(a, self.p), mat3_transpose(a));
        let kk_t = mat3_outer(k, k);
        self.p = mat3_add(apa_t, mat3_scale(kk_t, r));
        self.symmetrize_covariance();
        self.force_covariance_floor();
    }

    fn symmetrize_covariance(&mut self) {
        for i in 0..3 {
            for j in (i + 1)..3 {
                let avg = 0.5 * (self.p[i][j] + self.p[j][i]);
                self.p[i][j] = avg;
                self.p[j][i] = avg;
            }
        }
    }

    fn force_covariance_floor(&mut self) {
        self.p[0][0] = self.p[0][0].max(1e-5);
        self.p[1][1] = self.p[1][1].max(1e-5);
        self.p[2][2] = self.p[2][2].max(1e-8);
    }
}

fn mat3_mul(a: [[f64; 3]; 3], b: [[f64; 3]; 3]) -> [[f64; 3]; 3] {
    let mut out = [[0.0f64; 3]; 3];
    for i in 0..3 {
        for j in 0..3 {
            out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
        }
    }
    out
}

fn mat3_transpose(a: [[f64; 3]; 3]) -> [[f64; 3]; 3] {
    [
        [a[0][0], a[1][0], a[2][0]],
        [a[0][1], a[1][1], a[2][1]],
        [a[0][2], a[1][2], a[2][2]],
    ]
}

fn mat3_add(a: [[f64; 3]; 3], b: [[f64; 3]; 3]) -> [[f64; 3]; 3] {
    [
        [a[0][0] + b[0][0], a[0][1] + b[0][1], a[0][2] + b[0][2]],
        [a[1][0] + b[1][0], a[1][1] + b[1][1], a[1][2] + b[1][2]],
        [a[2][0] + b[2][0], a[2][1] + b[2][1], a[2][2] + b[2][2]],
    ]
}

fn mat3_scale(a: [[f64; 3]; 3], scalar: f64) -> [[f64; 3]; 3] {
    [
        [a[0][0] * scalar, a[0][1] * scalar, a[0][2] * scalar],
        [a[1][0] * scalar, a[1][1] * scalar, a[1][2] * scalar],
        [a[2][0] * scalar, a[2][1] * scalar, a[2][2] * scalar],
    ]
}

fn mat3_outer(a: [f64; 3], b: [f64; 3]) -> [[f64; 3]; 3] {
    [
        [a[0] * b[0], a[0] * b[1], a[0] * b[2]],
        [a[1] * b[0], a[1] * b[1], a[1] * b[2]],
        [a[2] * b[0], a[2] * b[1], a[2] * b[2]],
    ]
}

fn mat3_identity() -> [[f64; 3]; 3] {
    [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
}

fn run_inertial_pipeline(
    parsed: &ParsedLog,
    gt: Option<&[GpsSample]>,
    gt_is_ground_truth: bool,
) -> Result<InertialMetricsPayload> {
    let gps_enu_track = smooth_gps_enu_track(
        &sanitize_gps_enu_track(gps_samples_to_enu_track(&parsed.gps_samples).unwrap_or_default()),
    );

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

    let mut gyro_bias = normalize_gyro_units(estimate_startup_gyro_bias(&imu_by_step));

    let mut trajectory = Vec::new();
    let mut metrics = Vec::new();
    let mut roll = 0.0f64;
    let mut pitch = 0.0f64;
    let mut yaw = 0.0f64;
    let mut kf_x = AxisKalman::new(25.0, 9.0, 0.25);
    let mut kf_y = AxisKalman::new(25.0, 9.0, 0.25);
    let mut kf_z = AxisKalman::new(16.0, 9.0, 0.25);
    let mut prev_step = None::<u64>;
    let mut peak_abs_accel = 0.0f64;
    let mut peak_abs_gyro = 0.0f64;
    let mut max_vibration = 0.0f64;
    let mut baro_reference: Option<f64> = None;
    let mut baro_alt_lp: Option<f64> = None;
    let mut last_baro_altitude: Option<f64> = None;
    let mut accel_lp: Option<[f64; 3]> = None;
    let mut gyro_lp: Option<[f64; 3]> = None;
    let mut stationary_streak = 0usize;

    for (step_ms, samples) in imu_by_step {
        let (dt, lag_gap) = if let Some(prev) = prev_step {
            let raw_dt = (step_ms.saturating_sub(prev)) as f64 / 1000.0;
            (raw_dt.clamp(0.005, 0.1), raw_dt > 0.2)
        } else {
            (0.02, false)
        };
        prev_step = Some(step_ms);

        let (fused_accel, fused_gyro, accel_spread, gyro_spread) = fuse_imu_step(&samples);
        let vibration = vibe_by_step.get(&step_ms).copied().unwrap_or(0.0);
        max_vibration = max_vibration.max(vibration);

        let accel_raw = normalize_accel_units(fused_accel);
        let gyro_measured = normalize_gyro_units(fused_gyro);

        let accel_alpha = if lag_gap {
            0.55
        } else if vibration > 45.0 {
            0.38
        } else {
            0.24
        };
        let mut accel = accel_lp
            .map(|prev| blend_vec3(prev, accel_raw, accel_alpha))
            .unwrap_or(accel_raw);
        accel_lp = Some(accel);

        let gyro_unbiased = [
            gyro_measured[0] - gyro_bias[0],
            gyro_measured[1] - gyro_bias[1],
            gyro_measured[2] - gyro_bias[2],
        ];
        let gyro_alpha = if lag_gap {
            0.55
        } else if vibration > 45.0 {
            0.42
        } else {
            0.28
        };
        let mut gyro = gyro_lp
            .map(|prev| blend_vec3(prev, gyro_unbiased, gyro_alpha))
            .unwrap_or(gyro_unbiased);
        gyro_lp = Some(gyro);

        let accel_norm = norm3(accel);
        let gyro_norm = norm3(gyro);
        peak_abs_accel = peak_abs_accel.max(accel.iter().map(|v| v.abs()).fold(0.0, f64::max));
        peak_abs_gyro = peak_abs_gyro.max(gyro.iter().map(|v| v.abs()).fold(0.0, f64::max));

        let quasi_static_candidate = (accel_norm - 9.80665).abs() < 0.18
            && gyro_norm < (2.5 * PI / 180.0)
            && vibration < 35.0;
        stationary_streak = if quasi_static_candidate {
            stationary_streak.saturating_add(1)
        } else {
            0
        };
        let quasi_static = stationary_streak >= 4;
        if quasi_static {
            let bias_alpha = if vibration > 15.0 { 0.006 } else { 0.012 };
            gyro_bias = blend_vec3(gyro_bias, gyro_measured, bias_alpha);
            let corrected = [
                gyro_measured[0] - gyro_bias[0],
                gyro_measured[1] - gyro_bias[1],
                gyro_measured[2] - gyro_bias[2],
            ];
            gyro = gyro_lp
                .map(|prev| blend_vec3(prev, corrected, 0.45))
                .unwrap_or(corrected);
            gyro_lp = Some(gyro);
            accel = accel_lp
                .map(|prev| blend_vec3(prev, accel_raw, 0.18))
                .unwrap_or(accel_raw);
            accel_lp = Some(accel);
        }

        roll += gyro[0] * dt;
        pitch += gyro[1] * dt;
        yaw = wrap_angle_rad(yaw + gyro[2] * dt);

        if (6.0..13.5).contains(&accel_norm) {
            let roll_acc = accel[1].atan2(accel[2]);
            let pitch_acc = (-accel[0]).atan2((accel[1] * accel[1] + accel[2] * accel[2]).sqrt());
            let blend = if vibration > 40.0 { 0.012 } else { 0.028 };
            roll = roll * (1.0 - blend) + roll_acc * blend;
            pitch = pitch * (1.0 - blend) + pitch_acc * blend;
        }

        let rot = rotation_matrix_rpy(roll, pitch, yaw);
        let world_acc = mat3_mul_vec(rot, accel);
        let net_acc = [
            world_acc[0].clamp(-35.0, 35.0),
            world_acc[1].clamp(-35.0, 35.0),
            (world_acc[2] - 9.80665).clamp(-25.0, 25.0),
        ];

        let process_accel_sigma = (0.90 + 0.014 * vibration + 1.6 * accel_spread).clamp(0.90, 4.50);
        let process_bias_rw_sigma = (0.006 + 0.040 * gyro_spread + if quasi_static { 0.0 } else { 0.010 })
            .clamp(0.006, 0.080);

        if lag_gap {
            kf_x.update_velocity(0.0, 4.0);
            kf_y.update_velocity(0.0, 4.0);
            kf_z.update_velocity(0.0, 6.0);
        }

        kf_x.predict(dt, net_acc[0], process_accel_sigma, process_bias_rw_sigma);
        kf_y.predict(dt, net_acc[1], process_accel_sigma, process_bias_rw_sigma);
        kf_z.predict(dt, net_acc[2], process_accel_sigma * 0.9, process_bias_rw_sigma * 1.1);

        if let Some(baro_samples) = baro_by_step.get(&step_ms) {
            let baro_altitudes = baro_samples.iter().map(|s| s.altitude_m).collect::<Vec<_>>();
            let altitude = robust_average(baro_altitudes.clone());
            let baro_spread = robust_std(baro_altitudes);
            let altitude = if let Some(reference) = baro_reference {
                altitude - reference
            } else {
                baro_reference = Some(altitude);
                0.0
            };
            let filtered_altitude = if let Some(prev_alt) = baro_alt_lp {
                prev_alt + (altitude - prev_alt) * 0.18
            } else {
                altitude
            };
            baro_alt_lp = Some(filtered_altitude);
            last_baro_altitude = Some(filtered_altitude);
            let baro_sigma_m = (0.35 + 0.010 * vibration + 0.75 * baro_spread).clamp(0.35, 2.50);
            kf_z.update_position(filtered_altitude, baro_sigma_m * baro_sigma_m);
        }

        if quasi_static {
            let zupt_var_xy = if vibration > 30.0 { 0.14 * 0.14 } else { 0.08 * 0.08 };
            let zupt_var_z = if vibration > 30.0 { 0.18 * 0.18 } else { 0.10 * 0.10 };
            kf_x.update_velocity(0.0, zupt_var_xy);
            kf_y.update_velocity(0.0, zupt_var_xy);
            kf_z.update_velocity(0.0, zupt_var_z);

            // During static windows gravity-aligned attitude is more reliable than yaw integration.
            yaw = wrap_angle_rad(yaw * 0.998);
        }

        let pos = [kf_x.x[0], kf_y.x[0], kf_z.x[0]];
        let vel = [kf_x.x[1], kf_y.x[1], kf_z.x[1]];

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
            baro_altitude: last_baro_altitude,
        });
    }

    if trajectory.is_empty() {
        return Err(anyhow!("trajectory generation produced zero points"));
    }

    let comparison_reference_track = if let Some(gt_points) = gt {
        align_reference_track_to_estimated(normalize_reference_track(gt_points), &trajectory)
    } else {
        align_reference_track_to_estimated(gps_enu_track.clone(), &trajectory)
    };
    let gps_reference_track = align_reference_track_to_estimated(gps_enu_track, &trajectory);

    // Post-processing alignment is used only for visualization/comparison output.
    // It does not feed back into the INS state propagation.
    let mut trajectory = sanitize_trajectory(trajectory);
    if !comparison_reference_track.is_empty() {
        trajectory = align_trajectory_to_reference(&trajectory, &comparison_reference_track);
    } else if !gps_reference_track.is_empty() {
        trajectory = align_trajectory_to_reference(&trajectory, &gps_reference_track);
    }
    let (horizontal_distance, total_distance_3d) = trajectory_distances(&trajectory);

    let (endpoint_error_m, rms_crosstrack_error_m, ate_m, p95_crosstrack_error_m, gt_used) =
        if comparison_reference_track.is_empty() {
            (0.0, 0.0, 0.0, 0.0, false)
        } else {
            let stats = compare_trajectory_to_ground_truth(&trajectory, &comparison_reference_track);
            (stats.0, stats.1, stats.2, stats.3, gt_is_ground_truth)
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

    Ok(InertialMetricsPayload {
        point_count: trajectory.len(),
        endpoint_error_m,
        rms_crosstrack_error_m,
        ate_m,
        p95_crosstrack_error_m,
        horizontal_distance_m: horizontal_distance,
        total_distance_3d_m: total_distance_3d,
        max_vibration,
        peak_abs_accel,
        peak_abs_gyro,
        used_imu_count,
        used_baro_count,
        ground_truth_used: gt_used,
        comparison_reference_track,
        gps_reference_track,
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
            let x = idx_x
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .unwrap_or(0.0);
            let y = idx_y
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .unwrap_or(0.0);
            let z = idx_z
                .and_then(|i| cols.get(i).and_then(|v| v.parse::<f64>().ok()))
                .unwrap_or(0.0);
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
    if !raw.is_finite() || raw <= 0.0 {
        0
    } else if raw > 100_000_000_000_000_000.0 {
        (raw / 1_000_000.0) as u64
    } else if raw > 100_000_000_000_000.0 {
        (raw / 1000.0) as u64
    } else if raw > 100_000_000_000.0 {
        raw as u64
    } else if raw > 1_000_000.0 {
        (raw / 1000.0) as u64
    } else {
        raw as u64
    }
}

fn normalize_timestamp_to_us(raw: f64) -> u64 {
    if !raw.is_finite() || raw <= 0.0 {
        0
    } else if raw > 100_000_000_000_000_000.0 {
        (raw / 1_000.0) as u64
    } else if raw > 100_000_000_000.0 {
        raw as u64
    } else if raw > 1_000_000.0 {
        (raw * 1000.0) as u64
    } else {
        (raw * 1000.0) as u64
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
    let Some(_last) = estimated.last() else {
        return 0.0;
    };

    let mut tail_errors = Vec::new();
    for e in estimated.iter().rev().take(40) {
        if let Some(gt_pos) = sample_track_point(gt, e.timestamp) {
            tail_errors.push(distance3([e.x, e.y, e.z], gt_pos));
        }
    }

    if !tail_errors.is_empty() {
        tail_errors.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        return tail_errors[tail_errors.len() / 2];
    }

    let last = &estimated[estimated.len() - 1];
    let gt_last = gt
        .iter()
        .min_by_key(|g| g.timestamp_ms.abs_diff(last.timestamp))
        .unwrap_or(&gt[gt.len() - 1]);
    distance3([last.x, last.y, last.z], [gt_last.x, gt_last.y, gt_last.z])
}

fn compare_trajectory_to_ground_truth(estimated: &[TrajectoryPoint], gt: &[GpsSample]) -> (f64, f64, f64, f64) {
    if estimated.is_empty() || gt.is_empty() {
        return (0.0, 0.0, 0.0, 0.0);
    }

    let endpoint = endpoint_error(estimated, gt);
    let mut distances = Vec::with_capacity(estimated.len());

    for e in estimated {
        if let Some(gt_pos) = sample_track_point(gt, e.timestamp) {
            distances.push(distance3([e.x, e.y, e.z], gt_pos));
        }
    }

    if distances.is_empty() {
        return (endpoint, 0.0, 0.0, 0.0);
    }

    let rms = (distances.iter().map(|d| d * d).sum::<f64>() / (distances.len() as f64)).sqrt();
    let ate = distances.iter().sum::<f64>() / (distances.len() as f64);

    distances.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let p95_idx = ((distances.len() as f64) * 0.95).floor() as usize;
    let p95 = distances[p95_idx.min(distances.len().saturating_sub(1))];

    (endpoint, rms, ate, p95)
}

fn estimate_startup_gyro_bias(imu_by_step: &BTreeMap<u64, Vec<&ImuSample>>) -> [f64; 3] {
    let mut early_gyro = Vec::new();
    let Some((&first_step, _)) = imu_by_step.first_key_value() else {
        return [0.0, 0.0, 0.0];
    };

    for (step, samples) in imu_by_step {
        if step.saturating_sub(first_step) > 1_500 {
            break;
        }
        let fused = robust_average_vec3(samples.iter().map(|s| s.gyro).collect());
        early_gyro.push(fused);
    }

    if early_gyro.is_empty() {
        return [0.0, 0.0, 0.0];
    }

    robust_average_vec3(early_gyro)
}

fn interpolate_track_point(track: &[GpsSample], timestamp_ms: u64) -> Option<[f64; 3]> {
    if track.is_empty() {
        return None;
    }

    let idx = track.partition_point(|p| p.timestamp_ms <= timestamp_ms);
    let tolerance_ms = interpolation_tolerance_ms(track);
    if idx == 0 {
        let first = &track[0];
        if first.timestamp_ms.saturating_sub(timestamp_ms) <= tolerance_ms {
            return Some([first.x, first.y, first.z]);
        }
        return None;
    }

    if idx >= track.len() {
        let last = &track[track.len() - 1];
        if timestamp_ms.saturating_sub(last.timestamp_ms) <= tolerance_ms {
            return Some([last.x, last.y, last.z]);
        }
        return None;
    }

    let a = &track[idx - 1];
    let b = &track[idx];
    let dt = b.timestamp_ms.saturating_sub(a.timestamp_ms);
    if dt == 0 {
        return Some([a.x, a.y, a.z]);
    }

    let span_to_request = b.timestamp_ms.saturating_sub(timestamp_ms).max(timestamp_ms.saturating_sub(a.timestamp_ms));
    if span_to_request > tolerance_ms {
        return None;
    }

    let alpha = ((timestamp_ms.saturating_sub(a.timestamp_ms)) as f64 / (dt as f64)).clamp(0.0, 1.0);
    Some([
        a.x + (b.x - a.x) * alpha,
        a.y + (b.y - a.y) * alpha,
        a.z + (b.z - a.z) * alpha,
    ])
}

fn sample_track_point(track: &[GpsSample], timestamp_ms: u64) -> Option<[f64; 3]> {
    if let Some(point) = interpolate_track_point(track, timestamp_ms) {
        return Some(point);
    }

    let (nearest, dt) = nearest_track_point(track, timestamp_ms)?;
    if dt <= nearest_track_tolerance_ms(track) {
        Some([nearest.x, nearest.y, nearest.z])
    } else {
        None
    }
}

fn interpolation_tolerance_ms(track: &[GpsSample]) -> u64 {
    if track.len() < 2 {
        return 350;
    }

    let mut diffs = Vec::new();
    for pair in track.windows(2).take(512) {
        let dt = pair[1].timestamp_ms.saturating_sub(pair[0].timestamp_ms);
        if (1..=5_000).contains(&dt) {
            diffs.push(dt);
        }
    }

    if diffs.is_empty() {
        return 350;
    }

    diffs.sort_unstable();
    let median = diffs[diffs.len() / 2];
    (median.saturating_mul(5)).clamp(250, 1_500)
}

fn nearest_track_tolerance_ms(track: &[GpsSample]) -> u64 {
    interpolation_tolerance_ms(track)
        .saturating_mul(3)
        .clamp(500, 5_000)
}

fn nearest_track_point(track: &[GpsSample], timestamp_ms: u64) -> Option<(&GpsSample, u64)> {
    if track.is_empty() {
        return None;
    }

    let idx = track.partition_point(|p| p.timestamp_ms <= timestamp_ms);
    let prev = idx.checked_sub(1).and_then(|i| track.get(i));
    let next = track.get(idx);

    match (prev, next) {
        (Some(a), Some(b)) => {
            let dt_a = a.timestamp_ms.abs_diff(timestamp_ms);
            let dt_b = b.timestamp_ms.abs_diff(timestamp_ms);
            if dt_a <= dt_b {
                Some((a, dt_a))
            } else {
                Some((b, dt_b))
            }
        }
        (Some(a), None) => Some((a, a.timestamp_ms.abs_diff(timestamp_ms))),
        (None, Some(b)) => Some((b, b.timestamp_ms.abs_diff(timestamp_ms))),
        (None, None) => None,
    }
}

fn normalize_reference_track(track: &[GpsSample]) -> Vec<GpsSample> {
    let mut normalized = track
        .iter()
        .filter(|point| point.x.is_finite() && point.y.is_finite() && point.z.is_finite())
        .cloned()
        .collect::<Vec<_>>();
    normalized.sort_by_key(|point| point.timestamp_ms);
    normalized.dedup_by_key(|point| point.timestamp_ms);
    normalized
}

fn align_reference_track_to_estimated(mut track: Vec<GpsSample>, estimated: &[TrajectoryPoint]) -> Vec<GpsSample> {
    if track.is_empty() || estimated.is_empty() {
        return track;
    }

    track.sort_by_key(|point| point.timestamp_ms);
    track.dedup_by_key(|point| point.timestamp_ms);

    let est_start_ts = estimated[0].timestamp;
    let ref_start_ts = track[0].timestamp_ms;

    let overlap_hits = estimated
        .iter()
        .step_by(10)
        .take(150)
        .filter(|p| sample_track_point(&track, p.timestamp).is_some())
        .count();

    if overlap_hits == 0 {
        let delta = est_start_ts as i128 - ref_start_ts as i128;
        for point in &mut track {
            let shifted = point.timestamp_ms as i128 + delta;
            point.timestamp_ms = shifted.max(0) as u64;
        }
        track.sort_by_key(|point| point.timestamp_ms);
        track.dedup_by_key(|point| point.timestamp_ms);
    }

    track
}

fn sanitize_gps_enu_track(mut track: Vec<GpsSample>) -> Vec<GpsSample> {
    if track.is_empty() {
        return track;
    }

    track.sort_by_key(|p| p.timestamp_ms);
    track.dedup_by_key(|p| p.timestamp_ms);

    let mut out: Vec<GpsSample> = Vec::with_capacity(track.len());
    for point in track {
        if !point.x.is_finite() || !point.y.is_finite() || !point.z.is_finite() {
            continue;
        }

        if let Some(prev) = out.last() {
            let dt = point.timestamp_ms.saturating_sub(prev.timestamp_ms);
            if dt == 0 {
                continue;
            }

            let distance = distance3([point.x, point.y, point.z], [prev.x, prev.y, prev.z]);
            let speed = distance / ((dt as f64) / 1000.0);
            if speed > 120.0 {
                continue;
            }
        }

        out.push(point);
    }

    out
}

fn smooth_gps_enu_track(track: &[GpsSample]) -> Vec<GpsSample> {
    if track.len() < 3 {
        return track.to_vec();
    }

    let mut out = Vec::with_capacity(track.len());
    let mut prev_filtered = [track[0].x, track[0].y, track[0].z];
    let mut prev_filtered_vel = [0.0f64; 3];

    for idx in 0..track.len() {
        let start = idx.saturating_sub(2);
        let end = (idx + 3).min(track.len());
        let local = &track[start..end];
        let candidate = [
            robust_average(local.iter().map(|p| p.x).collect()),
            robust_average(local.iter().map(|p| p.y).collect()),
            robust_average(local.iter().map(|p| p.z).collect()),
        ];

        if idx == 0 {
            prev_filtered = candidate;
            out.push(GpsSample {
                timestamp_ms: track[idx].timestamp_ms,
                x: candidate[0],
                y: candidate[1],
                z: candidate[2],
            });
            continue;
        }

        let dt = ((track[idx].timestamp_ms.saturating_sub(track[idx - 1].timestamp_ms)) as f64 / 1000.0)
            .clamp(0.02, 1.0);
        let raw_vel = [
            (candidate[0] - prev_filtered[0]) / dt,
            (candidate[1] - prev_filtered[1]) / dt,
            (candidate[2] - prev_filtered[2]) / dt,
        ];
        let speed_xy = (raw_vel[0] * raw_vel[0] + raw_vel[1] * raw_vel[1]).sqrt();
        let accel = [
            (raw_vel[0] - prev_filtered_vel[0]) / dt,
            (raw_vel[1] - prev_filtered_vel[1]) / dt,
            (raw_vel[2] - prev_filtered_vel[2]) / dt,
        ];
        let accel_norm = norm3(accel);

        let mut alpha = if speed_xy > 18.0 {
            0.62
        } else if speed_xy > 8.0 {
            0.48
        } else if speed_xy > 2.0 {
            0.34
        } else {
            0.22
        };
        if accel_norm > 25.0 {
            alpha *= 0.55;
        }

        let filtered = [
            prev_filtered[0] + (candidate[0] - prev_filtered[0]) * alpha,
            prev_filtered[1] + (candidate[1] - prev_filtered[1]) * alpha,
            prev_filtered[2] + (candidate[2] - prev_filtered[2]) * alpha,
        ];

        prev_filtered_vel = [
            (filtered[0] - prev_filtered[0]) / dt,
            (filtered[1] - prev_filtered[1]) / dt,
            (filtered[2] - prev_filtered[2]) / dt,
        ];
        prev_filtered = filtered;

        out.push(GpsSample {
            timestamp_ms: track[idx].timestamp_ms,
            x: filtered[0],
            y: filtered[1],
            z: filtered[2],
        });
    }

    out
}

fn wrap_angle_rad(mut angle: f64) -> f64 {
    while angle > PI {
        angle -= 2.0 * PI;
    }
    while angle < -PI {
        angle += 2.0 * PI;
    }
    angle
}

fn angle_diff_rad(target: f64, current: f64) -> f64 {
    wrap_angle_rad(target - current)
}

fn blend_vec3(current: [f64; 3], target: [f64; 3], alpha: f64) -> [f64; 3] {
    let alpha = alpha.clamp(0.0, 1.0);
    [
        current[0] + (target[0] - current[0]) * alpha,
        current[1] + (target[1] - current[1]) * alpha,
        current[2] + (target[2] - current[2]) * alpha,
    ]
}

fn is_gps_fix_plausible(gps_vel: [f64; 3], prev_gps_vel: Option<[f64; 3]>, dt: f64) -> bool {
    let speed_xy = (gps_vel[0] * gps_vel[0] + gps_vel[1] * gps_vel[1]).sqrt();
    let speed_z = gps_vel[2].abs();
    if speed_xy > 75.0 || speed_z > 30.0 {
        return false;
    }

    if let Some(prev) = prev_gps_vel {
        let ax = (gps_vel[0] - prev[0]) / dt.max(0.01);
        let ay = (gps_vel[1] - prev[1]) / dt.max(0.01);
        let az = (gps_vel[2] - prev[2]) / dt.max(0.01);
        let accel = (ax * ax + ay * ay + az * az).sqrt();
        if accel > 35.0 {
            return false;
        }
    }

    true
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

fn fuse_imu_step(samples: &[&ImuSample]) -> ([f64; 3], [f64; 3], f64, f64) {
    if samples.is_empty() {
        return ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0], 0.0, 0.0);
    }

    let mut per_imu: BTreeMap<u8, Vec<&ImuSample>> = BTreeMap::new();
    for sample in samples {
        per_imu.entry(sample.imu_id).or_default().push(*sample);
    }

    let mut accel_candidates = Vec::with_capacity(per_imu.len());
    let mut gyro_candidates = Vec::with_capacity(per_imu.len());
    for group in per_imu.values() {
        accel_candidates.push(robust_average_vec3(group.iter().map(|s| s.accel).collect()));
        gyro_candidates.push(robust_average_vec3(group.iter().map(|s| s.gyro).collect()));
    }

    let accel_center = robust_average_vec3(accel_candidates.clone());
    let gyro_center = robust_average_vec3(gyro_candidates.clone());

    let accel_residuals = accel_candidates
        .iter()
        .map(|a| distance3(*a, accel_center))
        .collect::<Vec<_>>();
    let gyro_residuals = gyro_candidates
        .iter()
        .map(|g| distance3(*g, gyro_center))
        .collect::<Vec<_>>();

    let accel_scale = robust_average(accel_residuals.clone()).max(0.02);
    let gyro_scale = robust_average(gyro_residuals.clone()).max(0.002);

    let mut accel_weighted_sum = [0.0f64; 3];
    let mut gyro_weighted_sum = [0.0f64; 3];
    let mut accel_w_sum = 0.0;
    let mut gyro_w_sum = 0.0;

    for (candidate, residual) in accel_candidates.iter().zip(accel_residuals.iter()) {
        let ratio = residual / accel_scale;
        let weight = 1.0 / (1.0 + ratio * ratio);
        accel_weighted_sum[0] += candidate[0] * weight;
        accel_weighted_sum[1] += candidate[1] * weight;
        accel_weighted_sum[2] += candidate[2] * weight;
        accel_w_sum += weight;
    }
    for (candidate, residual) in gyro_candidates.iter().zip(gyro_residuals.iter()) {
        let ratio = residual / gyro_scale;
        let weight = 1.0 / (1.0 + ratio * ratio);
        gyro_weighted_sum[0] += candidate[0] * weight;
        gyro_weighted_sum[1] += candidate[1] * weight;
        gyro_weighted_sum[2] += candidate[2] * weight;
        gyro_w_sum += weight;
    }

    let fused_accel = if accel_w_sum > 1e-9 {
        [
            accel_weighted_sum[0] / accel_w_sum,
            accel_weighted_sum[1] / accel_w_sum,
            accel_weighted_sum[2] / accel_w_sum,
        ]
    } else {
        accel_center
    };
    let fused_gyro = if gyro_w_sum > 1e-9 {
        [
            gyro_weighted_sum[0] / gyro_w_sum,
            gyro_weighted_sum[1] / gyro_w_sum,
            gyro_weighted_sum[2] / gyro_w_sum,
        ]
    } else {
        gyro_center
    };

    let accel_spread = robust_std(accel_residuals);
    let gyro_spread = robust_std(gyro_residuals);
    (fused_accel, fused_gyro, accel_spread, gyro_spread)
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

fn robust_std(values: Vec<f64>) -> f64 {
    if values.is_empty() {
        return 0.0;
    }
    let mut sorted = values;
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let median = sorted[sorted.len() / 2];
    let mut abs_dev = sorted
        .iter()
        .map(|v| (v - median).abs())
        .collect::<Vec<_>>();
    abs_dev.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    // MAD to std-dev approximation for near-Gaussian residuals.
    1.4826 * abs_dev[abs_dev.len() / 2]
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

fn mat3_transpose_mul_vec(m: [[f64; 3]; 3], v: [f64; 3]) -> [f64; 3] {
    [
        m[0][0] * v[0] + m[1][0] * v[1] + m[2][0] * v[2],
        m[0][1] * v[0] + m[1][1] * v[1] + m[2][1] * v[2],
        m[0][2] * v[0] + m[1][2] * v[1] + m[2][2] * v[2],
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

fn get_text<'a>(record: &'a HashMap<String, FieldValue>, keys: &[&str]) -> Option<&'a str> {
    for key in keys {
        if let Some(FieldValue::Text(v)) = record.get(*key) {
            let trimmed = v.trim();
            if !trimmed.is_empty() {
                return Some(trimmed);
            }
        }
    }
    None
}

fn get_timestamp_us(record: &HashMap<String, FieldValue>) -> Option<u64> {
    if let Some(v) = get_f64(record, &["TimeUS", "TUS"]) {
        return Some(normalize_timestamp_to_us(v));
    }
    if let Some(v) = get_f64(record, &["TimeMS", "TMS"]) {
        return Some(normalize_timestamp_to_ms(v).saturating_mul(1000));
    }
    get_f64(record, &["TS", "Time", "Timestamp"]).map(normalize_timestamp_to_us)
}

fn json_value_from_f64(value: f64) -> Option<serde_json::Value> {
    if !value.is_finite() {
        return None;
    }

    let rounded = value.round();
    if (value - rounded).abs() <= 1e-9 && rounded.abs() <= (i64::MAX as f64) {
        return Some(serde_json::Value::from(rounded as i64));
    }

    serde_json::Number::from_f64(value).map(serde_json::Value::Number)
}

fn infer_suffix_id(name: &str) -> Option<u8> {
    let suffix = name
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .collect::<String>();
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

fn env_or_default(name: &str, default_value: &str) -> String {
    std::env::var(name)
        .ok()
        .filter(|v| !v.trim().is_empty())
        .unwrap_or_else(|| default_value.to_string())
}

fn env_u64_or_default(name: &str, default_value: u64) -> u64 {
    std::env::var(name)
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(default_value)
}

fn env_f64_or_default(name: &str, default_value: f64) -> f64 {
    std::env::var(name)
        .ok()
        .and_then(|v| v.parse::<f64>().ok())
        .unwrap_or(default_value)
}

fn required_env(name: &str) -> Result<String> {
    std::env::var(name)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .ok_or_else(|| anyhow!("environment variable {} is required", name))
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

fn init_logging() {
    let filter = std::env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string());
    tracing_subscriber::fmt()
        .with_env_filter(filter)
        .with_target(false)
        .compact()
        .init();
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
    fn normalize_coordinate_supports_e7() {
        assert_eq!(normalize_coordinate(550_000_000.0, 90.0), Some(55.0));
        assert_eq!(normalize_coordinate(37.1234, 180.0), Some(37.1234));
    }

    #[test]
    fn geodetic_and_enu_roundtrip_is_reasonable() {
        let ref_geo = (55.0, 37.0, 100.0);
        let enu = geodetic_to_enu(55.0001, 37.0001, 120.0, ref_geo);
        let back = enu_to_geodetic(enu.0, enu.1, enu.2, ref_geo);
        assert!((back.0 - 55.0001).abs() < 1e-5);
        assert!((back.1 - 37.0001).abs() < 1e-5);
        assert!((back.2 - 120.0).abs() < 1e-6);
    }

    #[test]
    fn normalize_timestamp_handles_epoch_millis() {
        assert_eq!(normalize_timestamp_to_ms(1_700_000_000_000.0), 1_700_000_000_000);
    }

    #[test]
    fn normalize_timestamp_us_scales_millis() {
        let mut record = HashMap::new();
        record.insert("TimeMS".to_string(), FieldValue::Number(1_234.0));
        assert_eq!(get_timestamp_us(&record), Some(1_234_000));
    }

    #[test]
    fn char_field_parser_preserves_text() {
        let payload = b"GPS1\0\0\0\0\0\0\0\0\0\0\0\0";
        let (value, consumed) = parse_field(&FieldParser::Char(16), payload).expect("char field should parse");
        assert_eq!(consumed, 16);
        match value {
            FieldValue::Text(text) => assert_eq!(text, "GPS1"),
            _ => panic!("expected text field"),
        }
    }

    #[test]
    fn parm_records_are_extracted_into_params() {
        let fmt = FmtDefinition {
            message_type: 1,
            message_len: 0,
            name: "PARM".to_string(),
            columns: vec!["Name".to_string(), "Value".to_string()],
            parsers: vec![FieldParser::Char(16), FieldParser::F32],
        };
        let mut record = HashMap::new();
        record.insert("Name".to_string(), FieldValue::Text("GPS1_TYPE".to_string()));
        record.insert("Value".to_string(), FieldValue::Number(9.0));

        let mut parsed = ParsedLog::default();
        collect_record(&mut parsed, &fmt, &record);

        assert_eq!(parsed.params.get("GPS1_TYPE"), Some(&serde_json::Value::from(9)));
    }

    #[test]
    fn gps_fallback_is_converted_to_enu_meters() {
        let samples = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 55.0,
                y: 37.0,
                z: 100.0,
            },
            GpsSample {
                timestamp_ms: 20,
                x: 55.0001,
                y: 37.0001,
                z: 101.5,
            },
        ];

        let converted = gps_samples_to_enu_track(&samples).expect("gps fallback should be produced");
        assert_eq!(converted.len(), 2);
        assert!(converted[0].x.abs() < 1e-6);
        assert!(converted[0].y.abs() < 1e-6);
        assert!(converted[1].x.abs() > 1.0);
        assert!(converted[1].y.abs() > 1.0);
        assert!((converted[1].z - 1.5).abs() < 1e-6);
    }

    #[test]
    fn interpolate_track_point_interpolates_middle() {
        let track = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 100,
                x: 10.0,
                y: 20.0,
                z: 5.0,
            },
        ];

        let p = interpolate_track_point(&track, 50).expect("point should interpolate");
        assert!((p[0] - 5.0).abs() < 1e-9);
        assert!((p[1] - 10.0).abs() < 1e-9);
        assert!((p[2] - 2.5).abs() < 1e-9);
    }

    #[test]
    fn angle_diff_wraps_correctly() {
        let d = angle_diff_rad(-PI + 0.1, PI - 0.1);
        assert!(d.abs() < 0.25);
    }

    #[test]
    fn endpoint_error_uses_tail_median() {
        let estimated = vec![
            TrajectoryPoint {
                timestamp: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
                speed: 0.0,
                roll: 0.0,
                pitch: 0.0,
                yaw: 0.0,
                vibration: 0.0,
            },
            TrajectoryPoint {
                timestamp: 20,
                x: 1.0,
                y: 0.0,
                z: 0.0,
                speed: 0.0,
                roll: 0.0,
                pitch: 0.0,
                yaw: 0.0,
                vibration: 0.0,
            },
            TrajectoryPoint {
                timestamp: 40,
                x: 200.0,
                y: 0.0,
                z: 0.0,
                speed: 0.0,
                roll: 0.0,
                pitch: 0.0,
                yaw: 0.0,
                vibration: 0.0,
            },
        ];

        let gt = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 20,
                x: 1.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 40,
                x: 2.0,
                y: 0.0,
                z: 0.0,
            },
        ];

        let e = endpoint_error(&estimated, &gt);
        assert!(e < 5.0);
    }

    #[test]
    fn gps_fix_plausibility_rejects_unrealistic_spike() {
        let ok = is_gps_fix_plausible([10.0, 0.0, 0.0], Some([9.0, 0.0, 0.0]), 0.1);
        assert!(ok);

        let bad_speed = is_gps_fix_plausible([120.0, 0.0, 0.0], None, 0.1);
        assert!(!bad_speed);

        let bad_accel = is_gps_fix_plausible([25.0, 0.0, 0.0], Some([0.0, 0.0, 0.0]), 0.1);
        assert!(!bad_accel);
    }

    #[test]
    fn sanitize_gps_enu_track_drops_unrealistic_jump() {
        let track = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 100,
                x: 2_000.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 200,
                x: 4.0,
                y: 0.0,
                z: 0.0,
            },
        ];

        let cleaned = sanitize_gps_enu_track(track);
        assert_eq!(cleaned.len(), 2);
        assert!((cleaned[1].x - 4.0).abs() < 1e-9);
    }

    #[test]
    fn interpolation_tolerance_expands_for_sparse_track() {
        let track = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 1_000,
                x: 1.0,
                y: 0.0,
                z: 0.0,
            },
        ];

        assert!(interpolation_tolerance_ms(&track) >= 1_000);
    }

    #[test]
    fn sample_track_point_falls_back_to_nearest_when_interpolation_misses() {
        let track = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 0.0,
                y: 0.0,
                z: 0.0,
            },
            GpsSample {
                timestamp_ms: 1_000,
                x: 10.0,
                y: 0.0,
                z: 0.0,
            },
        ];

        let point = sample_track_point(&track, 1_600).expect("nearest sample should be used");
        assert!((point[0] - 10.0).abs() < 1e-9);
    }

    #[test]
    fn worker_payload_includes_params_and_gps_projection() {
        let mut parsed = ParsedLog::default();
        parsed.gps_samples = vec![
            GpsSample {
                timestamp_ms: 0,
                x: 55.0,
                y: 37.0,
                z: 100.0,
            },
            GpsSample {
                timestamp_ms: 100,
                x: 55.0001,
                y: 37.0001,
                z: 101.0,
            },
        ];
        parsed.params.insert("GPS1_TYPE".to_string(), serde_json::Value::from(9));

        let inertial = InertialMetricsPayload {
            point_count: 2,
            endpoint_error_m: 1.0,
            rms_crosstrack_error_m: 1.0,
            ate_m: 1.0,
            p95_crosstrack_error_m: 1.0,
            horizontal_distance_m: 9.5,
            total_distance_3d_m: 10.0,
            max_vibration: 0.0,
            peak_abs_accel: 0.0,
            peak_abs_gyro: 0.0,
            used_imu_count: 1,
            used_baro_count: 1,
            ground_truth_used: false,
            comparison_reference_track: vec![
                GpsSample {
                    timestamp_ms: 0,
                    x: 0.0,
                    y: 0.0,
                    z: 0.0,
                },
                GpsSample {
                    timestamp_ms: 100,
                    x: 5.0,
                    y: 5.0,
                    z: 1.0,
                },
            ],
            gps_reference_track: vec![
                GpsSample {
                    timestamp_ms: 0,
                    x: 0.0,
                    y: 0.0,
                    z: 0.0,
                },
                GpsSample {
                    timestamp_ms: 100,
                    x: 5.0,
                    y: 5.0,
                    z: 1.0,
                },
            ],
            trajectory: vec![
                TrajectoryPoint {
                    timestamp: 0,
                    x: 0.0,
                    y: 0.0,
                    z: 0.0,
                    speed: 0.0,
                    roll: 0.0,
                    pitch: 0.0,
                    yaw: 0.0,
                    vibration: 0.0,
                },
                TrajectoryPoint {
                    timestamp: 100,
                    x: 4.5,
                    y: 4.5,
                    z: 0.9,
                    speed: 2.0,
                    roll: 0.0,
                    pitch: 0.0,
                    yaw: 0.0,
                    vibration: 0.0,
                },
            ],
            metrics: vec![],
        };

        let payload = build_worker_trajectory_payload(&parsed, &inertial, 0.1).expect("payload should build");

        assert_eq!(payload.params.get("GPS1_TYPE"), Some(&serde_json::Value::from(9)));
        assert!(payload.frames.iter().all(|frame| frame.gps.is_some()));
        assert!(payload.frames.iter().all(|frame| frame.position_error_m.is_some()));
    }

    #[test]
    fn sample_bin_exposes_gps_and_params_when_available() {
        let path = Path::new("../task/2_TR-C-Maneuver.BIN");
        if !path.exists() {
            return;
        }

        let bytes = std::fs::read(path).expect("sample bin should be readable");
        let mut parsed = parse_dataflash_log(&bytes).expect("sample bin should parse");
        sanitize_parsed_log(&mut parsed);

        assert!(!parsed.gps_samples.is_empty(), "expected gps samples from sample bin");
        assert!(!parsed.params.is_empty(), "expected params from sample bin");
    }
}
