## pace list groups

List Groups

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
      --api-host string   api host (default "localhost:50051")
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

