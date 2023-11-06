# processing platforms

List Processing Platforms

## Synopsis

list all the processing platforms that are connected to PACE.

```
pace list processing-platforms [flags]
```

## Examples

```
pace list processing-platforms --output table
 ID                                 TYPE

 databricks-pim@getstrm.com   DATABRICKS
 snowflake-demo                SNOWFLAKE
 bigquery-dev                   BIGQUERY
```

## Options

```
  -h, --help            help for processing-platforms
  -o, --output string   output formats [yaml, json, json-raw, table, plain] (default "yaml")
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
```

## SEE ALSO

* [pace list](./) - List entities
