---
description: A handy list with explanations of PACE terminology and concepts
---

# Glossary

**Schema**

A [`Schema`](../data-policy/schema.md) is your data structure, i.e. the column headers and data type.

**Principals**

[`Principals`](../data-policy/principals.md) are the way you authenticate to a data processing platform or catalog.

**Data Policy**

A [`Data Policy`](../cli-docs/pace\_upsert\_data-policy.md) is the combination of schema and [`Rule Sets`](../data-policy/rule-set/).&#x20;

**Data Contract**

A data contract is another way to refer to PACE's data policy definitions.&#x20;

**Rule set**

A [`Rule Set`](../data-policy/rule-set/) is a definition of how to treat a specific dataset to turn it into a dynamic view in a processing platform.&#x20;

It includes:

* [`Field Transforms`](../data-policy/rule-set/field-transform.md), how to transform data (e.g. regex or nullify)
* How to [`Filter`](../data-policy/rule-set/filter.md) data for a user based on conditions.
* [Access](../cli-docs/pace\_list\_groups.md) definitions (who can access the resulting view?)

**Processing platform**

A [`Processing Platform`](../reference/processing-platform-integrations/) is commonly referred to as a data platform, like Snowflake, Databricks or Bigquery.

**Data Catalog**

A [`Data Catalog`](../cli-docs/pace\_list\_catalogs.md) is your data shopping experience. For PACE, it's the source of the schema and global policies.&#x20;
