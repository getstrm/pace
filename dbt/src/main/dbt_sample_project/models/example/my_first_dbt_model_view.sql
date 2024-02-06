with "user_groups" as (select "rolname" from "pg_roles" where ("rolcanlogin" = false and pg_has_role(session_user, oid, 'member')))
select 'banaan' "id" from {{ ref('my_first_dbt_model') }}
