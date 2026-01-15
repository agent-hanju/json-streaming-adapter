package me.hanju.adapter.exception;

/**
 * JSON 스트리밍 처리 중 발생하는 예외
 */
public class JsonStreamingException extends RuntimeException {

  public JsonStreamingException(String message) {
    super(message);
  }

  public JsonStreamingException(String message, Throwable cause) {
    super(message, cause);
  }
}
