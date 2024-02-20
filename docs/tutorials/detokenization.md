---
description: Create Data Policies that detokenize previously tokenized data
---

# Detokenization

This tutorial assumes that you have completed the [quickstart](../pace-server/getting-started/quickstart.md) section of the docs. The prerequisites for this tutorial are the same as mentioned there.

The goal of this tutorial is to create a Data Policy that detokenizes previously tokenized data. Tokenization is a powerful data security method, especially when tokens are random, such as UUIDs. With PACE's `Detokenize` transform, we can implement data policies that provide the original values of tokenized data to authorized data consumers.

## Context

We approach this tutorial from the perspective of a use case of an imaginary global payment card provider, which we'll name **PCP**.

PCP has a strong data-driven culture with by-default access to transaction data for every employee. To comply with upcoming stricter European legislation, they need to take measures to restrict data usage of especially European card holders. Their global fraud and risk team should however still be able to access the data they need to ensure safe and proper usage of PCP's services, most notably in case of reasonable suspicions of fraud.

Let's have a look at a sample of their transaction data:

<figure><img src="../.gitbook/assets/image (4).png" alt=""><figcaption></figcaption></figure>

As part of their measures, PCP decided to apply tokenization to card numbers across their data landscape. Each card number is assigned a random but unique UUID as token. The transaction data is stored in the above tokenized format.

To allow (data) teams to create value from this data, while complying with the European legislation, they decide to put in place a policy that guarantees the following:

1. European transactions should be filtered out for all users, except fraud and risk employees.
2. Card holder names should be hidden for all users, except fraud investigators.
3. Card numbers may be detokenized for fraud investigation.
4. Transaction IDs should be partially masked for all users, except fraud and risk employees.

Let's see how we can enforce this policy with PACE.

## File and directory setup

{% tabs %}
{% tab title="Clone repository" %}
Clone the repository from GitHub, if you haven't already done so. This command assumes you're not using SSH, but feel free to do so.

```sh
git clone https://github.com/getstrm/pace.git
```
{% endtab %}

{% tab title="Manual setup" %}
Create a directory `detokenization` with the following directory tree structure:

```
detokenization
├── docker-compose.yaml
├── data.sql
├── config
│   └── application.yaml
└── data-policy.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/detokenization).
{% endtab %}
{% endtabs %}

Now navigate to the `detokenization` directory inside the `pace` repo:

```bash
cd pace/examples/detokenization
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

The PostgreSQL initialization SQL script that is run on startup for the `postgres_processing_platform` container. The database is configured to use the `public` schema (the default), with the following data:

* A `public.transactions` table, containing "transactions" with tokenized card numbers.
* A `public.tokens` table, containing the corresponding card number tokens and their respective original values.

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
      - id: "detokenization-example-connection"
        host-name: "postgres_processing_platform"
        port: 5432
        user-name: "detokenization"
        password: "detokenization"
        database: "detokenization"
```

Here, PACE is configured to connect to the `postgres_pace` host, which is the name of the Docker network that both the **pace\_app** and the **postgres\_pace** containers are configured with.

Furthermore, one Processing Platform of type _postgres_ is configured, named `detokenization-example-connection`.

</details>

<details>

<summary><code>data-policy.yaml</code></summary>

This is the Data Policy we'll be creating in this tutorial. It implements the desired security measures, which we will see below.

</details>

## Creating the policy

### Running PACE

To start the containers, execute the following command from the `detokenization` directory:

```bash
docker compose up --pull always
```

There should be quite a bit of logging, ending with the startup logs of the PACE app.

If all went well, the `pace list tables` CLI command should return two tables:

```bash
pace list tables --processing-platform detokenization-example-connection \
  --database detokenization --schema public --output table
 ID             NAME           TAGS

 tokens         tokens
 transactions   transactions

