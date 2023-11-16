# Pace Server Configuration
[spring-config]: https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files
[pace-config-src]: https://github.com/getstrm/pace/tree/alpha/app/src/main/kotlin/com/getstrm/pace/config
[spring-logging]: https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-logging.html#boot-features-custom-log-levels
[hikari-config]: https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby
[api]: https://github.com/getstrm/pace/blob/alpha/protos/getstrm/pace/api/entities/v1alpha/entities.proto#L237

Pace is a [Spring Boot Application][spring-config] configured via its standard mechanism. We typically use
an `application.yaml` in a `/config` subdirectory of the working directory the Pace server was started.

The schema of the configuration file is defined by the [Kotlin source code][pace-config-src]. 
 
## Structure
An outline of the yaml file is as follows.

* `spring` The basis of the Pace web application.
  * `datasource` This is where Pace stores its own data (such as the stored data-policies, the global policies etc.)
    * `url` This is the jdbc url to the Pace database. Example: `jdbc:postgresql://localhost:5432/pace`. The last part
      of the url is the name of the database
    * [`hikari`][hikari-config] Hikari is the standard database connection pool used by Spring applications.
      * `username` How to connect to the database.
      * `password`
      * `schema` Which database schema is used to create the tables that Pace needs. A typical value is `public`
* `logging` Spring logging [configuration][spring-logging]. You can skip this section completely.
  * `level`
    * _java package_: [DEBUG|INFO|WARN|OFF][spring-logging]
* `app` This is the actual Pace configuration
  * `processing-platforms` Defines a list of processing platforms that Pace connects to on startup.
    * ..
  * `catalogs` Defines a list of data catalogs that Pace connects to on startup.
    * ..
  * `global-transforms`
    * `tag-transforms`
      * `looseTagMatch` [See here](../global-transforms/README)

## Processing Platform Configuration
Each of the supported processing platforms has a different configuration format. Every configuration must have
a _distinct id_ that can be any non-empty string.

###  PostgreSQL
This might be the same as the Pace database, but can also be a different one.
* `id` The identifier of this PostgreSQL database in Pace.
* `hostName` The hostname of the PostgreSQL database server.
* `port` The tcp port number of the database server.
* `userName` Connection details.
* `password`
* `database`

###  BigQuery
* `id` The identifier of this BigQuery connection in Pace.
* `project-id` The Google Cloud project id
* `user-groups-table` A table where Pace stores user groups. See below for details. "stream-machine-development.user_groups.user_groups"
* `service-account-json-key` A json value that provides access to the BigQuery instance.

The `user-groups-table` is a Data set with a table in it that contains the groups a certain user is member of. The table
is created and populated _on-the-fly_. It contains two columns: `userEmail` and `userGroup`.

**@Ivan why is this needed? Is it indeed populated on the fly?**

Here's an example

```yaml
app:
  ...
  processing-platforms:
    ...
    bigquery:
      - id: "bigquery-pace"
        project-id: "pace-development"
        user-groups-table: "pace-development.user_groups.user_groups"
        service-account-json-key: |
          {
            "type": "service_account",
            "project_id": "pace-development",
            "private_key_id": "063e...",
            "private_key": "...",
            "client_email": "pace-development@pace-development.iam.gserviceaccount.com",
            "client_id": "104...",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/...",
            "universe_domain": "googleapis.com"
          }
```

### Databricks

* `id` The identifier of this Databricks connection in Pace.
* `workspaceHost`
* `accountHost`
* `accountId`
* `clientId`
* `clientSecret`
* `warehouseId`

Example:

```yaml
app:
  processing-platforms:
    databricks:
      - id: "dbr-pace"
        workspaceHost: "https://dbc-...fac4.cloud.databricks.com/"
        accountHost: "https://accounts.cloud.databricks.com"
        accountId: "4ca01e49-..."
        clientId: "87764673-..."
        clientSecret: "dosed0a20..."
        warehouseId: "3bd5bad..."
```

### Snowflake

* `id` The identifier of this Snowflake connection in Pace.
* `serverUrl`
* `database`
* `warehouse` Typically `COMPUTE_WH`
* `userName`
* `accountName`
* `organizationName`
* `privateKey` 

Example
```yaml
app:
  processing-platforms:
    snowflake:
      - id: "sf-pace"
        serverUrl: "https://xd....eu-central-1.snowflakecomputing.com"
        database: "PACE"
        warehouse: "COMPUTE_WH"
        userName: "pace_user"
        accountName: "MV7..."
        organizationName: "SP..."
        privateKey: |
          -----BEGIN PRIVATE KEY-----
          ...
          8jc6F8x+R03amdLQ4dCCOWs=
          -----END PRIVATE KEY-----
```

## Catalogs

Pace must have at least one connection to a processing platform. Catalogs connections are optional. Every catalog is
configured the same way.

* `id`  The identifier of this Data Catalog connection in Pace.
* `type` This type is taken from our [Protobuf api definition][api]
* `serverUrl` How to reach the Data Catalog
* `token` an authentication token. Some Data Catalogs require this.
* `userName` Some other Data Catalogs use `userName`/`password` combinations.
* `password`
* `fetchSize` Unspecified usage by Datahub. Keep at `1` for now.

Example:
```yaml
app:
  catalogs:
  - id: "collibra"
    type: "COLLIBRA"
    serverUrl: "https://test-drive.collibra.com/graphql/knowledgeGraph/v1"
    userName: "test-drive-user-..."
    password: "Dbxv..."
  - id: "datahub"
    type: "DATAHUB"
    serverUrl: "http://...:9002/api/graphql"
    fetchSize: 1
    token: "eyJhbGciOiJIUzI..."
  - id: odd
    type: ODD
    serverURL: http://some-host:8080
```
