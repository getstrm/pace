## pace list global-transforms

List Global Transforms

### Synopsis

Lists all the global transforms stored in Pace.

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
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace list](pace_list.md)	 - List entities

