# data-policy

Upsert a data policy

## Synopsis

Upserts (inserts or updates) a data policy into PACE AND optionally applies it to the target platform (default false).

The file to upsert is checked for validity, a transformation is generated for the processing platform, and then applied on it.

When updating an existing policy, the latest existing version should be set in the metadata. When creating a new policy, no version needs to be specified.

```
pace upsert data-policy (yaml or json file) [flags]
```

## Examples

```
pace upsert data-policy examples/sample_data/bigquery-cdc.json
data_policy:
  id: fb76958d-63a9-4b5e-bf36-fdc4d7ab807f
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

## Options

```
  -a, --apply   apply a data policy to the target processing platform when upserting
  -h, --help    help for data-policy
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

## SEE ALSO

* [pace upsert](./) - Upsert entities
