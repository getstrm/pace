FROM eclipse-temurin:23-jre

LABEL org.opencontainers.image.source=https://github.com/getstrm/pace

WORKDIR /app
COPY . ./

RUN echo "${version}" > VERSION
ENV APP_VERSION "${version}"

ENTRYPOINT [ "java", "-jar", "/app/dbt.jar", "/dbt" ]
