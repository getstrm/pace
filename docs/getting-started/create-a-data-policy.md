---
description: Complete walkthrough of creating a Data Policy
---

# Create a Data Policy

## Introduction

For this section, we assume you have either created a connection to a [`Processing Platform`](../reference/integrations/processing-platform-integrations/) or a [`Data Catalog`](../reference/integrations/data-catalog-integrations/) or you are familiar with the structure of your source data. You will, naturally, also need to have an instance of `PACE` running. We give a step-by-step walkthrough on how to create a `Data Policy`.&#x20;

Please refer to the [schema.md](../data-policy/schema.md "mention"), [principals.md](../data-policy/principals.md "mention") and [rule-set](../data-policy/rule-set/ "mention") sections for additional explanations.

## Source Data

For this example we will use a very small sample data set:

<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>alice@domain.com</td><td>16</td></tr><tr><td>bob@company.org</td><td>18</td></tr><tr><td>charlie@store.io</td><td>20</td></tr></tbody></table>

## Get blueprint policy

Let the id of your connected `Processing Platform` be `pace-pp` and the source reference be `pace-db.pace-schema. pace-table`. Getting a blueprint policy yields a nearly empty `Data Policy` with populated source fields, thus defining the structure of your source data. In this example we will use both the [`pace cli`](https://github.com/getstrm/cli) and call the \[REST API]\(../reference/api-reference. md#processing-platforms-platformid-tables-table\_id-blueprint-policy) using `curl`. The request has two variables, `platform-id` and `table-id`. The CLI makes use of gRPC. We assume here that the instance is running on localhost, the gRPC port is 50051 and the envoy proxy is listening on port 9090 as per the defaults. For the CLI holds, that if you specify the processing platform and the reference to the table, the corresponding blueprint policy is returned. Requesting a blueprint policy then yields:

{% tabs %}
{% tab title="CLI" %}
```bash
pace get data-policy --blueprint -p pace-pp \
    --database pace-db --schema pace-schema pace-table
```
{% endtab %}

{% tab title="curl" %}
{% code overflow="wrap" %}
```bash
```
{% endcode %}
{% endtab %}
{% endtabs %}

The resulting blueprint policy:

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
data_policy:
  id: ""
  metadata:
    title: pace-db.pace-schema.pace-table
    description: ""
    version: ""
    create_time: 2023-11-03T12:00:00.000Z
    update_time: 2023-11-03T12:00:00.000Z
    tags: []
  source:
    ref: pace-db.pace-schema.pace-table
    fields:
      - name_parts:
          - email
        type: STRING
        required: false
        tags: []
      - name_parts:
          - age
        type: INTEGER
        required: false
        tags: []
    tags: []
  platform:
    platform_type: <PLATFORM_TYPE>
    id: pace-pp
  rule_sets: []
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "data_policy": {
    "id": "",
    "metadata": {
      "title": "pace-db.pace-schema.pace-table",
      "description": "",
      "version": "",
      "create_time": "2023-11-03T12:00:00.000Z",
      "update_time": "2023-11-03T12:00:00.000Z",
      "tags": []
    },
    "source": {
      "ref": "pace-db.pace-schema.pace-table",
      "fields": [
        {
          "name_parts": [
            "email"
          ],
          "type": "STRING",
          "required": false,
          "tags": []
        },
        {
          "name_parts": [
            "age"
          ],
          "type": "INTEGER",
          "required": false,
          "tags": []
        }
      ],
      "tags": []
    },
    "platform": {
      "platform_type": "<PLATFORM_TYPE>",
      "id": "pace-pp"
    },
    "rule_sets": []
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

As you can see, the [`Rule Sets`](../data-policy/rule-set/) are empty and only the refs and `source.fields` have been populated. This dataset consists of 2 columns: `email`, `age`. Now that we know our data's structure, the next step is populating the yaml with a `Rule Set`.

If you do not have a `Processing Platform` or `Data Catalog` connected yet, use the blueprint policy from above to define the structure of your data by hand.

## Define Rule Sets

In this example we will show one `Rule Set`, with one `Field Transform` and one `Filter`. If you define multiple `Rule Sets` in your data policy, multiple views will be created. For a more extensive explanation on `Rule Sets`, we provide more in-depth documentation on [`Principals`](../data-policy/principals.md), [`Field Transforms`](../data-policy/rule-set/field-transform.md) and [`Filters`](../data-policy/rule-set/filter.md).

### Target

Let's start by defining the reference to the target table. We have chosen the target schema to be the same as the source schema. The [`Target`](../data-policy/rule-set/target.md) should then be defined as

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
rule_sets:
  - target:
      fullname: "pace-db.pace-schema.pace-view"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "rule_sets": [
    {
      "target": {
        "fullname": "pace-db.pace-schema.pace-view"
      }
    }
  ]
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

### Field Transform

We define one `Field Transform` and add it to the `Rule Set`. Our transform concerns the `email` field. The `field` definition is corresponding to the same field in the source fields. We define a [`Transform`](../data-policy/rule-set/field-transform.md#transforms) for the _Marketing_ (`MKTNG`) principal and one for the _Fraud and Risk_ (`F&R`) principal. For the _Marketing_ principal the _local-part_ of the email is replaced by `****` while leaving the `@` and the domain as-is, using a regular expression with replacement. For the _Fraud and Risk_ principal we apply an `identity` transform, returning the email as-is. Finally, if the viewer is not a member of either of these Principals, a fixed value `****` is returned instead of the email. More guidance and examples on how to define `Transforms` see the [docs](../data-policy/rule-set/field-transform.md#transforms).

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
- field:
    name_parts: 
      - email
  transforms:
    - principals: 
        - group: "MKTNG"
      regexp:
        regexp: "^.*(@.*)$"
        replacement: "****$1"
    - principals:
      - group: "F&R"
      identity: {}
    - principals: []
      fixed:
        value: "****"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
[
  {
    "field": {
      "name_parts": [
        "email"
      ],
      "type": "string",
      "required": true,
      "tags": []
    },
    "transforms": [
      {
        "principals": [
          {
            "group": "MKTNG"
          }
        ],
        "regexp": {
          "regexp": "^.*(@.*)$",
          "replacement": "****$1"
        }
      },
      {
        "principals": [
          {
            "group": "F&R"
          }
        ],
        "identity": {}
      },
      {
        "principals": [],
        "fixed": {
          "value": "****"
        }
      }
    ]
  }
]
```
{% endcode %}
{% endtab %}
{% endtabs %}

### Filter

To completely filter out rows, we here define one `Filter` based on the `age` field. Note that the condition can contain any arbitrary logic with any number of fields. For the _Fraud and Risk_ principal we return all rows whereas for all other principals we exclude all children from the target view. More details on `Filters` can be found [here](create-a-data-policy.md#filter).

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
filters:
  - generic_filter:
      conditions:
      - principals:
        - group: "F&R"
        condition: "true"
      - principals: []
        condition: "age > 18"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "filters": [
    {
      "conditions": [
        {
          "principals": [
            {
              "group": "F&R"
            }
          ],
          "condition": "true"
        },
        {
          "principals": [],
          "condition": "age > 18"
        }
      ]
    }
  ]
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

### Rule Set

Putting it all together in one `Rule Set`:

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
rule_sets:
  - target:
      fullname: "pace-db.pace-schema.pace-view"
    field_transforms:
      - field:
          name_parts: 
            - email
          type: "string"
          required: true
          tags: []
        transforms:
          - principals:
            - group: "MKTING"
            regexp:
              regexp: "^.*(@.*)$"
              replacement: "****$1"
          - principals:
            - group: "F&R"
            identity: {}
          - principals: []
            fixed:
              value: "****"
    filters:
      - generic_filter:
        - conditions:
          - principals:
            - group: "F&R"
            condition: "true"
          - principals: []
            condition: "age > 18"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "rule_sets": [
    {
      "target": {
        "fullname": "pace-db.pace-schema.pace-view"
      },
      "field_transforms": [
        {
          "field": {
            "name_parts": [
              "email"
            ],
            "type": "string",
            "required": true,
            "tags": []
          },
          "transforms": [
            {
              "principals": [
                {
                  "group": "MKTING"
                }
              ],
              "regexp": {
                "regexp": "^.*(@.*)$",
                "replacement": "****$1"
              }
            },
            {
              "principals": [
                {
                  "group": "F&R"
                }
              ],
              "identity": {}
            },
            {
              "principals": [],
              "fixed": {
                "value": "****"
              }
            }
          ]
        }
      ],
      "filters": [
        {
          "conditions": [
            {
              "principals": [
                {
                  "group": "F&R"
                }
              ],
              "condition": "true"
            },
            {
              "principals": [],
              "condition": "age > 18"
            }
          ]
        }
      ]
    }
  ]
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

## Upsert the Data Policy

Below you will find the resulting Data Policy.

{% tabs %}
{% tab title="YAML" %}
{% code title="data_policy.yaml" lineNumbers="true" %}
```yaml
metadata:
  description: ""
  version: 1
  title: public.demo
platform:
  id: standalone-sample-connection
  platform_type: POSTGRES
source:
  fields:
    - name_parts:
        - transactionid
      required: true
      type: integer
    - name_parts:
        - userid
      required: true
      type: integer
    - name_parts:
        - email
      required: true
      type: varchar
    - name_parts:
        - age
      required: true
      type: integer
    - name_parts:
        - brand
      required: true
      type: varchar
    - name_parts:
        - transactionamount
      required: true
      type: integer
  ref: public.demo
rule_sets:
  - target:
      fullname: "pace-db.pace-schema.pace-view"
    field_transforms:
      - field:
          name_parts: 
            - email
          type: "string"
          required: true
          tags: []
        transforms:
          - principals:
            - group: "MKTING"
            regexp:
              regexp: "^.*(@.*)$"
              replacement: "****$1"
          - principals:
            - group: "F&R"
            identity: {}
          - principals: []
            fixed:
              value: "****"
    filters:
      - generic_filter:
        - conditions:
          - principals:
            - group: "F&R"
            condition: "true"
          - principals: []
            condition: "age > 18"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
<pre class="language-json" data-title="data_policy.json" data-line-numbers><code class="lang-json"><strong>{
</strong>  "data_policy": {
    "id": "",
    "metadata": {
      "title": "pace-db.pace-schema.pace-table",
      "description": "",
      "version": "",
      "create_time": {
        "nanos": 0,
        "seconds": 1699012800
      },
      "update_time": {
        "nanos": 0,
        "seconds": 1699012800
      }
    },
    "source": {
      "ref": "pace-db.pace-schema.pace-table",
      "fields": [
        {
          "name_parts": [
            "email"
          ],
          "type": "STRING",
          "required": false,
          "tags": []
        },
        {
          "name_parts": [
            "age"
          ],
          "type": "INTEGER",
          "required": false,
          "tags": []
        }
      ],
      "tags": []
    },
    "platform": {
      "platform_type": "&#x3C;PROCESSING-PLATFORM-TYPE>",
      "id": "pace-pp"
    },
    "rule_sets": [
      {
        "target": {
          "fullname": "pace-db.pace-schema.pace-view"
        },
        "field_transforms": [
          {
            "field": {
              "name_parts": [
                "email"
              ],
              "type": "string",
              "required": true,
              "tags": []
            },
            "transforms": [
              {
                "principals": [
                    {
                    "group": "MKTNG" 
                    }
                ],
                "regexp": {
                  "regexp": "^.*(@.*)$",
                  "replacement": "****$1"
                }
              },
              {
                "principals": [
                    {
                    "group": "F&#x26;R" 
                    }
                ],
                "identity": {}
              },
              {
                "principals": [],
                "fixed": {
                  "value": "****"
                }
              }
            ]
          }
        ],
        "filters": [
          {
            "conditions": [
              {
                "principals": [
                    {
                    "group": "F&#x26;R" 
                    }
                ],
                "condition": "true"
              },
              {
                "principals": [],
                "condition": "age > 18"
              }
            ]
          }
        ]
      }
    ]
  }
}
</code></pre>
{% endtab %}
{% endtabs %}

Assuming we have saved the policy as `data_policy.json` or `data_policy.yaml` in your current working directory, we can upsert the Data Policy. The CLI accepts both YAML and JSON, curl only JSON:

{% tabs %}
{% tab title="CLI" %}
```bash
pace upsert data-policy ./data_policy.yaml --apply
```
{% endtab %}

{% tab title="curl" %}
{% code overflow="wrap" %}
```bash
curl -X POST -H "Content-Type: application/json" -d @./data_policy.json localhost:9090/data-policies
```
{% endcode %}
{% endtab %}
{% endtabs %}

{% hint style="info" %}
By adding the `--apply` flag, the Data Policy is applied immediately, and so is therefore the corresponding SQL view. It is possible to first upsert and only later apply a policy, in which case the ID and platform of the upserted policy must be provided:

```
pace apply data-policy your-data-policy-id -p your-platform-id
```
{% endhint %}

## View data

Depending on what `Principal` groups you are in, you will find that the actual data you have access to via the newly created view differs. Below you will find again the raw data and the views applied for several `Principals`.

{% tabs %}
{% tab title="RAW Data" %}
<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>alice@domain.com</td><td>16</td></tr><tr><td>bob@company.org</td><td>18</td></tr><tr><td>charlie@store.io</td><td>20</td></tr></tbody></table>
{% endtab %}
{% endtabs %}

{% tabs %}
{% tab title="[ MKTNG ]" %}
<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>****@store.io</td><td>20</td></tr></tbody></table>
{% endtab %}

{% tab title="[ F&R ]" %}
<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>alice@domain.com</td><td>16</td></tr><tr><td>bob@company.org</td><td>18</td></tr><tr><td>charlie@store.io</td><td>20</td></tr></tbody></table>
{% endtab %}

{% tab title="[ CMPLNCE ]" %}
<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>****</td><td>16</td></tr></tbody></table>
{% endtab %}
{% endtabs %}

For more examples and explanation visit the [`Rule Set` Documentation](../data-policy/rule-set/).
