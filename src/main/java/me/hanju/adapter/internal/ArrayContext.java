package me.hanju.adapter.internal;

/**
 * 배열 파싱 상태를 관리합니다.
 * 배열 필드명, 현재 요소 index, 자동 index 생성을 처리합니다.
 */
public class ArrayContext {

  private boolean inArray = false;
  private String arrayFieldName = null;
  private int autoIndex = 0;
  private Integer currentElementIndex = null;
  private boolean inArrayElement = false;

  public boolean isInArray() {
    return inArray;
  }

  public boolean isInArrayElement() {
    return inArrayElement;
  }

  public String getArrayFieldName() {
    return arrayFieldName;
  }

  /**
   * 현재 요소의 index를 반환합니다.
   * 명시적 index가 없으면 autoIndex를 반환합니다.
   */
  public int getCurrentIndex() {
    return (currentElementIndex != null) ? currentElementIndex : autoIndex;
  }

  /**
   * 배열 시작을 처리합니다.
   */
  public void startArray(String fieldName) {
    inArray = true;
    arrayFieldName = fieldName;
    autoIndex = 0;
  }

  /**
   * 배열 종료를 처리합니다.
   */
  public void endArray() {
    inArray = false;
    arrayFieldName = null;
    autoIndex = 0;
    currentElementIndex = null;
  }

  /**
   * 배열 요소 객체 시작을 처리합니다.
   */
  public void startElement() {
    inArrayElement = true;
    currentElementIndex = null;
  }

  /**
   * 배열 요소 객체 종료를 처리합니다.
   * index가 없으면 자동 생성됩니다.
   */
  public void endElement() {
    inArrayElement = false;
    if (currentElementIndex == null) {
      currentElementIndex = autoIndex++;
    } else {
      autoIndex = currentElementIndex + 1;
    }
  }

  /**
   * 명시적 index 값을 설정합니다.
   */
  public void setIndex(int index) {
    currentElementIndex = index;
  }

  /**
   * 필드가 index 필드인지 확인합니다.
   */
  public boolean isIndexField(String fieldName) {
    return inArrayElement && "index".equals(fieldName);
  }
}
