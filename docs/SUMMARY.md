# Table of contents

* [Getting Started](README.md)
  * [Quickstart](readme/quickstart.md)
  * [Installation](getting-started/installation.md)
  * [Connect a Processing Platform](getting-started/connect-a-processing-platform.md)
  * [Connect a Data Catalog](getting-started/connect-a-data-catalog.md)
  * [Create a Data Policy](getting-started/create-a-data-policy.md)
  * [Example configuration file](getting-started/example-configuration-file.md)
  * [Kubernetes Deployment](getting-started/kubernetes-deployment.md)
  * [Example use case](readme/example-use-case.md)

## Data policy

* [Schema](data-policy/schema.md)
* [Principals](data-policy/principals.md)
* [Rule Set](data-policy/rule-set/README.md)
  * [Target](data-policy/rule-set/target.md)
  * [Field Transform](data-policy/rule-set/field-transform.md)
  * [Filter](data-policy/rule-set/filter.md)

## Lineage

* [Overview](lineage/lineage.md)

## Tutorials

* [Detokenization](tutorials/detokenization.md)
* [Global Tag Transforms](tutorials/global-tag-transforms.md)
* [User Defined Functions in Python](tutorials/udfs.md)
* [Data Policy Generation](tutorials/data-policy-generator.md)
* [Random Sample Generation](tutorials/random-sample-generator.md)
* [Databricks](tutorials/databricks.md)
* [PostgreSQL](tutorials/postgresql.md)
* [Aggregation](tutorials/aggregation.md)
* [BigQuery IAM Check Extension](tutorials/bigquery-iam-check-extension.md)

## Global Actions

* [Global Transforms](global-actions/global-transforms/README.md)
  * [Processing Platform Tags](global-actions/global-transforms/processing-platform-tags/README.md)
    * [BigQuery](global-actions/global-transforms/processing-platform-tags/bigquery.md)
    * [Databricks](global-actions/global-transforms/processing-platform-tags/databricks.md)
    * [Snowflake](global-actions/global-transforms/processing-platform-tags/snowflake.md)
    * [PostgreSQL](global-actions/global-transforms/processing-platform-tags/postgresql.md)
  * [Data Catalog Tags](global-actions/global-transforms/data-catalog-tags.md)

## Plugins

* [Definition](plugins/definition.md)
* [Built In](plugins/built-in/README.md)
  * [OpenAI](plugins/built-in/openai.md)
* [SDK](plugins/sdk.md)

## Reference

* [API Reference](reference/api-reference/README.md)
  * [gRPC API](reference/api-reference/grpc.md)
  * [REST API](reference/api-reference/rest.md)
* [CLI Reference](reference/cli-reference/README.md)
  * [pace](cli-docs/pace.md)
    * [apply](cli-docs/pace\_apply.md)
      * [data-policy](cli-docs/pace\_apply\_data-policy.md)
    * [completion](cli-docs/pace\_completion.md)
      * [bash](cli-docs/pace\_completion\_bash.md)
      * [fish](cli-docs/pace\_completion\_fish.md)
      * [powershell](cli-docs/pace\_completion\_powershell.md)
      * [zsh](cli-docs/pace\_completion\_zsh.md)
    * [delete](cli-docs/pace\_delete.md)
      * [global-transform](cli-docs/pace\_delete\_global-transform.md)
    * [disable](cli-docs/pace\_disable.md)
      * [welcome](cli-docs/pace\_disable\_welcome.md)
    * [evaluate](cli-docs/pace\_evaluate.md)
      * [data-policy](cli-docs/pace\_evaluate\_data-policy.md)
    * [get](cli-docs/pace\_get.md)
      * [data-policy](cli-docs/pace\_get\_data-policy.md)
      * [global-transform](cli-docs/pace\_get\_global-transform.md)
      * [lineage](cli-docs/pace\_get\_lineage.md)
    * [invoke](cli-docs/pace\_invoke.md)
      * [plugin](cli-docs/pace\_invoke\_plugin.md)
    * [list](cli-docs/pace\_list.md)
      * [catalogs](cli-docs/pace\_list\_catalogs.md)
      * [data-policies](cli-docs/pace\_list\_data-policies.md)
      * [databases](cli-docs/pace\_list\_databases.md)
      * [global-transforms](cli-docs/pace\_list\_global-transforms.md)
      * [groups](cli-docs/pace\_list\_groups.md)
      * [lineage](cli-docs/pace\_list\_lineage.md)
      * [plugins](cli-docs/pace\_list\_plugins.md)
      * [processing-platforms](cli-docs/pace\_list\_processing-platforms.md)
      * [resources](cli-docs/pace\_list\_resources.md)
      * [schemas](cli-docs/pace\_list\_schemas.md)
      * [tables](cli-docs/pace\_list\_tables.md)
    * [transpile](cli-docs/pace\_transpile.md)
      * [data-policy](cli-docs/pace\_transpile\_data-policy.md)
    * [upsert](cli-docs/pace\_upsert.md)
      * [data-policy](cli-docs/pace\_upsert\_data-policy.md)
      * [global-transform](cli-docs/pace\_upsert\_global-transform.md)
    * [version](cli-docs/pace\_version.md)
* [Integrations](reference/integrations/README.md)
  * [Data Catalog Integrations](reference/integrations/data-catalog-integrations/README.md)
    * [Data Catalog Specifics](reference/integrations/data-catalog-integrations/catalog-specifics.md)
  * [Processing Platform Integrations](reference/integrations/processing-platform-integrations/README.md)
    * [BigQuery](reference/integrations/processing-platform-integrations/bigquery.md)
    * [Databricks](reference/integrations/processing-platform-integrations/databricks.md)
    * [Snowflake](reference/integrations/processing-platform-integrations/snowflake.md)
    * [PostgreSQL](reference/integrations/processing-platform-integrations/postgres.md)
    * [Synapse](reference/integrations/processing-platform-integrations/synapse.md)
* [PACE Configuration](reference/pace-configuration.md)
* [Roadmap](reference/roadmap.md)
* [Glossary](reference/glossary.md)

***

* [Contact](contact.md)
