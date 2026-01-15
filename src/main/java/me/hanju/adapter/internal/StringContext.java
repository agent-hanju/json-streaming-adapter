package me.hanju.adapter.internal;

/**
 * JSON 문자열 값의 증분 스트리밍 상태를 관리합니다.
 * 이스케이프 시퀀스를 디코딩하고 토큰 경계를 넘는 처리를 관리합니다.
 */
public class StringContext {

  private final StringBuilder rawBuffer;

  private boolean inStringValue = false;
  private int stringValueStartPos = -1;
  private int lastEmittedStringPos = -1;
  private boolean pendingEscape = false;
  private int totalEmittedStringLength = 0;

  public StringContext(StringBuilder rawBuffer) {
    this.rawBuffer = rawBuffer;
  }

  public boolean isInStringValue() {
    return inStringValue;
  }

  public int getTotalEmittedLength() {
    return totalEmittedStringLength;
  }

  /**
   * 버퍼에서 문자열 시작을 감지합니다.
   *
   * @return 문자열 시작 위치 (감지되지 않으면 -1)
   */
  public int checkForStringStart() {
    String buf = rawBuffer.toString();
    int colonPos = buf.lastIndexOf(':');

    if (colonPos >= 0) {
      for (int i = colonPos + 1; i < buf.length(); i++) {
        char c = buf.charAt(i);
        if (Character.isWhitespace(c)) {
          continue;
        }
        if (c == '"') {
          inStringValue = true;
          stringValueStartPos = i + 1;
          lastEmittedStringPos = stringValueStartPos;
          pendingEscape = false;
          totalEmittedStringLength = 0;
          return stringValueStartPos;
        }
        break;
      }
    }
    return -1;
  }

  /**
   * 버퍼에서 문자열 증분을 추출합니다.
   *
   * @param fromPos 추출 시작 위치
   * @return 추출된 문자열 증분 (없으면 null)
   */
  public String extractIncrement(int fromPos) {
    if (!inStringValue) {
      return null;
    }

    int startPos = Math.max(fromPos, lastEmittedStringPos);
    StringBuilder increment = new StringBuilder();
    int i = startPos;

    while (i < rawBuffer.length()) {
      char c = rawBuffer.charAt(i);

      if (pendingEscape) {
        if (c == 'u') {
          if (i + 4 < rawBuffer.length()) {
            String hex = rawBuffer.substring(i + 1, i + 5);
            try {
              increment.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
              increment.append('\\').append(c);
            }
            i += 5;
          } else {
            break;
          }
        } else {
          increment.append(decodeEscapeChar(c));
          i++;
        }
        pendingEscape = false;
      } else if (c == '\\') {
        if (i + 1 < rawBuffer.length()) {
          char next = rawBuffer.charAt(i + 1);
          if (next == 'u') {
            if (i + 5 < rawBuffer.length()) {
              String hex = rawBuffer.substring(i + 2, i + 6);
              try {
                increment.append((char) Integer.parseInt(hex, 16));
              } catch (NumberFormatException e) {
                increment.append('\\').append(next);
              }
              i += 6;
            } else {
              pendingEscape = true;
              i++;
              break;
            }
          } else {
            increment.append(decodeEscapeChar(next));
            i += 2;
          }
        } else {
          pendingEscape = true;
          i++;
          break;
        }
      } else if (c == '"') {
        break;
      } else {
        increment.append(c);
        i++;
      }
    }

    lastEmittedStringPos = i;

    if (increment.length() > 0) {
      totalEmittedStringLength += increment.length();
      return increment.toString();
    }
    return null;
  }

  /**
   * 문자열 완료 시 남은 부분을 계산합니다.
   *
   * @param fullValue Jackson이 제공한 전체 문자열 값
   * @return emit할 남은 문자열 (없으면 null)
   */
  public String completeString(String fullValue) {
    String remaining = null;

    if (inStringValue) {
      if (totalEmittedStringLength < fullValue.length()) {
        remaining = fullValue.substring(totalEmittedStringLength);
        if (remaining.isEmpty()) {
          remaining = null;
        }
      } else if (totalEmittedStringLength == 0 && fullValue.isEmpty()) {
        remaining = "";
      }
    } else {
      remaining = fullValue;
    }

    reset();
    return remaining;
  }

  public void reset() {
    inStringValue = false;
    stringValueStartPos = -1;
    lastEmittedStringPos = -1;
    pendingEscape = false;
    totalEmittedStringLength = 0;
  }

  private char decodeEscapeChar(char c) {
    return switch (c) {
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case '"' -> '"';
      case '\\' -> '\\';
      case '/' -> '/';
      default -> c;
    };
  }
}
