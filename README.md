# json-stream-adapter

JSON Schema 기반 스트리밍 어댑터. LLM 응답처럼 토큰 단위로 들어오는 JSON을 실시간으로 파싱하고 증분 델타를 추출합니다.

## 설치

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.agent-hanju:json-stream-adapter:0.0.1-SNAPSHOT'

    // 필수 의존성 (직접 추가 필요)
    implementation 'com.fasterxml.jackson.core:jackson-core:2.18.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
    implementation 'com.networknt:json-schema-validator:1.5.4'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.agent-hanju</groupId>
    <artifactId>json-stream-adapter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- 필수 의존성 -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-core</artifactId>
    <version>2.18.2</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.2</version>
</dependency>
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.5.4</version>
</dependency>
```

## 사용법

```java
String schema = """
    {
      "type": "object",
      "properties": {
        "content": { "type": "string" }
      }
    }
    """;

JsonStreamingAdapter adapter = new JsonStreamingAdapter(schema);

// 토큰 단위로 피드
List<Map<String, Object>> deltas1 = adapter.feedToken("{\"con");
List<Map<String, Object>> deltas2 = adapter.feedToken("tent\":\"Hel");
List<Map<String, Object>> deltas3 = adapter.feedToken("lo World\"}");

// 완료 및 스키마 검증
adapter.flush();
```

각 `feedToken()` 호출은 증분 델타 리스트를 반환합니다:

```java
// deltas2 예시
[{field=content, value=Hel, path=[]}]

// deltas3 예시
[{field=content, value=lo World, path=[]}]
```

## 요구사항

- Java 21+
- Jackson Core/Databind 2.18+
- networknt json-schema-validator 1.5+

## 라이선스

MIT License
