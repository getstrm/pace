---
description: Step-by-step example of how to leverage IAM groups, roles and permissions
---

# BigQuery IAM Check Extension

As mentioned in the [bigquery.md](../reference/integrations/processing-platform-integrations/bigquery.md "mention") integration docs, we offer two methods to control table access. In this tutorial we will look at an example with the BigQuery IAM Check Extension. Given you have the extension set up correctly (currently, this extension is not open source, but you can [request access](mailto:pace@getstrm.com?subject=BigQuery%20IAM%20Extension)), this tutorial will show you an example on how to leverage the IAM groups, roles and permissions.

## Prerequisite

For this tutorial to work you need to create a service account according to [#service-account-creation-and-privileges](../reference/integrations/processing-platform-integrations/bigquery.md#service-account-creation-and-privileges "mention").

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

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/standalone/).
{% endtab %}
{% endtabs %}

Now navigate to the `bigquery-iam-check-extension` directory inside the newly create `pace` folder:

```bash
cd pace/examples/bigquery-iam-check-extension
```

Next, let's have a look at the contents of these files.

<details>

<summary>employees.csv</summary>

The CSV file containing some mock-up data for this example. This tutorial requires you to create a dataset named `pace` in your BigQuery project, with a table named `demo`. Populate the table with the CSV.

</details>

<details>

<summary>config/application.yaml</summary>

Fill your BigQuery project ID and paste your service account json key in the corresponding places.

</details>

<details>

<summary>docker-compose.yaml</summary>

The compose file contains two services:

