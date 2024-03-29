## pace list databases

List Databases

### Synopsis

Lists all databases in a certain catalog or processing platform.

We map the native hierarchy of any platform or catalog onto the following:

database → schema → table

```
pace list databases [flags]
```

### Examples

```

    pace list databases --catalog COLLIBRA-testdrive  --output table
	 ID                                     NAME   TYPE

	 8665f375-e08a-4810-add6-7af490f748ad          Snowflake
	 99379294-6e87-4e26-9f09-21c6bf86d415          CData JDBC Driver for Google BigQuery 2021
	 b6e043a7-88f1-42ee-8e81-0fdc1c96f471          Snowflake

Another example:

	pace list databases --processing-platform bigquery-dev -o table
	 ID                           NAME                         TYPE

	 stream-machine-development   stream-machine-development   BIGQUERY

BigQuery is at the moment configured to a specific Google Cloud Project, and that project becomes the
'database'.
```

### Options

```
  -c, --catalog string               id of catalog
  -h, --help                         help for databases
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

