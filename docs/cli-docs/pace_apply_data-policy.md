## pace apply data-policy

Apply an existing data policy

### Synopsis

Applies an existing data policy to the target platform.

```
pace apply data-policy (policy-id) [flags]
```

### Examples

```
pace apply data-policy public.demo --processing-platform bigquery-dev
data_policy:
  id: public.demo
  metadata:
    title: stream-machine-development.dynamic_views.cdc_diabetes
  platform:
    id: bigquery-dev
    platform_type: BIGQUERY
  rule_sets:
  - field_transforms:
    - attribute:
        path_components:
        - HighChol
        type: integer
      transforms:
      - fixed:
          value: "****"
...
```

### Options

```
  -h, --help                         help for data-policy
  -p, --processing-platform string   id of processing platform
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace apply](pace_apply.md)	 - Apply a specification

