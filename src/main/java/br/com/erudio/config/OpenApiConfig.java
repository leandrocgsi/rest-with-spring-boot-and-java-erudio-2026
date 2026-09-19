package br.com.erudio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customOpenAPI(){
        return new OpenAPI()
            .info(new Info()
                .title("Formação Spring Boot 2026: do Zero ao Deploy na AWS e GCP com Java, Docker e Kubernetes")
                    .version("v1")
                    .description("Formação Spring Boot 2026: do Zero ao Deploy na AWS e GCP com Java, Docker e Kubernetes")
                    .termsOfService("https://pub.erudio.com.br/meus-cursos")
                    .license(new License()
                        .name("Apache 2.0")
                        .url("https://pub.erudio.com.br/meus-cursos")
                    )
            );
    }
}
