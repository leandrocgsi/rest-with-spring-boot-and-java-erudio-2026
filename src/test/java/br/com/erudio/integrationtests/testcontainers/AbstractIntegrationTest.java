package br.com.erudio.integrationtests.testcontainers;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;

import java.util.Map;
import java.util.stream.Stream;

@ContextConfiguration(initializers = AbstractIntegrationTest.Initializer.class)
public class AbstractIntegrationTest {

    protected static GreenMail greenMail() {
        return Initializer.smtp;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static final String SMTP_USERNAME = "sender@erudio.test";
        static final String SMTP_PASSWORD = "secret";

        static MySQLContainer mysql = new MySQLContainer("mysql:9.1.0").withConfigurationOverride("mysql-default-conf");

        static GreenMail smtp = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

        private static void startContainers() {
            Startables.deepStart(Stream.of(mysql)).join();
        }

        private static synchronized void startSmtp() {
            if (smtp.isRunning()) return;
            smtp.start();
            smtp.setUser(SMTP_USERNAME, SMTP_PASSWORD);
            Runtime.getRuntime().addShutdownHook(new Thread(smtp::stop));
        }

        private static Map<String, String> createConnectionConfiguration() {
            return Map.of(
                    "spring.datasource.url", mysql.getJdbcUrl(),
                    "spring.datasource.username", mysql.getUsername(),
                    "spring.datasource.password", mysql.getPassword(),
                    "spring.mail.host", "localhost",
                    "spring.mail.port", String.valueOf(smtp.getSmtp().getPort()),
                    "spring.mail.username", SMTP_USERNAME,
                    "spring.mail.password", SMTP_PASSWORD,
                    "spring.mail.properties.mail.smtp.auth", "true",
                    "spring.mail.properties.mail.smtp.starttls.enable", "false",
                    "spring.mail.properties.mail.smtp.starttls.required", "false"
            );
        }

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            startContainers();
            startSmtp();
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            MapPropertySource testcontainers = new MapPropertySource("testcontainers",
                    (Map) createConnectionConfiguration());
            environment.getPropertySources().addFirst(testcontainers);
        }
    }
}
