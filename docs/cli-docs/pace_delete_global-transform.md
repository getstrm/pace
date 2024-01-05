## pace delete global-transform

delete a global transform

```
pace delete global-transform (ref) [flags]
```

### Examples

```

    pace delete global-transform PII_EMAIL

	deleted_count: 1
```

### Options

```
  -h, --help          help for global-transform
  -t, --type string   type of global transform: TAG_TRANSFORM (default "TAG_TRANSFORM")
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace delete](pace_delete.md)	 - Delete entities

