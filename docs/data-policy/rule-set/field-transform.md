---
description: Transform values on a field level
---

# Field Transform

### Introduction

To be able to transform the input data on a field-level, we define `FieldTransforms`. Specific values can be transformed, masked or completely nullified. If no transform is defined for a field, the field will be included in the result set as-is.

This is useful if data needs to be presented differently for different groups of data users. That is, for _fraud detection_ you can usually access a lot more data than you can for _analysis_ or _marketing_ purposes.

The `FieldTransform` consists of a `Field` and a list of `Transforms`. The `Field` is a reference to the corresponding field in the source fields.&#x20;

In order to be able to change the on-view results for different users with different roles, each entry in the list of transforms specifies how to transform the specific field for a set of [`Principals`](../principals.md). Principals can be any set of users as defined in the processing platform for the target view. Every `FieldTransform` for one `Field` should at least contain one `Transform` without any `Principal`, defined as last item in the list. This `Transform` acts as the default or fallback transform for the specified field.

### Transforms

We define 6 types of transforms.

1.  **Regex**

    Replace a value from a field using regular expressions. Beware that you need to match the syntax for regular expressions of your processing platform as defined in the `Target`. To perform a regex extract, the replacement can either be an empty string or null. Below you will find an example using Snowflake as the processing platform, where we mask (`****`) the local-part of an email address.

```yaml
regexp:
  regexp: "^.*(@.*)$"
  replacement: "****\\\\1"
```

| before                  | after             |
| ----------------------- | ----------------- |
| `local-part@domain.com` | `****@domain.com` |

2.  **Identity**

    Return the original value.&#x20;

```yaml
identity: {}
```

| before                  | after                   |
| ----------------------- | ----------------------- |
| `local-part@domain.com` | `local-part@domain.com` |

3.  **Fixed Value**

    Replace a field with a fixed value

```yaml
fixed:  
  value: "****"
```

| before                  | after  |
| ----------------------- | ------ |
| `local-part@domain.com` | `****` |

4.  **Hash**

    Hash a field using an optional seed. The  hashing algorithm depends on the processing platform of your choice.

```yaml
hash:
  seed: "1234"
```

| before                  | after                  |
| ----------------------- | ---------------------- |
| `local-part@domain.com` | `-1230500920091472191` |

5.  **SQL Statement**

    Execute a SQL statement to transform the field value. The exact syntax is platform-specific.

```yaml
sql_statement:
  statement: "CASE WHEN haircolor = 'blonde' THEN 'fair' ELSE 'dark' END"
```

| before   | after  |
| -------- | ------ |
| `blonde` | `fair` |
| `red`    | `dark` |
| `brown`  | `dark` |
| `black`  | `dark` |

6.  **Nullify**

    Make the field value null.

```yaml
nullify: {}
```

| before                  | after  |
| ----------------------- | ------ |
| `local-part@domain.com` | `null` |

### Example Field Transform

Below you will find an example of a set of `Field Transform`. Note that for each set of `Transform` the last one always is without defined principals.

In this example, we want to transform the userID for three groups: Marketing, Fraud and Risk, everyone else. For Marketing, we nullify the the ID. For Fraud and Risk, we need to retain IDs and do not touch them. For everyone else, we hash the email so they can still be used as keys, but remain unidentified.

```yaml
field_transforms:
  - field:
      name_parts: [ userid ]
      type: "string"
      required: true
    transforms:
      - principals: [ "FRAUD_AND_RISK"]
        identity: {}
      - principals: [ "MARKETING" ]
        nullify: {}
      - principals: []
        hash:
          seed: "1234"
  - field:
      name_parts: [ email ]
      type: "string"
      required: true
    transforms:
      - principals: [ MARKETING ]
        regexp:
          regexp: "^.*(@.*)$"
          replacement: "****\\\\1"
      - principals: [ FRAUD_AND_RISK ]
        identity: {}
      - principals: []
        fixed:
          value: "****"
  - field:
      name_parts: [ haircolor ]
      type: "string"
      required: true
    transforms:
      - principals: []
        sql_statement:
          statement: "CASE WHEN haircolor = 'blonde' THEN 'fair' ELSE 'dark' END"

```
