---
description: Complete walkthrough of creating a Data Policy
---

# Create a Data Policy

## Introduction

For this section, we assume you have either created a connection to a [`Processing Platform`](connect-a-processing-platform.md) or a [`Data Catalog`](connect-a-data-catalog.md) or you are familiar with the structure of your source data. You will, naturally, also need to have an instance of `PACE` running. We give step-by-step walkthrough on how to create a `Data Policy`.&#x20;

## Source Data

For this example we will use a very small sample data set:

<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>alice@domain.com</td><td>16</td></tr><tr><td>bob@company.org</td><td>18</td></tr><tr><td>charlie@store.io</td><td>20</td></tr></tbody></table>

## Get bare policy

Let the id of your connected `Processing Platform` be `pace-pp` and the source be `pace-db.pace-schema.pace-table`. Getting a bare policy yields a nearly empty `Data Policy` with populated source fields, thus defining the structure of your source data. In this example we will use both the [`pace cli`](https://github.com/getstrm/cli) and call the [REST API](../reference/api-reference.md#processing-platforms-platformid-tables-table\_id-bare-policy) using `curl`. The request has two variables, `platform-id` and `table-id`. Provided the instance is running on localhost and the envoy proxy is listening on port 9090, this results in the following command for getting the bare policy and saving it to a JSON file:

{% tabs %}
{% tab title="CLI" %}
```bash
pace get data-policy --bare -p pace-pp pace-db.pace-schema.pace-table
```
{% endtab %}

{% tab title="curl" %}
{% code overflow="wrap" %}
```bash
curl localhost:9090/processing-platforms/pace-pp/tabl/bare-policy > bare_policy.json
```
{% endcode %}
{% endtab %}
{% endtabs %}

The resulting YAML

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

As you can see, the [`Rule Sets`](../data-policy/rule-set/) are empty and only the refs and `source.fields` has been populated. This dataset consists of 2 columns: `email`, `age`. Now that we know our data's structure, the next step is populating the yaml with a `Rule Set`.&#x20;

## Define Rule Sets

In this example we will show one `Rule Set`, with one `Field Transform` and one `Filter`. If you define multiple `Rule Sets` in your data policy, multiple views will be created. For a more extensive explanation of `Rule Sets`, we provide more in-depth documentation on [`Principals`](../data-policy/principals.md), [`Field Transforms`](../data-policy/rule-set/field-transform.md) and [`Filters`](../data-policy/rule-set/filter.md).&#x20;

### Target

Let's start by defining the reference to the target table. We have chosen the target schema to be the same as the source schema. The [`Target`](../data-policy/rule-set/target.md) should then be defined as

{% code lineNumbers="true" %}
```yaml
rule_sets:
  - target:
      fullname: "pace-db.pace-schema.pace-view"
```
{% endcode %}

### Field Transform

We define one `Field Transform` and add it to the `Rule Set`. Our transform concerns the `email` field. The `field` definition is corresponding to the same field in the source fields. We define a [`Transform`](../data-policy/rule-set/field-transform.md#transforms) for the _Marketing_ (`MKTNG`) principal and one for the _Fraud and Risk_ (`F&R`) principal. For the _Marketing_ principal the _local-part_ of the email is replaced by `****` while leaving the `@` and the domain as-is, using a regular expression with replacement. For the _Fraud and Risk_ principal we apply an `identity` transform, returning the email as-is. Finally, if the viewer is not a member of either of these Principals, a fixed value `****` is returned instead of the email. More guidance and examples on how to define `Transforms` see the [docs](../data-policy/rule-set/field-transform.md#transforms).

{% code lineNumbers="true" %}
```yaml
- field:
    name_parts: 
      - email
    type: "string"
    required: true
    tags: []
  transforms:
    - principals: 
        - group: "MKTNG"
      regexp:
        regexp: "^.*(@.*)$"
        replacement: "****\\\\1"
    - principals:
      - group: "F&R"
      identity: {}
    - principals: []
      fixed:
        value: "****"
```
{% endcode %}

### Filter

To completely filter out rows, we here define one `Filter`  based on the `age` field. For the _Fraud and Risk_ principal we return all rows whereas for all other principals we filter out all children from the target view. More details on `Filters` can be found [here](create-a-data-policy.md#filter).

{% code lineNumbers="true" %}
```yaml
filters:
  - conditions:
    - principals:
      - group: "F&R"
      condition: "true"
    - principals: []
      condition: "age > 18"
```
{% endcode %}

### Rule Set

Putting it all together in one `Rule Set`:

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
              replacement: "****\\\\1"
          - principals:
            - group: "F&R"
            identity: {}
          - principals: []
            fixed:
              value: "****"
    filters:
      - conditions:
        - principals:
          - group: "F&R"
          condition: "true"
        - principals: []
          condition: "age > 18"
```
{% endcode %}

## Upsert the Data Policy

Below you will find the resulting Data Policy in both YAML and JSON format.&#x20;

{% tabs %}
{% tab title="JSON" %}
<pre class="language-json" data-title="data_policy.json" data-line-numbers><code class="lang-json"><strong>{
</strong>  "data_policy": {
    "id": "",
    "metadata": {
      "title": "pace-db.pace-schema.gddemo",
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
      "ref": "pace-db.pace-schema.gddemo",
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
                  "replacement": "****\\\\1"
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

{% tab title="YAML" %}
{% code title="data_policy.yaml" lineNumbers="true" %}
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
                replacement: "****\\\\1"
            - principals:
              - group: "F&R"
              identity: {}
            - principals: []
              fixed:
                value: "****"
      filters:
        - conditions:
          - principals:
            - group: "F&R"
            condition: "true"
          - principals: []
            condition: "age > 18"
```
{% endcode %}
{% endtab %}
{% endtabs %}

Assuming we have saved the policy as `data_policy.json` or `data_policy.yaml` in your current working directory, we can upsert the Data Policy. The CLI accepts both YAML and JSON, curl only JSON:

{% tabs %}
{% tab title="CLI" %}
```bash
pace upsert data-policy ./data_policy.yaml
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

## View data

Depending on what `Principal` groups you are in, you will find that the actual data you have access to via the newly created view differs. Below you will find again the raw data and the views applied for several `Principals`.&#x20;

{% tabs %}
{% tab title="RAW Data" %}
<table><thead><tr><th>email</th><th data-type="number">age</th></tr></thead><tbody><tr><td>alice@domain.com</td><td>16</td></tr><tr><td>bob@company.org</td><td>18</td></tr><tr><td>charlie@store.io</td><td>20</td></tr></tbody></table>
{% endtab %}

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

For more examples and explaination visit the [`Rule Set` Documentation](../data-policy/rule-set/).
