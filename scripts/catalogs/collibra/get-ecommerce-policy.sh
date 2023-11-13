#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
exec $SCRIPT_DIR/../get-blueprint-policy.sh \
    -c COLLIBRA-testdrive \
    -d b6e043a7-88f1-42ee-8e81-0fdc1c96f471 \
    -s 10255be7-c2ac-43ae-be0a-a34d4e7c88b7 \-t 37f0dec4-097f-42b1-8cb6-23b46927a6ef