```

There should be no existing data policies:

```bash
pace list data-policies  --output table
No entities of this resource type exist.
```

### Available DB roles and users

Before we dive into the data policy definition, let's have a look at the user group principals (implemented as DB roles) and corresponding users that are configured on the sample database:

* `fraud_and_risk` is a role for the corresponding team to be used for purposes when there is _no concrete suspicion_ of fraud. A DB user named `far` with password `far` has been assigned this role.
* `fraud_investigation` is a role to be used when the fraud and risk team _does_ have a _reasonable suspicion_ of fraud. A DB user named `fin` with password `fin` has been assigned this role.
* A DB user named `other` with password `other` is also available and hasn't been assigned either of the roles.

We can connect with the sample database on port `5431` on `localhost`, with `detokenization` as database name, with either of the above users. None of these users will be able to read data from the `public.transactions` or `public.tokens` tables. Feel free to connect with the database using your favourite SQL client. If you _do_ want to see the data in these tables, you can connect with the user `detokenization` using the password `detokenization`.

<details>

<summary>psql examples</summary>

To illustrate, we can try to retrieve transactions with the `far` user:

```bash
psql postgresql://far:far@localhost:5431/detokenization -c "select * from public.transactions limit 5"
```

Which results in the following output:

```
ERROR:  permission denied for table transactions
```

And using `detokenization` instead:

```bash
psql postgresql://detokenization:detokenization@localhost:5431/detokenization -c "select * from public.transactions limit 5"
```

Which results in the following output:

```
 card_holder_name |             card_number              | transaction_id | transaction_amount | transaction_type |       region       |        date         
------------------+--------------------------------------+----------------+--------------------+------------------+--------------------+---------------------
 Maria Gonzalez   | f431ec17-f1d8-498a-8773-41a2c689d527 | 637246159      |              -1394 | payment          | Middle East/Africa | 2023-10-02 15:27:04
 Shawn Lopez      | 9437a24a-6ecf-4e0b-b2ec-b7cb44ed49ee | 120990465      |               4885 | refund           | Europe             | 2023-10-01 15:27:04
 Ronald Skinner   | 810f9606-aa81-4aa6-8dc0-46ceeb176358 | 214706393      |               3318 | bank_transfer    | Asia Pacific       | 2023-09-16 15:27:04
 Meghan Knight    | 078793cf-9fcd-475a-9cd0-ad7e125c3f71 | 338527142      |               3691 | refund           | Americas           | 2023-10-14 15:27:04
 Gary Castillo    | 6d96495a-da9b-4098-9839-4ce29534246f | 904246603      |               3297 | bank_transfer    | Asia Pacific       | 2023-06-22 15:27:04
(5 rows)
```

With this user, we can also retrieve the tokens:

```
psql postgresql://detokenization:detokenization@localhost:5431/detokenization -c "select * from public.tokens limit 5"
                token                 |    value     
--------------------------------------+--------------
 f431ec17-f1d8-498a-8773-41a2c689d527 | 676249732592
 9437a24a-6ecf-4e0b-b2ec-b7cb44ed49ee | 502069034259
 810f9606-aa81-4aa6-8dc0-46ceeb176358 | 676273039476
 078793cf-9fcd-475a-9cd0-ad7e125c3f71 | 676162902214
 6d96495a-da9b-4098-9839-4ce29534246f | 571421649641
(5 rows)
```

</details>

### Fetching a blueprint Data Policy

We can use the PACE CLI to retrieve a blueprint policy for the `transactions` table as follows:

{% code fullWidth="false" %}
```
pace get data-policy --blueprint \
  --processing-platform detokenization-example-connection \ 
  --database detokenization \
  --schema public \
   transactions 
```
{% endcode %}

This returns the following data policy definition in YAML, without any field transforms or filters yet:

```yaml
metadata:
  description: ""
  title: public.transactions
source:
  fields:
  - name_parts:
    - card_holder_name
    required: true
    type: varchar
  - name_parts:
    - card_number
    required: true
    type: varchar
  - name_parts:
    - transaction_id
    required: true
    type: varchar
  - name_parts:
    - transaction_amount
    required: true
    type: integer
  - name_parts:
    - transaction_type
    required: true
    type: varchar
  - name_parts:
    - region
    required: true
    type: varchar
  - name_parts:
    - date
    required: true
    type: varchar
  ref: 
    integration_fqn: public.transactions
    platform:
      id: detokenization-example-connection
      platform_type: POSTGRES
```

This definition essentially contains the reference to and schema of the source table. If desired, we can change the title and provide a description, such as:

```yaml
metadata:
  description: "This is policy restricts access to the transactions data."
  title: "Public transactions"
```

We can start filling in the policy by adding a `rule_sets` section:

```yaml
[...]
rule_sets:
  - target:
      ref:
        integration_fqn: public.transactions_view
```

Here we specify the full name of the view that will be created by the policy. By adding a filter, we can codify that only users from the `fraud_and_risk` and `fraud_investigation` groups can view European data:

```yaml
[...]
rule_sets:
  - target:
      ref:
        integration_fqn: public.transactions_view
    filters:
      - conditions:
        - principals:
          - group: fraud_and_risk
          - group: fraud_investigation
          condition: "true"
        - principals: []
          condition: "region <> 'Europe'"
