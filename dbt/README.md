# dbt module

This PACE module is used to extend dbt with Data Policies as PACE knows them.

## Setup

Assuming that you have a Python environment set up (feel free to use Pipenv, Virtualenv, etc), you
can install the required packages with the following command:

```shell
pip install dbt-postgres
pip install dbt-bigquery
```

The directories `dbt_bigquery_project` and `dbt_postgres_project` have been created with the
`dbt init` command.

## Caveats

- With BigQuery, the user groups table should be in the same region as the table for which a view is
  created.
- A custom `generate_schema_name` macro is needed to allow specifying a different schema for the
  PACE views.
  See https://discourse.getdbt.com/t/from-where-does-dbt-bigquery-take-project-and-dataset-while-running-the-model/9046/2
    - For docs: target ref fqn doesn't necessarily need to be specified, there is a default suffix
      of '_view'. However, the target dataset is then the same, which may not be desired from an
      access perspective.
    - We require literals for dbt models, as data types are often absent.
