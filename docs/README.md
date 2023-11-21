---
description: Welcome to the PACE docs!
---

# Getting Started

**About PACE**

PACE is the **P**olicy **A**nd **C**ontract **E**ngine. It helps you to programmatically create and apply a data policy to a processing platform (like Databricks, Snowflake or BigQuery). Through a data contract, you can apply filters, field transforms and access settings to create a view inside a data platform.

_`Data policy IN, dynamic view OUT`_ is the easiest way to describe it.

{% hint style="info" %}
PACE is currently in closed alpha. [Request access!](https://pace-alpha.framer.ai/)&#x20;
{% endhint %}

**ProblemsPACE**

PACE is designed to remove friction and cost from using data in real-world organizational settings. In other words: define and implement a policy to "just build" with data, instead of jumping through hoop after hoop.

If (one of) these sound familiar and you are using one of the currently supported processing platforms, PACE is worth a try:

* You have to navigate many (competing) policies, constraints and stakeholders to access data.
* The data approval process is complicated, costly and lengthy.
* Data policies cannot be configured uniformly over hybrid and multi-cloud setups.
* Governance and processing are done in different, unconnected tools

**Positioning**

Once installed, PACE sits between your data definitions (often a [catalog](cli-docs/pace\_list\_catalogs.md)) and [processing platform](cli-docs/pace\_list\_processing-platforms.md):

<figure><img src=".gitbook/assets/PACE-process-2.0@2x+interlace (1).png" alt=""><figcaption></figcaption></figure>

**Supported platforms**

PACE currently [supports](integrations-and-reference/integrations/) Collibra, Datahub and Open Data Discovery on the catalog side, connecting to Snowflake, Databricks, Google BigQuery and PostgreSQL for creating your dynamic views.

**Let's go**

To install and use PACE, you need:

* The [PACE app ](https://github.com/getstrm/pace)(as Spring Boot app or Docker image)
* The PACE [CLI](https://github.com/getstrm/cli) to interact with your deployment
