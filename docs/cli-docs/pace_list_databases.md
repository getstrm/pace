## pace list databases

List Databases

### Synopsis

Lists all databases in a certain catalog. Some catalogs (like Collibra) are hierarchical, while others
are just a flat list of tables. In that case we might  just return a single 'Database' for the whole catalog.

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
```

### Options

```
  -c, --catalog string   id of catalog
  -h, --help             help for databases
  -o, --output string    output formats [yaml, json, json-raw, table, plain] (default "yaml")
```

### Options inherited from parent commands

```
      --api-host string    api host (default "localhost:50051")
  -P, --page_size uint32   the maximum number of records per page (default 10)
  -S, --skip uint32        the number of records that need to be skipped
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

