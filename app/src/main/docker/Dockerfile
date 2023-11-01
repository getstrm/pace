FROM envoyproxy/envoy:v1.25-latest as envoy

FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.source=https://github.com/getstrm/pace

COPY --from=envoy /usr/local/bin/envoy /usr/local/bin/envoy

WORKDIR /app
COPY . ./
RUN mkdir -p /app/config

RUN echo "${version}" > VERSION
ENV APP_VERSION "${version}"

# Spring Boot Actuator
EXPOSE 8080
# Envoy gRPC -> REST proxy
EXPOSE 9090
# gRPC server
EXPOSE 50051

# Ensure that the /app/config dir can be used for extra configuration files.
ENV SPRING_PROFILES_ACTIVE "docker"

ENTRYPOINT [ "./startup.sh" ]
