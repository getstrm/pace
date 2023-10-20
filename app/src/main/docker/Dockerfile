FROM envoyproxy/envoy:v1.25-latest as envoy


FROM eclipse-temurin:21-jre-jammy

# TODO change the authentication with Snowflake, so that we can remove Chrome
# Snowflake requires OAuth authorization code flow, therefore we need a headless browser
RUN curl -O https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
RUN apt update
RUN apt install -y ./google-chrome-stable_current_amd64.deb
RUN rm google-chrome-stable_current_amd64.deb

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
