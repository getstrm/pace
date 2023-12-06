---
description: Transform values on a field level
---

# Field Transform

## Introduction

To be able to transform the input data on a field-level, we define `Field Transforms`. Specific values can be transformed, masked or completely nullified. If no transform is defined for a field, the field will be included in the resulting view as-is.

This is useful if data needs to be presented differently for different groups of data users. That is, for _fraud detection_ you can usually access a lot more data than you can for _analysis_ or _marketing_ purposes.

The `Field Transform` consists of a `Field` and a list of `Transforms`. The `Field` is a reference to the corresponding field in the source fields.

In order to be able to change the on-view results for different users with different roles, each entry in the list of transforms specifies how to transform the specific field for a set of [`Principals`](../principals.md). Principals can be any set of users as defined in the processing platform for the target view. Every `Field Transform` for one `Field` should at least contain one `Transform` without any `Principal`, defined as last item in the list. This `Transform` acts as the default or fallback transform for the specified field.

## Transforms

### 1. Regex

The syntax for the replacement string also follows the target platform's syntax, with **one important difference:** capturing group backreferences always use the **dollar notation**. In other words, you can use dollar-escaped digits (e.g. `$1` or `$2`) within the `replacement` string to insert text matching the corresponding parenthesized group in the `regexp` pattern. Use `$0` to refer to the entire matching text. It is quite common for platforms to limit this to 9 subgroups (i.e. `$1`-`$9`).

The below example replaces the local part of an email address with `****` but preserves the domain part using a capturing group backreference.

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
regexp:
  regexp: "^.*(@.*)$"
  replacement: "****$1"
```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "regexp": {
    "regexp": "^.*(@.*)$",
    "replacement": "****$1"
  }
}
```
{% endcode %}
{% endtab %}
{% endtabs %}

| before                  | after             |
| ----------------------- | ----------------- |
| `local-part@domain.com` | `****@domain.com` |

{% hint style="danger" %}
This transform is not available for [Azure Synapse Analytics](../../integrations-and-reference/integrations/processing-platform-integrations/synapse.md), as Regular Expression (Replacements) are not supported natively in Synapse
{% endhint %}

### **2. Identity**

Return the original value.

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

### **3. Fixed Value**

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

### **4. Hash**

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

### **5. SQL Statement**

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
|-----------|---------|
| `MacBook` | `Apple` |
| `Lenovo`  | `Other` |
| `HP`      | `Other` |
| `Acer`    | `Other` |

We can use User Defined Functions, using SQL or other languages like Python (depending on the processing platform).
We explore a trivial Python UDF on the Databricks platform in [this tutorials page](../../tutorials/udfs.md).

### **6. Nullify**

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

### **7. Detokenize**

If you have tokenized data and the processing platform principal can query the token table, you can detokenize the data.

{% tabs %}
{% tab title="YAML" %}
```yaml
detokenize:
  token_source_ref: my_schema.token_table
  token_field:
    name_parts:
      - token
  value_field:
    name_parts:
      - value
```
{% endtab %}

{% tab title="JSON" %}
```json
{
  "detokenize": {
    "token_source_ref": "my-schema.token-table",
    "token_field": {
      "name_parts": [
        "token"
      ]
    },
    "value_field": {
      "name_parts": [
        "value"
      ]
    }
  }
}
```
{% endtab %}
{% endtabs %}

| before      | after              |
| ----------- | ------------------ |
| `TOKEN-123` | `5425233430109903` |

{% hint style="warning" %}
While multiple detokenize transforms are allowed in a single rule set, each must use a different token source.
{% endhint %}

### **8. Numeric rounding**

For numeric fields, the option exists to round the data. The following rounding options exist:

1. **Ceil**: Apply the ceiling function to the data.
2. **Floor**: Apply the floor function to the data.
3. **Round**: Apply logical rounding to the data.

{% tabs %}
{% tab title="YAML" %}
```yaml
numeric_rounding:
  floor:
    divisor: 20
```

```yaml
numeric_rounding:
  ceil:
    divisor: 20
```

```yaml
numeric_rounding:
  round:
    precision: 0
```
{% endtab %}

{% tab title="JSON" %}
```json
{
  "numeric_rounding": {
    "floor": {
      "divisor": 20
    }
  }
}
```

```json
{
  "numeric_rounding": {
    "ceil": {
      "divisor": 20
    }
  }
}
```

```json
{
  "numeric_rounding": {
    "round": {
      "precision": 20
    }
  }
}
```
{% endtab %}
{% endtabs %}

Examples of rounding:

| Rounding type | Parameters         | Input  | Output |
| ------------- | ------------------ | ------ | ------ |
| Ceil          | Divisor = 10       | 33     | 40     |
| Ceil          | Divisor = 0.1      | 33.3   | 34     |
| Ceil          | Divisor = -10      | 33     | 30[^1] |
| Floor         | Divisor = 10       | 33     | 30     |
| Floor         | Divisor = 0.1      | 33.3   | 33     |
| Floor         | Divisor = -10      | 33     | 40[^2] |
| Round         | Precision = 1      | 33.345 | 33.3   |
| Round         | Precision = 0      | 33.345 | 33     |
| Round         | Precision = -1[^3] | 33.345 | 30     |
| Round         | Precision = -2[^4] | 33.345 | 0      |

