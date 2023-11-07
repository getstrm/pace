---
description: Connecting PACE to a Snowflake database
---

# Snowflake

## Key pair creation and user privileges

PACE translates Data Policies to Snowflake views. A few steps are required to connect a PACE instance with a Databricks database. First, a public/private key pair must be created. Then, a new user and role must be created in Snowflake.

{% hint style="info" %}
PACE currently expects the private key file's path to be passed as a property, and the file itself to be available on the classpath. We are working on an easier approach.
{% endhint %}

To create a key pair, start with the private key:

{% code overflow="wrap" %}
```bash
openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -out pace-snowflake-private-key.p8 -nocrypt
```
{% endcode %}

Then, create a public key for this private key:

{% code overflow="wrap" %}
```bash
openssl rsa -in pace-snowflake-private-key.p8 -pubout -out pace-snowflake-public-key.pub
```
{% endcode %}

Now, create a new user (e.g. `pace_user`) in snowflake, with this public key:

```sql
create user pace_user rsa_public_key = '<key here>';
```

Next, we recommend creating a dedicated role for the PACE user. Provide this role with `usage` privileges on the desired warehouse, as well as all relevant databases and schemas, and `select` privileges on all **source** tables (i.e. tables for which Data Policies are to be created). Grant the right to `create view`s on all desired **target** schemas. For example:

```sql
create role pace;
grant role pace to user pace_user;
alter user pace_user set default_role = pace;
grant usage on warehouse compute_wh to role pace;
grant usage on database pace_db to role pace;
grant usage on all schemas in database pace_db to role pace;
grant select on all tables in database pace_db to role pace;
grant create view on schema public to role pace;
```

## PACE application properties

After following the above steps, provide the corresponding configuration to the PACE application for each Snowflake database you want to connect with.

{% hint style="info" %}
PACE currently supports a single database per configuration. Apart from the `id` and `database` properties though, the remaining properties can be reused across configs.
{% endhint %}

For example:

```yaml
app:
  processing-platforms:
    snowflake:
      - id: "snowflake-pace"
        serverUrl: "https://<account-locater>.snowflakecomputing.com"
        database: "MY_DATABASE"
        warehouse: "COMPUTE_WH"
        userName: "pace_user"
        accountName: "AB12345"
        organizationName: "ABCDEFG"
        privateKey: |
          -----BEGIN PRIVATE KEY-----
          ... private key contents ...
          -----END PRIVATE KEY-----
```

The properties are expected to contain the following:

* `id`: an arbitrary identifier unique within your organization for the specific platform (Snowflake).
* `serverUrl`: the full url pointing to your Snowflake instance, typically ending with `snowflakecomputing.com`.
* `database`: name of the database.
* `warehouse`: the compute warehouse to use for all operations (listing tables, creating views).
* `userName`: name of the user to be used by PACE.
* `accountName`: the name of the account to be used, typically of the form `AB12345`.
* `organizationName`: the name (id) of the organization that owns the account.
* `privateKey`: the contents of the generated private key file.
