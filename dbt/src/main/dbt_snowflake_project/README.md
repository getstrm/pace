# Snowflake grants

TODO -> move this to post hooks.

```sql
grant usage on warehouse compute_wh to role accountadmin;
grant usage on database pace to role accountadmin;
grant usage on all schemas in database pace to role accountadmin;
grant select on all tables in database pace to role accountadmin;
grant create view on schema pace.online_retail to role accountadmin;
grant select on table pace.online_retail.retail_2010 to role accountadmin;

grant usage on schema pace.online_retail to role fraud_and_risk;
grant usage on schema pace.online_retail to role marketing;
grant select on view pace.online_retail.retail_2010_secured to role fraud_and_risk;
grant select on view pace.online_retail.retail_2010_secured to role marketing;
```
