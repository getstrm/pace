## pace list groups

List Groups

### Synopsis

list the groups that exist in a processing platform.

These groups are needed in the rule sets to determine group membership of the
entity executing the query on the view in the rule set.

```
pace list groups [flags]
```

### Examples

```
pace list groups --processing-platform bigquery-dev --output table
 NAME

 marketing
 foo-bar
 fraud-detection
```

### Options

```
  -h, --help                         help for groups
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