{% hint style="warning" %}
Note that ceiling and floor functions with negative numbers may behave differently based on the processing platform.
{% endhint %}

## Example Field Transform

Below you will find an example of a set of `Field Transforms`. Note that for each set of `Transforms` the last one always is without defined principals.

{% hint style="info" %}
Note that the order of the `Field Transform` in the policy matters. That is, if you are a member of multiple `Principal` groups, for each `Field Transform`, the transform with the first intersection with your `Principal` groups will be applied.
{% endhint %}

In this example, we want to transform the userID for three groups: _Marketing_, _Fraud and Risk_, everyone else. For _Marketing_, we nullify the the ID. For _Fraud and Risk_, we need to retain IDs and do not touch them. For everyone else, we hash the email so they can still be used as keys, but remain unidentified. By default, card numbers are tokenized, but for _Fraud and Risk_ they are detokenized, i.e. replaced with their actual values.

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
      name_parts: [ card_number ]
      type: "string"
      required: true
    transforms:
      - principals:
        - group: "F&R"
      detokenize:
        token_source_ref: my_schema.token_table
        token_field:
          name_parts:
            - token
        value_field:
          name_parts:
            - value
      - principals: []
        identity: {}
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
          replacement: "****$1"
      - principals:
        - group: "F&R"
        identity: {}
      - principals: []
        fixed:
          value: "****"
  - field:
      name_parts:
        - age
    transforms:
      - principals:
        - group: "MKTNG"
        identity: { }
      - principals:
        - group: "COMP"
        numeric_rounding:
          floor:
            divisor: 20
      - principals: [ ]
        numeric_rounding:
          round:
            precision: -1
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
<table data-full-width="true"><thead><tr><th>userId</th><th>card_number</th><th>email</th><th data-type="number">age</th><th>brand</th></tr></thead><tbody><tr><td>123</td><td>TOKEN_123</td><td>alice@store.com</td><td>33</td><td>MacBook</td></tr><tr><td>456</td><td>TOKEN_456</td><td>bob@company.com</td><td>26</td><td>Lenovo</td></tr><tr><td>789</td><td>TOKEN_789</td><td>carol@domain.com</td><td>11</td><td>HP</td></tr></tbody></table>
{% endtab %}

{% tab title="my_schema.token_table" %}
| token      | value            |
| ---------- | ---------------- |
| TOKEN\_123 | 5425233430109903 |
| TOKEN\_456 | 2223000048410010 |
| TOKEN\_789 | 2222420000001113 |
{% endtab %}
{% endtabs %}

{% tabs %}
{% tab title="[ F&R ]" %}
<table><thead><tr><th>userId</th><th>card_number</th><th>email</th><th data-type="number">age</th><th>brand</th></tr></thead><tbody><tr><td>123</td><td>5425233430109903</td><td>alice@store.com</td><td>30</td><td>Apple</td></tr><tr><td>456</td><td>2223000048410010</td><td>bob@company.com</td><td>30</td><td>Other</td></tr><tr><td>789</td><td>2222420000001113</td><td>carol@domain.com</td><td>10</td><td>Other</td></tr></tbody></table>
{% endtab %}

{% tab title="[ MKTNG ]" %}
<table><thead><tr><th>userId</th><th>card_number</th><th>email</th><th data-type="number">age</th><th>brand</th></tr></thead><tbody><tr><td><code>null</code></td><td>TOKEN_123</td><td>****@store.com</td><td>33</td><td>Apple</td></tr><tr><td><code>null</code></td><td>TOKEN_456</td><td>****@company.com</td><td>26</td><td>Other</td></tr><tr><td><code>null</code></td><td>TOKEN_789</td><td>****@domain.com</td><td>11</td><td>Other</td></tr></tbody></table>
{% endtab %}

{% tab title="[ COMP, F&R ]" %}
<table><thead><tr><th>userId</th><th>card_number</th><th>email</th><th data-type="number">age</th><th>brand</th></tr></thead><tbody><tr><td>123</td><td>TOKEN_123</td><td>****@store.com</td><td>20</td><td>Apple</td></tr><tr><td>456</td><td>TOKEN_456</td><td>****@company.com</td><td>20</td><td>Other</td></tr><tr><td>789</td><td>TOKEN_789</td><td>****@domain.com</td><td>0</td><td>Other</td></tr></tbody></table>
{% endtab %}

{% tab title="[ ANALYSIS ]" %}
<table><thead><tr><th>userId</th><th>card_number</th><th>email</th><th data-type="number">age</th><th>brand</th></tr></thead><tbody><tr><td>23459023894857195</td><td>TOKEN_123</td><td>****</td><td>30</td><td>Apple</td></tr><tr><td>-903845745009147219</td><td>TOKEN_456</td><td>****</td><td>30</td><td>Other</td></tr><tr><td>-872050645009147732</td><td>TOKEN_789</td><td>****</td><td>10</td><td>Other</td></tr></tbody></table>
{% endtab %}
{% endtabs %}

[^1]: See remark below, this depends on the platform specific implementation.

[^2]: See remark below, this depends on the platform specific implementation.

[^3]: This might not be supported on all processing platforms.

[^4]: This might not be supported on all processing platforms.
