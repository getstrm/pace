## pace invoke plugin

Invoke an action for a plugin with the provided payload (JSON or YAML)

### Synopsis

Invoke an action for a plugin with the provided payload (JSON or YAML).
The payload file is checked for validity. The result is plugin-dependent.

```
pace invoke plugin (plugin-id) (action) --payload (payload-file) [flags]
```

### Examples

```
pace invoke plugin openai GENERATE_DATA_POLICY --payload example.yaml
```

### Options

```
  -h, --help             help for plugin
      --payload string   path to a json or yaml file containing the payload to invoke a plugin with
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace invoke](pace_invoke.md)	 - Invoke a functionality