```

The condition is defined in (standard) SQL, compatible with the target platform (our Postgres database in this case). Next, we can codify the remaining requirements as field transforms, resulting in the below rule set specification. See our [overview of transforms](../data-policy/rule-set/field-transform.md) for more details on their configuration.

{% code overflow="wrap" %}
```yaml
[...]
rule_sets:
  - target:
      ref:
        integration_fqn: public.transactions_view
    filters:
      - conditions:
        - principals:
          - group: fraud_and_risk
          - group: fraud_investigation
          condition: "true"
        # Any other users do not see any European data
        - principals: []
          condition: "region <> 'Europe'"
    field_transforms:
      - field:
          name_parts: [ card_holder_name ]
        transforms:
          # Only the fraud_investigation group can see the card holder name
          - principals:
            - group: fraud_investigation
            identity: {}
          # The card holder name is made null for everyone else
          - principals: []
            nullify: {}
      - field:
          name_parts: [ card_number ]
        transforms:
          # The card number is detokenized for the fraud_investigation group, thus making the original value visible
          - principals:
              - group: fraud_investigation
            detokenize:
              token_source_ref: public.tokens
              token_field:
                name_parts: [ token ]
              value_field:
                name_parts: [ value ]
          # The card number remains tokenized for everyone else
          - principals: []
            identity: {}
      - field:
          name_parts: [ transaction_id ]
        transforms:
          # The fraud_investigation and fraud_and_risk groups can see the full transaction id
          - principals:
              - group: fraud_investigation
              - group: fraud_and_risk
            identity: {}
          # Everyone else can only see the last 3 digits of the transaction id
          - principals: []
            regexp:
              regexp: "^\\d+(\\d{3})$"
              replacement: "******$1"
```
{% endcode %}

The `detokenize` transform references the `tokens` table and specifies which field (column) contains the token and which the value.

The `data-policy.yaml` file in the `detokenization` directory contains the same policy. To apply it and create the `transactions_view`, we can use the PACE CLI again:

```bash
pace upsert data-policy data-policy.yaml --apply
```

The `pace list data-policies` command will now return it.

## Querying the view

Now that the `public.transactions_view` has been created, we can compare the query results the various users get.

{% tabs %}
{% tab title="transactions" %}
<figure><img src="../.gitbook/assets/image (4).png" alt=""><figcaption><p>The first few rows in the original table.</p></figcaption></figure>
{% endtab %}
{% endtabs %}

{% tabs %}
{% tab title="Fraud and Risk" %}
<figure><img src="../.gitbook/assets/image (1).png" alt=""><figcaption><p>All records are included. <code>card_holder_name</code> is nullified and the other columns are as-is.</p></figcaption></figure>
{% endtab %}

{% tab title="Fraud Investigation" %}
<figure><img src="../.gitbook/assets/image (1) (1).png" alt=""><figcaption><p>All records are included. <code>card_number</code> is detokenized and the other columns are as-is.</p></figcaption></figure>
{% endtab %}

{% tab title="Other" %}
<figure><img src="../.gitbook/assets/image (2).png" alt=""><figcaption><p>European records are excluded. <code>card_holder_name</code> is nullified and <code>transaction_id</code> is truncated.</p></figcaption></figure>
{% endtab %}
{% endtabs %}

<details>

<summary>psql examples</summary>

You could use the following `psql` commands to show the complete result sets.

{% code title="Fraud and Risk" %}
```bash
psql postgresql://far:far@localhost:5431/detokenization -c "select * from public.transactions_view"
```
{% endcode %}

{% code title="Fraud Investigation" %}
```bash
psql postgresql://fin:fin@localhost:5431/detokenization -c "select * from public.transactions_view"
```
{% endcode %}

{% code title="Other" %}
```bash
psql postgresql://other:other@localhost:5431/detokenization -c "select * from public.transactions_view"
```
{% endcode %}

</details>

## Cleanup

That wraps up this detokenization tutorial. To clean up all resources, run the following command after stopping the currently running process with `ctrl+C`.

```bash
docker compose down
```

Any questions or comments? Please ask them on [Slack](https://join.slack.com/t/pace-getstrm/shared\_invite/zt-27egzg7ye-iGANVdQZO6ov6ZMVzmsA4Q).
