---
description: >-
  Generate dbt models for your (existing) dbt projects, secured by PACE Data
  Policies
---

# Getting started

If you use dbt for your data processing, you may prefer to use the PACE dbt module. Instead of storing Data Policies and applying them directly on your data platform(s), like the PACE server application does, PACE dbt adds models to your dbt project which implement the desired policies. Dbt then generates views for these PACE dbt models, as it would for any other dbt model. PACE dbt is not a continuously running server application, but a command line executable, which can be integrated in your local development or CI/CD workflow. Read on to find out how to get started with PACE dbt.

## Prerequisites

* A dbt project with a target data processing platform (typically the output type specified in your dbt project's `profiles.yml` file) supported by PACE. See [processing-platform-integrations](../reference/integrations/processing-platform-integrations/ "mention")for an overview of supported platforms.
* The dbt cli (you should be able to run `dbt compile` and `dbt run`).
* The PACE dbt executable JAR file (`dbt.jar`), which you can find under a release's **Assets** on the [Releases page](https://github.com/getstrm/pace/releases).
* A (basic) understanding of PACE's Field Transforms and Filters, see [rule-set](../data-policy/rule-set/ "mention").

{% hint style="info" %}
The PACE dbt `jar` requires a JVM to run. There are various ways to install a JVM on your local machine, please refer to the [official Java site for installation instructions](https://www.java.com/en/download/help/download\_options.html) for your platform. If you prefer to use a package manager to install Java, that will do as well. The JRE version is enough for PACE dbt to be executed, there is no need for the JDK.
{% endhint %}

## Defining Data Policies

PACE dbt uses the same Rule Set yaml notation for Field Transforms as the PACE server application. Any example yaml shown elsewhere in the docs is therefore applicable to PACE dbt.

{% hint style="warning" %}
An exception is the _**fixed**_ field transform. Due to dbt models typically not explicitly specifying column data types, PACE dbt cannot fully infer whether the provided fixed replacement value matches the original data type. Therefore, use _literal values_ whenever data type ambiguity  arises, to distinguish between for example a numeric value (`value: "42"`) or a string value (`value: "'42'"`, note the additional single quotes, which are SQL syntax).
{% endhint %}

Let's see how to enrich a dbt model with PACE Data Policies.

### Adding Rule Sets to a model's meta section

PACE dbt looks for rule sets in a `pace_rule_sets` section under the `meta` sections of dbt models. With this approach, the eventual yaml will look quite similar to that used with the PACE server. It is also the way to go when multiple secure views are desired for a single model, each with a different rule set.

Let's start with a basic dbt schema file:

{% code title="schema.yml" lineNumbers="true" %}
```yaml
version: 2

models:
  - name: dim_transactions
    columns:
      - name: transactionid
      - name: userid
      - name: email
      - name: age
      - name: transactionamount
      - name: brand
```
{% endcode %}

This `dim_transactions` model contains sensitive data dat we want to protect with a PACE Data Policy. Let's say that we want the following:

* The `userid` should be nullified for all users, except for `administrator`s.
* The `email` should be masked to only show the domain, except for `administrator`s.
* `Administrator`s can see all records, but everyone else can only see records for users with an `age` greater than 18.

A modified model `schema.yml` file that achieves this would look like (see the comments for further explanation):

{% code title="models/schema.yml" lineNumbers="true" %}
```yaml
version: 2

models:
  - name: dim_transactions
    columns:
      - name: transactionid
      - name: userid
      - name: email
      - name: age
      - name: transactionamount
      - name: brand
    meta:
      pace_rule_sets:
        # The target section could be omitted, in which case PACE dbt will
        # generate a model with a default suffix in the same db and schema as
        # this source model.
        - target:
            ref:
              # You can specify a different target db and/or schema in the 
              # integration_fqn field. Some caveats apply, explained later.
              integration_fqn: 'my_db.my_schema.demo_transactions_secure'
          field_transforms:
            # Fields are referred to by their name, which should match the ones
            # under the column section above.
            - field:
                name_parts: [ userid ]
              transforms:
                # Here we say only administrators get to see user ids, everyone
                # else gets null values.
                - principals: [ { group: administrator } ]
                  identity: { }
                # As with PACE server Data Policies, the last transform must have
                # an empty list of principals. The principals field for the
                # last transform can be omitted in the yaml.
                - principals: [ ]
                  nullify: { }
            - field:
                name_parts: [ email ]
              transforms:
                - principals: [ { group: administrator } ]
                  identity: { }
                # Here the email address is masked for everyone else but the
                # administrators.
                - principals: [ ]
                  regexp:
                    regexp: "^.*(@.*)$"
                    replacement: "****$1"
          filters:
            - generic_filter:
                conditions:
                  # Administrators get to see all the records, other users only
                  # those with an age greater than 18.
                  - principals: [ { group: administrator } ]
                    condition: "true"
                  - principals: [ ]
                    condition: "age > 18"
```
{% endcode %}

### Specifying Field Transforms on column meta sections

Alternatively, you can specify field transforms in the meta section of the respective columns. This reduces the total amount of yaml and may provide more overview.

{% hint style="warning" %}
When specifying transforms under the meta section of at least one column, PACE dbt will ignore any field transforms under the model meta section, and only use the target and filters from the first rule set listed there (if any). Therefore, when using the column meta sections, only put field transforms there.
{% endhint %}

The following yaml would result in an identical generated PACE dbt model. As you can see, we simply lifted the transforms sections out of the field transforms sections from the previous example, and placed them in the respective columns' meta section under a `pace_transforms` key.

{% code title="models/schema.yml" lineNumbers="true" %}
```yaml
version: 2

models:
  - name: dim_transactions
    columns:
      - name: transactionid
      - name: userid
        meta:
          pace_transforms:
            - principals: [ { group: administrator } ]
              identity: { }
            - principals: [ ]
              nullify: { }
      - name: email
        meta:
          pace_transforms:
            - principals: [ { group: administrator } ]
              identity: { }
            - principals: [ ]
              regexp:
                regexp: "^.*(@.*)$"
                replacement: "****$1"
      - name: age
      - name: transactionamount
      - name: brand
    meta:
      pace_rule_sets:
        - target:
            ref:
              integration_fqn: 'my_db.my_schema.demo_transactions_secure'
          filters:
            - generic_filter:
                conditions:
                  - principals: [ { group: administrator } ]
                    condition: "true"
                  - principals: [ ]
                    condition: "age > 18"
```
{% endcode %}

If no filters are needed, and the PACE dbt model's relation may be created in the same schema as the source model, using the default PACE dbt suffix, the yaml can be simplified by removing the model's entire `meta.pace` section. Note that in the following example, we also removed the explicit `principals` fields for the fallback cases (i.e. "everyone else").

{% code title="models/schema.yml" lineNumbers="true" %}
```yaml
version: 2

models:
  - name: dim_transactions
    columns:
      - name: transactionid
      - name: userid
        meta:
          pace_transforms:
            - principals: [ { group: administrator } ]
              identity: { }
            - nullify: { }
      - name: email
        meta:
          pace_transforms:
            - principals: [ { group: administrator } ]
              identity: { }
            - regexp:
                regexp: "^.*(@.*)$"
                replacement: "****$1"
      - name: age
      - name: transactionamount
      - name: brand
```
{% endcode %}

### Additional configuration for BigQuery

When using PACE dbt with BigQuery, a `pace_user_groups_table` meta key must be specified with the full ID of the table that contains the user group mapping. See also [#user-group-mapping-table](../reference/integrations/processing-platform-integrations/bigquery.md#user-group-mapping-table "mention").

This is best done in the model meta section of the `dbt_project.yml` file, such as:

```yaml
models:
  +meta:
    pace_user_groups_table: your-gcp-project.your_dataset.your_user_groups_table
```

## Generating PACE dbt models

Now that you know how to define PACE Data Policies in your dbt model metadata, let's proceed with generating the corresponding models with PACE dbt. Here we will be using the first example policy listed above (functionally identical to the second example).

From the **root directory of your dbt project**, execute the following steps:

1. Execute a `dbt compile`, or `dbt run`. This will update dbt's `target/manifest.json` file, which is what PACE dbt uses as input.
2. Execute the PACE dbt jar file. Assuming the jar file is called `dbt.jar` and is located in the parent directory of the dbt project, it would look as follows: `java -jar ../dbt.jar`. This should result in one line being outputted per model with a PACE dbt policy configuration, similar to:\
   `Generated PACE model models/demo_transactions.sql`.&#x20;
3. Resume your dbt workflow as usual, e.g. with `dbt compile` or `dbt run`. Dbt will create the corresponding relations on your target data platform. You may want to first inspect the generated model SQL, or add some metadata for it in a `schema.yml` file.

{% hint style="info" %}
To use PACE dbt in a dockerized (Python) environment, see [containerized\_dbt\_module.md](containerized\_dbt\_module.md "mention") for an example Dockerfile.
{% endhint %}

### Example generated SQL

The model generated by PACE dbt will look similar to the following (this SQL query is specific to BigQuery, other target data platforms will result in slightly different statements):

{% code title="models/demo_transactions_secure.sql" lineNumbers="true" %}
```sql
{#
    This file was auto-generated by PACE. Do not edit this file directly.
#}
{{
    config(
      materialized='view',
      meta={'pace_generated': true}
      database='my_db',
      schema='my_schema',
      grant_access_to=[
        {'project': 'a-gcp-project', 'dataset': 'dbt_pace'}
      ]
    )
}}

with
  user_groups as (
    select userGroup
    from `a-gcp-project.user_groups.user_groups`
    where userEmail = SESSION_USER()
  )
select
  `transactionid`,
  case
    when ('administrator' IN ( SELECT `userGroup` FROM `user_groups` )) then `userid`
    else null
  end `userid`,
  case
    when ('administrator' IN ( SELECT `userGroup` FROM `user_groups` )) then `email`
    else regexp_replace(email, '^.*(@.*)$', '****\\1')
  end `email`,
  `age`,
  `transactionamount`,
  `brand`
from {{ ref('stg_demo') }}
where case
  when ('administrator' IN ( SELECT `userGroup` FROM `user_groups` )) then true
  else age > 18
end
```
{% endcode %}

### Using a different target db and/or schema

As shown in the example, PACE dbt will add the required config keys if the specified target `integration_fqn` uses a different db and/or schema than the source model. Note that the [default dbt behaviour](https://docs.getdbt.com/docs/build/custom-schemas) regarding custom schemas still applies.

### Explicitly enabling or disabling PACE dbt configuration

By adding a `pace_enabled` boolean key to a model or column meta section, transforms or filters can be explicitly enabled or disabled. When the `pace_enabled` key is absent, but a `pace_rule_sets` or `pace_transforms` key is present, it is implicitly considered to be `true`. By adding `pace_enabled: false` , the respective configuration will be excluded from the generated PACE dbt model. No model will be generated if all PACE configuration is disabled.

You can "force" the creation of a PACE dbt model without any field transforms or filters by just adding a `pace_enabled: true` key to a model's meta section. By adding it to your general model metadata in `dbt_project.yml`, you can do this for all your models by default (and override that again on model level):

{% code title="dbt_project.yml" %}
```yaml
models:
  +meta:
    pace_enabled: true
```
{% endcode %}

{% hint style="info" %}
Models generated by PACE dbt are always ignored when generating models, even when `pace_enabled` is set to `true` on project level.
{% endhint %}

## Deleting PACE dbt models

Completely removing the `pace` section(s) of a model's `meta` and/or column `meta` sections will result in no PACE dbt being generated for the respective model anymore upon the next run. The previously generated PACE dbt model will however remain in the dbt project and needs to be removed manually. As with any dbt model, the corresponding relations in your data processing platform need to be dropped manually, as dbt itself does not delete them when their model files are removed.
