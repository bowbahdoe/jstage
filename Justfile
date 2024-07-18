help:
    just --list

clean:
    rm -rf build

compile: clean
    javac \
        -d build/javac \
        --module-path libs \
        --release 21 \
        --module-version 2024.07.18 \
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


