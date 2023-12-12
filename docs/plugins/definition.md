# Definition

A plugin is an extension of PACE, with custom logic. A plugin consists of the following properties:

* `id`: the unique identifier for this plugin implementation
* `implementation`: the canonical (full) class reference of this plugin
* `actions`: the list of actions that this plugin supports

Actions are interfaces that can be implemented by the plugin creator, in order for the plugin to offer certain functionalities. The interfaces are defined by the [action types](https://buf.build/getstrm/pace/docs/main:getstrm.pace.api.plugins.v1alpha#getstrm.pace.api.plugins.v1alpha.Action.Type). Furthermore, a plugin can possibly be invoked, which means that the action can be triggered through the API (and thus the CLI).

Configuring and enabling a certain plugin differs, as each plugin requires its own configuration. The minimum required plugin configuration looks as follows:

```yaml
app:
  plugins:
    my-plugin:
      enabled: true
```

This enables the `my-plugin` plugin. More details on building your own plugin can be found in the [sdk.md](sdk.md "mention") section.

### Actions

Below, an overview of all available action types and their description is provided. Also, whether the plugin is `invokable` is specified. A plugin action is `invokable` if it can be initiated through the PACE API, via the [`InvokePlugin` RPC](https://buf.build/getstrm/pace/docs/main:getstrm.pace.api.plugins.v1alpha#getstrm.pace.api.plugins.v1alpha.PluginsService.InvokePlugin). If a plugin action requires a payload, it needs to be provided as a string value, which can be either a JSON, YAML, or base 64 encoded version of the JSON/YAML.

<table data-full-width="true"><thead><tr><th width="249">Action name</th><th width="194">Description</th><th width="280">Parameters</th><th width="169">Result</th><th data-type="checkbox">Invokable</th></tr></thead><tbody><tr><td><code>GENERATE_DATA_POLICY</code></td><td>Generate / complete a Data Policy using a plugin, given certain parameters</td><td><a href="https://buf.build/getstrm/pace/docs/main:getstrm.pace.api.plugins.v1alpha#getstrm.pace.api.plugins.v1alpha.DataPolicyGenerator.Parameters">A payload</a>, that differs per plugin</td><td><a href="https://buf.build/getstrm/pace/docs/main:getstrm.pace.api.entities.v1alpha#getstrm.pace.api.entities.v1alpha.DataPolicy">DataPolicy</a></td><td>true</td></tr><tr><td><code>GENERATE_SAMPLE_DATA</code></td><td>Generate sample data, for example to test run your data policy on a sample data set.</td><td><a href="https://buf.build/getstrm/pace/docs/main:getstrm.pace.api.plugins.v1alpha#getstrm.pace.api.plugins.v1alpha.SampleDataGenerator.Parameters">A payload</a>, that differs per plugin</td><td>String (currently only CSV is supported)</td><td>true</td></tr></tbody></table>

Missing an action or a way to integrate certain functionality with PACE? [Let us know](../contact.md).
