#!/bin/bash

catalog=datahub-on-dev
database='urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)'
schema='urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)'
table='urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)'

while getopts "c:d:s:t:" opt; do
    case $opt in
        c)
            catalog=$OPTARG
            ;;
        d)
            database=$OPTARG
            ;;
        s)
            schema=$OPTARG
            ;;
        t)
            table=$OPTARG
            ;;
    esac
done

query=$( jq -n -r \
    --arg catalog $catalog \
    --arg database $database \
    --arg schema $schema \
    --arg table $table \
    '{
        "table": {
            "id": $table,
"schema":{"id": $schema, "database": {"id":$database, "catalog":{"id":$catalog}}}
        }
    }')

echo $query | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call GetCatalogBlueprintPolicy
