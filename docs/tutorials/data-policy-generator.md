---
description: Generate a Data Policy using the OpenAI plugin
---

# Data Policy Generation

Extensibility is an important aspect of PACE. Functionality can be added through [_plugins_](../plugins/definition.md), the first type of which is the **Data Policy Generator**. More detail on creating your own plugins will follow soon. In this tutorial, we cover our OpenAI Data Policy Generator implementation.

The [OpenAI Data Policy Generator](../plugins/built-in/openai.md) uses the OpenAI Chat API to generate a Rule Set for a given blueprint Data Policy, based on a textual description of filters and field transforms.

{% hint style="warning" %}
An OpenAI API key is required for this tutorial. You can generate one in the OpenAI platform at [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys). We recommend creating a new API key for this PACE plugin
{% endhint %}

## File and directory setup

We provide an example setup in our GitHub repository, as explained below. If you already have a running instance of PACE, you may skip this setup and simply add the OpenAI API key to the PACE application configuration. See the `config/application.yaml` section below.

{% tabs %}
{% tab title="Clone repository" %}
Clone the repository from GitHub, if you haven't already done so. This command assumes you're not using SSH, but feel free to do so.

```bash
git clone https://github.com/getstrm/pace.git
```
{% endtab %}

{% tab title="Manual setup" %}
Create a directory `data-policy-generator` with the following directory tree structure:

```
data-policy-generator
├── docker-compose.yaml
├── config
│   └── application.yaml
└── data-policy.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/data-policy-generator).
{% endtab %}
{% endtabs %}

Now navigate to the `data-policy-generator` directory inside the `pace` repo:

```bash
cd pace/examples/data-policy-generator
```

Next, let's have a look at the contents of these files.

<details>

<summary><code>docker-compose.yaml</code></summary>

The compose file defines three services:

* **pace\_app** with the [ports](../../examples/detokenization/docker-compose.yaml#L41) for all different interfaces exposed to the host:
  * `9090` -> Envoy JSON / gRPC REST Transcoding proxy.
  * `50051` -> gRPC.
  * `8080` -> Spring Boot Actuator.
* **postgres\_pace** acts as the persistent layer for PACE to store its Data Policies.
  * Available under `localhost:5432` on your machine.

</details>

<details>

<summary><code>config/application.yaml</code></summary>

This is the Spring Boot application configuration, which specifies the PACE database connection, and the OpenAI API key.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres_pace:5432/pace
    hikari:
      username: pace
      password: pace
      schema: public

app:
  plugins:
    openai:
      api-key: "put-your-api-key-here"
```

Make sure to set a valid API key, which you can generate at [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys).

</details>

<details>

<summary><code>openai-plugin.yaml</code></summary>

This file contains the blueprint Data Policy and the textual instructions we'll use to generate a Rule Set using the OpenAI Data Policy Generator plugin. Feel free to modify it to your own liking.

</details>

## Generating the Data Policy

### Tutorial video

{% embed url="https://youtu.be/PV94frpUva8" %}

### Running PACE

Make sure your current working directory is the same as the directory you've set up in the previous section. Start the containers by running:

```bash
docker compose up
```

There should be quite a bit of logging, ending in the banner of the PACE app booting.

### Invoking the plugin

In the same directory, execute the following PACE CLI command:

```bash
pace invoke plugin openai GENERATE_DATA_POLICY --payload openai-plugin.yaml
```

This will take a little while (around 20 seconds during our testing). If OpenAI replied with a valid Data Policy, it will be printed to your terminal. The output should look similar to this:

```yaml
metadata:
  description: Users of the data policy generator example
  title: generator.users
  version: 3
platform:
  id: data-policy-generator-sample-connection
  platform_type: POSTGRES
rule_sets:
  - field_transforms:
      - field:
          name_parts:
            - username
        transforms:
          - fixed:
              value: omitted
            principals:
              - group: administrators
          - identity: {}
            principals:
              - group: analytics
          - regexp:
              regexp: .*@(.*)
              replacement: $1
    filters:
      - retention_filter:
          conditions:
            - period:
                days: "30"
          field:
            name_parts:
              - date
      - generic_filter:
          conditions:
            - condition: "TRUE"
              principals:
                - group: administrators
            - condition: age > 18
              principals:
                - group: analytics
            - condition: email LIKE '%@google.com'
    target:
      fullname: filtered_view
      type: SQL_VIEW
source:
  fields:
    - name_parts:
        - email
      required: true
      type: varchar
    - name_parts:
        - username
      required: true
      type: varchar
    - name_parts:
        - organization
      required: true
      type: varchar
    - name_parts:
        - age
      required: true
      type: int
    - name_parts:
        - date
      required: true
      type: timestamp
  ref: generator.users
```

This is all still quite experimental, so not all instructions may work as well. Let us know if you encounter any issues, and we will further explore this thing called GenAI!
