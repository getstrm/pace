## pace list catalogs

List Catalogs

### Synopsis

Shows all the catalogs that have been configured on this Pace instance.

Catalogs are connected via configuration, and only read upon startup of the Pace server.

```
pace list catalogs [flags]
```

### Examples

```
pace list catalogs
 ID                       TYPE

 COLLIBRA-testdrive   COLLIBRA
 datahub-on-dev        DATAHUB

# in yaml
pace list catalogs -o yaml
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
      --api-host string   api host (default "localhost:50051")
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

