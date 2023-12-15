---
description: Aggregate sensitive data
---

# Aggregation

This tutorial assumes that you have completed the [quickstart](../readme/quickstart.md) section of the docs. The prerequisites for this tutorial are the same as mentioned there.

The goal of this tutorial is to be able to aggregate data based on simple prerequisites. Some data may be considered sensitive in itself, but when aggregated lose the sensitivity while preserving statistical value.

## Context

Consider the following snippet of a salary database.

```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+--------
 Adrian Shaw      | Rotterdam | Netherlands |  61560
 Anaïs Lejeune    | London    | UK          |  69575
 Angela Mueller   | Amsterdam | Netherlands |  81765
 Benjamin Wilson  | Aberdeen  | UK          |  75709
 Bernhard Fechner | Singapore | Singapore   |  74503
```

These salaries could be considered sensitive information in itself. But if we were able to present specific principals with an aggregation of the salary field, like the average within a country, then the sensitivity will considerably reduced.

This is where PACE's `aggregation` field transform comes in. In this tutorial we will show how to go enforce this in a data policy.

## File and directory setup

{% tabs %}
{% tab title="Clone repository" %}
Clone the repository from GitHub, if you haven't already done so. This command assumes you're not using SSH, but feel free to do so.

```sh
git clone https://github.com/getstrm/pace.git
```
{% endtab %}

{% tab title="Manual setup" %}
Create a directory `aggregation` with the following directory tree structure:

```
aggregation
├── docker-compose.yaml
├── data.sql
├── config
│   └── application.yaml
└── data-policy.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/aggregation-transforms).
{% endtab %}
{% endtabs %}

Now navigate to the `aggregation` directory inside the `pace` repo:

```bash
cd pace/examples/aggregation
```

Next, let's have a look at the contents of these files.

{% hint style="warning" %}
The compose file is set up without any persistence of data across different startups of the services. Keep in mind that any changes to the data will be persisted for as long as you keep the services running.
{% endhint %}

<details>

<summary><code>docker-compose.yaml</code></summary>

The compose file defines three services:

* **pace\_app** with the [ports](../../examples/detokenization/docker-compose.yaml#L41) for all different interfaces exposed to the host:
  * `9090` -> Envoy JSON / gRPC REST Transcoding proxy.
  * `50051` -> gRPC.
  * `8080` -> Spring Boot Actuator.
* **postgres\_pace** acts as the persistent layer for PACE to store its Data Policies.
  * Available under `localhost:5432` on your machine.
* **postgres\_processing\_platform** is the pre-populated database.
  * Available under `localhost:5431` on your machine.

</details>

<details>

<summary><code>data.sql</code></summary>

The PostgreSQL initialization SQL script that is run on startup for the `postgres_processing_platform` container. The database is configured to use the `public` schema (the default), with one table: `public.salary`, containing employee salary data

The script also creates a few roles and corresponding users, which we will see later on.

</details>

<details>

<summary><code>config/application.yaml</code></summary>

This is the Spring Boot application configuration, which specifies the PACE database connection, and Processing Platform.

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
      - id: "aggregation-transforms-sample-connection"
        host-name: "postgres_processing_platform"
        port: 5432
        user-name: "aggregation"
        password: "aggregation"
        database: "aggregation"
```

Here, PACE is configured to connect to the `postgres_pace` host, which is the name of the Docker network that both the **pace\_app** and the **postgres\_pace** containers are configured with.

Furthermore, one Processing Platform of type _postgres_ is configured, named `aggregation-transforms-example-connection`.

</details>

<details>

<summary><code>data-policy.yaml</code></summary>

This is the Data Policy we'll be creating in this tutorial. It implements the desired sensitivity measures, which we will see below.

</details>

## Creating the policy

### Running PACE

To start the containers, execute the following command from the `aggregation` directory:

```bash
docker compose up --pull always
```

There should be quite a bit of logging, ending with the startup logs of the PACE app.

If all went well, the `pace list tables` CLI command should return two tables:

```bash
pace list tables --processing-platform aggregation-transforms-sample-connection
tables:
- public.salary
```

