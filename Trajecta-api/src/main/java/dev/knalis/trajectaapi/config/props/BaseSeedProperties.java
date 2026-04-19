package dev.knalis.trajectaapi.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.seed")
public class BaseSeedProperties {
    private boolean enabled = false;
    private String username = "Owner";
    private String password = "Owner123";
    private String mail = "owner@ownermail.own";
    private boolean adminEnabled = false;
    private String adminUsername = "Admin";
    private String adminPassword = "Admin123";
    private String adminMail = "admin@adminmail.own";
}
