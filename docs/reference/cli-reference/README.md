---
description: The getSTRM PACE CLI (pace) reference
---

# CLI Reference

The getSTRM PACE CLI (`pace`) is the primary tool for managing your data policies.

The CLI follows the same structure as many other popular CLI tools, like `kubectl`.

### Principles

#### Verbs first

Most commands in the CLI start with a verb, and then a noun, for example: `list data-policies`.

The currently supported verbs are:

* `get`: Get a resource by its primary key (usually a "id" or "ref" attribute). An error is returned if the resource does not exist.
* `list`: List resources of a given type. An empty list is returned if nothing is found.
* `upsert`: Create a new or update an existing resource.

#### Sensible defaults

Not every attribute needs to be specified when creating resources, so typically only one or two options are needed to create a resource.

### Global commands

The following commands that are not directly tied to a resource are available in the CLI:

* `help`: The general help page.
* `version`: Displays the current version and build information.
* `completion`: generates completions for a given terminal (bash, zsh, fish, PowerShell)

### Global flags <a href="#global-flags" id="global-flags"></a>

\--api-host --help --output

\--api-host\
API host name (default "localhost:50051")

\--help\
Displays the help page

### Entities

The following entities are available in the CLI:

* catalog
* data-policy
* database
* group
* processing-platform
* schema
* table

## Telemetry

The cli collects minimal statistics in a local file (`~/.config/pace/telemetry.yaml`) to see which calls (verbs and nouns) have been done, and what their command exit code was.

Below an annotated example file:

```yaml
metric_points:
    pace get data-policy: # the command without command arguments
        0: # the command exit code (0 means a successfull call)
            cumulative_count: 2 # the cumulative number of times this has occurred since
                                # the telemetry.yaml file was created
        1: # a non-successful command execution
            cumulative_count: 1 # occurred once.
    pace list processing-platforms:
        0:
            cumulative_count: 1
    pace list tables:
        0:
            cumulative_count: 2
    pace version:
        0:
            cumulative_count: 17
cli_version: v1.16.0
operating_system: darwin # macOS
id: 36be4c46-4e1b-431d-b9db-1b315f537a85 # a random identifier of this cli instance.
```

A global flag named `telemetry-upload-interval-seconds` defines the interval in seconds that the CLI uses between consecutive uploads of the collected telemetry. This does not happen in the background, but whenever you execute some command with the CLI. There's a file named `~/.config/pace/telemetry-last-upload-timestamp` that holds the unix timestamp of the last telemetry upload.

**Disabling telemetry:** of course you can easily disable the telemetry uploads. You can do one of the following.

1. add `telemetry-upload-interval-seconds: -1` to `~/.config/pace/config.yaml`
2. put `export PACE_TELEMETRY_UPLOAD_INTERVAL_SECONDS=-1` into your shell environment
3. add `--telemetry-upload-interval-seconds=-1` to every pace cli call you execute.

You cannot disable the collection of calls into the statistics file, but the telemetry will be kept in the local file only, they will not be uploaded.

Of course since the CLI is open-source, you can have a look at the telemetry implementation in the file `pkg/entity/metrics/metrics.go`.

The default of the CLI configuration which is created on the first call of the `pace` CLI is with telemetry enabled, with a 1 hour interval. The PACE developers would really like to see which calls are being used and which ones are not, in order to improve PACE and the CLI. But the choice is yours.
