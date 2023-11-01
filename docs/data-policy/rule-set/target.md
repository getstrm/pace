# Target

The `Target` is part of a `Rule Set` and defines where the result should be saved.  It requires a `Full Name` and a `Target Type`. The platform and database will be derived from the `Source` in the `Data Policy`\
The `Full Name` is the full and unique name to be used in the target platform. For example, this could be the view name. Currently we only support creating a SQL view as target `Target Type`. &#x20;

```yaml
rule_sets:
  - target:
      target: "SQL_VIEW"
      fullname: "SCHEMA.VIEW_NAME"
```
