---
description: Pave the way to creating your first Data Policy
---

# Connect a Processing Platform

## Provide additional application configuration

Before you can [create and apply your first Data Policy](create-a-data-policy.md), PACE requires a little bit of additional configuration.

For details on the required configuration for your specific platform, see the [corresponding reference pages](../../reference/integrations/processing-platform-integrations/). Below, we will use Databricks as an example.

During installation, you provided a few database related properties in a Spring Boot application yaml format, either as a file or through Kubernetes configmaps or secrets. To configure a Databricks Workspace as a processing platform, the required additional configuration would look like this:

```yaml
app:
  processing-platforms:
    databricks:
      - id: "pace-databricks"
        workspaceHost: "https://<deployment-name>.cloud.databricks.com/"
        accountHost: "https://accounts.cloud.databricks.com"
        accountId: "<account-id>"
        clientId: "<client-id>"
        clientSecret: "<client-secret>"
        warehouseId: "<warehouse-id>"
```

We add the `app` and `processing-platforms` properties, as well as a `databricks` property, which contains a **list** of databricks configurations (just a single one in this example).

Each platform requires an ID, which is an arbitrary name of your choosing, but unique within your PACE processing platform configuration.

## Verify your configuration

To verify your setup, run or restart your PACE instance. Your platform should show up when listed:

{% tabs %}
{% tab title="CLI" %}
```bash
pace list processing-platforms
processing_platforms:
- id: pace-databricks
  platform_type: DATABRICKS
```
{% endtab %}

{% tab title="curl" %}
```bash
curl localhost:9090/processing-platforms
{"processingPlatforms":[{"platformType":"DATABRICKS","id":"pace-databricks"}]}
```
{% endtab %}
{% endtabs %}

If your service principal or similar has read access to tables on the platform, they will show up when listed:

{% tabs %}
{% tab title="CLI" %}
```bash
pace list databases --processing-platform dbr-pace -o table
 ID       NAME     TYPE

 demo     demo     DATABRICKS
 main     main     DATABRICKS
 pace     pace     DATABRICKS
 system   system   DATABRICKS

pace list schemas --processing-platform dbr-pace --database demo -o table
 ID                   NAME

 default              default
 information_schema   information_schema
 public               public

pace list tables --processing-platform dbr-pace --database demo\
 --schema public  -o table 
 
 ID             NAME           TAGS

 transactions   transactions

```
{% endtab %}

{% tab title="curl" %}
```bash
```
{% endtab %}
{% endtabs %}

With your first processing platform connection in place, you can go ahead and[ create your first Data Policy](create-a-data-policy.md), or connect a Data Catalog first.
