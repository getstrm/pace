#!/bin/bash

catalog=datahub-on-dev
database='urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)'

while getopts "c:d:" opt; do
    case $opt in
        c)
            catalog=$OPTARG
            ;;
        d)
            database=$OPTARG
            ;;
    esac
done
query=$( jq -n -r \
    --arg catalog $catalog \
    --arg database $database \
    '{"database": {"id":$database, "catalog":{"id":$catalog}}}' )

echo $query | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call ListSchemas
