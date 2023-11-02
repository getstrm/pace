## pace list data-policies

List Datapolicies

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
      --api-host string   api host (default "localhost:50051")
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

