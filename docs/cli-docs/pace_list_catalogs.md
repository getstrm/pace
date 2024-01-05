## pace list catalogs

List Catalogs

### Synopsis

Shows all the catalogs that have been configured on this PACE instance.

Catalogs are connected via configuration, and only read upon startup of the PACE server.

```
pace list catalogs [flags]
```

### Examples

```
pace list catalogs --output table
 ID                       TYPE

 COLLIBRA-testdrive   COLLIBRA
 datahub-on-dev        DATAHUB

pace list catalogs
catalogs:
- id: COLLIBRA-testdrive
  type: COLLIBRA
- id: datahub-on-dev
  type: DATAHUB
- id: odd
  type: ODD
```

### Options

```
  -h, --help            help for catalogs
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

