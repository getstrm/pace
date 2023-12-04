## pace invoke plugin

Invoke a plugin with the provided payload (JSON or YAML)

### Synopsis

Invoke a plugin with the provided payload (JSON or YAML).
The payload file is checked for validity. The result is plugin-dependent.

```
pace invoke plugin (plugin-id) --payload (payload-file) [flags]
```

### Examples

```
pace invoke plugin openai-data-policy-generator --payload example.yaml
```

### Options

```
  -h, --help             help for plugin
      --payload string   path to a json or yaml file containing the payload to invoke a plugin with
```

### Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

### SEE ALSO

* [pace invoke](pace_invoke.md)	 - Invoke a functionality
