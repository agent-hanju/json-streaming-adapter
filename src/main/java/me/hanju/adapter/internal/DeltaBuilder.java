package me.hanju.adapter.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delta 객체 생성 및 수집을 담당합니다.
 * 경로 기반 delta 구성과 배열 래핑을 처리합니다.
 */
public class DeltaBuilder {

  private final List<Map<String, Object>> deltas = new ArrayList<>();

  public List<Map<String, Object>> getDeltas() {
    return new ArrayList<>(deltas);
  }

  public void clear() {
    deltas.clear();
  }

  /**
   * 일반 필드의 delta를 추가합니다.
   */
  public void add(String field, Object value, Deque<String> fieldPath) {
    if (field == null) {
      return;
    }
    Map<String, Object> delta = buildDelta(field, value, fieldPath);
    deltas.add(delta);
  }

  /**
   * 배열 요소 내 필드의 delta를 추가합니다.
   */
  public void addArrayElement(String field, Object value, Deque<String> fieldPath,
      String arrayFieldName, int index) {
    if (field == null) {
      return;
    }
    Map<String, Object> delta = buildArrayDelta(field, value, fieldPath, arrayFieldName, index);
    deltas.add(delta);
  }

  private Map<String, Object> buildDelta(String field, Object value, Deque<String> fieldPath) {
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, Object> current = result;

    for (String pathElement : fieldPath) {
      Map<String, Object> nested = new LinkedHashMap<>();
      current.put(pathElement, nested);
      current = nested;
    }
    current.put(field, value);

    return result;
  }

  private Map<String, Object> buildArrayDelta(String field, Object value, Deque<String> fieldPath,
      String arrayFieldName, int index) {
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, Object> current = result;

    for (String pathElement : fieldPath) {
      Map<String, Object> nested = new LinkedHashMap<>();
      current.put(pathElement, nested);
      current = nested;
    }

    Map<String, Object> element = new LinkedHashMap<>();
    element.put("index", index);
    element.put(field, value);

    List<Map<String, Object>> array = new ArrayList<>();
    array.add(element);
    current.put(arrayFieldName, array);

    return result;
  }
}
