---
description: Create Data Policies with blueprint transforms
---

# Global Tag Transforms

This tutorial assumes that you have completed the [quickstart](../readme/quickstart.md) section of the docs. The prerequisites for this tutorial are the same as mentioned there.

The goal of this tutorial is to be able to fetch a Data Policy with a ruleset included, based on tags that are attached to a field on the respective processing platform. This enables the end-user to define transforms once, and reuse them without leaving the data processing platform or data catalog.

## File and directory setup

{% tabs %}
{% tab title="Clone Repository" %}
Clone the repository from GitHub, if you haven't already done so. This command assumes you're not using SSH, but feel free to do so.

```sh
git clone https://github.com/getstrm/pace.git
```
{% endtab %}

{% tab title="Manual setup" %}
Create a directory `global-tag-transforms` with the following directory tree structure:

```
global-tag-transforms
├── docker-compose.yaml
├── data.sql
├── config
│   └── application.yaml
└── global-tag-transform.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/global-tag-transforms).
{% endtab %}
{% endtabs %}

Now navigate to the `global-tag-transforms` directory inside the `pace` repo:

```bash
cd pace/examples/global-tag-transforms
```

Next, let's have a look at the contents of these files.

{% hint style="warning" %}
The compose file is set up without any persistence of data across different startups of the services. Keep in mind that any changes to the data will be persisted for as long as you keep the services running.
{% endhint %}

{% hint style="info" %}
Since PostgreSQL has no "native" support for tags on columns, we've come up with a syntax [to allow specifying tags in comments on columns](../global-actions/global-transforms/processing-platform-tags/postgresql.md).
{% endhint %}

<details>

<summary><code>docker-compose.yaml</code></summary>

The compose file defines three services:

* **pace\_app** with [ports](../../examples/global-tag-transforms/docker-compose.yaml) for all different interfaces exposed to the host:
  * `8080` -> Spring Boot Actuator.
  * `9090` -> Envoy JSON / gRPC REST Transcoding proxy.
  * `50051` -> gRPC.
* **postgres\_pace** acts as the persistent layer for PACE to store its Data Policies.
  * Available under `localhost:5432` on your machine.
* **postgres\_processing\_platform** is the pre-populated database.
  * Available under `localhost:5431` on your machine.

</details>

<details>

<summary><code>data.sql</code></summary>

The PostgreSQL initialization SQL script that is run on startup for the `postgres_processing_platform` container. The database is configured to use the `public` schema (the default), with the following data:

* A table called `public.demo`, for the data schema, please see the [file contents](../../examples/global-tag-transforms/data.sql).
* A comment on the `email` field of the `public.demo` table, that includes the tag `pace::pii-email`.

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
      - id: "global_transforms-sample-connection"
        host-name: "postgres_processing_platform"
        port: 5432
        user-name: "global_transforms"
        password: "global_transforms"
        database: "global_transforms"
```

Here, PACE is configured to connect to the `postgres_pace` host, which is the name of the Docker network that both the **pace\_app** and the **postgres\_pace** containers are configured with.

Furthermore, one Processing Platform of type _postgres_ is configured, named `global_transforms-sample-connection`.

</details>

<details>

<summary><code>global-tag-transforms.yaml</code></summary>

This is the Global Transform we'll be creating in this tutorial. It uses tags on the processing platform, which are attached to fields in the data schema, and therefore, is of type `tag_transform`.

A global transform allows for specifying multiple transforms based on the principal of the data consumer, similar to how it's done [when creating rule sets](../getting-started/create-a-data-policy.md#upsert-the-data-policy). In fact, this transform is translated into a rule set of its own, as we'll see later in the tutorial.

</details>

## Running the example

Make sure your current working directory is the same as the directory you've set up in the previous section. Start the containers by running:

```bash
docker compose up --pull always
```

There should be quite a bit of logging, ending in the banner of the PACE app booting. Once everything has started, try to connect to the **postgres\_processing\_platform** to view the table and it's comments. We'll use `psql` here.

### Viewing the table

Connect to the PostgreSQL database.

```bash
psql postgresql://global_transforms:global_transforms@localhost:5431/global_transforms
```

Next, view the table and the comments.

```sql
select column_name, data_type, col_description('public.demo'::regclass, ordinal_position) comment
from information_schema.columns
where table_schema = 'public' and table_name = 'demo';
```

This results in the following representation of the demo table. As you can see, the `pii-email` tag is already set in the comment of the field `email`.

