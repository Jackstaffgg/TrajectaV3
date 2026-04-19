package dev.knalis.trajectaapi.service.impl.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.knalis.trajectaapi.dto.task.AiConclusionGenerationResult;
import dev.knalis.trajectaapi.model.task.ai.AiModel;
import dev.knalis.trajectaapi.service.intrf.task.AiConclusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiConclusionServiceImpl implements AiConclusionService {

    private static final String MISSING_API_KEY_MESSAGE =
            "AI is currently unavailable: API key is not configured yet.";
    private static final String PROVIDER_UNAVAILABLE_MESSAGE =
            "AI is currently unavailable. Please try again later.";
    private static final String SYSTEM_PROMPT =
            "You are a senior flight telemetry analyst. " +
            "Provide 3-5 short sentences with practical conclusions that a human could miss. " +
            "Prioritize: (1) non-obvious trends, (2) anomaly/risk signals, (3) confidence limits if data is sparse. " +
            "Do not restate raw numbers unless they support an insight. " +
            "If nothing unusual is detected, explicitly state stable/nominal behavior and why.";
    private static final double TARGET_INTERVAL_SECONDS = 2.0;
    private static final double EVENT_WINDOW_SECONDS = 1.0;
    private static final double SIGNIFICANT_ALTITUDE_DELTA = 15.0;
    private static final double SIGNIFICANT_SPEED_DELTA = 5.0;
    private static final double SHARP_ALTITUDE_DELTA = 120.0;
    private static final double SHARP_SPEED_DELTA = 30.0;
    private static final int MAX_AI_TRAJECTORY_CHARS = 8_000;
    private static final int MAX_AI_EVENTS = 30;

    private final String apiKey;
    private final String modelName;
    private final String chatUrl;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiConclusionServiceImpl(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelName,
            @Value("${spring.ai.openai.chat.url:https://api.openai.com/v1/chat/completions}") String chatUrl,
            @Value("${spring.ai.openai.chat.timeout-seconds:15}") long timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.chatUrl = chatUrl;
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.requestTimeout).build();
    }

    @Override
    public AiConclusionGenerationResult generateConclusion(String trajectoryContent) {
        if (!StringUtils.hasText(apiKey)) {
            return new AiConclusionGenerationResult(
                    MISSING_API_KEY_MESSAGE,
                    AiModel.FALLBACK_UNAVAILABLE,
                    MISSING_API_KEY_MESSAGE
            );
        }

        try {
            String aiTrajectoryContent = compactForProviderLimit(buildUltraCompactTrajectoryForAi(trajectoryContent));

            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", modelName,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "Trajectory payload:\n" + aiTrajectoryContent)
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(chatUrl))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 413) {
                return unavailable("AI provider rejected payload (HTTP 413)");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable("AI provider returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isTextual() ? contentNode.asText().trim() : "";
            if (!StringUtils.hasText(content)) {
                return unavailable("AI provider returned an empty response");
            }

            return new AiConclusionGenerationResult(content, resolveModel(modelName), null);
        } catch (Exception ex) {
            log.warn("AI provider request failed", ex);
            return unavailable("AI provider request failed");
        }
    }

    private AiConclusionGenerationResult unavailable(String errorMessage) {
        log.warn("AI fallback engaged: {}", errorMessage);
        return new AiConclusionGenerationResult(
                PROVIDER_UNAVAILABLE_MESSAGE,
                AiModel.FALLBACK_UNAVAILABLE,
                errorMessage
        );
    }

    private AiModel resolveModel(String rawModelName) {
        if (!StringUtils.hasText(rawModelName)) {
            return AiModel.CUSTOM;
        }

        String normalized = rawModelName.trim().toLowerCase();
        return switch (normalized) {
            case "gpt-4o" -> AiModel.GPT_4O;
            case "gpt-4o-mini" -> AiModel.GPT_4O_MINI;
            case "gpt-3.5-turbo", "gpt-3.5" -> AiModel.GPT_3_5;
            default -> AiModel.CUSTOM;
        };
    }

    private String downsampleTrajectoryForAi(String trajectoryContent) {
        try {
            JsonNode root = objectMapper.readTree(trajectoryContent);
            if (!(root instanceof ObjectNode rootObject)) {
                return trajectoryContent;
            }

            JsonNode framesNode = rootObject.path("frames");
            if (!(framesNode instanceof ArrayNode frames) || frames.size() <= 2) {
                return trajectoryContent;
            }

            List<Double> eventTimes = extractEventTimes(rootObject.path("events"));
            ArrayNode sampledFrames = objectMapper.createArrayNode();

            JsonNode lastKeptFrame = null;
            double lastKeptTime = Double.NEGATIVE_INFINITY;
            boolean hasTime = false;
            final int fallbackStep = 20;

            for (int i = 0; i < frames.size(); i++) {
                JsonNode frame = frames.get(i);
                double frameTime = extractFrameTime(frame, i);
                if (!Double.isNaN(frameTime)) {
                    hasTime = true;
                }

                boolean isFirst = sampledFrames.isEmpty();
                boolean isLast = i == frames.size() - 1;

                boolean keepByInterval;
                if (hasTime && !Double.isNaN(frameTime) && lastKeptFrame != null) {
                    keepByInterval = frameTime - lastKeptTime >= TARGET_INTERVAL_SECONDS;
                } else {
                    keepByInterval = i % fallbackStep == 0;
                }

                boolean keepByEvent = hasTime && !Double.isNaN(frameTime) && isNearEvent(frameTime, eventTimes);
                boolean keepByChange = lastKeptFrame != null && isSignificantChange(lastKeptFrame, frame);

                if (isFirst || isLast || keepByInterval || keepByEvent || keepByChange) {
                    sampledFrames.add(frame);
                    lastKeptFrame = frame;
                    if (!Double.isNaN(frameTime)) {
                        lastKeptTime = frameTime;
                    }
                }
            }

            if (sampledFrames.size() >= frames.size()) {
                return trajectoryContent;
            }

            ObjectNode result = rootObject.deepCopy();
            result.set("frames", sampledFrames);
            result.set("aiSampling", objectMapper.valueToTree(Map.of(
                    "originalFrames", frames.size(),
                    "sampledFrames", sampledFrames.size(),
                    "targetIntervalSeconds", TARGET_INTERVAL_SECONDS
            )));
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return trajectoryContent;
        }
    }

    private String compactForProviderLimit(String trajectoryContent) {
        if (trajectoryContent == null || trajectoryContent.length() <= MAX_AI_TRAJECTORY_CHARS) {
            return trajectoryContent;
        }

        try {
            JsonNode root = objectMapper.readTree(trajectoryContent);
            if (!(root instanceof ObjectNode rootObject)) {
                return truncateForProvider(trajectoryContent);
            }

            ObjectNode compact = objectMapper.createObjectNode();
            compact.put("note", "Trajectory compacted to fit provider payload limits");
            compact.put("originalChars", trajectoryContent.length());

            JsonNode eventsNode = rootObject.path("events");
            if (eventsNode instanceof ArrayNode events) {
                compact.put("originalEvents", events.size());
                compact.set("events", compactEvents(events));
            }

            for (String fieldName : List.of("taskId", "status", "startedAt", "finishedAt", "duration")) {
                if (rootObject.has(fieldName)) {
                    compact.set(fieldName, rootObject.get(fieldName));
                }
            }

            String serialized = objectMapper.writeValueAsString(compact);
            return serialized.length() <= MAX_AI_TRAJECTORY_CHARS
                    ? serialized
                    : truncateForProvider(serialized);
        } catch (Exception ex) {
            return truncateForProvider(trajectoryContent);
        }
    }

    private String buildUltraCompactTrajectoryForAi(String trajectoryContent) {
        try {
            JsonNode root = objectMapper.readTree(trajectoryContent);
            if (!(root instanceof ObjectNode rootObject)) {
                return truncateForProvider(trajectoryContent);
            }

            ObjectNode compact = objectMapper.createObjectNode();
            compact.put("mode", "ultra-compact");

            JsonNode framesNode = rootObject.path("frames");
            if (framesNode instanceof ArrayNode frames && !frames.isEmpty()) {
                compact.put("frameCount", frames.size());

                JsonNode first = frames.get(0);
                JsonNode last = frames.get(frames.size() - 1);

                ObjectNode firstPoint = objectMapper.createObjectNode();
                firstPoint.put("t", extractFrameTime(first, 0));
                firstPoint.put("alt", extractAltitude(first));
                firstPoint.put("speed", extractSpeed(first));
                compact.set("firstPoint", firstPoint);

                ObjectNode lastPoint = objectMapper.createObjectNode();
                lastPoint.put("t", extractFrameTime(last, frames.size() - 1));
                lastPoint.put("alt", extractAltitude(last));
                lastPoint.put("speed", extractSpeed(last));
                compact.set("lastPoint", lastPoint);

                double minAlt = Double.POSITIVE_INFINITY;
                double maxAlt = Double.NEGATIVE_INFINITY;
                double minSpeed = Double.POSITIVE_INFINITY;
                double maxSpeed = Double.NEGATIVE_INFINITY;

                int stride = Math.max(1, frames.size() / 300);
                for (int i = 0; i < frames.size(); i += stride) {
                    JsonNode frame = frames.get(i);
                    double alt = extractAltitude(frame);
                    if (!Double.isNaN(alt)) {
                        minAlt = Math.min(minAlt, alt);
                        maxAlt = Math.max(maxAlt, alt);
                    }
                    double speed = extractSpeed(frame);
                    if (!Double.isNaN(speed)) {
                        minSpeed = Math.min(minSpeed, speed);
                        maxSpeed = Math.max(maxSpeed, speed);
                    }
                }

                ObjectNode stats = objectMapper.createObjectNode();
                if (minAlt != Double.POSITIVE_INFINITY) {
                    stats.put("minAlt", minAlt);
                    stats.put("maxAlt", maxAlt);
                }
                if (minSpeed != Double.POSITIVE_INFINITY) {
                    stats.put("minSpeed", minSpeed);
                    stats.put("maxSpeed", maxSpeed);
                }
                compact.set("stats", stats);
                compact.set("insights", buildFrameInsights(frames));
            }

            JsonNode eventsNode = rootObject.path("events");
            if (eventsNode instanceof ArrayNode events) {
                compact.put("eventCount", events.size());
                compact.set("events", compactEvents(events));
                compact.set("eventSummary", buildEventSummary(events));
            }

            for (String fieldName : List.of("taskId", "status", "startedAt", "finishedAt", "duration")) {
                if (rootObject.has(fieldName)) {
                    compact.set(fieldName, rootObject.get(fieldName));
                }
            }

            return objectMapper.writeValueAsString(compact);
        } catch (Exception ex) {
            return truncateForProvider(trajectoryContent);
        }
    }

    private ArrayNode compactEvents(ArrayNode events) {
        ArrayNode result = objectMapper.createArrayNode();
        int limit = Math.min(MAX_AI_EVENTS, events.size());
        for (int i = 0; i < limit; i++) {
            JsonNode event = events.get(i);
            ObjectNode compactEvent = objectMapper.createObjectNode();
            copyIfPresent(event, compactEvent, "t");
            copyIfPresent(event, compactEvent, "type");
            copyIfPresent(event, compactEvent, "code");
            if (event.has("message") && event.get("message").isTextual()) {
                String message = event.get("message").asText();
                compactEvent.put("message", message.length() > 180 ? message.substring(0, 180) + "..." : message);
            }
            result.add(compactEvent);
        }
        return result;
    }

    private ObjectNode buildFrameInsights(ArrayNode frames) {
        ObjectNode insights = objectMapper.createObjectNode();
        if (frames.size() < 2) {
            insights.put("dataQuality", "too_few_frames");
            return insights;
        }

        double firstAlt = extractAltitude(frames.get(0));
        double lastAlt = extractAltitude(frames.get(frames.size() - 1));
        double firstSpeed = extractSpeed(frames.get(0));
        double lastSpeed = extractSpeed(frames.get(frames.size() - 1));

        if (!Double.isNaN(firstAlt) && !Double.isNaN(lastAlt)) {
            insights.put("altitudeTrend", classifyTrend(lastAlt - firstAlt, 40.0));
        }
        if (!Double.isNaN(firstSpeed) && !Double.isNaN(lastSpeed)) {
            insights.put("speedTrend", classifyTrend(lastSpeed - firstSpeed, 8.0));
        }

        double maxAltJump = 0.0;
        double maxSpeedJump = 0.0;
        int altitudeDirectionChanges = 0;
        int previousAltDirection = 0;

        int stride = Math.max(1, frames.size() / 150);
        JsonNode prev = null;
        for (int i = 0; i < frames.size(); i += stride) {
            JsonNode current = frames.get(i);
            if (prev != null) {
                double prevAlt = extractAltitude(prev);
                double curAlt = extractAltitude(current);
                if (!Double.isNaN(prevAlt) && !Double.isNaN(curAlt)) {
                    double delta = curAlt - prevAlt;
                    maxAltJump = Math.max(maxAltJump, Math.abs(delta));
                    int direction = delta > 0 ? 1 : (delta < 0 ? -1 : 0);
                    if (direction != 0 && previousAltDirection != 0 && direction != previousAltDirection) {
                        altitudeDirectionChanges++;
                    }
                    if (direction != 0) {
                        previousAltDirection = direction;
                    }
                }

                double prevSpeed = extractSpeed(prev);
                double curSpeed = extractSpeed(current);
                if (!Double.isNaN(prevSpeed) && !Double.isNaN(curSpeed)) {
                    maxSpeedJump = Math.max(maxSpeedJump, Math.abs(curSpeed - prevSpeed));
                }
            }
            prev = current;
        }

        insights.put("maxAltitudeStep", maxAltJump);
        insights.put("maxSpeedStep", maxSpeedJump);
        insights.put("altitudeDirectionChanges", altitudeDirectionChanges);
        insights.put("possibleOscillation", altitudeDirectionChanges >= 6);
        insights.put("sharpAltitudeChangeDetected", maxAltJump >= SHARP_ALTITUDE_DELTA);
        insights.put("sharpSpeedChangeDetected", maxSpeedJump >= SHARP_SPEED_DELTA);

        return insights;
    }

    private ObjectNode buildEventSummary(ArrayNode events) {
        ObjectNode summary = objectMapper.createObjectNode();
        if (events.isEmpty()) {
            summary.put("hasEvents", false);
            return summary;
        }

        summary.put("hasEvents", true);
        Map<String, Integer> typeCounts = new HashMap<>();
        int inspected = Math.min(events.size(), MAX_AI_EVENTS);
        for (int i = 0; i < inspected; i++) {
            JsonNode event = events.get(i);
            String type = event.path("type").asText("unknown");
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }

        ArrayNode topTypes = objectMapper.createArrayNode();
        typeCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(entry -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("type", entry.getKey());
                    item.put("count", entry.getValue());
                    topTypes.add(item);
                });

        summary.set("topTypes", topTypes);
        return summary;
    }

    private String classifyTrend(double delta, double epsilon) {
        if (delta > epsilon) {
            return "increasing";
        }
        if (delta < -epsilon) {
            return "decreasing";
        }
        return "stable";
    }

    private String truncateForProvider(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_AI_TRAJECTORY_CHARS) {
            return content;
        }
        return content.substring(0, MAX_AI_TRAJECTORY_CHARS) + "\n... [truncated for provider limit]";
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String fieldName) {
        if (source.has(fieldName)) {
            target.set(fieldName, source.get(fieldName));
        }
    }

    private double extractFrameTime(JsonNode frame, int index) {
        double direct = asDouble(frame.path("t"));
        if (!Double.isNaN(direct)) {
            return direct;
        }

        double timestamp = asDouble(frame.path("timestamp"));
        if (!Double.isNaN(timestamp)) {
            return timestamp;
        }

        return index;
    }

    private boolean isSignificantChange(JsonNode prev, JsonNode next) {
        double prevAlt = extractAltitude(prev);
        double nextAlt = extractAltitude(next);
        if (!Double.isNaN(prevAlt) && !Double.isNaN(nextAlt) && Math.abs(nextAlt - prevAlt) >= SIGNIFICANT_ALTITUDE_DELTA) {
            return true;
        }

        double prevSpeed = extractSpeed(prev);
        double nextSpeed = extractSpeed(next);
        return !Double.isNaN(prevSpeed) && !Double.isNaN(nextSpeed) && Math.abs(nextSpeed - prevSpeed) >= SIGNIFICANT_SPEED_DELTA;
    }

    private double extractAltitude(JsonNode frame) {
        double direct = asDouble(frame.path("alt"));
        if (!Double.isNaN(direct)) {
            return direct;
        }
        return asDouble(frame.path("pos").path("alt"));
    }

    private double extractSpeed(JsonNode frame) {
        double direct = asDouble(frame.path("speed"));
        if (!Double.isNaN(direct)) {
            return direct;
        }
        return asDouble(frame.path("vel"));
    }

    private List<Double> extractEventTimes(JsonNode eventsNode) {
        if (!(eventsNode instanceof ArrayNode events) || events.isEmpty()) {
            return List.of();
        }

        return events.findValues("t").stream()
                .map(this::asDouble)
                .filter(value -> !Double.isNaN(value))
                .toList();
    }

    private boolean isNearEvent(double time, List<Double> eventTimes) {
        for (Double eventTime : eventTimes) {
            if (Math.abs(time - eventTime) <= EVENT_WINDOW_SECONDS) {
                return true;
            }
        }
        return false;
    }

    private double asDouble(JsonNode node) {
        if (node == null) {
            return Double.NaN;
        }
        return node.isNumber() ? node.asDouble() : Double.NaN;
    }
}





