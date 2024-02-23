# Target

The `Target` is part of a `Rule Set` and defines where the result should be saved.  It requires a reference and type. The platform and database will be derived from the `Source` in the `Data Policy` . The `integration_fqn` or _fully qualified name_ is the full and unique name to be used in the target platform. For example, this could be the view name.

```yaml
rule_sets:
  - target:
      type: "SQL_VIEW"
      ref:
        integration_fqn: "SCHEMA.VIEW_NAME"
```
