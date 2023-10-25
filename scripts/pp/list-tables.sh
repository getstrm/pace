#!/bin/bash

platform='databricks-pim@getstrm.com'

while getopts "p:" opt; do
    case $opt in
        p)
            platform=$OPTARG
            ;;
    esac
done
query=$( jq -n -r --arg id $platform '{"platform":{"id":$id}}' )
echo $query | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call ListProcessingPlatformTables
