#!/bin/bash

f=${1:-sample_data/cdc-diabetes.yaml}
data_policy=$(json-case $f)
request=$(echo '{}' | jq -r --argjson p "$data_policy" '{data_policy: $p}')

echo $request | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call UpsertDataPolicy
