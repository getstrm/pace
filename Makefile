.PHONY: clean-common-protos

SHELL := /bin/bash

common_protos := ${CURDIR}/.common-protos

grpc_version := 1.50.0
protobuf_version := 3.21.9
git_branch := $(shell git rev-parse --abbrev-ref HEAD)

buf-publish-current-branch:
	[[ "$$OSTYPE" == "darwin"* ]] && SED=gsed || SED=sed && \
	commit_hash=$$(cd protos > /dev/null && buf push --branch "${git_branch}") && \
	commit_hash_short=$$(echo "$$commit_hash" | cut -c1-12) && \
	$$SED -i "s|generatedBufDependencyVersion=.*|generatedBufDependencyVersion=00000000000000.$$commit_hash_short|g" gradle.properties

run-docker-local:
	./gradlew buildDocker && docker run -p 8080:8080 -p 9090:9090 -p 50051:50051 -e SPRING_PROFILES_ACTIVE=dockerdev pace:latest
