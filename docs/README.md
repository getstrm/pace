# What is PACE?

**About PACE**

{% hint style="info" %}
Looking for a non-tech intro to PACE? Start with a video of a[ simple use case](pace-server/getting-started/example-use-case.md) .
{% endhint %}

PACE is the **P**olicy **A**s **C**ode **E**ngine. It helps you to programmatically create and apply a data policy to a processing platform (like [Databricks](tutorials/databricks.md), Snowflake or BigQuery). Through a data contract, you can apply filters, field transforms and access settings to create a view inside a data platform.

_`Data policy IN, dynamic view OUT`_ is the easiest way to describe it.

Jump right in with our [quickstart](pace-server/getting-started/quickstart.md), or read on and watch our 5-minute intro below with the most import principles and concepts.&#x20;

**ProblemsPACE**

PACE is designed to remove friction and cost from using data in real-world organizational settings. In other words: define and implement a policy to "just build" with data, instead of jumping through hoop after hoop.

If (one of) these sound familiar and you are using one of the currently supported processing platforms, PACE is worth a try:

* You have to navigate many (competing) policies, constraints and stakeholders to access data.
* The data approval process is complicated, costly and lengthy.
* Data policies cannot be configured uniformly over hybrid and multi-cloud setups.
* Governance and processing are done in different, unconnected tools

**Positioning**

Once installed, PACE sits on top of or between your data definitions (often a [catalog](cli-docs/pace\_list\_catalogs.md)) and [processing platform](cli-docs/pace\_list\_processing-platforms.md):

<figure><img src=".gitbook/assets/PACE-process-2.0@2x+interlace (1).png" alt=""><figcaption></figcaption></figure>

**Supported platforms**

PACE currently [supports](reference/integrations/) Collibra, Datahub and Open Data Discovery on the catalog side, connecting to Snowflake, Databricks, Synapse, Google BigQuery and PostgreSQL for creating your dynamic views.

**Let's go**

To install and use PACE, you need:

* The [PACE app ](https://github.com/getstrm/pace)(as Spring Boot app or Docker image)
* The PACE [CLI](https://github.com/getstrm/cli) to interact with your deployment

### Intro video

In this 5-minute intro video, our founder Pim explains what a data policy is and how PACE enables you to create, manage, maintain, and enforce data policies in a platform-independent and lightweight way. We'll also discuss how PACE works as a standalone Docker app and how you can deploy it on Kubernetes. Finally we go over a quick example of how a fictional retailer applies PACE to a simple use case. Join me to learn more about PACE and its benefits for your organization.

{% embed url="https://youtu.be/mR19Rv40GmI" %}
A 5-minute intro to PACE with the most important principles and background.
{% endembed %}
