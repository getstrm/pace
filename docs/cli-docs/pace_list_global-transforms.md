## pace list global-transforms

List Global Transforms

### Synopsis

Lists all the global transforms stored in PACE.

```
pace list global-transforms [flags]
```

### Examples

```

    pace list global-transforms

	global_transforms:
	- description: This ...
	  ref: pii-email
	  tag_transform:
		tag_content: pii-email
	...
```

### Options

```
  -h, --help   help for global-transforms
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
  -P, --page-size uint32                        the maximum number of records per page (default 10)
  -T, --page-token string                       next page token. Used by BigQuery
  -S, --skip uint32                             the number of records that need to be skipped
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

