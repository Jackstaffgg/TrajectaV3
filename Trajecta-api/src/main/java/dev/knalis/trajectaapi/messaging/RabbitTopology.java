package dev.knalis.trajectaapi.messaging;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class RabbitTopology {

    public static final String EXCHANGE = "telemetry.exchange";

    public static final String PARSE_QUEUE = "telemetry.parse";
    public static final String PARSE_ROUTING_KEY = "telemetry.parse";

    public static final String RESULTS_QUEUE = "telemetry.results";
    public static final String RESULTS_ROUTING_KEY = "telemetry.results";
    
}

