## pace list tables

List Tables

### Synopsis

Lists all tables in a certain schema.

```
pace list tables [flags]
```

### Examples

```
pace list tables --catalog COLLIBRA-testdrive \
	--database b6e043a7-88f1-42ee-8e81-0fdc1c96f471  \
	--schema 10255be7-c2ac-43ae-be0a-a34d4e7c88b7 -o table

 ID                                     NAME                                                               TAGS

 54443cf7-3974-4050-b742-7babe4edc50e   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO>MANAGERS
 3fc8b5ff-ae92-4cd2-9e02-d2d5ca61ed29   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO>EMPLOYEES
 37f0dec4-097f-42b1-8cb6-23b46927a6ef   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO>ECOMMERCE_PRODUCTS
 c50ddafe-4263-44d2-8bc7-7080260013f0   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO>CAR_DETAILS
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
      --api-host string                         api host (default "localhost:50051")
  -P, --page_size uint32                        the maximum number of records per page (default 10)
  -S, --skip uint32                             the number of records that need to be skipped
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

