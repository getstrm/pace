---
description: How PACE connects to 0 or more data catalogs.
---

# Connect a Data Catalog

PACE can connect to various data catalogs, to retrieve the meta information that these contain about tables. Currently supported are [Collibra](https://www.collibra.com/us/en/resources/enterprise-data-catalogs), [Datahub](https://datahubproject.io/) and [Open Data Discovery](https://opendatadiscovery.org/). The [data-catalog-integrations.md](../reference/data-catalog-integrations.md "mention")section goes into details about how we use their information.



### Connections

Connections to the data catalog(s) are configured in a configuration file that is read on _startup_ of the PACE server. This file contains things like urls, credentials etc.

#### Configuration structure

Every catalog is configured via the same configuration structure, defined via the following Kotlin data class:

```
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

