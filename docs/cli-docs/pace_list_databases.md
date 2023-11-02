# databases

List Databases

```
pace list databases [flags]
```

## Examples

```
pace list databases --catalog COLLIBRA-testdrive --output table
 ID                                     NAME

 8665f375-e08a-4810-add6-7af490f748ad
 99379294-6e87-4e26-9f09-21c6bf86d415
 b6e043a7-88f1-42ee-8e81-0fdc1c96f471
```

## Options

```
  -c, --catalog string   id of catalog
  -h, --help             help for databases
  -o, --output string    output formats [yaml, json, json-raw, table, plain] (default "yaml")
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
```

## SEE ALSO

* [pace list](pace\_list.md) - List entities