```bash
    column_name    |     data_type     |                                 comment
-------------------+-------------------+--------------------------------------------------------------------------
 transactionid     | integer           |
 userid            | integer           |
 name              | character varying |
 email             | character varying | This is a user email which should be considered as such. pace::pii-email
 age               | integer           |
 salary            | integer           |
 postalcode        | character varying |
 brand             | character varying |
 transactionamount | integer           |
(9 rows)
```

### Fetching a blueprint Data Policy without global transforms

Before we create the global transform, first, let's see what is returned when we fetch the Data Policy created from the table when there are no global transforms defined.

```bash
pace get data-policy --blueprint \
    --processing-platform global_transforms-sample-connection \
    --database global_transforms \
    --schema public \
    demo 
```

Which returns the following data policy.

```yaml
metadata:
  description: ""
  title: public.demo
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
    - name
    required: true
    type: varchar
  - name_parts:
    - email
    required: true
    tags:
    - pii-email
    type: varchar
  - name_parts:
    - age
    required: true
    type: integer
  - name_parts:
    - salary
    required: true
    type: integer
  - name_parts:
    - postalcode
    required: true
    type: varchar
  - name_parts:
    - brand
    required: true
    type: varchar
  - name_parts:
    - transactionamount
    required: true
    type: integer
  ref: 
    integration_fqn: public.demo
    platform:
      id: global_transforms-sample-connection
      platform_type: POSTGRES
```

In this data policy, no `rule_sets` section is present, which is correct, since there are no global transforms yet.

{% hint style="success" %}
Note that the `email` field has a tag, `pii-email`. This has been extracted from the comment on the column in the PostgreSQL table.
{% endhint %}

### Creating a global transform

Let's have a more detailed look at the `global-tag-transforms.yaml`:

{% code title="global-tag-transforms.yaml" overflow="wrap" %}
```yaml
ref: "pii-email"
description: "This is a global transform that nullifies all fields tagged with the value 'pii-email' for all other users, except the administrator and the fraud_and_risk members. In this example, this is a comment on the column of the 'email' field in the test dataset. Please see the 'data.sql' file for the comment on the field."
tag_transform:
  tag_content: "pii-email"
  transforms:
    # The administrator group can see all data
    - principals: [ { group: administrator } ]
      identity: { }
    # The fraud_and_risk group should see a part of the email
    - principals: [ { group: fraud_and_risk } ]
      regexp:
        regexp: "^.*(@.*)$"
        replacement: "****$1"
    # All other users should not see the email
    - principals: [ ]
      nullify: { }
```
{% endcode %}

So for any fields in the schema with the tag `pii-email`, this transform should be included. Next, create the global transform.

```bash
pace upsert global-transform global-tag-transform.yaml
```

Feel free to list the global transforms to see whether it has been correctly created (`pace list global-transforms`)

### Fetching a Data Policy with a rule set based on global transforms

When we fetch the Data Policy now, the global transform should be added to the `rule_sets` section of the data policy. Run the command to get a blueprint data policy for our table again.

```bash
pace get data-policy --blueprint \
    --processing-platform global_transforms-sample-connection \
    --database global_transforms \
    --schema public \
    demo vim
    
```

Which returns the following data policy.

```yaml
metadata:
  description: ""
  title: public.demo
rule_sets:
- field_transforms:
  - field:
      name_parts:
      - email
      required: true
      tags:
      - pii-email
      type: varchar
    transforms:
    - identity: {}
      principals:
      - group: administrator
    - principals:
      - group: fraud_and_risk
      regexp:
        regexp: ^.*(@.*)$
        replacement: '****$1'
    - nullify: {}
  target:
    ref: 
      integration_fqn: public.demo_pace_view
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
    - name
    required: true
    type: varchar
  - name_parts:
    - email
    required: true
    tags:
    - pii-email
    type: varchar
  - name_parts:
    - age
    required: true
    type: integer
  - name_parts:
    - salary
    required: true
    type: integer
  - name_parts:
    - postalcode
    required: true
    type: varchar
  - name_parts:
    - brand
    required: true
    type: varchar
  - name_parts:
    - transactionamount
    required: true
    type: integer
  ref: 
    integration_fqn: public.demo
    platform:
      id: global_transforms-sample-connection
      platform_type: POSTGRES
```

In this data policy, a `rule_sets` section is present, and it has been populated with the global transforms, since the `email` field has a tag `pii-email` and the global transform should be added for fields that have that specific tag.

## Cleanup

That wraps up the global transforms example. To clean up all resources, run the following command after stopping the currently running process with `ctrl+C`.

```bash
docker compose down
```

Any questions or comments? Please ask them on [Slack](https://join.slack.com/t/pace-getstrm/shared\_invite/zt-27egzg7ye-iGANVdQZO6ov6ZMVzmsA4Q).
