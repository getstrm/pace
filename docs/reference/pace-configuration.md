# PACE Server Configuration
[spring-config]: https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files
[pace-config-src]: https://github.com/getstrm/pace/tree/alpha/app/src/main/kotlin/com/getstrm/pace/config
[spring-logging]: https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-logging.html#boot-features-custom-log-levels
[hikari-config]: https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby
[api]: https://github.com/getstrm/pace/blob/alpha/protos/getstrm/pace/api/entities/v1alpha/entities.proto#L237

PACE is a [Spring Boot Application][spring-config] configured via its standard mechanism. We typically use
an `application.yaml` in a `/config` subdirectory of the working directory the PACE server was started.

The schema of the configuration file is defined by the [Kotlin source code][pace-config-src]. 
 
## Structure
An outline of the yaml file is as follows.

* `spring` The basis of the PACE web application.
  * `datasource` This is where PACE stores its own data (such as the stored data-policies, the global policies etc.)
    * `url` This is the jdbc url to the PACE database. Example: `jdbc:postgresql://localhost:5432/pace`. The last part
      of the url is the name of the database.
    * [`hikari`][hikari-config] Hikari is the standard database connection pool used by Spring applications.
      * `username` How to connect to the database.
      * `password`
      * `schema` Which database schema is used to create the tables that PACE needs. A typical value is `public`.
* `logging` Spring logging [configuration][spring-logging]. You can skip this section completely.
  * `level`
    * _java package_: [DEBUG|INFO|WARN|OFF][spring-logging]
* `app` This is the actual PACE configuration
  * `processing-platforms` Defines a list of processing platforms that PACE connects to on startup.
    * ..
  * `catalogs` Defines a list of data catalogs that PACE connects to on startup.
    * ..
  * `global-transforms`
    * `tag-transforms`
      * `looseTagMatch` [See here](../global-transforms/README).

## Processing Platforms Configuration
Each of the supported processing platforms has a different configuration format. Every configuration must have
a _distinct id_ that can be any non-empty string.

* [PostgreSQL](processing-platform-integrations/postgres.md)
* [BigQuery](processing-platform-integrations/bigquery.md)
* [Databricks](processing-platform-integrations/databricks.md)
* [Snowflake](processing-platform-integrations/snowflake.md)

## Data Catalogs Configuration
[See here](data-catalog-integrations/README.md).
