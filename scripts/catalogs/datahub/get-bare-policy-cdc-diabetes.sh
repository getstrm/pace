#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
exec $SCRIPT_DIR/../get-bare-policy.sh \
    -d 'urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.cdc_diabetes,PROD)' \
    -s 'urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.cdc_diabetes,PROD)' \
    -t 'urn:li:dataset:(urn:li:dataPlatform:snowflake,strm.poc.cdc_diabetes,PROD)'

