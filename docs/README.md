# API Reference

{% swagger src=".gitbook/assets/openapi.yaml" path="/catalogs" method="get" summary="ListCatalogs" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/catalogs/{catalogId}/databases" method="get" summary="ListDatabases" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/catalogs/{catalogId}/databases/{databaseId}/schemas" method="get" summary="ListSchemas" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/catalogs/{catalogId}/databases/{databaseId}/schemas/{schemaId}/tables" method="get" summary="ListTables" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/catalogs/{catalogId}/databases/{databaseId}/schemas/{schemaId}/tables/{tableId}/bare-policy" method="get" summary="GetCatalogBarePolicy" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/data-policies" method="get" summary="ListDataPolicies" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/data-policies" method="post" summary="UpsertDataPolicy" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/data-policies/{dataPolicyId}" method="get" summary="GetDataPolicy" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/processing-platforms" method="get" summary="ListProcessingPlatforms" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/processing-platforms/{platformId}/groups" method="get" summary="ListProcessingPlatformGroups" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/processing-platforms/{platformId}/tables" method="get" summary="ListProcessingPlatformTables" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}

{% swagger src=".gitbook/assets/openapi.yaml" path="/processing-platforms/{platformId}/tables/{table_id}/bare-policy" method="get" summary="GetProcessingPlatformBarePolicy" %}
[openapi.yaml](.gitbook/assets/openapi.yaml)
{% endswagger %}