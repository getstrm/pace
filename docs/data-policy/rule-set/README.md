---
description: On-view adaptations of the data
---

# Rule Set

You have defined the structure of your data, either using a [`Processing Platform Integration`](../../reference/processing-platform-integrations/)  a [`Data Catalog Integration`](../../reference/data-catalog-integrations.md) or by hand in the source fields list of the data policy. The [`Principals`](../principals.md) in your processing platform of choice are defined and you want to distinguish regarding what data is available to which principals. Here `Rule Sets` come in to play.

Each `Rule Set` defines a [`Target`](target.md) view, dynamically defined for each (set of) `Principal`. It  allows you to carefully control what principals have access to what data. [`Field Transforms`](field-transform.md) apply transforms (masking, nullifying, replacing, etc.) to a specific field, whereas [`Filters`](filter.md) are able to filter complete rows from the view. Linking transforms and filters to the correct principals makes for a powerful tool.
