package br.com.erudio.config;

import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson 3 changed a few defaults that show up in the API payloads. JSON and XML get Jackson 2 defaults from
 * {@code spring.jackson.use-jackson2-defaults}; this class covers the rest so that the responses stay exactly
 * as they were before the upgrade.
 */
@Configuration
public class JacksonConfig {

    /**
     * Jackson 3 writes the properties inherited from {@link RepresentationModel} (the HATEOAS "links") before the
     * ones declared by the DTO, Jackson 2 wrote them last. Collections ({@link CollectionModel}, e.g. the paged
     * result) already come out with the links first in both, so they are left alone.
     * Registered for JSON and XML by Spring Boot and for YAML below.
     */
    @Bean
    JacksonModule hateoasLinksLastModule() {
        return new SimpleModule("hateoasLinksLast").setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> orderProperties(
                    SerializationConfig config,
                    BeanDescription.Supplier beanDesc,
                    List<BeanPropertyWriter> beanProperties) {

                Class<?> beanClass = beanDesc.getBeanClass();
                if (!RepresentationModel.class.isAssignableFrom(beanClass)
                        || CollectionModel.class.isAssignableFrom(beanClass)) return beanProperties;

                List<BeanPropertyWriter> ordered = new ArrayList<>(beanProperties);
                ordered.stream()
                    .filter(property -> "links".equals(property.getName()))
                    .findFirst()
                    .ifPresent(links -> {
                        ordered.remove(links);
                        ordered.add(links);
                    });
                return ordered;
            }
        });
    }

    /**
     * Spring Boot only configures the JSON and XML mappers. The YAML converter would use plain Jackson 3
     * defaults (alphabetical properties, dates as ISO strings), so it is configured here with what it used to have:
     * declaration order, dates as epoch milliseconds and unknown properties ignored.
     */
    @Bean
    ServerHttpMessageConvertersCustomizer yamlMessageConverterCustomizer(JacksonModule hateoasLinksLastModule) {
        return builder -> builder.withYamlConverter(new JacksonYamlHttpMessageConverter(
            YAMLMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(hateoasLinksLastModule)));
    }
}
