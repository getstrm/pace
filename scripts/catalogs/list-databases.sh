#!/bin/bash

catalog=datahub-on-dev
while getopts "c:" opt; do
    case $opt in
        c)
            catalog=$OPTARG
            ;;
    esac
done

query=$( jq -n -r --arg id $catalog '{"catalog":{"id":$id}}' )

echo $query | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call ListDatabases
