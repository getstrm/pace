FROM envoyproxy/envoy:v1.25-latest as envoy


FROM eclipse-temurin:21-jre-jammy

COPY --from=envoy /usr/local/bin/envoy /usr/local/bin/envoy

WORKDIR /app
COPY . ./

RUN echo "${version}" > VERSION
ENV APP_VERSION "${version}"

# Spring Boot Actuator
EXPOSE 8080
# Envoy gRPC -> REST proxy
EXPOSE 9090
# gRPC server
EXPOSE 50051

ENTRYPOINT [ "./startup.sh" ]
