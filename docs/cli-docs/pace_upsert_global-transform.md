## pace upsert global-transform

Upsert a global transform

### Synopsis

Insert or update a global transform. The 'ref' field in the global policy is the identifier of the global policy.
If it is the same as that of an already existing one, the existing one will be replaced.

```
pace upsert global-transform (yaml or json file) [flags]
```

### Examples

```
    pace upsert global-transform global-tag-transform.yaml

	transform:
	  description: This is a global transform ...
	  ref: PII_EMAIL
	  tag_transform:
		tag_content: PII_EMAIL
		transforms:
		- principals:
		  - group: FRAUD_AND_RISK
		  regexp:
			regexp: ^.*(@.*)$
			replacement: '****\1'
		- nullify: {}
```

### Options

```
  -h, --help   help for global-transform
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace upsert](pace_upsert.md)	 - Upsert entities

