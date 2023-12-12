---
description: Using tags in BigQuery
---

# BigQuery

In BigQuery we use [Policy tags](https://console.cloud.google.com/bigquery/policy-tags) to add PACE tags to a data column. Policy tags are part of a Policy tag taxonomy, which you would first have to create. Here I've created a taxonomy named `pace` with a Policy tag named `pii_email`

<figure><img src="../../../.gitbook/assets/image (5).png" alt=""><figcaption></figcaption></figure>

You don't _have to enforce_ the BigQuery Policy tags, for PACE we only use the Policy tag name. Once you have the Policy tags, you can add them to columns.

<figure><img src="../../../.gitbook/assets/image (6).png" alt=""><figcaption></figcaption></figure>

The taxonomy name is ignored by PACE but the `pii_email` Policy tag name becomes available as a PACE tag.

```
pace get data-policy --processing-platform bigquery-dev\
  stream-machine-development.dynamic_views.pace_demo
metadata:
  create_time: "2023-11-09T13:57:40.336Z"
  title: stream-machine-development.dynamic_views.pace_demo
  update_time: "2023-11-09T14:03:03.613Z"
platform:
  id: bigquery-dev
  platform_type: BIGQUERY
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
  ref: stream-machine-development.dynamic_views.pace_demo

```
