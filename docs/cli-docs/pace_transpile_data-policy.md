## pace transpile data-policy

Transpile a data policy to view the result for the target platform (e.g. SQL DDL)

### Synopsis

Takes a Data Policy as input with one or more rule sets and transpiles it to the respective target platform.
For example, this can be used to view the SQL DDL that would be generated for a BigQuery platform.

```
pace transpile data-policy [flags]
```

### Examples

```


pace transpile data-policy --data-policy-file data-policy.yaml --output plain

create or replace view "public"."demo_view"
as
with
  "user_groups" as (
    select "rolname"
    from "pg_roles"
    where (
      "rolcanlogin" = false
      and pg_has_role(session_user, oid, 'member')
    )
  )
select
  "transactionid",
  case
    when (
      ('fraud_and_risk' IN ( SELECT rolname FROM user_groups ))
      or ('administrator' IN ( SELECT rolname FROM user_groups ))
    ) then "userid"
    else 0
  end as userid,
  case
    when ('administrator' IN ( SELECT rolname FROM user_groups )) then "email"
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then regexp_replace(email, '^.*(@.*)$', '****\1', 'g')
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then "email"
    else '****'
  end as email,
  "age",
  case
    when ('administrator' IN ( SELECT rolname FROM user_groups )) then "brand"
    else CASE WHEN brand = 'MacBook' THEN 'Apple' ELSE 'Other' END
  end as brand,
  "transactionamount"
from "public"."demo"
where case
  when (
    ('administrator' IN ( SELECT rolname FROM user_groups ))
    or ('fraud_and_risk' IN ( SELECT rolname FROM user_groups ))
  ) then true
  else age > 8
end;
grant SELECT on public.demo_view to "fraud_and_risk";
grant SELECT on public.demo_view to "administrator";
grant SELECT on public.demo_view to "marketing";

```

### Options

```
      --data-policy-file string      path to a data policy file, must be a yaml or json representation of a data policy
      --data-policy-id string        an id of an existing data policy (does not have to be applied)
  -h, --help                         help for data-policy
  -p, --processing-platform string   id of processing platform
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace transpile](pace_transpile.md)	 - Transpile a specification