There should be no existing data policies:

```bash
pace list data-policies
{}
```

### Available DB roles and users

Before we dive into the data policy definition, let's have a look at the user group principals (implemented as DB roles) and corresponding users that are configured on the sample database:

* `finance` is a role for the corresponding team that tracks company spending. A DB user named `fin` with password `fin` has been assigned this role.
* `analytics` is a role to the team responsible for providing statistics about the company. A DB user named `anna` with password `anna` has been assigned this role.
* `uk_manager` is a role, specifically for any manager located in the UK. A DB user named `ukm` with passowrd `ukm` has been assigned this role.
* A DB user named `other` with password `other` is also available and hasn't been assigned either of the roles.

We can connect with the sample database on port `5431` on `localhost`, with `aggregation` as database name, with either of the above users. None of these users will be able to read data from the `public.salary` table. Feel free to connect with the database using your favourite SQL client. If you _do_ want to see the data in these tables, you can connect with the user `aggregation` using the password `aggregation`.

<details>

<summary>psql examples</summary>

To illustrate, we can try to retrieve transactions with the `fin` user:

```bash
psql postgresql://fin:fin@localhost:5431/aggregation -c "select * from public.salary limit 5"
```

Which results in the following output:

```
ERROR:  permission denied for table salary
```

And using `aggregation` instead:

```bash
psql postgresql://aggregation:aggregation@localhost:5431/aggregation -c "select * from public.salary limit 5"
```

Which results in the following output:

```
         employee         |   city    |  country  | salary
--------------------------+-----------+-----------+--------
 Courtney Chen            | Singapore | Singapore |  72341
 Isabell Heinrich-Langern | Singapore | Singapore |  84525
 Vincent Regnier          | London    | UK        |  81356
 Kristina Johnson         | Aberdeen  | UK        |  68651
 Timothy Sampson          | Singapore | Singapore |  60218
```

</details>

### Fetching a blueprint Data Policy

We can use the PACE CLI to retrieve a blueprint policy for the `transactions` table as follows:

{% code fullWidth="false" %}
```bash
pace get data-policy --blueprint --processing-platform aggregation-transforms-sample-connection public.salary
```
{% endcode %}

This returns the following data policy definition in YAML, without any field transforms or filters yet:

```yaml
metadata:
  description: ""
  title: public.salary
platform:
  id: aggregation-transforms-sample-connection
  platform_type: POSTGRES
source:
  fields:
  - name_parts:
    - employee
    required: true
    type: varchar
  - name_parts:
    - city
    required: true
    type: varchar
  - name_parts:
    - country
    required: true
    type: varchar
  - name_parts:
    - salary
    required: true
    type: integer
  ref: public.salary
```

This definition essentially contains the reference to and schema of the source table. If desired, we can change the title and provide a description, such as:

```yaml
metadata:
  description: "This is policy restricts and aggregates sensitive salary data."
  title: "Employee salary"
```

We can start filling in the policy by adding a `rule_sets` section:

```yaml
[...]
rule_sets:
  - target:
      fullname: public.salary_view
```

Here we specify the full name of the view that will be created by the policy. Let's start with a filter, that makes sure that the `uk_manager` role can only see uk data:

```yaml
[...]
rule_sets:
  - target:
      fullname: public.salary_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ { group: uk_manager } ]
              condition: "country = 'UK'"
            - principals: [ ]
              condition: "true"
```

The condition is defined in (standard) SQL, compatible with the target platform (our Postgres database in this case). Next, we can codify the remaining requirements as field transforms, resulting in the below rule set specification. See our [overview of transforms](../data-policy/rule-set/field-transform.md) for more details on their configuration.

```yaml
[...]
rule_sets:
  - target:
      fullname: public.salary_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ { group: uk_manager } ]
              condition: "country = 'UK'"
            - principals: [ ]
              condition: "true"
    field_transforms:
      - field:
          name_parts: [ salary ]
        transforms:
          - principals: [ { group: administrator } ]
            identity: { }
          - principals: [ { group: finance } ]
            aggregation:
              sum: { }
          - principals: [ { group: uk_manager } ]
            aggregation:
              partition_by:
                - name_parts: [ city ]
              avg:
                precision: 0
                cast_to: "int4"
          - principals: [ { group: analytics } ]
            numeric_rounding:
              round:
                precision: -4
          - principals: [ ]
            nullify: {}
```

