---
description: Using tags in Snowflake
---

# Snowflake

In Snowflake one needs to create tags before they can be added to a column. The easiest way is by executing a SQL statement.

```
create tag pii_email;
```

Even though you've created a lowercase `pii_email` tag, it will be transformed to uppercase `PII_EMAIL` by Snowflake. Once you've created the tag, you can assign it to a column.

<figure><img src="../../../.gitbook/assets/image (9).png" alt=""><figcaption></figcaption></figure>

Note that you **must** give the tag a value, even though Pace doesn't use it. Now that you've added the tag to Snowflake, it becomes visible in the blueprint policies.

```
pace get data-policy --processing-platform sf-pace ALPHA_TEST.GDDEMO
metadata:
  title: ALPHA_TEST.GDDEMO
platform:
  id: sf-pace
  platform_type: SNOWFLAKE
source:
  fields:
  - ...
  - name_parts:
    - EMAIL
    required: true
    tags:
    - PII_EMAIL
    type: varchar
  - ...
  ref: ALPHA_TEST.GDDEMO


```
