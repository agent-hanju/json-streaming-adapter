package me.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.hanju.adapter.exception.JsonStreamingException;

class JsonStreamingAdapterTest {

  private static final String SIMPLE_SCHEMA = """
      {
        "type": "object",
        "properties": {
          "content": { "type": "string" }
        }
      }
      """;

  private static final String REQUIRED_SCHEMA = """
      {
        "type": "object",
        "properties": {
          "content": { "type": "string" }
        },
        "required": ["content"]
      }
      """;

  @Nested
  @DisplayName("문자열 스트리밍")
  class StringStreaming {

    @Test
    @DisplayName("토큰 단위로 문자열 증분 반환")
    void returnsIncrementalDeltas() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas1 = adapter.feedToken("{\"content\":\"Hel");
      assertThat(deltas1).hasSize(1);
      assertThat(deltas1.get(0).get("content")).isEqualTo("Hel");

      List<Map<String, Object>> deltas2 = adapter.feedToken("lo Wor");
      assertThat(deltas2).hasSize(1);
      assertThat(deltas2.get(0).get("content")).isEqualTo("lo Wor");

      List<Map<String, Object>> deltas3 = adapter.feedToken("ld\"}");
      assertThat(deltas3).hasSize(1);
      assertThat(deltas3.get(0).get("content")).isEqualTo("ld");
    }

