package dev.knalis.trajectaapi.config;

import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import dev.knalis.trajectaapi.repo.PunishmentRepository;
import dev.knalis.trajectaapi.service.intrf.auth.JwtService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.Instant;

@Configuration
@RequiredArgsConstructor
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {
    
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final PunishmentRepository punishmentRepository;
    
    /**
     * Applies JWT authentication during STOMP CONNECT command processing.
     *
     * Expected native header: Authorization: Bearer <JWT_TOKEN>
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        String username = jwtService.extractUsername(token);
                        
                        if (username != null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            
                            if (jwtService.isTokenValid(token, userDetails)) {
                                if (userDetails instanceof User user
                                        && user.getId() != null
                                        && punishmentRepository.existsActivePunishment(user, PunishmentType.BAN, Instant.now())) {
                                    throw new AccessDeniedException("Banned users cannot connect to WebSocket");
                                }
                                
                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities()
                                );
                                accessor.setUser(auth);
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}


