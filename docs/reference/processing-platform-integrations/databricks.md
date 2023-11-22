---
description: Connecting PACE to a Databricks workspace
---

# Databricks

## Service principal creation and privileges

PACE leverages Databricks' **Unity Catalog** to create dynamic views based on Data Policies. A few steps are required to connect a PACE instance with a Databricks workspace.

1. Create a service principal, e.g. `pace-user` through Databricks' [Account Console](https://accounts.cloud.databricks.com/users/serviceprincipals/add).
2. On the new principal's information page, generate an OAuth secret. Make sure to copy the secret's value. Additional secrets can be generated anytime.
3. On the **Roles** tab, enable the **Account admin** role. This permission is required for PACE to retrieve the available groups, for listing and Data Policy validation purposes.
4. Next, grant the **User** permission to the principal on your desired Databricks Workspace, also through the [Account Console](https://accounts.cloud.databricks.com/workspaces).
5. In your workspace, either create a new SQL Warehouse, or choose an existing one, and grant usage permission on it to the service principal. Its size can be very small, as it is only used to create views or list tables.
6. For PACE to be able to list source tables and apply Data Policies through dynamic views, the service principal requires the `USE CATALOG`, `USE SCHEMA` and `SELECT` privileges on all desired **source** resources. The principal requires `CREATE TABLE` privileges on all **target** schemas where Data Policy views are to be created.
7. If one wishes to use User Defined Functions, both the PACE service principal, as well as any user on any of the target views where the UDF is being used require an `EXECUTE` permssion. See [the UDF tutorial](/tutorials/udfs.md) for more detail.

## PACE application properties

After following the above steps, provide the corresponding configuration to the PACE application for each Databricks workspace you want to connect with. For example:

<pre class="language-yaml" data-line-numbers><code class="lang-yaml">app:
  processing-platforms:
    databricks:
<strong>      - id: "pace-databricks"
</strong>        workspaceHost: "https://&#x3C;deployment-name>.cloud.databricks.com/"
        accountHost: "https://accounts.cloud.databricks.com"
        accountId: "&#x3C;account-id>"
        clientId: "&#x3C;client-id>"
        clientSecret: "&#x3C;client-secret>"
        warehouseId: "&#x3C;warehouse-id>"
</code></pre>

The properties are expected to contain the following:

* `id`: an arbitrary identifier unique within your organization for the specific platform (Databricks).
* `workspaceHost`: the URL to your Databricks workspace, containing its unique deployment name.
* `accountHost`: typically `https://accounts.cloud.databricks.com`.
* `accountId`: the id of the Databricks account that owns the workspace.
* `clientId`: the client id of the generated OAuth secret for the service principal to be used by PACE.
* `clientSecret`: the secret value of this generated OAuth secret.
* `warehouseId`: the id of the SQL Warehouse to be used by PACE.
