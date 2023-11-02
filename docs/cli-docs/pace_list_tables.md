## pace list tables

List Tables

```
pace list tables [flags]
```

### Examples

```
pace list tables --catalog COLLIBRA-testdrive \
	--database 99379294-6e87-4e26-9f09-21c6bf86d415 \
	--schema c0a8b864-83e7-4dd1-a71d-0c356c1ae9be

pace list tables --processing-platform bigquery-dev
```

### Options

```
  -c, --catalog string               id of catalog
  -d, --database string              database in the catalog
  -h, --help                         help for tables
  -o, --output string                output formats [yaml, json, json-raw, table, plain] (default "yaml")
  -p, --processing-platform string   id of processing platform
  -s, --schema string                schema in database on catalog
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

