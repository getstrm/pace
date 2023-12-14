---
description: Groups of users or machines
---

# Principals

Every processing platform has its own conventions and naming regarding groups of users or machines. In order not to use these terms interchangeably, we use `Principal` as the definition for any group of users, machines, entities, etc.&#x20;

In the table below, you will find the concept names per processing platform. For each processing platform you can click the name to go to the docs describing creation of the principals. For Postgres and Synapse, read below.

| Processing Platform                                                                                                              | Principal for Platform       |
| -------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| [BigQuery](../reference/integrations/processing-platform-integrations/bigquery.md#service-account-creation-and-privileges)       | custom user-to-group mapping |
| [Databricks](../reference/integrations/processing-platform-integrations/databricks.md#service-principal-creation-and-privileges) | group                        |
| [Snowflake](../reference/integrations/processing-platform-integrations/snowflake.md#key-pair-creation-and-user-privileges)       | role                         |
| Postgres                                                                                                                         | role                         |
| Synapse                                                                                                                          | role                         |

## Postgres

Using a database client, like `psql` or `DBeaver`, execute the following commands to create a role, a user and grant the role to the user:&#x20;

```sql
create role <your_role>;
create user <your_user> with encrypted password '<your_password>';
grant <your_role> to <your_user>
```

## Synapse

Very similarly to postgres execute the following from a SQL script in your Synapse Studio:

```sql
create role <your_role>;
create login [<your_user>] with password = '<your_password>';
alter role <your_role> add member <your_user>;
```