Let's break it down.

* `administrator`: can see any salary as is, due to `identity` transform.
* `finance`: is only allowed to see the sum of all salaries.
* `uk_manager`: can only see UK salaries. The salaries are averaged for each city and rounded to the nearest integer.
* `analytics`: can see any salary, but the salaries are rounded to the nearest 10k
* `other`: can see all rows, but the salaries are all nullified.

## Querying the view

{% tabs %}
{% tab title="public.salary" %}
```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+--------
 Adrian Shaw      | Rotterdam | Netherlands |  61560
 Anaïs Lejeune    | London    | UK          |  69575
 Angela Mueller   | Amsterdam | Netherlands |  81765
 Benjamin Wilson  | Aberdeen  | UK          |  75709
 Bernhard Fechner | Singapore | Singapore   |  74503
```
{% endtab %}
{% endtabs %}

{% tabs %}
{% tab title="administrator" %}
```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+--------
 Adrian Shaw      | Rotterdam | Netherlands |  61560
 Anaïs Lejeune    | London    | UK          |  69575
 Angela Mueller   | Amsterdam | Netherlands |  81765
 Benjamin Wilson  | Aberdeen  | UK          |  75709
 Bernhard Fechner | Singapore | Singapore   |  74503
```
{% endtab %}

{% tab title="finance" %}
```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+---------
 Adrian Shaw      | Rotterdam | Netherlands | 7526410
 Anaïs Lejeune    | London    | UK          | 7526410
 Angela Mueller   | Amsterdam | Netherlands | 7526410
 Benjamin Wilson  | Aberdeen  | UK          | 7526410
 Bernhard Fechner | Singapore | Singapore   | 7526410
```
{% endtab %}

{% tab title="uk_manager" %}
```
      employee      |   city   | country | salary
--------------------+----------+---------+--------
 Anaïs Lejeune      | London   | UK      |  74382
 Benjamin Wilson    | Aberdeen | UK      |  74159
 Charles Renault    | London   | UK      |  74382
 Christina Copeland | Aberdeen | UK      |  74159
 Christine King     | Aberdeen | UK      |  74159
```
{% endtab %}

{% tab title="analytics" %}
```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+--------
 Adrian Shaw      | Rotterdam | Netherlands |  60000
 Anaïs Lejeune    | London    | UK          |  70000
 Angela Mueller   | Amsterdam | Netherlands |  80000
 Benjamin Wilson  | Aberdeen  | UK          |  80000
 Bernhard Fechner | Singapore | Singapore   |  70000
```
{% endtab %}

{% tab title="other" %}
```
     employee     |   city    |   country   | salary
------------------+-----------+-------------+--------
 Adrian Shaw      | Rotterdam | Netherlands |
 Anaïs Lejeune    | London    | UK          |
 Angela Mueller   | Amsterdam | Netherlands |
 Benjamin Wilson  | Aberdeen  | UK          |
 Bernhard Fechner | Singapore | Singapore   |
```
{% endtab %}
{% endtabs %}

<details>

<summary>psql examples</summary>

You could use the following `psql` commands to show the complete result sets.

{% code title="finance" %}
```bash
psql postgresql://fin:fin@localhost:5431/aggregation -c "select * from public.salary_view order by employee limit 5"
```
{% endcode %}

{% code title="uk_manager" %}
```bash
psql postgresql://ukm:ukm@localhost:5431/aggregation -c "select * from public.salary_view order by employee limit 5"
```
{% endcode %}

{% code title="analytics" %}
```bash
psql postgresql://anna:anna@localhost:5431/aggregation -c "select * from public.salary_view order by employee limit 5"
```
{% endcode %}

{% code title="other" %}
```bash
psql postgresql://other:other@localhost:5431/aggregation -c "select * from public.salary_view order by employee limit 5"
```
{% endcode %}

</details>
