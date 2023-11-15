---
description: Set up the standalone example
---

# Quickstart

This document helps you to set up a standalone example for PACE. This includes:

* **pace\_app:** PACE application running as a container image
* **postgres\_pace**: PostgreSQL database for PACE to persist Data Policies
* **postgres\_processing\_platform**: Another PostgreSQL database for PACE to connect to as a Processing Platform\
  This database is pre-populated with data to give a good overview of what PACE is able to do for you or your organization.

The goal of this tutorial is to run the standalone example, create a Data Policy based on the data schema of the sample data table, and view the data as the different users that we will define.

The source of the standalone example can be found in the [GitHub repository](https://github.com/getstrm/pace/tree/standalone/examples/standalone).

{% hint style="info" %}
Make sure you have everything setup according to the [GitHub authentication](github-authentication.md) and [installation](../getting-started/installation.md) steps.
{% endhint %}

## Prerequisites

Before you get started, make sure you've installed the following tools:

* [Docker](https://www.docker.com/)
* [CLI for PACE](https://github.com/getstrm/cli)
* A PostgreSQL client, e.g. [psql](https://www.postgresql.org/docs/current/app-psql.html) or [DBeaver](https://dbeaver.io/)

## File and directory setup

{% tabs %}
{% tab title="Clone Repository" %}
Clone the repository from GitHub. This command assumes you're not using SSH, but feel free to do so.

```sh
git clone https://github.com/getstrm/pace.git
```
{% endtab %}

{% tab title="Manual setup" %}
Create a directory `standalone` with the following directory tree structure:

```
standalone
├── docker-compose.yaml
├── data.sql
├── config
│   └── application.yaml
└── data-policy.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/standalone).
{% endtab %}
{% endtabs %}

Now navigate  to the `standalone` directory inside the newly create `pace` folder:

```bash
cd pace/examples/standalone
```

Next, let's have a look at the contents of these files.

{% hint style="warning" %}
The compose file is set up without any persistence of data across different startups of the services. Keep in mind that any changes to the data will be persisted for as long as you keep the services running.
{% endhint %}

<details>

<summary><code>docker-compose.yaml</code></summary>

The compose file contains three services, matching the introduction section of this document:

* **pace\_app** with all [ports](https://github.com/getstrm/pace/blob/standalone/examples/standalone/docker-compose.yaml#L42) exposed to the host for all different interfaces (REST, gRPC, and directly to the [Spring Boot app](#user-content-fn-1)[^1]):
  * `8080` -> Spring Boot Actuator
  * `9090` -> Envoy JSON / gRPC Transcoding proxy
  * `50051` -> gRPC
* **postgres\_pace** acts as the persistent layer for PACE to store its Data Policies
  * Available under `localhost:5432` on your machine.
* **postgres\_processing\_platform** is the pre-populated database
  * Available under `localhost:5431` on your machine.

</details>

<details>

<summary><code>data.sql</code></summary>

The PostgreSQL initialization SQL script that is run on startup for the `postgres_processing_platform` container. The database is configured to use the `public` schema (the default), with the following data:

* A table called `public.demo`, for the data schema, please see the [file contents](https://github.com/getstrm/pace/blob/standalone/examples/standalone/data.sql).
* Several users:
  * `standalone` (password = `standalone`) - this is the user we're using to connect PACE to PostgreSQL as a Processing Platform
  * `mark` (password = `mark`)
  * `far` (password = `far`)
  * `other` (password = `other`)
* Several groups (and their users):
  * `administrator`: `standalone`
  * `marketing`: `mark`
  * `fraud_and_risk`: `far`

The idea here is that `standalone` is the super user that should be able to see all data in its raw form, and that users `mark` and `far` should only see the view that is created when creating a Data Policy in PACE.

</details>

<details>

<summary><code>data-policy.yaml</code></summary>

We'll look at the Data Policy contents at a later stage in this quickstart. Feel free to already take a peek, the policy contains:

1. _Platform_\
   The reference to the Processing Platform (both the type and id). **Note**: the _id_ is a self assigned identifier, that can be found in `config/application.yaml`. For this quickstart, the id is `standalone-sample-connection`.
2. _Data Schema_\
   Shape of the data, all fields and their data type.
3. _Ruleset_\
   Rules defining what the view that will be created by PACE will look like, which data will be included and how the data will be presented to different users.

</details>

<details>

<summary><code>config/application.yaml</code></summary>

This is the Spring Boot application configuration, which allows for configuring the PACE database, and for configuring Data Catalog and Processing Platform connections.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres_pace:5432/pace
    hikari:
      username: pace
      password: pace
      schema: public

app:
  processing-platforms:
    postgres:
      - id: "standalone-sample-connection"
        host-name: "postgres_processing_platform"
        port: 5432
        user-name: "standalone"
        password: "standalone"
        database: "standalone"
```

Here, PACE is configured to connect to the `postgres_pace` host, which is the name of the Docker network that both the **pace\_app** and the **postgres\_pace** containers are configured with.

Furthermore, one Processing Platform of type _postgres_ is configured, named `standalone-sample-connection`.

</details>

## Running the standalone example

Make sure your current working directory is the same as the directory you've set up in the previous section. Start the containers by running:

```bash
docker compose up
```

There should be quite a bit of logging, ending in the banner of the PACE app booting. Once everything has started, try to connect to the **postgres\_processing\_platform** to view the pre-populated data. We'll use `psql` here.

### Viewing the sample data

#### As `standalone`

```bash
psql postgresql://standalone:standalone@localhost:5431/standalone
```

List the available tables.

```bash
standalone=# \dt
            List of relations
 Schema | Name | Type  |      Owner
--------+------+-------+-----------------
 public | demo | table | standalone
(1 row)
```

Next, query some of the sample data.

```bash
standalone=# select * from public.demo limit 3;
 transactionid | userid |           email           | age |  brand  | transactionamount
---------------+--------+---------------------------+-----+---------+-------------------
     861200791 | 533445 | jeffreypowell@hotmail.com |  33 | Lenovo  |               123
     733970993 | 468355 | forbeserik@gmail.com      |  16 | Macbook |                46
     494723158 | 553892 | wboone@gmail.com          |  64 | Lenovo  |                73
(3 rows)
```

This is the raw data, which we're able to see, as we're connected to the PostgreSQL database as the `standalone`.

#### As a different user

When connecting to the database using either `mark` or `far`, we won't be able to see the raw data, as the grants have not been configured that way.

```bash
psql postgresql://mark:mark@localhost:5431/standalone
```

The user will be able to see that the table exists, but when querying the data, the user will get:

```bash
standalone=> select * from public.demo limit 3;
ERROR:  permission denied for table demo
```

### Using the CLI

By default, the CLI connects to `localhost:50051`, as it uses the gRPC interface of PACE. Let's see whether we can list the available groups.

{% hint style="success" %}
**Tip**: set up the [CLI](https://github.com/getstrm/cli) and try out the autocomplete on arguments and flags, e.g. `--processing-platform <tab>` to see the available options for your PACE deployment.
{% endhint %}

```bash
pace list groups --processing-platform standalone-sample-connection
```

Which results in the following groups, that have been configured by the `data.sql` init script:

```bash
groups:
- administrator
- marketing
- fraud_and_risk
```

Try to list the available tables.

```bash
pace list tables --processing-platform standalone-sample-connection
```

Which results in the following tables:

```
tables:
- public.demo
```

### The Data Policy file

#### Create a blueprint policy

We start with a blueprint policy (without any rule sets) by reading the description of a table on the processing 
platform.

```bash
pace get data-policy --processing-platform standalone-sample-connection public.demo
```

This results in the following YAML.

```yaml
metadata:
  description: ""
  title: public.demo
platform:
  id: standalone-sample-connection
  platform_type: POSTGRES
source:
  fields:
  - name_parts:
    - transactionid
    required: true
    type: integer
  - name_parts:
    - userid
    required: true
    type: integer
  - name_parts:
    - email
    required: true
    type: varchar
  - name_parts:
    - age
    required: true
    type: integer
  - name_parts:
    - brand
    required: true
    type: varchar
  - name_parts:
    - transactionamount
    required: true
    type: integer
  ref: public.demo
```

The only thing missing here, is a `rule_sets` section, that defines how the PostgreSQL view should behave that PACE will create.

#### Create a ruleset

It's possible to define multiple rulesets for a single source table, where each ruleset results in a separate view. For this quickstart, we'll stick with a single ruleset. We won't discuss every filter or transform, but we'll discuss a few.

**Filters**

For the filters, the goals are:

* Users in the `administrator` group should always see the complete data
* Users in the `fraud_and_risk` group should always see the complete data
* Any other users should only see data where the age field has a value greater than 8.

In YAML, this would look as follows:

```yaml
filters:
  - conditions:
      - principals: [ { group: administrator }, { group: fraud_and_risk } ]
        condition: "true"
      - principals: [ ]
        condition: "age > 8"
```

**Transforms**

In the final `data-policy.yaml`, there are quite a bit of field transforms defined. We'll discuss the transforms for the field `email`, as that's the most complex one. The goals for the `email` field are:

* Users in the `administrator` group should see the raw value
* Users in the `marketing` group should only see the domain of the email, everything before the `@` should be redacted.
* Users in the `fraud_and_risk` group should see the raw value
* Any other users should see the entire `email` as a redacted value

In YAML, this would look as follows:

```yaml
field_transforms:
- field:
    name_parts: [ email ]
  transforms:
    - principals: [ { group: administrator } ]
      identity: { }
    - principals: [ { group: marketing } ]
      regexp:
        regexp: "^.*(@.*)$"
        replacement: "****\1"
    - principals: [ { group: fraud_and_risk } ]
      identity: { }
    - principals: [ ]
      fixed:
        value: "****"
```

{% hint style="warning" %}
Ordering of `transforms` is important! The first match determines the behavior of the transform for the respective user. Imagine a user being both in the `marketing` and `fraud_and_risk` group. Even though the user is in both, the `marketing` transform has a higher precedence, hence that will be used for presenting the `email` field value.
{% endhint %}

**Final `data-policy.yaml`**

For the final Data Policy, please have a look at the [file here](https://github.com/getstrm/pace/blob/standalone/examples/standalone/data-policy.yaml). This is the file that is used when creating the Data Policy.

## Creating the Data Policy

### CLI

Make sure your current working directory is the same as where the `data-policy.yaml` file resides. Next, run:

```bash
pace upsert data-policy data-policy.yaml
```

This will create a view with the name `public.demo_view` in the **postgres\_processing\_platform**.

### Explore the data

Click through the tabs below to see the data as the different users created in this quickstart.

{% tabs %}
{% tab title="standalone" %}
Connect to the database as `standalone`.

```bash
psql postgresql://standalone:standalone@localhost:5431/standalone
```

Query the view:

```bash
standalone=> select * from public.demo_view limit 3;
 transactionid | userid |           email           | age |  brand  | transactionamount
---------------+--------+---------------------------+-----+---------+-------------------
     861200791 | 533445 | jeffreypowell@hotmail.com |  33 | Lenovo  |               123
     733970993 | 468355 | forbeserik@gmail.com      |  16 | Macbook |                46
     494723158 | 553892 | wboone@gmail.com          |  64 | Lenovo  |                73
(3 rows)
```

As you can see, this matches data of the raw table exactly, as presented [above](quickstart.md#viewing-the-sample-data) in this document.
{% endtab %}

{% tab title="mark" %}
Connect to the database as `mark`.

```bash
psql postgresql://mark:mark@localhost:5431/standalone
```

Query the view:

```bash
standalone=> select * from public.demo_view limit 3;
 transactionid | userid |      email       | age | brand | transactionamount
---------------+--------+------------------+-----+-------+-------------------
     861200791 |      0 | ****@hotmail.com |  33 | Other |               123
     733970993 |      0 | ****@gmail.com   |  16 | Apple |                46
     494723158 |      0 | ****@gmail.com   |  64 | Other |                73
(3 rows)
```

As you can see:

* `userid` is null
* All text before the `@` in the`email` is redacted
* `brand` has been mapped to either `Apple` or `Other`
{% endtab %}

{% tab title="far" %}
Connect to the database as `far`.

```bash
psql postgresql://far:far@localhost:5431/standalone
```

Query the view:

```bash
standalone=> select * from public.demo_view limit 3;
 transactionid | userid |           email           | age | brand | transactionamount
---------------+--------+---------------------------+-----+-------+-------------------
     861200791 | 533445 | jeffreypowell@hotmail.com |  33 | Other |               123
     733970993 | 468355 | forbeserik@gmail.com      |  16 | Apple |                46
     494723158 | 553892 | wboone@gmail.com          |  64 | Other |                73
(3 rows)
```

As you can see:

* `brand` has been mapped to either `Apple` or `Other`
* All other fields have been untouched, as the `fraud_and_risk` group mostly uses `identity` functions.
{% endtab %}

{% tab title="other" %}
Connect to the database as `other`.

```bash
psql postgresql://other:other@localhost:5431/standalone
```

Query the view:

```bash
standalone=> select * from public.demo_view limit 3;
 transactionid | userid | email | age | brand | transactionamount
---------------+--------+-------+-----+-------+-------------------
     861200791 |      0 | ****  |  33 | Other |               123
     733970993 |      0 | ****  |  16 | Apple |                46
     494723158 |      0 | ****  |  64 | Other |                73
(3 rows)
```

As you can see, all transforms with an empty principals list (i.e. all other users) are applied here:

* `userid` has been replaced by a zero.
* `email` has been replaced by asterisks.
* `brand` has been mapped to either `Apple` or `Other`.
* All other fields have been untouched, as the `fraud_and_risk` group mostly uses `identity` functions.
{% endtab %}
{% endtabs %}

## Cleanup

That wraps up the standalone example. To clean up all resources, run the following command after stopping the currently running process with `ctrl+C`.

```bash
docker compose down
```



Any questions or comments? Please ask them on [GitHub discussions](https://github.com/getstrm/pace/discussions).

[^1]: Configuration should be mounted under the container path `/app/config`, which will be automatically included by the Spring Boot application.
