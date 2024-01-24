---
description: Using tags in Databricks
---

# Databricks

Databricks allows adding tags with a key and a value. Only the `key` is being used by PACE.

<figure><img src="../../../.gitbook/assets/image (11).png" alt=""><figcaption></figcaption></figure>

```
pace get data-policy --processing-platform dbr-pace \
  --database pace --schema alpha_test demo
...
platform:
  id: dbr-pace
  platform_type: DATABRICKS
...
source:
  fields:
  ...
  - name_parts:
    - email
    tags:
    - pii_email
    type: varchar
    ...
```
