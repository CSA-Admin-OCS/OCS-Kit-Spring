# Compiles and runs untrusted student Java, so it needs a full JDK (javac) at runtime.
FROM eclipse-temurin:21-jdk-alpine

RUN adduser -D -u 1000 -s /sbin/nologin runner

WORKDIR /runner

COPY Runner.java .

RUN javac Runner.java && rm Runner.java

ENV HOME=/tmp

USER 1000:1000

EXPOSE 8592

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD nc -z 127.0.0.1 8592 || exit 1

CMD ["java", "Runner"]
