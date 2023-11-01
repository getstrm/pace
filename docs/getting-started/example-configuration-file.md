---
description: PACE yaml configuration file
---

# Example configuration file

PACE is a Spring Boot application that needs a configuration file following its conventions. Here's an example configuration file.

{% code title="app/src/mainapp/src/main/resources/application-local.yaml" %}
```yaml
spring:
  datasource:
    # the db where Pace stores its data-policies
    url: jdbc:postgresql://localhost:5432/pace
    hikari:
      username: pace # this principal must have all rights on the pace db
      password: pace
      schema: public # this is where the flyway migration goes.
logging:
  level:
    com.getstrm.pace.snowflake.SnowflakeJwtIssuer: DEBUG
app:
  processing-platforms:
    bigquery:
      - id: "bigquery-dev"
        project-id: "stream-machine-development"
        # this table is used by PACE for SQL group membership statements.
        # it needs to be able to be created by the service account
        user-groups-table: "stream-machine-development.user_groups.user_groups"
        # the service account that can query tables and create views
        service-account-key-json: |
          {
            "type": "service_account",
            "project_id": "stream-machine-development",
            ...
          }
    databricks:
      - id: "dbr-pace"
        workspaceHost: "https://dbc-....cloud.databricks.com/"
        accountHost: "https://accounts.cloud.databricks.com"
        accountId: "... uuid ..."
        clientId: "... uuid ..."
        clientSecret: "..."
        warehouseId: "..."
    snowflake:
      - id: "sf-pace"
        serverUrl: "https://....eu-central-1.snowflakecomputing.com"
        database: "PACE"
        warehouse: "COMPUTE_WH"
        userName: "pace_user"
        accountName: "MV.."
        organizationName: "SP.."
        privateKeyPath: "processing-platforms/snowflake/pace-private-key.p8"
  catalogs:
    - id: "COLLIBRA"
      type: "COLLIBRA"
      serverUrl: "https://....collibra.com/graphql/knowledgeGraph/v1"
      userName: "..."
      password: "..."
    - id: "datahub"
      type: "DATAHUB"
      serverUrl: "http://datahub...:9002/api/graphql"
      token: "..." # datahub api token
    - id: "odd"
      type: "ODD"
      serverUrl: "http://34.90.77.173:8080"
```
{% endcode %}
