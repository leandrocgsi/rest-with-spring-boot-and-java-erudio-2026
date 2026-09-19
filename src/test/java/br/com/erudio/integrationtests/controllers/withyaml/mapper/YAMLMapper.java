package br.com.erudio.integrationtests.controllers.withyaml.mapper;

import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;

public class YAMLMapper implements ObjectMapper {

    private tools.jackson.dataformat.yaml.YAMLMapper mapper;

    public YAMLMapper() {
        mapper = tools.jackson.dataformat.yaml.YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    @Override
    public Object deserialize(ObjectMapperDeserializationContext context) {
        var content = context.getDataToDeserialize().asString();
        Class type = (Class) context.getType();
        try {
            return mapper.readValue(content, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Error deserializing YAML content", e);
        }
    }

    @Override
    public Object serialize(ObjectMapperSerializationContext context) {
        try {
            return mapper.writeValueAsString(context.getObjectToSerialize());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Error serializing YAML content", e);
        }
    }
}
