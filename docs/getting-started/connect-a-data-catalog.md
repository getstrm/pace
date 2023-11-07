---
description: Retrieving metadata for one or more data catalogs
---

# Connect a Data Catalog

PACE can connect to various data catalogs, to retrieve the metadata that these contain about tables. Currently supported are:

* [Collibra](https://www.collibra.com/us/en/resources/enterprise-data-catalogs)
* [Datahub](https://datahubproject.io/)
* [Open Data Discovery](https://opendatadiscovery.org/)

The [Data Catalog Integrations](../reference/data-catalog-integrations/) section goes into detail about how we use their information.

## Connections

Connections to the data catalog(s) are configured in a configuration file that is read on _startup_ of the PACE application. This file contains things like urls, credentials etc. An example of a configuration file can be found [here](example-configuration-file.md).

**Data Catalog Configuration structure**

Every catalog is configured via the same configuration structure, defined via the following Kotlin data class:

```.kotlin
data class CatalogConfiguration(
    val type: DataCatalog.Type, // COLLIBRA, ODD, DATAHUB
    val id: String, // a unique non-empty string
    val serverUrl: String, // where's the server?
    val token: String?, // used by datahub
    val userName: String?, // used in collibra
    val password: String?, // used in collibra
    val fetchSize: Int? = 1, // used in datahub
)
```

The same configuration block is used for every different catalog, but not everything is being used by every catalog.

### Collibra

Collibra uses `serverUrl`, `userName` and `password` (for now, we'll add other types of authentication later)

`serverUrl` typically contains `https://....collibra.com/graphql/knowledgeGraph/v1`.

We use the [Collibra Knowledge Graph GraphQL interface](https://developer.collibra.com/api/graphql/knowledge-graph-documentation) interface

### Datahub

The datahub configuration uses `serverUrl` (typically `https://...:9002/api/graphql`) and `token` which is a datahub api token. Datahub access tokens can be created via `Settings` → `Manage Access Tokens`

### Open Data Discovery

Our current implementation only uses `serverUrl`, currently `http://some.ip.address:8080`. This is obviously _alpha_!
