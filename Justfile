help:
    just --list

clean:
    rm -rf build

compile: clean
    javac \
      -d build/javac \
      --module-path libs \
      --release 21 \
      --module-version 2024.07.17 \
      $(find ./src -name "*.java" -type f)

    jar --create \
        --file build/jar/jstage.jar \
        --main-class dev.mccue.jstage.JStage \
        -C build/javac .

    jar --create \
        --file build/jar/sources.jar \
        -C src .

    javadoc \
        -d build/javadoc \
        --module-path libs \
        $(find ./src -name "*.java" -type f)

debug: compile
    java --module-path build/jar/jstage.jar:libs \
      --module dev.mccue.jstage \
      --pom pom.xml \
      --jar build/jar/jstage.jar \
      --sources src \
      --documentation build/javadoc \
      --output build/staging

    jreleaser assemble --output-directory build