    @Test
    @DisplayName("완전한 문자열은 단일 delta 반환")
    void completeStringReturnsSingleDelta() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"content\":\"Hello\"}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("content")).isEqualTo("Hello");
    }

    @Test
    @DisplayName("빈 문자열도 delta 반환")
    void emptyStringReturnsDelta() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"content\":\"\"}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("content")).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("이스케이프 시퀀스")
  class EscapeSequences {

    @Test
    @DisplayName("\\n, \\t 등 이스케이프 문자 처리")
    void handlesEscapeCharacters() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"content\":\"Line1\\nLine2\\tTabbed\"}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("content")).isEqualTo("Line1\nLine2\tTabbed");
    }

    @Test
    @DisplayName("이스케이프된 따옴표 처리")
    void handlesEscapedQuotes() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"content\":\"Say \\\"Hello\\\"\"}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("content")).isEqualTo("Say \"Hello\"");
    }

    @Test
    @DisplayName("이스케이프된 백슬래시 처리")
    void handlesEscapedBackslash() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"content\":\"C:\\\\Users\\\\test\"}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("content")).isEqualTo("C:\\Users\\test");
    }

    @Test
    @DisplayName("토큰 경계에서 이스케이프 시퀀스 분리 처리")
    void handlesEscapeSplitAcrossTokens() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas1 = adapter.feedToken("{\"content\":\"Hello\\");
      assertThat(deltas1).hasSize(1);
      assertThat(deltas1.get(0).get("content")).isEqualTo("Hello");

      List<Map<String, Object>> deltas2 = adapter.feedToken("nWorld\"}");
      assertThat(deltas2).hasSize(1);
      assertThat(deltas2.get(0).get("content")).isEqualTo("\nWorld");
    }
  }

  @Nested
  @DisplayName("중첩 객체")
  class NestedObjects {

    private static final String NESTED_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "age": { "type": "integer" }
              }
            }
          }
        }
        """;

    @Test
    @DisplayName("중첩 객체 내 문자열 스트리밍")
    void streamsNestedStringField() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(NESTED_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"user\":{\"name\":\"Alice");

      assertThat(deltas).hasSize(1);
      @SuppressWarnings("unchecked")
      Map<String, Object> user = (Map<String, Object>) deltas.get(0).get("user");
      assertThat(user.get("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("중첩 객체의 여러 필드")
    void handlesMultipleNestedFields() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(NESTED_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"user\":{\"name\":\"Alice\",\"age\":30}}");

      assertThat(deltas).hasSize(2);

      @SuppressWarnings("unchecked")
      Map<String, Object> nameUser = (Map<String, Object>) deltas.get(0).get("user");
      assertThat(nameUser.get("name")).isEqualTo("Alice");

      @SuppressWarnings("unchecked")
      Map<String, Object> ageUser = (Map<String, Object>) deltas.get(1).get("user");
      assertThat(ageUser.get("age")).isEqualTo(30);
    }

    @Test
    @DisplayName("깊은 중첩 객체 (3단계)")
    void handlesDeepNesting() {
      String deepSchema = """
          {
            "type": "object",
            "properties": {
              "level1": {
                "type": "object",
                "properties": {
                  "level2": {
                    "type": "object",
                    "properties": {
                      "value": { "type": "string" }
                    }
                  }
                }
              }
            }
          }
          """;

      JsonStreamingAdapter adapter = new JsonStreamingAdapter(deepSchema);

      List<Map<String, Object>> allDeltas = new ArrayList<>();
      allDeltas.addAll(adapter.feedToken("{\"level1\":{\"level2\":{\"value\":\"Hel"));
      allDeltas.addAll(adapter.feedToken("lo\"}}}"));

      assertThat(allDeltas).hasSize(2);

      @SuppressWarnings("unchecked")
      Map<String, Object> l1 = (Map<String, Object>) allDeltas.get(0).get("level1");
      @SuppressWarnings("unchecked")
      Map<String, Object> l2 = (Map<String, Object>) l1.get("level2");
      assertThat(l2.get("value")).isEqualTo("Hel");
    }
  }

  @Nested
  @DisplayName("숫자 및 리터럴 값")
  class NumbersAndLiterals {

    @Test
    @DisplayName("정수 값")
    void handlesInteger() {
      String schema = """
          {"type":"object","properties":{"count":{"type":"integer"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"count\":42}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("count")).isEqualTo(42);
    }

    @Test
    @DisplayName("음수")
    void handlesNegativeNumber() {
      String schema = """
          {"type":"object","properties":{"count":{"type":"integer"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"count\":-123}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("count")).isEqualTo(-123);
    }

    @Test
    @DisplayName("소수점 숫자")
    void handlesFloat() {
      String schema = """
          {"type":"object","properties":{"value":{"type":"number"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"value\":3.14}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("value")).isEqualTo(3.14);
    }

    @Test
    @DisplayName("boolean true")
    void handlesBooleanTrue() {
      String schema = """
          {"type":"object","properties":{"active":{"type":"boolean"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"active\":true}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("active")).isEqualTo(true);
    }

    @Test
    @DisplayName("boolean false")
    void handlesBooleanFalse() {
      String schema = """
          {"type":"object","properties":{"active":{"type":"boolean"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"active\":false}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("active")).isEqualTo(false);
    }

    @Test
    @DisplayName("null 값")
    void handlesNull() {
      String schema = """
          {"type":"object","properties":{"value":{"type":"null"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"value\":null}");

      assertThat(deltas).hasSize(1);
      assertThat(deltas.get(0).get("value")).isNull();
    }
  }

  @Nested
  @DisplayName("스키마 검증")
  class SchemaValidation {

    @Test
    @DisplayName("유효한 객체는 검증 통과")
    void validObjectPasses() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(REQUIRED_SCHEMA);

      adapter.feedToken("{\"content\":\"Hello\"}");
      adapter.flush(); // 예외 없이 통과
    }

    @Test
    @DisplayName("필수 필드 누락 시 예외 발생")
    void missingRequiredFieldThrows() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(REQUIRED_SCHEMA);

      adapter.feedToken("{\"wrong\":\"value\"}");

      assertThatThrownBy(() -> adapter.flush())
          .isInstanceOf(JsonStreamingException.class);
    }

    @Test
    @DisplayName("불완전한 JSON은 검증하지 않음")
    void incompleteJsonSkipsValidation() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      adapter.feedToken("{\"content\":\"Hello");

      List<Map<String, Object>> result = adapter.flush();
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("다중 필드")
  class MultipleFields {

    @Test
    @DisplayName("여러 필드가 각각 delta로 반환")
    void eachFieldReturnsSeparateDelta() {
      String schema = """
          {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"name\":\"Alice\",\"age\":30}");

      assertThat(deltas).hasSize(2);
      assertThat(deltas.get(0).get("name")).isEqualTo("Alice");
      assertThat(deltas.get(1).get("age")).isEqualTo(30);
    }

    @Test
    @DisplayName("토큰 경계를 넘는 다중 필드")
    void fieldsAcrossTokenBoundaries() {
      String schema = """
          {"type":"object","properties":{"greeting":{"type":"string"},"count":{"type":"integer"}}}
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

      List<Map<String, Object>> allDeltas = new ArrayList<>();
      allDeltas.addAll(adapter.feedToken("{\"greeting\":\"Hel"));
      allDeltas.addAll(adapter.feedToken("lo\",\"count\":"));
      allDeltas.addAll(adapter.feedToken("42}"));

      assertThat(allDeltas).hasSize(3);
      assertThat(allDeltas.get(0).get("greeting")).isEqualTo("Hel");
      assertThat(allDeltas.get(1).get("greeting")).isEqualTo("lo");
      assertThat(allDeltas.get(2).get("count")).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("빈 객체는 delta 없음")
    void emptyObjectReturnsNoDeltas() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken("{}");

      assertThat(deltas).isEmpty();
    }

    @Test
    @DisplayName("null 토큰은 빈 리스트 반환")
    void nullTokenReturnsEmpty() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(null);

      assertThat(deltas).isEmpty();
    }

    @Test
    @DisplayName("빈 토큰은 빈 리스트 반환")
    void emptyTokenReturnsEmpty() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(SIMPLE_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken("");

      assertThat(deltas).isEmpty();
    }

    @Test
    @DisplayName("null 스키마로 생성 시 예외")
    void nullSchemaThrows() {
      assertThatThrownBy(() -> new JsonStreamingAdapter((String) null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 스키마로 생성 시 예외")
    void blankSchemaThrows() {
      assertThatThrownBy(() -> new JsonStreamingAdapter("   "))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("배열 처리")
  class Arrays {

    private static final String ARRAY_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "items": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "index": { "type": "integer" },
                  "value": { "type": "string" }
                }
              }
            }
          }
        }
        """;

    @Test
    @DisplayName("단순 배열 요소")
    void simpleArrayElements() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(ARRAY_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"items\":[{\"index\":0,\"value\":\"Hello\"}]}");

      assertThat(deltas).hasSize(2);

      // index delta
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items0 = (List<Map<String, Object>>) deltas.get(0).get("items");
      assertThat(items0.get(0).get("index")).isEqualTo(0);

      // value delta
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items1 = (List<Map<String, Object>>) deltas.get(1).get("items");
      assertThat(items1.get(0).get("value")).isEqualTo("Hello");
    }

    @Test
    @DisplayName("배열 요소 내 문자열 스트리밍")
    void arrayElementStringStreaming() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(ARRAY_SCHEMA);

      List<Map<String, Object>> deltas1 = adapter.feedToken(
          "{\"items\":[{\"index\":0,\"value\":\"Hel");

      assertThat(deltas1).hasSize(2);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items1 = (List<Map<String, Object>>) deltas1.get(1).get("items");
      assertThat(items1.get(0).get("value")).isEqualTo("Hel");

      List<Map<String, Object>> deltas2 = adapter.feedToken("lo\"}]}");
      assertThat(deltas2).hasSize(1);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items2 = (List<Map<String, Object>>) deltas2.get(0).get("items");
      assertThat(items2.get(0).get("value")).isEqualTo("lo");
    }

    @Test
    @DisplayName("index 자동 생성")
    void autoIndexGeneration() {
      String schemaNoIndex = """
          {
            "type": "object",
            "properties": {
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" }
                  }
                }
              }
            }
          }
          """;
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(schemaNoIndex);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"items\":[{\"value\":\"First\"},{\"value\":\"Second\"}]}");

      // 각 요소마다 value delta
      assertThat(deltas).hasSize(2);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> first = (List<Map<String, Object>>) deltas.get(0).get("items");
      assertThat(first.get(0).get("index")).isEqualTo(0);
      assertThat(first.get(0).get("value")).isEqualTo("First");

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> second = (List<Map<String, Object>>) deltas.get(1).get("items");
      assertThat(second.get(0).get("index")).isEqualTo(1);
      assertThat(second.get(0).get("value")).isEqualTo("Second");
    }

    @Test
    @DisplayName("여러 배열 요소")
    void multipleArrayElements() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(ARRAY_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken(
          "{\"items\":[{\"index\":0,\"value\":\"A\"},{\"index\":1,\"value\":\"B\"}]}");

      assertThat(deltas).hasSize(4); // index, value for each element

      // 첫 번째 요소
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> idx0 = (List<Map<String, Object>>) deltas.get(0).get("items");
      assertThat(idx0.get(0).get("index")).isEqualTo(0);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> val0 = (List<Map<String, Object>>) deltas.get(1).get("items");
      assertThat(val0.get(0).get("value")).isEqualTo("A");

      // 두 번째 요소
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> idx1 = (List<Map<String, Object>>) deltas.get(2).get("items");
      assertThat(idx1.get(0).get("index")).isEqualTo(1);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> val1 = (List<Map<String, Object>>) deltas.get(3).get("items");
      assertThat(val1.get(0).get("value")).isEqualTo("B");
    }

    @Test
    @DisplayName("빈 배열")
    void emptyArray() {
      JsonStreamingAdapter adapter = new JsonStreamingAdapter(ARRAY_SCHEMA);

      List<Map<String, Object>> deltas = adapter.feedToken("{\"items\":[]}");

      assertThat(deltas).isEmpty();
    }
  }
}
