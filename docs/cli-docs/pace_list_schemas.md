# schemas

List Schemas

```
pace list schemas [flags]
```

## Examples

```
pace list schemas --catalog COLLIBRA-testdrive \
	--database b6e043a7-88f1-42ee-8e81-0fdc1c96f471 --output table
 ID                                     NAME

 68c97f58-fa4f-4b55-b8c3-95c321f7dae9   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DCC_22_DEMO
 10255be7-c2ac-43ae-be0a-a34d4e7c88b7   Snowflake Protect>DC22_PROTECT_TESTDRIVE>DEMO
```

## Options

```
  -c, --catalog string    id of catalog
  -d, --database string   database in the catalog
  -h, --help              help for schemas
  -o, --output string     output formats [yaml, json, json-raw, table, plain] (default "yaml")
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
```

## SEE ALSO

* [pace list](pace\_list.md) - List entities
