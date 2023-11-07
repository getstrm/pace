---
description: Welcome to the PACE docs!
---

# Getting Started

**About PACE**

PACE is the **P**olicy **A**nd **C**ontract **E**ngine. It helps you to programatically create a data contract to define and apply a data policy to a processing platform (like Databricks, Snowflake or BigQuery). &#x20;

<mark style="color:blue;background-color:blue;">Data policy IN, dynamic view OUT</mark> is the easiest way to describe it.&#x20;

**Problem sPACE**

PACE is designed to remove friction and cost from using data in real-world organisational settings. In other words: just build with data, instead of jumping through hoop after hoop to  \*access\* the data you need to deliver that next project, report or data product.&#x20;

PACE does this by connecting data governance tooling (usually a catalog) to the actual data processing.&#x20;

If this sounds familiar AND you're using one the currently supported platforms, PACE is worth a try:

* You have to navigate many (competing) policies, constraints and stakeholders to access data.&#x20;
* The data approval process is complicated, costly and lengthy.
* Not all stakeholders are involved in data policy definition, usually this present as someone being mad at you after the fact.&#x20;
* Auditing still takes a ton of manual effort despite everything being just data.&#x20;
* Privacy and security policies mostly exist on paper. Risk is not mitigated in (system) realities.
* Data policies cannot be configured uniformly over hybrid and multi-cloud setups.
* Governance and processing are done in different, unconnected tools

**Positioning**

Once installed, PACE sits between your data definitions (often a [catalog](cli-docs/pace\_list\_catalogs.md)) and [processing platform](cli-docs/pace\_list\_processing-platforms.md):

\[add image van paul]

**Supported platforms**

Pace currently [supports](integrations-and-reference/integrations/) Collibra, Datahub and Open Data Discovery on the catalog side, connecting to Snowflake, Databricks, Google BigQuery, and PostgreSQL.&#x20;

**Let's go**

To install and use PACE, you need:

* The [PACE app ](https://github.com/getstrm/pace)(as Spring Boot app or Docker image)&#x20;
* The PACE [CLI](https://github.com/getstrm/cli) to interact with your deployment

In the current Alpha release, PACE is a closed repo. Get in touch to request access and take it for a spin! pace \[a] getstrm.com
