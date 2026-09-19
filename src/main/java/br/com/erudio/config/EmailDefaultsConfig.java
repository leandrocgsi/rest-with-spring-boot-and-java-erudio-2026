package br.com.erudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Subject and message used when the request does not bring its own ({@code email.subject} / {@code email.message}).
 */
@Configuration
@ConfigurationProperties(prefix = "email")
public class EmailDefaultsConfig {

    private String subject;
    private String message;

    public EmailDefaultsConfig() {}

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
