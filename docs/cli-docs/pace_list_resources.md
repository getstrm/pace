## pace list resources

list resources

### Synopsis

list resources using a directory path style interface, i.e. forward slash
separated path components.

The top level path component is the identifier of one of the configured data-catalogs or
processing platforms in PACE.

```
pace list resources (resource-path) [flags]
```

### Examples

```


# Show all the configured integrations (data catalogs and processing platforms)
pace list resources
 INTEGRATION           TYPE         ID

 processing-platform   DATABRICKS   dbr-pace
 processing-platform   SNOWFLAKE    sf-pace
 processing-platform   BIGQUERY     bigquery-dev
 processing-platform   POSTGRES     paceso
 data-catalog          COLLIBRA     COLLIBRA-testdrive
 data-catalog          DATAHUB      datahub-on-dev

pace list -o table resources bigquery-dev/stream-machine-development/batch_job_demo
 TABLE              FQN

 retail_0           stream-machine-development.batch_job_demo.retail_0
 retail_encrypted   stream-machine-development.batch_job_demo.retail_encrypted
 retail_in          stream-machine-development.batch_job_demo.retail_in
 retail_keys        stream-machine-development.batch_job_demo.retail_keys

```

### Options

```
  -h, --help            help for resources
  -o, --output string   output formats [table, yaml, json, json-raw] (default "table")
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -P, --page-size uint32                        the maximum number of records per page (default 10)
  -T, --page-token string                       next page token. Used by BigQuery
  -S, --skip uint32                             the number of records that need to be skipped
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

