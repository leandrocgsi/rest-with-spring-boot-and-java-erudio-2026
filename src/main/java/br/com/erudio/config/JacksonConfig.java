package br.com.erudio.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Links;
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Configuration
public class JacksonConfig {

    @Bean
    ServerHttpMessageConvertersCustomizer yamlMessageConverterCustomizer() {
        return builder -> builder.withYamlConverter(new JacksonYamlHttpMessageConverter(
            YAMLMapper.builder().addMixIn(EntityModel.class, EntityModelYamlMixin.class)));
    }

    private abstract static class EntityModelYamlMixin {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract Links getLinks();
    }
}
