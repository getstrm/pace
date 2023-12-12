# Data Catalog Specifics

Much as we try to make the interaction with all Data Catalogs identical, there are some oddities.

## OpenDataDiscovery
This catalog doesn't really have a hierarchy of `database` → `schema` → `table`.
It contains the concept of `DataSource`, but not all of these even have associated data sets (i.e. things with a schema
that PACE can interpret).

In order to provide a useful hierarchy we interpret all those Data Sources that have at least one DataSet as a `database`.
Schemas don't really exist on OpenDataDiscovery, but we need it in the PACE hierarchy. We create one _dummy_ Schema with
an `id` of `schema` and a name identical to the Data Source name.

The tables work as expected.

An example (using the OpenDataDiscovery sample data). I've used `jq` to simplify the output somewhat.

### List Databases
This list all data sources with at least one OpenDataDiscovery Data Set.
```shell
pace list databases --catalog odd -o json | jq -r '.databases[]|[.id,.display_name,.type]|@csv'
"2","BookShop Data Lake","Data Lake"
"3","BookShop Transactional","Transactional"
"5","User Transactions","Messaging"
"7","KDS Clickstream","Messaging"
"8","Snowflake Sample Data","Samples"
```

### List Schemas
There's just one schema in each `Database`, because OpenDataDiscovery doesn't have _schemas_ as such.
```shell
pace list schemas --catalog odd --database 3 -o json | jq -r '.schemas[]|[.id,.name]|@csv'
"schema","BookShop Transactional"
```

### List Tables
To list the tables (same as in any other catalog), we need the `database` and the `schema`. OpenDataDiscovery uses
numeric entity id's, and the one for the `BookShop Transactional` postgres database is `3`. The query below shows that
the `name` of the `schema` is identical to the `name` of the `database`.

```shell
pace list tables --catalog odd --database 3 --schema schema -o json | jq -r '.tables[]|[.id,.name,.schema.name]|@csv'
"15","dim_publishers","BookShop Transactional"
"14","fct_sales","BookShop Transactional"
"13","fct_inventory","BookShop Transactional"
"12","dim_currency","BookShop Transactional"
"11","dim_books","BookShop Transactional"
"10","dim_promo","BookShop Transactional"
"9","customer_tier_sbx","BookShop Transactional"
"8","dim_countries","BookShop Transactional"
"7","dim_cards","BookShop Transactional"
"6","dim_customer","BookShop Transactional"
"5","dim_payment","BookShop Transactional"
```
### Get Data Policy
Retrieving a blueprint data policy works the same as with any other catalog. The table named 
```shell
pace get data-policy --catalog odd --database 3 --schema schema 14
metadata:
  description: BookShop Transactional
  title: fct_sales
source:
  fields:
  - name_parts:
    - card_uuid
    required: true
    type: varchar
  - name_parts:
    - update_tmst
...

```
