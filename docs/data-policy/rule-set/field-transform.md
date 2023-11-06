---
description: Transform values on a field level
---

# Field Transform

## Introduction

To be able to transform the input data on a field-level, we define `Field Transforms`. Specific values can be transformed, masked or completely nullified. If no transform is defined for a field, the field will be included in the resulting view as-is.

This is useful if data needs to be presented differently for different groups of data users. That is, for _fraud detection_ you can usually access a lot more data than you can for _analysis_ or _marketing_ purposes.

The `Field Transform` consists of a `Field` and a list of `Transforms`. The `Field` is a reference to the corresponding field in the source fields.&#x20;

In order to be able to change the on-view results for different users with different roles, each entry in the list of transforms specifies how to transform the specific field for a set of [`Principals`](../principals.md). Principals can be any set of users as defined in the processing platform for the target view. Every `Field Transform` for one `Field` should at least contain one `Transform` without any `Principal`, defined as last item in the list. This `Transform` acts as the default or fallback transform for the specified field.

## Transforms

We define 6 types of transforms.

1.  **Regex**

    Replace a value from a field using regular expressions. Beware that you need to match the syntax for regular expressions of your processing platform as defined in the `Target`. To perform a regex extract, the replacement can either be an empty string or null. Below you will find an example using Snowflake as the processing platform, where we mask (`****`) the local-part of an email address.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
regexp:
  regexp: "^.*(@.*)$"
  replacement: "****\\\\1"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "regexp": {
    "regexp": "^.*(@.*)$",
    "replacement": "****\\\\1"
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after             |
| ----------------------- | ----------------- |
| `local-part@domain.com` | `****@domain.com` |

2.  **Identity**

    Return the original value.&#x20;

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
identity: {}
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "identity": {}
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after                   |
| ----------------------- | ----------------------- |
| `local-part@domain.com` | `local-part@domain.com` |

3.  **Fixed Value**

    Replace a field with a fixed value

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
fixed:  
  value: "****"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "fixed": {
    "value": "****"
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after  |
| ----------------------- | ------ |
| `local-part@domain.com` | `****` |

4.  **Hash**

    Hash a field using an optional seed. The hashing algorithm depends on the processing platform of your choice.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
hash:
  seed: "1234"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "hash": {
    "seed": "1234"
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after                  |
| ----------------------- | ---------------------- |
| `local-part@domain.com` | `-1230500920091472191` |

5.  **SQL Statement**

    Execute a SQL statement to transform the field value. The exact syntax is platform-specific.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
sql_statement:
  statement: "CASE WHEN brand = 'MacBook' THEN 'Apple' ELSE 'Other' END"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "sql_statement": {
    "statement": "CASE WHEN brand = 'MacBook' THEN 'Apple' ELSE 'Other' END"
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before    | after   |
| --------- | ------- |
| `Macbook` | `Apple` |
| `Lenovo`  | `Other` |
| `HP`      | `Other` |
| `Acer`    | `Other` |

6.  **Nullify**

    Make the field value null.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
nullify: {}
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "nullify": {}
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after  |
| ----------------------- | ------ |
| `local-part@domain.com` | `null` |

## Example Field Transform

Below you will find an example of a set of `Field Transforms`. Note that for each set of `Transforms` the last one always is without defined principals.

{% hint style="info" %}
Note that the order of the `Field Transform` in the policy matters. That is, if you are a member of multiple `Principal` groups, for each `Field Transform`, the transform with the first intersection with your `Principal` groups will be applied.
{% endhint %}

In this example, we want to transform the userID for three groups: _Marketing_, _Fraud and Risk_, everyone else. For _Marketing_, we nullify the the ID. For _Fraud and Risk_, we need to retain IDs and do not touch them. For everyone else, we hash the email so they can still be used as keys, but remain unidentified.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
field_transforms:
  - field:
      name_parts: [ userid ]
      type: "string"
      required: true
    transforms:
      - principals:
        - group: "F&R"
        identity: {}
      - principals:
        - group: "MKTNG"
        nullify: {}
      - principals: []
        hash:
          seed: "1234"
  - field:
      name_parts: [ email ]
      type: "string"
      required: true
    transforms:
      - principals:
        - group: "MKTNG"
        - group: "COMP"
        regexp:
          regexp: "^.*(@.*)$"
          replacement: "****\\\\1"
      - principals:
        - group: "F&R"
        identity: {}
      - principals: []
        fixed:
          value: "****"
  - field:
      name_parts: [ brand ]
      type: "string"
      required: true
    transforms:
      - principals: []
        sql_statement:
          statement: "CASE WHEN brand = 'MacBook' THEN 'Apple' ELSE 'Other' END"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "field_transforms": [
    {
      "field": {
        "name_parts": [
          "userid"
        ],
        "type": "string",
        "required": true
      },
      "transforms": [
        {
          "principals": [
            {
              "group": "F&R"
            }
          ],
          "identity": {}
        },
        {
          "principals": [
            {
              "group": "MKTNG"
            }
          ],
          "nullify": {}
        },
        {
          "principals": [],
          "hash": {
            "seed": "1234"
          }
        }
      ]
    },
    {
      "field": {
        "name_parts": [
          "email"
        ],
        "type": "string",
        "required": true
      },
      "transforms": [
        {
          "principals": [
            {
              "group": "MKTNG"
            },
            {
              "group": "COMP"
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
    },
    {
      "field": {
        "name_parts": [
          "brand"
        ],
        "type": "string",
        "required": true
      },
      "transforms": [
        {
          "principals": [],
          "sql_statement": {
            "statement": "CASE WHEN brand = 'MacBook' THEN 'Apple' ELSE 'Other' END"
          }
        }
      ]
    }
  ]
}

```
{% endcode %}
{% endtab %}
{% endtabs %}

## Example Results

Below you will find raw data and sample outputs for different (sets of) principals.

{% tabs %}
{% tab title="RAW Data" %}
| userId | email            | brand   |
| ------ | ---------------- | ------- |
| 123    | alice@store.com  | Macbook |
| 456    | bob@company.com  | Lenovo  |
| 789    | carol@domain.com | HP      |
{% endtab %}
{% endtabs %}

{% tabs %}
{% tab title="[ F&R ]" %}
| userId | email            | brand |
| ------ | ---------------- | ----- |
| 123    | alice@store.com  | Apple |
| 456    | bob@company.com  | Other |
| 789    | carol@domain.com | Other |
{% endtab %}

{% tab title="[ MKTNG ]" %}
| userId | email                | brand |
| ------ | -------------------- | ----- |
| `null` | \*\*\*\*@store.com   | Apple |
| `null` | \*\*\*\*@company.com | Other |
| `null` | \*\*\*\*@domain.com  | Other |
{% endtab %}

{% tab title="[ COMP, F&R ]" %}
| userId | email                | brand |
| ------ | -------------------- | ----- |
| 123    | \*\*\*\*@store.com   | Apple |
| 456    | \*\*\*\*@company.com | Other |
| 789    | \*\*\*\*@domain.com  | Other |


{% endtab %}

{% tab title="[ ANALYSIS ]" %}
| userId              | email    | brand |
| ------------------- | -------- | ----- |
| 23459023894857195   | \*\*\*\* | Apple |
| -903845745009147219 | \*\*\*\* | Other |
| -872050645009147732 | \*\*\*\* | Other |


{% endtab %}
{% endtabs %}
