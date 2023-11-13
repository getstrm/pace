#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
exec $SCRIPT_DIR/../get-blueprint-policy.sh \
    -d "urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.gddemo,PROD)" \
    -s "urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.gddemo,PROD)" \
    -t "urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.gddemo,PROD)"

