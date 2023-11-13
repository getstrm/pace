## Running locally

(This has to be improved and parameterized more)

1. Make sure you can connect to all required external hosts (db, platforms, catalogs).
2. Either run the Spring Boot app from the DataPolicyServiceApplication class, or run `make run-docker-local` to build
   and run a local docker image.

## Running examples with a locally built image

1. Build the image with `./gradlew buildDocker`.
2. Run an example with `IMAGE=pace-local docker compose up -f examples/<example>/docker-compose.yaml`.

Tip: want to make this easier? Just create a `examples/.envrc` file with the following contents:
```
export IMAGE=pace-local
```

Then you can run `docker compose up` as usual.

## gRPC / REST API
[//]: # (TODO explain how we use gRPC / Protobuf and how we facilitate a REST API on top of that)
[//]: # (TODO explain that we use Google API standards)
