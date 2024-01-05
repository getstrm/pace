## pace completion bash

Generate the autocompletion script for bash

### Synopsis

Generate the autocompletion script for the bash shell.

This script depends on the 'bash-completion' package.
If it is not installed already, you can install it via your OS's package manager.

To load completions in your current shell session:

	source <(pace completion bash)

To load completions for every new session, execute once:

#### Linux:

	pace completion bash > /etc/bash_completion.d/pace

#### macOS:

	pace completion bash > $(brew --prefix)/etc/bash_completion.d/pace

You will need to start a new shell for this setup to take effect.


```
pace completion bash
```

### Options

```
  -h, --help              help for bash
      --no-descriptions   disable completion descriptions
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
  -o, --output string                           output format [yaml, json, json-raw] (default "yaml")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace completion](pace_completion.md)	 - Generate the autocompletion script for the specified shell

