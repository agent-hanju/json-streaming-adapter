package me.hanju.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import me.hanju.adapter.exception.JsonStreamingException;
import me.hanju.adapter.internal.ArrayContext;
import me.hanju.adapter.internal.DeltaBuilder;
import me.hanju.adapter.internal.StringContext;

/**
 * JSON Schema 기반 스트리밍 어댑터 (Jackson 하이브리드 방식)
 * <p>
 * Jackson NonBlockingByteArrayParser를 기반으로 하되,
 * 문자열 필드는 토큰 단위로 증분 스트리밍합니다.
 * </p>
 */
public class JsonStreamingAdapter {

  private final JsonSchema schema;
  private final JsonParser parser;
  private final ByteArrayFeeder feeder;
  private final ObjectMapper objectMapper;

  // 버퍼
  private final StringBuilder rawBuffer = new StringBuilder();

  // 분리된 핸들러들
  private final StringContext stringContext;
  private final ArrayContext arrayContext;
  private final DeltaBuilder deltaBuilder;

  // 상태 추적
  private final Deque<String> fieldPath = new ArrayDeque<>();
  private String currentFieldName;
  private boolean awaitingValue = false;

  // 누적 객체 (스키마 검증용)
  private final Deque<Map<String, Object>> objectStack = new ArrayDeque<>();
  private Map<String, Object> rootObject;

  public JsonStreamingAdapter(String schemaJson) {
    this(createSchema(schemaJson));
  }

  public JsonStreamingAdapter(JsonSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    this.schema = schema;
    this.objectMapper = new ObjectMapper();
    this.stringContext = new StringContext(rawBuffer);
    this.arrayContext = new ArrayContext();
    this.deltaBuilder = new DeltaBuilder();

    try {
      JsonFactory factory = new JsonFactory();
      this.parser = factory.createNonBlockingByteArrayParser();
      this.feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
    } catch (IOException e) {
      throw new JsonStreamingException("Failed to initialize JSON parser", e);
    }
  }

