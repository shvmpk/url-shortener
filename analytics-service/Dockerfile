# Module detection: three-layer strategy
#   1. jdeps (base)            — static bytecode refs in app code
#   2. jdeps + --class-path    — static refs inside BOOT-INF/lib/* dependencies
#   3. --add-modules extras    — runtime/reflection/bytecode-gen refs (Hibernate proxies, JDBC, etc.)
FROM eclipse-temurin:26-jdk-alpine AS jre-builder
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN mkdir -p /tmp/unpacked && cd /tmp/unpacked && jar -xf /app/app.jar && \
    jdeps --ignore-missing-deps --print-module-deps --multi-release 26 \
          --class-path 'BOOT-INF/lib/*' \
          /app/app.jar > /tmp/modules.txt && \
    jlink --add-modules $(cat /tmp/modules.txt | tr -d '\n'),jdk.crypto.ec,jdk.unsupported,java.naming,java.desktop,java.management,java.net.http,java.security.jgss,java.xml,java.sql,java.instrument,java.compiler \
          --strip-debug --compress=zip-6 --no-header-files --no-man-pages \
          --output /jre

FROM alpine:3.21
RUN apk add --no-cache tzdata ca-certificates && \
    addgroup -S app && adduser -S app -G app
COPY --from=jre-builder /jre /jre
COPY --from=jre-builder /app/app.jar /app/app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["/jre/bin/java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
