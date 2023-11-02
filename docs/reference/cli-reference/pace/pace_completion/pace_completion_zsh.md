# zsh

Generate the autocompletion script for zsh

## Synopsis

Generate the autocompletion script for the zsh shell.

If shell completion is not already enabled in your environment you will need to enable it. You can execute the following once:

```
echo "autoload -U compinit; compinit" >> ~/.zshrc
```

To load completions in your current shell session:

```
source <(pace completion zsh)
```

To load completions for every new session, execute once:

### Linux:

```
pace completion zsh > "${fpath[1]}/_pace"
```

### macOS:

```
pace completion zsh > $(brew --prefix)/share/zsh/site-functions/_pace
```

You will need to start a new shell for this setup to take effect.

```
pace completion zsh [flags]
```

## Options

```
  -h, --help              help for zsh
      --no-descriptions   disable completion descriptions
```

## Options inherited from parent commands

```
      --api-host string   api host (default "localhost:50051")
  -o, --output string     output format [yaml, json, json-raw] (default "yaml")
```

## SEE ALSO

* [pace completion](./) - Generate the autocompletion script for the specified shell
