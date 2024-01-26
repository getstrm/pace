---
description: Define once, reuse in all data policies
---

# Global Transforms

Global Transforms are a way to define reusable transformations of data that can be reused in multiple data policies. This is useful for example, when you want apply the same data transformation to fields that should be considered as `email`. Or another use case, say you want to treat string-based Personal Identifiable Information (PII) similarly, for example to nullify the data.

## Types

Currently, we support one type of global transforms, which are tag based.

### Tag based global transforms

A tag based global transform, means that the transform will be included in the blueprint Data Policy whenever the tag of the global transform is present on the data field. As we are talking about the [retrieval of a blueprint Data Policy](../../data-policy/schema.md#blueprint-policy), it is retrieved from a Data Catalog or a Processing Platform. Tags are also retrieved from the respective connection, as many catalogs and processing platforms allow defining tags (only value based or key-value based) on field level of a table.

How tags can be added to data fields for each data catalog and processing platform is described in [sub sections of this document](processing-platform-tags/).

## Creating global transforms

A global transform can be created by creating a YAML or JSON file that complies with the type [`GlobalTransform`](https://github.com/getstrm/pace/blob/870001670fda4b95583628d71c008692a9d822cc/protos/getstrm/pace/api/entities/v1alpha/entities.proto#L157). An example global transform is shown below.

{% code title="example-global-transform.yaml" lineNumbers="true" %}
```yaml
tag_transform:
  tag_content: "pii-email"
  transforms:
    # The administrator group can see all data
    - principals: [ { group: administrator } ]
      identity: { }
    # All other users should not see the data
    - principals: [ ]
      nullify: { }
```
{% endcode %}

The global transform reads as follows:

> When creating a blueprint Data Policy, and when a field is tagged with `pii-email`, add the following transform to a ruleset:
>
> * Users in the `administrator` group should see the data as is.
> * Users not in the `administrator` group, should see a `null` value.

The global transform can be created easily with the CLI:

```bash
pace upsert global-transform example-global-transform.yaml
```

## Using global transforms

If no tags have been set on any of the fields in the Data Catalog or Processing platform, or if no global transforms have been created, then only the source ref and the fields will be included in the blueprint data policy.&#x20;

{% hint style="info" %}
The `tag_content` of the global transform must match that of a tag on the field in order for the global transform to be included in the blueprint data policy.
{% endhint %}

However, if a tag that is set on a field and the tag matches that of an existing tag transform, it will be included in the blueprint data policy. For the example below, consider a table named `my_table` exists on the platform Databricks, with a tag set on the field email.

{% code title="blueprint-data-policy-with-global-transforms.yaml" %}
```yaml
metadata:
  title: global-transforms
platform:
  id: databricks
  platform_type: DATABRICKS
source:
  ref: my_table
  fields:
  - name_parts:
    - name
    required: true
    type: varchar
  - name_parts:
    - email
    required: true
    tags:
    - pii-email
    type: varchar
rule_sets:
    -   target:
            ref:
                integration_fqn: my_table_pace_view
        field_transforms:
            -   field:
                    name_parts:
                        - email
                    required: true
                    tags:
                        - pii-email
                    type: varchar
                transforms:
                    -   identity: { }
                        principals:
                            -   group: administrator
                    -   nullify: { }
```
{% endcode %}

As can be seen, the blueprint Data Policy includes a ruleset with the transforms [defined in the previous section](./#creating-global-transforms).

<a name="tag-value-matching"></a>
## Tag value matching
The processing platforms and catalogs that we support have quite different constraints on the entity that PACE uses to collect `tags`.
Some platforms only have upper-case, others prohibit dashes, and others whitespace.

In order to make it possible to re-use the same global transform on different processing platforms, we've made it so that tag value matching
is _loose_:
* case-insensitive matching
* whereby ` `, `-` and `_` are all considered to be equal

So you can type `pii-email`, `PII EMAIL` or similar in your global transform definition, and whichever tag format your platform supports, it
will be matched.

**Note**: This mechanism can be disabled via a PACE configuration value (`app.global-transforms.tagTransforms.looseTagMatch` can be `true` (the default) or `false`)
