---
description: Welcome to the PACE docs!
---

# Getting Started

**About PACE**

PACE is the **P**olicy **A**nd **C**ontract **E**ngine. It helps you to programatically create a data contract to define and apply a data policy to a processing platform (like Databricks, Snowflake or BigQuery). &#x20;

_`Data policy IN, dynamic view OUT`_ is the easiest way to describe it.&#x20;

{% hint style="info" %}
PACE is currently in closed alpha! Reach out to pace \[a] getstrm.com to request access! Please list your name, org, and why you would like to try PACE.&#x20;
{% endhint %}

**ProblemsPACE**

PACE is designed to remove friction and cost from using data in real-world organisational settings. In other words: just build with data, instead of jumping through hoop after hoop. PACE does this by connecting data governance tooling (usually a catalog) to the  data processing platform

If (one of) these sound familiar and you're using one the currently supported platforms, PACE is worth a try:

* You have to navigate many (competing) policies, constraints and stakeholders to access data.&#x20;
* The data approval process is complicated, costly and lengthy.
* Not all stakeholders are involved in data policy definition, usually this present as someone being mad at you after the fact.&#x20;
* Auditing still takes a ton of manual effort despite everything being just data.&#x20;
* Privacy and security policies mostly exist on paper. Risk is not mitigated in (system) realities.
* Data policies cannot be configured uniformly over hybrid and multi-cloud setups.
* Governance and processing are done in different, unconnected tools

**Positioning**

Once installed, PACE sits between your data definitions (often a [catalog](cli-docs/pace\_list\_catalogs.md)) and [processing platform](cli-docs/pace\_list\_processing-platforms.md):

<figure><img src=".gitbook/assets/PACE-process-2.0@2x+interlace (1).png" alt=""><figcaption></figcaption></figure>

**Supported platforms**

Pace currently [supports](integrations-and-reference/integrations/) Collibra, Datahub and Open Data Discovery on the catalog side, connecting to Snowflake, Databricks, Google BigQuery, and PostgreSQL for creating your dynamic views.

**Let's go**

To install and use PACE, you need:

* The [PACE app ](https://github.com/getstrm/pace)(as Spring Boot app or Docker image)&#x20;
* The PACE [CLI](https://github.com/getstrm/cli) to interact with your deployment