  private static JsonSchema createSchema(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) {
      throw new IllegalArgumentException("Schema JSON cannot be null or blank");
    }
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    return factory.getSchema(schemaJson);
  }

  public List<Map<String, Object>> feedToken(String token) {
    if (token == null || token.isEmpty()) {
      return Collections.emptyList();
    }

    deltaBuilder.clear();
    int tokenStartPos = rawBuffer.length();
    rawBuffer.append(token);

    // 문자열 스트리밍 중이면 증분 추출
    if (stringContext.isInStringValue()) {
      emitStringIncrement(tokenStartPos);
    }

    // Jackson 파싱
    try {
      byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
      feeder.feedInput(bytes, 0, bytes.length);
      processTokens();
    } catch (IOException e) {
      throw new JsonStreamingException("Failed to feed token", e);
    }

    return deltaBuilder.getDeltas();
  }

  public List<Map<String, Object>> flush() {
    try {
      feeder.endOfInput();
      processTokens();
    } catch (IOException e) {
      // ignore
    }

    // 루트 객체가 완성되었으면 스키마 검증
    if (rootObject != null && objectStack.isEmpty()) {
      validateSchema(rootObject);
    }

    return Collections.emptyList();
  }

  private void processTokens() throws IOException {
    JsonToken token;
    while ((token = parser.nextToken()) != null && token != JsonToken.NOT_AVAILABLE) {
      processJsonToken(token);
    }

    // NOT_AVAILABLE이고 값 대기 중이면 문자열 시작 체크
    if (awaitingValue && !stringContext.isInStringValue()) {
      int startPos = stringContext.checkForStringStart();
      if (startPos >= 0) {
        emitStringIncrement(startPos);
      }
    }
  }

  private void processJsonToken(JsonToken token) throws IOException {
    switch (token) {
      case START_OBJECT -> handleStartObject();
      case END_OBJECT -> handleEndObject();
      case FIELD_NAME -> {
        currentFieldName = parser.currentName();
        awaitingValue = true;
      }
      case VALUE_STRING -> {
        handleStringComplete(parser.getText());
        awaitingValue = false;
      }
      case VALUE_NUMBER_INT -> {
        int intVal = parser.getIntValue();
        if (arrayContext.isIndexField(currentFieldName)) {
          arrayContext.setIndex(intVal);
        }
        emitDelta(currentFieldName, intVal);
        setValueInCurrentObject(currentFieldName, intVal);
        awaitingValue = false;
      }
      case VALUE_NUMBER_FLOAT -> {
        double doubleVal = parser.getDoubleValue();
        emitDelta(currentFieldName, doubleVal);
        setValueInCurrentObject(currentFieldName, doubleVal);
        awaitingValue = false;
      }
      case VALUE_TRUE -> {
        emitDelta(currentFieldName, true);
        setValueInCurrentObject(currentFieldName, true);
        awaitingValue = false;
      }
      case VALUE_FALSE -> {
        emitDelta(currentFieldName, false);
        setValueInCurrentObject(currentFieldName, false);
        awaitingValue = false;
      }
      case VALUE_NULL -> {
        emitDelta(currentFieldName, null);
        setValueInCurrentObject(currentFieldName, null);
        awaitingValue = false;
      }
      case START_ARRAY -> {
        arrayContext.startArray(parser.currentName());
        awaitingValue = false;
      }
      case END_ARRAY -> arrayContext.endArray();
      default -> {
      }
    }
  }

  private void handleStartObject() {
    Map<String, Object> newObj = new LinkedHashMap<>();
    if (rootObject == null) {
      rootObject = newObj;
    } else if (arrayContext.isInArray()) {
      arrayContext.startElement();
    } else {
      objectStack.push(getCurrentObject());
      fieldPath.addLast(currentFieldName);
    }
    objectStack.push(newObj);
    awaitingValue = false;
  }

  private void handleEndObject() {
    Map<String, Object> completedObj = objectStack.pop();
    if (arrayContext.isInArrayElement()) {
      arrayContext.endElement();
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> array = (List<Map<String, Object>>) rootObject.computeIfAbsent(
          arrayContext.getArrayFieldName(), k -> new ArrayList<>());
      array.add(completedObj);
    } else if (!objectStack.isEmpty()) {
      String parentField = fieldPath.pollLast();
      setValueInCurrentObject(parentField, completedObj);
    }
  }

  private void emitStringIncrement(int fromPos) {
    String increment = stringContext.extractIncrement(fromPos);
    if (increment != null) {
      emitDelta(currentFieldName, increment);
    }
  }

  private void handleStringComplete(String fullValue) {
    String remaining = stringContext.completeString(fullValue);
    if (remaining != null) {
      emitDelta(currentFieldName, remaining);
    }
    setValueInCurrentObject(currentFieldName, fullValue);
  }

  private void emitDelta(String field, Object value) {
    if (arrayContext.isInArrayElement()) {
      deltaBuilder.addArrayElement(field, value, fieldPath,
          arrayContext.getArrayFieldName(), arrayContext.getCurrentIndex());
    } else {
      deltaBuilder.add(field, value, fieldPath);
    }
  }

  private Map<String, Object> getCurrentObject() {
    return objectStack.isEmpty() ? rootObject : objectStack.peek();
  }

  private void setValueInCurrentObject(String field, Object value) {
    Map<String, Object> current = getCurrentObject();
    if (current != null && field != null) {
      current.put(field, value);
    }
  }

  private void validateSchema(Map<String, Object> obj) {
    try {
      JsonNode node = objectMapper.valueToTree(obj);
      Set<ValidationMessage> errors = schema.validate(node);

      if (!errors.isEmpty()) {
        String message = errors.stream()
            .map(ValidationMessage::getMessage)
            .collect(java.util.stream.Collectors.joining(", "));
        throw new JsonStreamingException("JSON Schema validation failed: " + message);
      }
    } catch (JsonStreamingException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonStreamingException("Schema validation failed", e);
    }
  }

  public String getCurrentBuffer() {
    return rawBuffer.toString();
  }
}
