help:
    just --list

clean:
    BUILD_FOLDER=".build/$(date +%s)" && \
      mkdir -p $BUILD_FOLDER && \
      rm -rf build && \
      ln -s $BUILD_FOLDER build

install:
    jresolve \
      --purge-output-directory \
      --output-directory libs \
      --enrich-pom pom.xml \
      --use-module-names \
      @libs.txt

compile: clean
    javac \
        -d build/javac \
        --module-path libs \
        --release 21 \
        --module-version 2024.07.19 \
        $(find ./src -name "*.java" -type f)

    jar --create \
        --file build/jar/jstage.jar \
        --main-class dev.mccue.jstage.JStage \
        -C build/javac .

    javadoc \
        -d build/javadoc \
        --module-path libs \
        -Xdoclint:none \
        -quiet \
        $(find ./src -name "*.java" -type f)


assemble: compile
    jreleaser assemble --output-directory build

stage: compile
    java --module-path build/jar/jstage.jar:libs \
        --module dev.mccue.jstage \
        --pom pom.xml \
        --artifact build/jar/jstage.jar \
        --output build/staging

    java --module-path build/jar/jstage.jar:libs \
       --module dev.mccue.jstage \
       --pom pom.xml \
       --artifact src \
       --classifier sources \
       --output build/staging

    java --module-path build/jar/jstage.jar:libs \
        --module dev.mccue.jstage \
        --pom pom.xml \
        --artifact build/javadoc \
        --classifier javadoc \
        --output build/staging

deploy: stage
    jreleaser deploy --output-directory build

uberjar: compile
    mkdir -p build/uberjar/temp
    unzip -q -d build/uberjar/temp libs/info.picocli.jar -x "META-INF/*"
    unzip -q -d build/uberjar/temp build/jar/jstage.jar -x "module-info.class"
    javac -d build/uberjar/temp --class-path build/uberjar src_uber/module-info.java
    jar --create \
      --file build/uberjar/jstage-uber.jar \
      --main-class dev.mccue.jstage.JStage \
      -C build/uberjar/temp .

