package me.hanju.adapter;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;

class ArrayEventTest {

  @Test
  void checkArrayEvents_simpleStringArray() throws Exception {
    JsonFactory factory = new JsonFactory();
    JsonParser parser = factory.createNonBlockingByteArrayParser();
    ByteArrayFeeder feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();

    System.out.println("=== 단순 문자열 배열: {\"items\":[\"a\",\"b\",\"c\"]} ===");
    byte[] bytes = "{\"items\":[\"a\",\"b\",\"c\"]}".getBytes(StandardCharsets.UTF_8);
    feeder.feedInput(bytes, 0, bytes.length);

    JsonToken token;
    while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
      System.out.println("Token: " + token + 
          ", name: " + parser.currentName() + 
          ", text: " + safeGetText(parser, token));
    }
  }

  @Test
  void checkArrayEvents_objectArray() throws Exception {
    JsonFactory factory = new JsonFactory();
    JsonParser parser = factory.createNonBlockingByteArrayParser();
    ByteArrayFeeder feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();

    System.out.println("=== 객체 배열: {\"users\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]} ===");
    byte[] bytes = "{\"users\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]}".getBytes(StandardCharsets.UTF_8);
    feeder.feedInput(bytes, 0, bytes.length);

    JsonToken token;
    while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
      System.out.println("Token: " + token + 
          ", name: " + parser.currentName() + 
          ", text: " + safeGetText(parser, token));
    }
  }

  @Test
  void checkArrayEvents_objectWithIndex() throws Exception {
    JsonFactory factory = new JsonFactory();
    JsonParser parser = factory.createNonBlockingByteArrayParser();
    ByteArrayFeeder feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();

    System.out.println("=== index 포함 객체 배열: {\"items\":[{\"index\":0,\"value\":\"A\"},{\"index\":1,\"value\":\"B\"}]} ===");
    byte[] bytes = "{\"items\":[{\"index\":0,\"value\":\"A\"},{\"index\":1,\"value\":\"B\"}]}".getBytes(StandardCharsets.UTF_8);
    feeder.feedInput(bytes, 0, bytes.length);

    JsonToken token;
    while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
      System.out.println("Token: " + token + 
          ", name: " + parser.currentName() + 
          ", text: " + safeGetText(parser, token));
    }
  }

  @Test
  void checkArrayEvents_streamedObjectArray() throws Exception {
    JsonFactory factory = new JsonFactory();
    JsonParser parser = factory.createNonBlockingByteArrayParser();
    ByteArrayFeeder feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();

    System.out.println("=== 스트리밍 객체 배열 (토큰 분리) ===");
    
    String[] tokens = {
        "{\"items\":[{\"index\":0,\"val",
        "ue\":\"Hel",
        "lo\"},{\"index\":1,\"value\":\"World\"}]}"
    };

    for (int i = 0; i < tokens.length; i++) {
      System.out.println("\n--- Token " + (i+1) + ": " + tokens[i] + " ---");
      byte[] bytes = tokens[i].getBytes(StandardCharsets.UTF_8);
      feeder.feedInput(bytes, 0, bytes.length);

      JsonToken token;
      while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
        System.out.println("Token: " + token + 
            ", name: " + parser.currentName() + 
            ", text: " + safeGetText(parser, token));
      }
    }
  }

  private String safeGetText(JsonParser parser, JsonToken token) {
    try {
      if (token == JsonToken.VALUE_STRING || token == JsonToken.FIELD_NAME) {
        return parser.getText();
      }
      if (token == JsonToken.VALUE_NUMBER_INT) {
        return String.valueOf(parser.getIntValue());
      }
    } catch (Exception e) {
      return "<error>";
    }
    return null;
  }
}