* **pace\_app** with all [ports](../../examples/standalone/docker-compose.yaml#L18) exposed to the host for all different interfaces (REST, gRPC, and directly to the [Spring Boot app](#user-content-fn-1)[^1]):
  * `8080` -> Spring Boot Actuator
  * `9090` -> Envoy JSON / gRPC Transcoding proxy
  * `50051` -> gRPC
* **postgres\_pace** acts as the persistent layer for PACE to store its Data Policies
  * Available under `localhost:5432` on your machine.

</details>

<details>

<summary>data-policy.yaml</summary>

This is the Data Policy we'll be creating in this tutorial. It implements the desired sensitivity measures, which we will see below.

</details>

## Creating service accounts as test users

For this example, we will make use of a set of user groups with service accounts as test users. First create three [groups](https://console.cloud.google.com/iam-admin/groups) within your organization, name `testgroup1` and `testgroup2`, `testgroup3` and configure the group email as `testgroup1@your-domain.com`, `testgroup2@your-domain.com`and `testgroup3@your-domain.com`, respectively.

### Role creation

Go to the [IAM](https://console.cloud.google.com/iam-admin/iam) page and grant the following roles to all three groups: `BigQuery Data Viewer` and `BigQuery Job User`. These two roles are mandatory to be able to query the created view in BigQuery.

Another option for the roles is to create a custom role with the following permissions:

* `bigquery.jobs.create`
* `bigquery.routines.get`
* `bigquery.connections.use`

These are the minimal right to invoke the `check_principal_access` routine that is created by the extension. More role settings are needed for the view, but more on that later.

### Service account creation

Create three service accounts with names `testuser1`, `testuser2` and `testuser3` and assign them to their respective groups.

## Running the BigQuery Example

If you have created the table in BigQuery with the employees data and filled out your service account json key and project id in the application yaml, it is time to start the containers:

```bash
docker compose up
```

## The Data Policy

Replace all principal domains to the domain of your principals. Let's break down the policy.

### Ruleset

There is only one target in this ruleset, hence we will be creating one view.

#### Target

```yaml
- target:
    type: SQL_VIEW
    ref:
      platform:
        platform_type: BIGQUERY
        id: bigquery-pp
      integration_fqn: <--PROJECT-ID-->.<--DATABASE-->.<--VIEW-->
```

We are creating a SQL view. Replace the `<--PROJECT-ID-->`, `<--DATABASE--> and <--VIEW-->` with your project id, target database and target view name respectively.

#### Filters

```yaml
filters:
  - generic_filter:
      conditions:
        - principals:
            - group: testgroup1@your-domain.com
          condition: "true"
        - principals:
            - group: testgroup2@your-domain.com
          condition: "Age > 40"
        - principals: []
          condition: "Age > 50"
```

One generic filter block. `testgroup1` can view all rows, `testgroup2` can view only rows where age is 40. All other principals, i.e. `testgroup3`, can only see people older than 50.

#### Field Transforms

```yaml
- field:
    name_parts:
      - Name
  transforms:
    - principals:
        - group: testgroup1@your-domain.com
      identity: {}
    - principals: []
      fixed:
        value: "**REDACTED**"
```

Names are shown to `testgroup1` and redacted for all other principals.

***

```yaml
- field:
    name_parts:
      - Employee_ID
  transforms:
    - principals:
        - group: testgroup1@your-domain.com
        - group: testgroup2@your-domain.com
      identity: {}
    - principals: []
      fixed:
        value: "0000"
```

Employee IDs are shown to `testgroup1` and `testgroup2`, but replaced with `0000` for all other principals.

***

```yaml
- field:
    name_parts:
      - IBAN
  transforms:
    - principals:
        - group: testgroup1@your-domain.com
      identity: {}
    - principals:
        - group: testgroup2@your-domain.com
      regexp:
        regexp: ^([a-zA-Z0-9]{8}).*$
        replacement: \\1**REDACTED**
    - principals: []
      fixed:
        value: "****"
```

IBAN is shown to `testgroup1`. For `testgroup2`, only the first 8 characters are shown. All other principals get a fixed value of `****`.

***

```yaml
- field:
    name_parts:
      - Salary__USD_
  transforms:
    - principals:
        - group: testgroup1@your-domain.com
      identity: {}
    - principals:
        - group: testgroup2@your-domain.com
      aggregation:
        partition_by:
          - name_parts: [ Base_Country ]
        avg:
          precision: 0
    - principals: []
      nullify: {}
```

The salary is shown to `testgroup1`, averaged by `Base_Country` for `testgroup2` and nullified for `testgroup3`

***

### Source.ref

```yaml
source:
  (...)
  ref:
    integration_fqn: <--PROJECT-->.<--DATABASE-->.<--TABLE-->
    platform:
      platform_type: BIGQUERY
      id: bigquery-pp

```

In the `source.ref` section, change the `integration_fqn` to the correct reference to the source table.

## Applying the data policy and setting roles

Now, using the pace CLI, upsert and apply the data policy:

```bash
pace upsert data-policy data-policy.yaml --apply
```

In your BigQuery studio you should be able to see the view you just created.  Now in order to be able to query the view we need to set the `BigQuery Data Viewer` role on the view to the test groups. Any other role that lets the test groups query the view are also sufficient.

## Querying the view

Using the `gcloud` and `bq` command line interfaces, we can impersonate the service accounts and query the views. In the tabs below, you can find the different results for the different principals

{% tabs %}
{% tab title="Original Data" %}
If you are logged in locally as a user that has access to the dataset, use the `bq` cli to query the original table.

<pre class="language-bash" data-full-width="true"><code class="lang-bash">bq query --nouse_cache --use_legacy_sql=false 'select Employee_ID, Name, Base_City, Base_Country, Department, Years_of_Experience, Salary__USD_, Age, IBAN from sales.employees order by Employee_ID limit 10;'
<strong>+-------------+--------------------+-----------+--------------+------------+---------------------+--------------+-----+--------------------+
</strong>| Employee_ID |        Name        | Base_City | Base_Country | Department | Years_of_Experience | Salary__USD_ | Age |        IBAN        |
+-------------+--------------------+-----------+--------------+------------+---------------------+--------------+-----+--------------------+
| E1056       | James Richards     | Rotterdam | Netherlands  | HR         |                  18 |        68754 |  35 | NL98VXTS9541531481 |
| E1109       | Elizabeth Santiago | Singapore | Singapore    | HR         |                  19 |        89036 |  42 | IN55SFAG6135789633 |
| E1227       | Jamie Hodges       | Aberdeen  | UK           | Logistics  |                  19 |        60426 |  48 | IN59PRFC9669490582 |
| E1322       | Keith King         | Houston   | USA          | Finance    |                   4 |        74996 |  27 | DE96HCDB4822669380 |
| E1335       | Justin Forbes      | London    | UK           | Finance    |                   6 |        60861 |  26 | IN69ZXWR9447134304 |
| E1481       | Charles Barrera    | Houston   | USA          | Finance    |                  16 |        71468 |  46 | IN72VVYY5857043010 |
| E1507       | Michael Garcia     | London    | UK           | Operations |                   9 |        79023 |  41 | GB25ZPNV4859942021 |
| E1605       | 苏丽                | London    | UK           | HR         |                  20 |        79668 |  27 | IN82BQQK5940726608 |
| E1665       | Jennifer Brooks    | London    | UK           | Marketing  |                   3 |        85358 |  28 | GB70CIZL1935002050 |
| E1677       | Wesley Monroe      | Rotterdam | Netherlands  | Operations |                  10 |        61221 |  33 | FR35POMV0305660191 |
+-------------+--------------------+-----------+--------------+------------+---------------------+--------------+-----+--------------------+
</code></pre>
{% endtab %}

{% tab title="testuser1" %}
To impersonate the service account run the following `gcloud` command.

{% code overflow="wrap" %}
```bash
gcloud config set auth/impersonate_service_account testuser1@<your-project>.iam.gserviceaccount.com
```
{% endcode %}

Now query the view using the `bq` command line interface.

```bash
bq query --nouse_cache --use_legacy_sql=false 'select * from sales.pace_view limit 15;'
+-------------+--------------------+-----------+--------------+------------------------+---------------------+--------------+-----+--------------------+
| Employee_ID |        Name        | Base_City | Base_Country |       Department       | Years_of_Experience | Salary__USD_ | Age |        IBAN        |
+-------------+--------------------+-----------+--------------+------------------------+---------------------+--------------+-----+--------------------+
| E1056       | James Richards     | Rotterdam | Netherlands  | HR                     |                  18 |        68754 |  35 | NL98VXTS9541531481 |
| E1109       | Elizabeth Santiago | Singapore | Singapore    | HR                     |                  19 |        89036 |  42 | IN55SFAG6135789633 |
| E1227       | Jamie Hodges       | Aberdeen  | UK           | Logistics              |                  19 |        60426 |  48 | IN59PRFC9669490582 |
| E1322       | Keith King         | Houston   | USA          | Finance                |                   4 |        74996 |  27 | DE96HCDB4822669380 |
| E1335       | Justin Forbes      | London    | UK           | Finance                |                   6 |        60861 |  26 | IN69ZXWR9447134304 |
| E1481       | Charles Barrera    | Houston   | USA          | Finance                |                  16 |        71468 |  46 | IN72VVYY5857043010 |
| E1507       | Michael Garcia     | London    | UK           | Operations             |                   9 |        79023 |  41 | GB25ZPNV4859942021 |
| E1605       | 苏丽                | London    | UK           | HR                     |                  20 |        79668 |  27 | IN82BQQK5940726608 |
| E1665       | Jennifer Brooks    | London    | UK           | Marketing              |                   3 |        85358 |  28 | GB70CIZL1935002050 |
| E1677       | Wesley Monroe      | Rotterdam | Netherlands  | Operations             |                  10 |        61221 |  33 | FR35POMV0305660191 |
+-------------+--------------------+-----------+--------------+------------------------+---------------------+--------------+-----+--------------------+
```
{% endtab %}

{% tab title="testuser2" %}
To impersonate the service account run the following `gcloud` command.

{% code overflow="wrap" %}
```bash
gcloud config set auth/impersonate_service_account testuser2@<your-project>.iam.gserviceaccount.com
```
{% endcode %}

Now query the view using the `bq` command line interface.

```bash
bq query --nouse_cache --use_legacy_sql=false 'select * from sales.pace_view limit 15;'
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+----------------------+
| Employee_ID |     Name     | Base_City | Base_Country |       Department       | Years_of_Experience | Salary__USD_ | Age |         IBAN         |
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+----------------------+
| E1109       | **REDACTED** | Singapore | Singapore    | HR                     |                  19 |        70705 |  42 | IN55SFAG**REDACTED** |
| E1227       | **REDACTED** | Aberdeen  | UK           | Logistics              |                  19 |        73474 |  48 | IN59PRFC**REDACTED** |
| E1481       | **REDACTED** | Houston   | USA          | Finance                |                  16 |        77412 |  46 | IN72VVYY**REDACTED** |
| E1507       | **REDACTED** | London    | UK           | Operations             |                   9 |        73474 |  41 | GB25ZPNV**REDACTED** |
| E1761       | **REDACTED** | London    | UK           | HR                     |                  16 |        73474 |  50 | GB55GSPC**REDACTED** |
| E1794       | **REDACTED** | Aberdeen  | UK           | Marketing              |                   6 |        73474 |  50 | NL29SNBA**REDACTED** |
| E1859       | **REDACTED** | Singapore | Singapore    | Finance                |                  10 |        70705 |  44 | IN38RVPA**REDACTED** |
| E1951       | **REDACTED** | Singapore | Singapore    | Research & Development |                  11 |        70705 |  42 | IN82LJXC**REDACTED** |
| E2008       | **REDACTED** | Aberdeen  | UK           | Logistics              |                   7 |        73474 |  53 | DE35OQWK**REDACTED** |
| E2101       | **REDACTED** | Aberdeen  | UK           | Finance                |                   1 |        73474 |  55 | FR81CFJT**REDACTED** |
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+----------------------+
```

Notice how the `Name` is redacted, everyone is older than 40, the salaries are averaged by the `Base_Country` and the `IBAN` is only the first 8 characters
{% endtab %}

{% tab title="testuser3" %}
To impersonate the service account run the following `gcloud` command.

{% code overflow="wrap" %}
```bash
gcloud config set auth/impersonate_service_account testuser3@<your-project>.iam.gserviceaccount.com
```
{% endcode %}

Now query the view using the `bq` command line interface.

```bash
bq query --nouse_cache --use_legacy_sql=false 'select * from sales.pace_view limit 15;'
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+------+
| Employee_ID |     Name     | Base_City | Base_Country |       Department       | Years_of_Experience | Salary__USD_ | Age | IBAN |
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+------+
| 0000        | **REDACTED** | London    | UK           | Operations             |                   5 |         NULL |  53 | **** |
| 0000        | **REDACTED** | London    | UK           | Operations             |                  15 |         NULL |  54 | **** |
| 0000        | **REDACTED** | Aberdeen  | UK           | Logistics              |                   7 |         NULL |  53 | **** |
| 0000        | **REDACTED** | Houston   | USA          | Research & Development |                  20 |         NULL |  52 | **** |
| 0000        | **REDACTED** | Houston   | USA          | Operations             |                  10 |         NULL |  52 | **** |
| 0000        | **REDACTED** | Singapore | Singapore    | Operations             |                  12 |         NULL |  53 | **** |
| 0000        | **REDACTED** | Houston   | USA          | Marketing              |                  16 |         NULL |  55 | **** |
| 0000        | **REDACTED** | Aberdeen  | UK           | HR                     |                   5 |         NULL |  51 | **** |
| 0000        | **REDACTED** | Aberdeen  | UK           | Finance                |                   1 |         NULL |  55 | **** |
| 0000        | **REDACTED** | London    | UK           | Marketing              |                  15 |         NULL |  51 | **** |
+-------------+--------------+-----------+--------------+------------------------+---------------------+--------------+-----+------+
```
{% endtab %}
{% endtabs %}

## Request Access

Currently, the extension is not publicly available. If you want to leverage the IAM groups, roles and permissions using PACE, feel free to [reach out](mailto:pace@getstrm.com?subject=BigQuery%20IAM%20Extension)!

[^1]: Configuration should be mounted under the container path `/app/config`, which will be automatically included by the Spring Boot application.
