---
description: Connecting PACE to Azure Synapse Analytics
---

# Synapse

The configuration in PACE for Azure Synapse is very similar to the configuration for [PostgreSQL](../../../reference/processing-platform-integrations/postgres.md). The connection with Synapse is established via a SQLServer JDBC connection. The config for looks as follows:

<pre class="language-yaml"><code class="lang-yaml">app:
  processing-platforms:
    synapse:
      - id: "pace-synapse"
        host-name: <a data-footnote-ref href="#user-content-fn-1">&#x3C;Serverless SQL endpoint> OR &#x3C;Dedicated SQL endpoint></a>
        user-name: <a data-footnote-ref href="#user-content-fn-2">&#x3C;SQL admin username></a>
        password: <a data-footnote-ref href="#user-content-fn-3">&#x3C;SQL admin password></a>
        database: <a data-footnote-ref href="#user-content-fn-4">&#x3C;DATABASE_NAME></a>
        port: <a data-footnote-ref href="#user-content-fn-5">1433</a>

</code></pre>

PACE makes use of database roles as in Synapse. All used [`Principals`](../../../data-policy/principals.md) in the policy should be available as database roles in the Synapse database. Available roles on the platform can be found by running the sql query:

```sql
select * from sys.database_principals where type_desc = 'DATABASE_ROLE'; 
```

[^1]: Either of these can be found in the azure portal under `Azure Synapse Analytics`.

[^2]: Can be found in the azure portal under `Azure Synapse Analytics`.

[^3]: The one you have chosen at creation of the Synapse workspace.

[^4]: Within the Synapse workspace

[^5]: Defaults to `1433`
