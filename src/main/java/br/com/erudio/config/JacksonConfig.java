package br.com.erudio.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Links;
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

@Configuration
public class JacksonConfig {

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

    @Bean
    ServerHttpMessageConvertersCustomizer yamlMessageConverterCustomizer(JacksonModule hateoasLinksLastModule) {
        return builder -> builder.withYamlConverter(new JacksonYamlHttpMessageConverter(
            YAMLMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addMixIn(EntityModel.class, EntityModelYamlMixin.class)
                .addModule(hateoasLinksLastModule)));
    }

    private abstract static class EntityModelYamlMixin {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract Links getLinks();
    }
}
