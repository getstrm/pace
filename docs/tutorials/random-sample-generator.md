---
description: Generate some Random Sample Values using the OpenAI plugin
---

# Random Sample Generation

Extensibility is an important aspect of PACE. Functionality can be added through [_plugins_](../plugins/definition.md), the first type of which is the **OpenAI plugin**. More detail on creating your own plugins will follow soon. In this tutorial, we cover our OpenAI Data Policy Generator implementation.

```shell
pace list plugins
plugins:
- actions:
  - invokable: true
    type: GENERATE_DATA_POLICY
  - invokable: true
    type: GENERATE_SAMPLE_DATA
  id: openai
  implementation: com.getstrm.pace.plugins.builtin.openai.OpenAIPlugin
```

This plugin has two actions, we'll explore the GENERATE\_SAMPLE\_DATA action in this tutorial

The [OpenAI ](../plugins/built-in/openai.md)plugin GENERATE\_SAMPLE\_DATA action uses the OpenAI Chat API to create random sample data for a given table definition, that one could create for instance via one of the `pace get data-policy ...` command invocations.

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
Create a directory `sample-data-generator` with the following directory tree structure:

```
sample-data-generator
├── docker-compose.yaml
├── config
│   └── application.yaml
└── data-policy.yaml
```

Grab the contents of the files from the [GitHub repository](https://github.com/getstrm/pace/tree/alpha/examples/sample-data-generator).
{% endtab %}
{% endtabs %}

Now navigate to the `sample-data-generator` directory inside the `pace` repo:

```bash
cd pace/examples/sample-data-generator
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
      enabled: true
      model: "gpt-4-1106-preview"
      
```

Make sure to set a valid API key, which you can generate at [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys).

</details>

<details>

<summary><code>instructions.yaml</code></summary>

Some simple instructions to generate the sample data.

Note: there are some Dutch language column names whose meaning and resulting significance we'll explain below.

```yaml
source:
  ref: generate_sample_demo
  fields:
    - name_parts: [ email ]
      type: varchar
      required: true
    - name_parts: [ gebruikersnaam ]
      type: varchar
      required: true
    - name_parts: [ organisatie ]
      type: varchar
      required: true
    - name_parts: [ klantnummber ]
      type: varchar
      required: true
    - name_parts: [ bankrekening ]
      type: varchar
      required: false

additional_system_instructions:
- don't forget to add the csv column headers
- please return 5 result rows
```

</details>

<details>

<summary><code>complex-instructions.yaml</code></summary>

In this file, we've added some more system instructions, to enrich the generated sample data.

Note: there are some Dutch language column names whose meaning and resulting significance we'll explain below.

```yaml
source:
  ref: generate_sample_demo
  fields:
    - name_parts: [ email ]
      type: varchar
      required: true
    - name_parts: [ gebruikersnaam ]
      type: varchar
      required: true
    - name_parts: [ organisatie ]
      type: varchar
      required: true
    - name_parts: [ klantnummber ]
      type: varchar
      required: true
    - name_parts: [ bankrekening ]
      type: varchar
      required: false

additional_system_instructions:
  - don't forget to add the csv column headers
  - please return 20 result rows
  - if you recognize something as being an email, please use email domains in europe
  - if you recognize something as a customer number please generate 7 digits between 1000000 and 2999999
  - for recognized bank accounts use european style IBAN
```

</details>

## Generating the Data Policy

### Tutorial video

{% embed url="https://youtu.be/_1y6l4Sz2Y8" %}

### Running PACE

Make sure your current working directory is the same as the directory you've set up in the previous section. Start the containers by running:

```bash
docker compose up
```

There should be quite a bit of logging, ending in the banner of the PACE app booting.

### Invoking the plugin

First we prepare some instructions:

```yaml
source:
  ref: generate_sample_demo
  fields:
    - name_parts: [ email ]
      type: varchar
      required: true
    - name_parts: [ gebruikersnaam ]
      type: varchar
      required: true
    - name_parts: [ organisatie ]
      type: varchar
      required: true
    - name_parts: [ klantnummber ]
      type: varchar
      required: true
    - name_parts: [ bankrekening ]
      type: varchar
      required: false
additional_system_instructions:
  - don't forget to add the csv column headers
  - please return 5 result rows
```

Note the following Dutch column names:

* `gebruikersnaam` = `username`
* `organisatie` = `organization`
* `klantnummer` = `customernumber`
* `bankrekening` = `bankaccount`.

Note that we are not explaining this to GPT, nor is there any hard-coded _Dutch_ somewhere inside our source code.

In the same directory, execute the following PACE CLI command:

```bash
pace  invoke plugin openai GENERATE_SAMPLE_DATA --payload instructions.yaml
```

This will take a little while (around 20 seconds during our testing). If OpenAI replied within the configured timeout, PACE will print the generated random csv to your terminal. The output should look similar to this:

```
"email","gebruikersnaam","organisatie","klantnummer","bankrekening"
"jane.doe@example.com","janedoe","Example Corp","CUST12345","NL91ABNA0417164300"
"john.smith@business.com","johnsmith","Business LLC","CUST54321","NL62RABO0300065264"
"alice.johnson@internet.org","alicej","Internet Org","CUST67890","NL20INGB0001234567"
"bob.brown@ecommerce.net","bobbrown","Ecommerce Inc","CUST09876","NL80ABNA0484869868"
"carol.white@tech.biz","carolwhite","Tech Solutions","CUST13579","NL39RABO0331609111"
```

NOTE: GPT figured out that `klantnummer` must relate to a _customer_ hence the `CUST` prefix. Even more interesting it figured out that `bankrekening` should probably be given Dutch style IBAN numbers!

This is all still quite experimental, so not all instructions may work as well. We've also frequently encountered OpenAI timeouts, resulting in an Internal Error response to your cli. Let us know if you encounter any issues, and we will further explore this thing called GenAI!

#### Enriching the instructions

Adding some more configuration to the instructions:

```yaml
additional_system_instructions:
  - don't forget to add the csv column headers
  - please return 20 result rows
  - if you recognize something as being an email, please use email domains in europe
  - if you recognize something as a customer number please generate 7 digits between 1000000 and 2999999
  - for recognized bank accounts use european style IBAN
```

```bash
pace invoke plugin openai GENERATE_SAMPLE_DATA --payload complex-instructions.yaml
```

This will result in something similar as shown below:

```
"email","gebruikersnaam","organisatie","klantnummer","bankrekening"
"johndoe@example.de","johndoe","Doe Enterprises","1000001","DE89370400440532013000"
"janedoe@example.fr","janedoe","Doe Industries","1000002","FR7630006000011234567890189"
"alice.smith@example.it","alicesmith","Smith Co.","1000003","IT60X0542811101000000123456"
"bob.jones@example.es","bobjones","Jones LLC","1000004","ES9121000418450200051332"
"charlie.brown@example.nl","charliebrown","Brown Corp.","1000005","NL91ABNA0417164300"
"emily.white@example.fi","emilywhite","White Ltd.","1000006","FI5542345678901234"
"david.miller@example.pt","davidmiller","Miller Inc.","1000007","PT50000201231234567890154"
"grace.lee@example.be","gracelee","Lee Group","1000008","BE68539007547034"
"henry.wilson@example.no","henrywilson","Wilson & Sons","1000009","NO9386011117947"
"lisa.clark@example.se","lisaclark","Clark Partners","1000010","SE3550000000054910000003"
"michael.evans@example.dk","michaelevans","Evans Services","1000011","DK5000400440116243"
"nancy.king@example.ie","nancyking","King's Goods","1000012","IE29AIBK93115212345678"
"oliver.hall@example.at","oliverhall","Hall Ltd.","1000013","AT611904300234573201"
"peter.allen@example.ch","peterallen","Allen AG","1000014","CH9300762011623852957"
"rachel.johnson@example.pl","racheljohnson","Johnson S.A.","1000015","PL61109010140000071219812874"
"samantha.davis@example.cz","samanthadavis","Davis s.r.o.","1000016","CZ6508000000192000145399"
"thomas.moore@example.hu","thomasmoore","Moore Kft.","1000017","HU42117730161111101800000000"
"victoria.baker@example.gr","victoriabaker","Baker EPE","1000018","GR1601101250000000012300695"
"william.carter@example.ro","williamcarter","Carter SRL","1000019","RO49AAAA1B31007593840000"
"zoe.turner@example.bg","zoeturner","Turner Ltd.","1000020","BG80BNBG96611020345678"

```
