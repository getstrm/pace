.PHONY: clean-common-protos, buf-create-descriptor-binpb, run-rest-proxy, run-docker-local

SHELL := /bin/bash

git_branch := $(shell git rev-parse --abbrev-ref HEAD)
descriptor_file := "rest/descriptor.binpb"

buf-publish-current-branch:
	[[ "$$OSTYPE" == "darwin"* ]] && SED=gsed || SED=sed && \
	commit_hash=$$(cd protos > /dev/null && buf push --branch "${git_branch}") && \
	commit_hash_short=$$(echo "$$commit_hash" | cut -c1-12) && \
	$$SED -i "s|generatedBufDependencyVersion=.*|generatedBufDependencyVersion=00000000000000.$$commit_hash_short|g" gradle.properties

run-docker-local:
	./gradlew buildDocker && docker run -p 8080:8080 -p 9090:9090 -p 50051:50051 -e SPRING_PROFILES_ACTIVE=dockerdev pace:latest

buf-create-descriptor-binpb: # PHONY on purpose, as we want to regenerate every time
	rm -f ${descriptor_file} && \
	buf build protos --config ./protos/buf.yaml -o ${descriptor_file}

run-rest-proxy: buf-create-descriptor-binpb
	docker run -p 9090:9090 -p 9000:9000 -v $$(pwd)/rest/envoy-local.yaml:/etc/envoy/envoy.yaml -v $$(pwd)/${descriptor_file}:/tmp/envoy/descriptor.binpb envoyproxy/envoy:v1.28-latest
