## pace get data-policy

Get a data policy

### Synopsis

retrieves a DataPolicy from Pace.

A bare policy is a policy without a rule set that can be retrieved from a data catalog or a
processing platform. This means we use the table information in the platform to
build the `source` part of a data policy. We must either provide a platform or a catalog
id to make the call succeed.

Without a `--processing-platform` or a `--catalog` it just means we interact with the Pace
database and retrieve succesfully applied data policies.

```
pace get data-policy (table-id|policy-id) [flags]
```

### Examples

```
# get a bare policy without rulesets from Catalog Collibra
pace get data-policy --catalog COLLIBRA-testdrive \
	--database 99379294-6e87-4e26-9f09-21c6bf86d415 \
	--schema 342f676c-341e-4229-b3c2-3e71f9ed0fcd \
	6e978083-bb8f-459d-a48b-c9a50289b327
data_policy:
  info:
    title: employee_yearly_income
    description: Google BigQuery
  source:
    attributes:
      - path_components:
          - employee_salary
        type: bigint
      - path_components:
          - employee_id
        type: varchar
	...

# get a bare policy without rulesets from Processing Platform BigQuery
pace get data-policy \
	--processing-platform bigquery-dev \
	stream-machine-development.dynamic_view_poc.gddemo
dataPolicy:
  info:
    createTime: '2023-10-04T09:04:56.246Z'
    description: ''
    title: stream-machine-development.dynamic_view_poc.gddemo
    updateTime: '2023-10-04T09:04:56.246Z'
  platform:
    id: bigquery-dev
    platformType: BIGQUERY
  source:
    attributes:
    - pathComponents:
      - transactionId
      type: INTEGER
    - pathComponents:
      - userId
      type: INTEGER


# get a complete datapolicy from the Pace database
pace get data-policy --processing-platform bigquery-dev \
	stream-machine-development.dynamic_views.cdc_diabetes

id: stream-machine-development.dynamic_views.cdc_diabetes
metadata:
  create_time: "2023-11-02T12:51:23.108319730Z"
  description: ""
  title: stream-machine-development.dynamic_views.cdc_diabetes
  update_time: "2023-11-02T12:51:23.108319730Z"
  version: 1
platform:
  id: bigquery-dev
  platform_type: BIGQUERY
rule_sets:
- field_transforms:
  - field:
      name_parts:
      - HighChol
      type: integer
    transforms:
    - fixed:
        value: blabla
  target:
```

### Options

```
  -c, --catalog string               id of catalog
  -d, --database string              database in the catalog
  -h, --help                         help for data-policy
  -p, --processing-platform string   id of processing platform
  -s, --schema string                schema in database on catalog
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace get](pace_get.md)	 - Get a single entity

