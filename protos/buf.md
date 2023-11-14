# Policy And Contract Engine APIs

Common entities can be found in the `entities` package, and other packages should be considered services.

## Versioning

We take the following into consideration for versioning:

- `v1alpha`: The API is not stable yet, and is subject to (breaking) changes without notice.
- `v1beta`: The API is not stable yet, but it is likely to be promoted to stable. In this stage, we are looking for
  feedback. Breaking changes will be announced.
- `v1`: The API is stable, and will not change without notice. Deprecations will be announced, with a migration path and
  timeline.

Currently, PACE only offers a `v1alpha` API.

## REST API transcoding

The PACE container image comes shipped with a REST API. With REST API design, we try to adhere to
the [Zalando REST API guidelines](https://opensource.zalando.com/restful-api-guidelines). Mapping from JSON to gRPC
can be done using
Envoy's [gRPC-JSON transcoder](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/grpc_json_transcoder_filter),
and follows
the [gRPC Transcoding specification](https://cloud.google.com/endpoints/docs/grpc-service-config/reference/rpc/google.api#grpc-transcoding).
