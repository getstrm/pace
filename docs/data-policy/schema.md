---
description: The source's data structure
---

# Schema

## Introduction

The first step to defining a `Data Policy` is knowing what your source data looks like. This source data most likely will live in a [`Data Platform`](../reference/integrations/processing-platform-integrations/) or [`Data Catalog`](../reference/integrations/data-catalog-integrations/). But you will also be able to define the structure yourself. Below we demonstrate the different options to define your schema

### Blueprint Policy

Below we will talk about getting a _blueprint policy_. A _blueprint policy_ is a `Data Policy` where only the source ref and fields, and potentially a ruleset are populated. This serves as a starting point for defining the rest of the `Data Policy`. A ruleset _can_ be present in the blueprint policy, but this depends on whether [global transforms are defined](../global-actions/global-transforms/). A blueprint policy is retrieved from either a Data Catalog or a Processing Platform

### Sample Blueprint Policy

A blueprint policy consists of metadata such as a _title_, _version_, _create time_ and _last updated time_ as well as user defined _tags_. It has information about the processing platform, being its type and the configured id. But most importantly it contains the fields, or schema, of the source data. Each field consists of an array of `name_parts`, which is the path to field and typically contains only one entry for columnar/flat data. Furthermore, it contains the type, whether or not it is a required field and user defined tags. Lastly the source section contains a reference to the source table and again user defined tags for the source.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
data_policy:
  rule_sets: []
  id: ""
  metadata:
    tags: []
    title: SCHEMA.TABLE
    version: ""
    create_time: null
    update_time: null
  source:
    fields:
      - name_parts:
          - TRANSACTIONID
        tags: []
        type: numeric
        required: true
      - name_parts:
          - USERID
        tags: []
        type: varchar
        required: true
      - name_parts:
          - EMAIL
        tags: []
        type: varchar
        required: true
      - name_parts:
          - AGE
        tags: []
        type: numeric
        required: true
      - name_parts:
          - BRAND
        tags: []
        type: varchar
        required: true
      - name_parts:
          - TRANSACTIONAMOUNT
        tags: []
        type: numeric
        required: true
    tags: []
    ref: SCHEMA.TABLE
  platform:
    platform_type: SNOWFLAKE
    id: snowflake-demo-connection
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
    "data_policy": {
        "rule_sets": [],
        "id": "",
        "metadata": {
            "tags": [],
            "title": "SCHEMA.TABLE",
            "version": "",
            "create_time": null,
            "update_time": null
        },
        "source": {
            "fields": [
                {
                    "name_parts": [
                        "TRANSACTIONID"
                    ],
                    "tags": [],
                    "type": "NUMBER(38,0)",
                    "required": true
                },
                {
                    "name_parts": [
                        "USERID"
                    ],
                    "tags": [],
                    "type": "VARCHAR(16777216)",
                    "required": true
                },
                {
                    "name_parts": [
                        "EMAIL"
                    ],
                    "tags": [],
                    "type": "VARCHAR(16777216)",
                    "required": true
                },
                {
                    "name_parts": [
                        "AGE"
                    ],
                    "tags": [],
                    "type": "NUMBER(38,0)",
                    "required": true
                },
                {
                    "name_parts": [
                        "BRAND"
                    ],
                    "tags": [],
                    "type": "VARCHAR(16777216)",
                    "required": true
                },
                {
                    "name_parts": [
                        "TRANSACTIONAMOUNT"
                    ],
                    "tags": [],
                    "type": "NUMBER(38,0)",
                    "required": true
                }
            ],
            "tags": [],
            "ref": "SCHEMA.TABLE"
        },
        "platform": {
            "platform_type": "SNOWFLAKE",
            "id": "snowflake-demo-connection"
        }
    }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

## Data Platform

If your `Data Platform` (or [`Processing Platform`](../reference/integrations/processing-platform-integrations/)) has knowledge of the source's data structure, we provide both a \[REST API]\(../reference/api-reference. md#processing-platforms-platformid-tables-table\_id-blueprint-policy) and a CLI to receive a _blueprint policy_. Find out what the minimum required permissions are per `Processing Platform` in our [processing platform integration pages](../reference/integrations/processing-platform-integrations/).

## Data Catalog

The source's data structure can also be retrieved from a [`Data Catalog`](../reference/integrations/data-catalog-integrations/). Here too we provide both a \[REST API]\(../reference/api-reference. md#catalogs-catalogid-databases-databaseid-schemas-schemaid-tables-tableid-blueprint-policy) and a CLI to receive the _blueprint policy_. Find out what the minimum required permissions are per `Data Catalog` in our [data catalog integration pages](../reference/integrations/data-catalog-integrations/).
