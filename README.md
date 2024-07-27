# JStage

## Installation

### Bash

```
bash < <(curl -s https://raw.githubusercontent.com/bowbahdoe/jstage/main/install)
```

### Maven

```xml
<dependency>
    <groupId>dev.mccue</groupId>
    <artifactId>jstage</artifactId>
    <version>2024.07.19</version>
</dependency>
```

## Why

When you want to publish something to a Maven repo using [jreleaser](https://jreleaser.org/guide/latest/examples/maven/maven-central.html)
you need to first put your artifacts into a "staging repository" layout. 

This is usually handled for you by Maven or Gradle, but if you are building your code some other way you need
to do it manually. 

This is both a little annoying and error-prone.

## Usage (cli)

```bash
jstage \
  --pom pom.xml \
  --artifact build/jar/jstage.jar \
  --output build/staging

jstage \
  --pom pom.xml \
  --artifact src \
  --classifier sources \
  --output build/staging

jstage \
  --pom pom.xml \
  --artifact build/javadoc \
  --classifier javadoc \
  --output build/staging
```

## Usage (Tool Provider)

```java
import java.util.spi.ToolProvider;

void main() {
    var jstage = ToolProvider.findFirst("jstage");
    
    // ... same as cli ...
}
```