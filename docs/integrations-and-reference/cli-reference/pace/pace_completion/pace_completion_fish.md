# fish

Generate the autocompletion script for fish

## Synopsis

Generate the autocompletion script for the fish shell.

To load completions in your current shell session:

```
pace completion fish | source
```

To load completions for every new session, execute once:

```
pace completion fish > ~/.config/fish/completions/pace.fish
```

You will need to start a new shell for this setup to take effect.

```
pace completion fish [flags]
```

## Options

```
  -h, --help              help for fish
      --no-descriptions   disable completion descriptions
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

## SEE ALSO

* [pace completion](./) - Generate the autocompletion script for the specified shell
