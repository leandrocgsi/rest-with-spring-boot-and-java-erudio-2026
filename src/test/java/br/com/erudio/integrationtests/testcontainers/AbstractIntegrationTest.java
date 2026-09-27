package br.com.erudio.integrationtests.testcontainers;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
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

        static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.11.1"))
                .withServices(LocalStackContainer.Service.S3);

        static GreenMail smtp = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

        private static void startContainers() {
            Startables.deepStart(Stream.of(mysql, localstack)).join();
        }

        private static synchronized void startSmtp() {
            if (smtp.isRunning()) return;
            smtp.start();
            smtp.setUser(SMTP_USERNAME, SMTP_PASSWORD);
            Runtime.getRuntime().addShutdownHook(new Thread(smtp::stop));
        }

        private static Map<String, String> createConnectionConfiguration() {
            return Map.ofEntries(
                    Map.entry("spring.datasource.url", mysql.getJdbcUrl()),
                    Map.entry("spring.datasource.username", mysql.getUsername()),
                    Map.entry("spring.datasource.password", mysql.getPassword()),
                    Map.entry("spring.mail.host", "localhost"),
                    Map.entry("spring.mail.port", String.valueOf(smtp.getSmtp().getPort())),
                    Map.entry("spring.mail.username", SMTP_USERNAME),
                    Map.entry("spring.mail.password", SMTP_PASSWORD),
                    Map.entry("spring.mail.properties.mail.smtp.auth", "true"),
                    Map.entry("spring.mail.properties.mail.smtp.starttls.enable", "false"),
                    Map.entry("spring.mail.properties.mail.smtp.starttls.required", "false"),
                    Map.entry("aws.s3.bucket", "erudio-files-test"),
                    Map.entry("aws.s3.region", localstack.getRegion()),
                    Map.entry("aws.s3.endpoint", localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()),
                    Map.entry("aws.s3.access-key", localstack.getAccessKey()),
                    Map.entry("aws.s3.secret-key", localstack.getSecretKey())
            );
        }

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            startContainers();
            startSmtp();
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            MapPropertySource testcontainers = new MapPropertySource("testcontainers",
                    new HashMap<String, Object>(createConnectionConfiguration()));
            environment.getPropertySources().addFirst(testcontainers);
        }
    }
}
