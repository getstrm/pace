#!/bin/bash

platform='databricks-pim@getstrm.com'
table='strm.poc.gddemo'
while getopts "p:t:" opt; do
    case $opt in
        p)
            platform=$OPTARG
            ;;
        t)
            table=$OPTARG
            ;;
    esac
done
query=$( jq -n -r --arg id $platform --arg table $table \
    '{"platform":{"id":$id},"table":$table}' )

echo $query | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call GetProcessingPlatformBlueprintPolicy
