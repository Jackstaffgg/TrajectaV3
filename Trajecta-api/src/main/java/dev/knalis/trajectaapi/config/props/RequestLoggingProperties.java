package dev.knalis.trajectaapi.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "application.logging.http")
@Data
public class RequestLoggingProperties {

    private long slowThresholdMs = 1000;
    private SuccessLogLevel successLogLevel = SuccessLogLevel.DEBUG;
    private List<String> excludePathPrefixes = List.of(
            "/actuator",
            "/api-docs",
            "/swagger-ui",
            "/ws"
    );

    public enum SuccessLogLevel {
        DEBUG,
        INFO,
        OFF
    }
}

