## pace get global-transform

Get a global transform

### Synopsis

Returns a global transform from PACE, by transform reference and transform type.

```
pace get global-transform (ref) [flags]
```

### Examples

```
    pace get global-transform PII_EMAIL

	description: ...
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
  -h, --help          help for global-transform
  -t, --type string   type of global transform: TAG_TRANSFORM (default "TAG_TRANSFORM")
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace get](pace_get.md)	 - Get a single entity

