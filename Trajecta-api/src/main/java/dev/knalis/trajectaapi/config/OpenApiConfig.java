package dev.knalis.trajectaapi.config;

import dev.knalis.trajectaapi.dto.ws.SocketEvent;
import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.payload.NotificationPayload;
import dev.knalis.trajectaapi.dto.ws.payload.TaskUpdateStatusPayload;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    
    @Bean
    public OpenAPI trajectaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trajecta API")
                        .version("v1")
                        .description("""
                                REST and WebSocket API for telemetry task management.

                                This specification documents:
                                - authentication and user management
                                - telemetry task lifecycle and file downloads
                                - notification workflows
                                - internal worker integration
                                - WebSocket event contract for real-time updates
                                """)
                        .contact(new Contact()
                                .name("knalis")
                                .email("vitallot21@gmail.com"))
                        .license(new License()
                                .name("Private API")))
                .servers(List.of(
                        new Server().url("/").description("Default server")
                ))
                .tags(List.of(
                        new Tag().name("Authentication").description("Registration and login endpoints."),
                        new Tag().name("Tasks").description("Telemetry task creation, status retrieval, and file downloads."),
                        new Tag().name("Notifications").description("Notification retrieval and read/delete operations."),
                        new Tag().name("Users").description("Current user profile endpoints."),
                        new Tag().name("Admin Users").description("Administrative user management endpoints."),
                        new Tag().name("Internal Worker").description("Internal endpoints used by telemetry workers."),
                        new Tag().name("WebSocket").description("WebSocket/STOMP connection and event format contract.")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .externalDocs(new ExternalDocumentation()
                        .description("WebSocket quick reference")
                        .url("/swagger-ui/index.html"))
                .extensions(Map.of(
                        "x-websocket",
                        Map.of(
                                "endpoint", "/ws",
                                "protocol", "STOMP over WebSocket",
                                "connectHeaders", Map.of("Authorization", "Bearer <JWT_TOKEN>"),
                                "brokerPrefixes", List.of("/topic", "/queue"),
                                "applicationPrefix", "/app",
                                "userEventDestination", "/user/queue/events",
                                "eventEnvelopeSchema", "SocketEvent",
                                "eventTypes", List.of("NEW_NOTIFICATION", "TASK_STATUS_UPDATE")
                        )
                ));
    }

    @Bean
    public OpenApiCustomizer websocketContractCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            var schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                schemas = new java.util.LinkedHashMap<>();
                openApi.getComponents().setSchemas(schemas);
            }
            schemas.putAll(ModelConverters.getInstance().readAll(SocketEvent.class));
            schemas.putAll(ModelConverters.getInstance().readAll(NotificationPayload.class));
            schemas.putAll(ModelConverters.getInstance().readAll(TaskUpdateStatusPayload.class));
            schemas.putAll(ModelConverters.getInstance().readAll(WsEventType.class));

            Operation wsContractOperation = new Operation()
                    .tags(List.of("WebSocket"))
                    .summary("WebSocket contract (documentation only)")
                    .description("""
                            This path exists only for API documentation purposes and is not a runtime HTTP endpoint.

                            WebSocket details:
                            - STOMP endpoint: /ws
                            - CONNECT header: Authorization: Bearer <JWT_TOKEN>
                            - User destination: /user/queue/events
                            - Event envelope schema: SocketEvent
                            - Event payload schemas: NotificationPayload, TaskUpdateStatusPayload
                            """)
                    .responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                            .description("WebSocket contract in JSON")
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/SocketEvent")))))
                    );
            wsContractOperation.addExtension("x-doc-only", true);
            wsContractOperation.addExtension("x-websocket-endpoint", "/ws");

            openApi.path("/api/v1/ws/contract", new PathItem().get(wsContractOperation));
        };
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/api/internal/**")
                .build();
    }
}


