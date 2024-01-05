# Phony because we always want to download.
# equivalent to `make -B`
.PHONY: components.yaml openapi.yaml

all: components.yaml openapi.yaml

*.yaml:
	curl -o $@ https://raw.githubusercontent.com/opendatadiscovery/odd-platform/main/odd-platform-specification/$@
