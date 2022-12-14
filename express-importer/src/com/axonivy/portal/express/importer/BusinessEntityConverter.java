package com.axonivy.portal.express.importer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ch.ivyteam.ivy.environment.Ivy;

public class BusinessEntityConverter {

  public static ObjectMapper objectMapper;

  public BusinessEntityConverter() {}

  public static String entityToJsonValue(Object entity) {
    try {
      return getObjectMapper().writeValueAsString(entity);
    } catch (JsonProcessingException e) {
      Ivy.log().error("Can't write json value", e);
      throw new ExpressImportException(e);
    }
  }

  public static <T> T jsonValueToEntity(String jsonValue, Class<T> classType) {
    try {
      return getObjectMapper().readValue(jsonValue, classType);
    } catch (IOException e) {
      Ivy.log().error("Can't read json value", e);
      throw new ExpressImportException(e);
    }
  }

  public static <T> List<T> jsonValueToEntities(String jsonValue, Class<T> classType) {
    if (StringUtils.isBlank(jsonValue)) {
      return new ArrayList<>();
    }
    try {
      return getObjectMapper().readValue(jsonValue,
          getObjectMapper().getTypeFactory().constructCollectionType(List.class, classType));
    } catch (IOException e) {
      Ivy.log().error("Can't read json value", e);
      throw new ExpressImportException(e);
    }
  }

  public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
    return getObjectMapper().convertValue(fromValue, toValueType);
  }

  public static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = JsonMapper.builder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build();
    }
    return objectMapper;
  }
}
