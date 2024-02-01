#!/usr/bin/env bash

/usr/local/bin/envoy --config-path /app/envoy.yaml &

exec java -Dlog4j2.formatMsgNoLookups=true -jar app.jar
