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
