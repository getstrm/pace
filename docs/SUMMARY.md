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

## Tutorials

* [Detokenization](tutorials/detokenization.md)
* [Global Tag Transforms](tutorials/global-tag-transforms.md)
* [User Defined Functions in Python](tutorials/udfs.md)
* [Data Policy Generation](tutorials/data-policy-generator.md)
* [Databricks](tutorials/databricks.md)
* [PostgreSQL](tutorials/postgresql.md)

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
  * [pace](reference/cli-reference/pace/README.md)
    * [apply](reference/cli-reference/pace/pace\_apply/README.md)
      * [data-policy](reference/cli-reference/pace/pace\_apply/pace\_apply\_data-policy.md)
    * [completion](reference/cli-reference/pace/pace\_completion/README.md)
      * [bash](reference/cli-reference/pace/pace\_completion/pace\_completion\_bash.md)
      * [fish](reference/cli-reference/pace/pace\_completion/pace\_completion\_fish.md)
      * [powershell](reference/cli-reference/pace/pace\_completion/pace\_completion\_powershell.md)
      * [zsh](reference/cli-reference/pace/pace\_completion/pace\_completion\_zsh.md)
    * [delete](reference/cli-reference/pace/pace\_delete/README.md)
      * [global-transform](reference/cli-reference/pace/pace\_delete/pace\_delete\_global-transform.md)
    * [evaluate](reference/cli-reference/pace/pace\_evaluate/README.md)
      * [data-policy](reference/cli-reference/pace/pace\_evaluate/pace\_evaluate\_data-policy.md)
    * [get](reference/cli-reference/pace/pace\_get/README.md)
      * [data-policy](reference/cli-reference/pace/pace\_get/pace\_get\_data-policy.md)
      * [global-transform](reference/cli-reference/pace/pace\_get/pace\_get\_global-transform.md)
    * [invoke](reference/cli-reference/pace/pace\_invoke/README.md)
      * [plugin](reference/cli-reference/pace/pace\_invoke/pace\_invoke\_plugin.md)
    * [list](reference/cli-reference/pace/pace\_list/README.md)
      * [catalogs](reference/cli-reference/pace/pace\_list/pace\_list\_catalogs.md)
      * [data-policies](reference/cli-reference/pace/pace\_list/pace\_list\_data-policies.md)
      * [databases](reference/cli-reference/pace/pace\_list/pace\_list\_databases.md)
      * [global-transforms](reference/cli-reference/pace/pace\_list/pace\_list\_global-transforms.md)
      * [groups](reference/cli-reference/pace/pace\_list/pace\_list\_groups.md)
      * [plugins](reference/cli-reference/pace/pace\_list/pace\_list\_plugins.md)
      * [processing-platforms](reference/cli-reference/pace/pace\_list/pace\_list\_processing-platforms.md)
      * [schemas](reference/cli-reference/pace/pace\_list/pace\_list\_schemas.md)
      * [tables](reference/cli-reference/pace/pace\_list/pace\_list\_tables.md)
    * [upsert](reference/cli-reference/pace/pace\_upsert/README.md)
      * [data-policy](reference/cli-reference/pace/pace\_upsert/pace\_upsert\_data-policy.md)
      * [global-transform](reference/cli-reference/pace/pace\_upsert/pace\_upsert\_global-transform.md)
    * [version](reference/cli-reference/pace/pace\_version.md)
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
