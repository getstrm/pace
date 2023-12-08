# Envoy proxy with gRPC / JSON Transcoder

To support a REST interface, we use Envoy
with [the gRPC / JSON Transcoder](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/grpc_json_transcoder_filter).

In order to run the gRPC proxy locally, start it by simply running `make run-grpc-proxy` in the root of the repository.
Make sure that the Spring Boot application is running as well.
