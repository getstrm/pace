#!/bin/bash

HOST=${2:-localhost}
PORT=${1:-50051}
MAX_WAIT_TIME=15
SECONDS_WAITED=0

while [ $SECONDS_WAITED -lt $MAX_WAIT_TIME ] && ! nc -z "$HOST" "$PORT"; do
    sleep 1
    SECONDS_WAITED=$((SECONDS_WAITED + 1))

    if [ $SECONDS_WAITED -gt $MAX_WAIT_TIME ]; then
        echo "Maximum wait time exceeded. TCP socket is still not available."
        exit 1
    fi
done
