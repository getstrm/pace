## pace list data-policies

List Data Policies

### Synopsis

lists all the active policies defined and applied by PACE.

These will always include at least one rule set.

```
pace list data-policies [flags]
```

### Examples

```
pace list data-policies --output table
 PLATFORM       SOURCE                                                  TAGS

 bigquery-dev   stream-machine-development.dynamic_views.cdc_diabetes
```

### Options

```
  -h, --help            help for data-policies
  -o, --output string   output formats [yaml, json, json-raw, table, plain] (default "yaml")
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -P, --page_size uint32                        the maximum number of records per page (default 10)
  -S, --skip uint32                             the number of records that need to be skipped
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

