---
description: >-
  PACE can use a data catalog to retrieve schemas and labels or tags on tables
  or table columns. These can be used to create data-policies that will be
  applied on the processing platforms.
---

# Data Catalog Integrations

PACE can retrieve metadata from a data catalog. This includes schemas, as well as field or table tags. These can be used to create Data Policies that will be applied on the processing platforms.

The tables in a data catalog are referenced via the following hierarchy

* data catalog(s)
  * database(s)
    * schema(s)
      * table(s)

The PACE cli clearly shows this hierarchy.

{% code title="list all configured data catalogs" fullWidth="false" %}
```
pace list catalogs --output table
catalogs:
- id: COLLIBRA-testdrive
  type: COLLIBRA
- id: datahub
  type: DATAHUB
- id: odd
  type: ODD
```
{% endcode %}

{% code title="list the databases on Collibra" fullWidth="false" %}
```
pace list databases --catalog COLLIBRA-testdrive --output table
databases:
- catalog:
    id: COLLIBRA-testdrive
    type: COLLIBRA
  id: 8665f375-e08a-4810-add6-7af490f748ad
  type: Snowflake
- catalog:
    id: COLLIBRA-testdrive
    type: COLLIBRA
  id: 99379294-6e87-4e26-9f09-21c6bf86d415
  type: CData JDBC Driver for Google BigQuery 2021
- catalog:
    id: COLLIBRA-testdrive
    type: COLLIBRA
  id: b6e043a7-88f1-42ee-8e81-0fdc1c96f471
  type: Snowflake
```
{% endcode %}

{% code overflow="wrap" fullWidth="false" %}
```
pace list schemas --catalog COLLIBRA-testdrive \
   --database 99379294-6e87-4e26-9f09-21c6bf86d415 -o table
 ID                                     NAME

 c0a8b864-83e7-4dd1-a71d-0c356c1ae9be   Google BigQuery>test-drive-329411>Marketing
 342f676c-341e-4229-b3c2-3e71f9ed0fcd   Google BigQuery>test-drive-329411>HR

```
{% endcode %}

{% code fullWidth="false" %}
```
# command output shortened
pace list tables --catalog COLLIBRA-testdrive \
  --database 99379294-6e87-4e26-9f09-21c6bf86d415 \
  --schema 342f676c-341e-4229-b3c2-3e71f9ed0fcd -o table
tables:
- id: 821b684d-7fd4-428f-8d10-8e90f52aa5b9
  name: Google BigQuery>test-drive-329411>HR>attendancelogs
  ...
- id: f9ad905f-09e6-4259-a8a2-80b135cd3f1b
  name: Google BigQuery>test-drive-329411>HR>employees_income
- id: 8254e494-7856-4f2c-b736-6f6ca310081a
  name: Google BigQuery>test-drive-329411>HR>accounts_salarypayments
- id: 27231897-24f9-4b26-9171-afe8d88156c7
  name: Google BigQuery>test-drive-329411>HR>employeearchive
- id: 5f345874-0055-4349-8f7d-0bfab88796a1
  name: Google BigQuery>test-drive-329411>HR>departments
- id: 89b34f6f-4664-4a6a-99c6-2cdc27abd5c3
  name: Google BigQuery>test-drive-329411>HR>payroll
- id: 5ad8ea41-df6d-4421-9da5-791d0461f7f7
  name: Google BigQuery>test-drive-329411>HR>salaries
- id: 6e978083-bb8f-459d-a48b-c9a50289b327
  name: Google BigQuery>test-drive-329411>HR>employee_yearly_income
```
{% endcode %}

We can retrieve a bare policy from a catalog as follows.

```
pace get data-policy --catalog datahub-on-dev --database ...\
  --schema .. .. 
metadata:
  description: snowflake
  tags:
  - purpose:marketing
  title: Snowflake
source:
  fields:
  - name_parts:
    - transactionid
    type: numeric
  - name_parts:
    - userid
    tags:
    - PII
    - sensitive
    type: varchar
  - name_parts:
    - email
    tags:
    - PII
    - sensitive
    - countrycodematch
    type: varchar
  - name_parts:
    - age
    tags:
    - sensitive
    type: numeric
  - name_parts:
    - size
    type: varchar
  - name_parts:
    - haircolor
    tags:
    - PII
    type: varchar
  - name_parts:
    - transactionamount
    tags:
    - sensitive
    type: numeric
  - name_parts:
    - items
    type: varchar
  - name_parts:
    - itemcount
    type: numeric
  - name_parts:
    - date
    type: time
  - name_parts:
    - purpose
    type: numeric

```

We would typically redirect the output of this command into a file `> bare.yaml`, and add a rule set to the file. See [create-a-data-policy.md](../../getting-started/create-a-data-policy.md "mention")for details on how to do this.
