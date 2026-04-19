package dev.knalis.trajectaapi.config.props;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.rate-limit")
public class RateLimitProperties {

    @Valid
    private Rule login = new Rule(5, 300, "rate_limit:login:");

    @Valid
    private Rule taskCreate = new Rule(10, 60, "rate_limit:task_create:");

    @Valid
    private Rule aiConclusion = new Rule(5, 60, "rate_limit:ai_conclusion:");

    @Getter
    @Setter
    public static class Rule {

        @Min(1)
        private int maxAttempts;

        @Min(1)
        private long windowSeconds;

        @NotBlank
        private String keyPrefix;

        public Rule() {
        }

        public Rule(int maxAttempts, long windowSeconds, String keyPrefix) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
            this.keyPrefix = keyPrefix;
        }
    }
}



