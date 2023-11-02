## pace upsert data-policy

Upsert a data policy

### Synopsis

Upserts (inserts or updates) a data policy into Pace AND
applies it to the target platform.

The file to upsert is checked for validity, a transformation is generated
for the processing platform, and then applied on it.

```
pace upsert data-policy (yaml or json file) [flags]
```

### Examples

```
pace upsert data-policy sample_data/bigquery-cdc.json
data_policy:
  id: fb76958d-63a9-4b5e-bf36-fdc4d7ab807f
  info:
    context: 120ee556-8666-4438-9c2b-315a0ab2f494
    create_time: "2023-10-27T11:28:37.658384414Z"
    title: stream-machine-development.dynamic_views.cdc_diabetes
    update_time: "2023-10-27T14:17:14.422071500Z"
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
  -h, --help   help for data-policy
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace upsert](pace_upsert.md)	 - Upsert entities

