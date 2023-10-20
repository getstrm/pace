.PHONY: clean-common-protos

common_protos := ${CURDIR}/.common-protos

grpc_version := 1.50.0
protobuf_version := 3.21.9
google_common_protos_version := 2.10.0
git_branch := $(shell git rev-parse --abbrev-ref HEAD)

# google/protobuf dependencies (predefined Protos for e.g. Timestamp, Duration, etc)
${common_protos}/protobuf-java.jar:
	curl "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/${protobuf_version}/protobuf-java-${protobuf_version}.jar" --create-dirs -o "${common_protos}/protobuf-java.jar"

# google/api dependencies (Common Google Protos, such as field_behavior)
${common_protos}/proto-google-common-protos.jar:
	curl "https://repo1.maven.org/maven2/com/google/api/grpc/proto-google-common-protos/${google_common_protos_version}/proto-google-common-protos-${google_common_protos_version}.jar" --create-dirs -o "${common_protos}/proto-google-common-protos.jar"

${common_protos}: ${common_protos}/proto-google-common-protos.jar ${common_protos}/protobuf-java.jar ${common_protos}/validate/validate.proto

clean-common-protos:
	rm -rf ${common_protos}

${common_protos}/google/protobuf: ${common_protos}/protobuf-java.jar
	unzip -d ${common_protos} $< "google/**/*.proto"

${common_protos}/google/api: ${common_protos}/proto-google-common-protos.jar
	unzip -d ${common_protos} $< "google/**/*.proto"

${common_protos}/validate/validate.proto:
	curl "https://raw.githubusercontent.com/bufbuild/protoc-gen-validate/v0.9.1/validate/validate.proto" --create-dirs -o "${common_protos}/validate/validate.proto"

# To ensure that we use the same Google Common Protobuf files in all languages, we extract them from the jar
default-google-dependencies: ${common_protos}/google/protobuf ${common_protos}/google/api
protoc-gen-validate-dependency: ${common_protos}/validate/validate.proto

intellij: ${common_protos}
	./protos/setup-ide-protobuf-plugins.sh

buf-publish-current-branch:
	[[ "$$OSTYPE" == "darwin"* ]] && SED=gsed || SED=sed && \
	commit_hash=$$(cd protos && buf push --branch "${git_branch}") && \
	commit_hash_short=$$(echo "$$commit_hash" | cut -c1-12) && \
	$$SED -i "s|generatedBufDependencyVersion=.*|generatedBufDependencyVersion=00000000000000.$$commit_hash_short|g" gradle.properties

