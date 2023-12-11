.PHONY: clean-common-protos, buf-create-descriptor-binpb, run-grpc-proxy, run-docker-local \
    json-schema integration-test copy-json-schema-to-resources

SHELL := /bin/bash

git_branch := $(shell git rev-parse --abbrev-ref HEAD)
descriptor_file := "grpc-proxy/descriptor.binpb"

buf-publish-current-branch: copy-json-schema-to-resources
	@ [[ "$$OSTYPE" == "darwin"* ]] && SED=gsed || SED=sed && \
	commit_hash=$$(cd protos > /dev/null && buf push --branch "${git_branch}") && \
	[ ! -z "$$commit_hash" ] && commit_hash_short=$$(echo "$$commit_hash" | cut -c1-12) && $$SED -i "s|generatedBufDependencyVersion=.*|generatedBufDependencyVersion=00000000000000.$$commit_hash_short|g" gradle.properties && echo $$commit_hash || echo "No changes to protos, gradle.properties not updated"

run-docker-local:
	./gradlew buildDocker && docker run -p 8080:8080 -p 9090:9090 -p 50051:50051 -e SPRING_PROFILES_ACTIVE=dockerdev pace:latest

buf-create-descriptor-binpb: # PHONY on purpose, as we want to regenerate every time
	rm -f ${descriptor_file} && \
	buf build protos --config ./protos/buf.yaml -o ${descriptor_file}

create-envoy-spec:
	@ rm -f grpc-proxy/envoy-local.yaml && \
	export GRPC_SERVICES=$$(buf build -o -#format=json | jq -rc '.file | map(select(.name | startswith("getstrm"))) | map(select(.service > 0) | (.package + "." + .service[].name))')  && \
	envsubst < grpc-proxy/envoy-local-template.yaml > grpc-proxy/envoy-local.yaml

run-grpc-proxy: buf-create-descriptor-binpb create-envoy-spec
	docker run -p 9090:9090 -p 9000:9000 -v $$(pwd)/grpc-proxy/envoy-local.yaml:/etc/envoy/envoy.yaml -v $$(pwd)/${descriptor_file}:/tmp/envoy/descriptor.binpb envoyproxy/envoy:v1.28-latest

/tmp/envoy.yaml: grpc-proxy
	sed 's/address: host\.docker\.internal/address: localhost/' $< > $@

run-grpc-proxy-localhost: buf-create-descriptor-binpb /tmp/envoy.yaml
	docker run --net=host -p 9090:9090 -p 9000:9000 -v /tmp/envoy.yaml:/etc/envoy/envoy.yaml -v $$(pwd)/${descriptor_file}:/tmp/envoy/descriptor.binpb envoyproxy/envoy:v1.28-latest

json-schema:
	@ rm -rf protos/json-schema
	@ (cd protos; buf generate)

copy-json-schema-to-resources: json-schema
	@ find app/src/main/resources/jsonschema -type d -maxdepth 1 -mindepth 1 | xargs -I{} rm -rf {}
	@ mkdir -p app/src/main/resources/jsonschema
	@ cp -r protos/json-schema/* app/src/main/resources/jsonschema

integration-test:
	@ chmod go-rw integration-test/pgpass
	@ make -C integration-test clean pace all
