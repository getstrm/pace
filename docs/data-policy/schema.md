---
description: The source's data structure
---

# Schema

## Introduction

The first step to defining a `Data Policy` is knowing what your source data looks like. This source data most likely will live in a [`Data Platform`](../reference/processing-platform-integrations/) or [`Data Catalog`](../reference/data-catalog-integrations/). But you will also be able to define the structure yourself. Below we demonstrate the different options to define your schema

### Bare Policy

Below we will talk about getting a _bare policy_. A _bare policy_ is a `Data Policy` where only the source fields are populated. This serves as a starting point for defining the rest of the `Data Policy`.&#x20;

### Sample Bare Policy

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
                        "HAIRCOLOR"
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

## Data Platform

If your `Data Platform` (or [`Processing Platform`](../reference/processing-platform-integrations/)) has knowledge of the source's data structure, we provide both a [REST API](../reference/api-reference.md#processing-platforms-platformid-tables-table\_id-bare-policy) and a CLI to receive a _bare policy_. Find out what the minimum required permissions are per `Processing Platform` in our [processing platform integration pages](../reference/processing-platform-integrations/).&#x20;

## Data Catalog

The source's data structure can also be retrieved from a [`Data Catalog`](../reference/data-catalog-integrations/). Here too we provide both a [REST API](../reference/api-reference.md#processing-platforms-platformid-tables-table\_id-bare-policy) and a CLI to receive the _bare policy_. Find out what the minimum required permissions are per `Data Catalog` in our [data catalog integration pages](../reference/data-catalog-integrations/).&#x20;
