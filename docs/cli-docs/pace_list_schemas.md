## pace list schemas

List Schemas

### Synopsis

Lists all schemas in a certain database in a certain catalog or processing platform.

We map the native hierarchy of any platform or catalog onto the following:

database → schema → table

```
pace list schemas [flags]
```

### Examples

```


	pace list schemas --catalog COLLIBRA-testdrive --database b6e043a7-88f1-42ee-8e81-0fdc1c96f471 --output table

	ID                                     NAME

	68c97f58-fa4f-4b55-b8c3-95c321f7dae9   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DCC_22_DEMO
	10255be7-c2ac-43ae-be0a-a34d4e7c88b7   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO

Another example:

	pace list schemas --processing-platform bigquery-dev --database stream-machine-development -o table
	ID                           NAME

	batch_job_demo               batch_job_demo
	dlp_demo                     dlp_demo
	dynamic_view_poc             dynamic_view_poc
	dynamic_views                dynamic_views
	jdbc_bigquery_test           jdbc_bigquery_test

```

### Options

```
  -c, --catalog string               id of catalog
  -d, --database string              database in the catalog
  -h, --help                         help for schemas
  -o, --output string                output formats [yaml, json, json-raw, table, plain] (default "yaml")
  -p, --processing-platform string   id of processing platform
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

