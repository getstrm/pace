---
description: Using tags in Postgres
---

# PostgreSQL

Since PostgreSQL has no "native" support for tags on columns, we've come up with a syntax to allow specifying tags in comments on columns. The syntax allows specifying tags in any position of the comment string, in the following formats:

* `pace::my_tag`
* `pace::my-tag`
* `pace::mytag`
* `pace::"my tag"`

Keep in mind that tags are currently prefixed with the fixed string `pace::`.

## Comment examples

Consider the following comment on a non-nullable field `email`, with data type `string`.

> This field should be considered as `pace::pii`. It should only be used for approved purposes. Furthermore, it's format is an `pace::email` and the domain is the only part that should be shown.

This will translate into the following field in the `fields` section of the Data Policy:

```yaml
source:
  fields:
  - name_parts:
    - email
    required: true
    tags:
    - pii
    - email
    type: varchar
```
