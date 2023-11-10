# Pace: Policy And Contract Engine
[![release status](https://img.shields.io/badge/release_status-alpha-orange)](https://github.com/getstrm/pace/tree/alpha) [![latest release](https://img.shields.io/github/v/release/getstrm/pace?include_prereleases&label=release&logo=github)](https://github.com/getstrm/pace/releases/latest)

[//]: # (TODO add shields from shields.io for build status, app version, Maven artifact [if we're going to publish to Sonatype], and Docker Image version)

![orange-filled](./assets/svg/pace-logo-orange-filled.svg)  
![black](./assets/svg/pace-logo-black.svg)  
![outline](./assets/svg/pace-logo-orange-outline.svg)

> PACE is the _Policy and Contract Engine_. It helps you to programatically create and apply a data policy to a processing platform (like Databricks, Snowflake or BigQuery). Through a data contract, you can apply filters, field transforms, tag-based conditions and access settings to create a view inside a data platform. With Pace, you can enforce policies to data to ensure that data is only used by those allowed and in the way it was intended to be used.

Follow the [quickstart](https://pace.getstrm.com/docs/readme/quickstart) if you want to dive right in. 

## Motivation and problem

PACE is designed to remove friction and cost from using data in real-world organisational settings. In other words: define and implement a policy to "just build" with data, instead of jumping through hoop after hoop.

If (one of) these sound familiar and you're using one of the currently supported platforms, PACE is worth a try:
* You have to navigate many (competing) policies, constraints and stakeholders to access data. 
* The data approval process is complicated, costly and lengthy. "Approval" is only a ticket, configuration is not done automatically.
* Data policies cannot be configured uniformly over hybrid and multi-cloud setups.
* Governance and processing are done in different, unconnected tools.

## Positioning
Once installed, PACE sits between your data definitions (often a catalog) and processing platform. The deep dive below provides more background.

## Supported platforms
Pace currently supports Collibra, Datahub and Open Data Discovery on the catalog side, connecting to Snowflake, Databricks, Google BigQuery, and PostgreSQL for creating your dynamic views.

## Supported policy methods
It's early for PACE (we're in alpha)|. The following policy methods (called a `rule set` in PACE) are currently available:
- Field transforms, e.g. "replace everything before the @ in the column `email`, or "nullify the `phone number`".
- Access definitions (called _Principals_): who can access this view?
- Filter conditions, e.g. "analysts in Greece can only see transaction from our Greek stores".
- Tag-based rules, e.g. "do `something` if data is tagged `Greece`.
- Global rulesets, e.g. "All data tagged `PII` should always be masked.

These policy methods can be layered to create a powerful programmatic interface to define, implement, maintain and update policies. Create an issue if you think a valuable policy method is missing!

## Let's go
To install and use PACE, you need:
- The PACE app (as Spring Boot app or Docker image) 
- The PACE CLI to interact with your deployment

## Learn more
Head over to the [docs](https://pace.getstrm.com/docs/readme/quickstart) for more info and reach out to the [STRM](https://getstrm.com) team for more info and/or to test and implement PACE together. 

## Deep dive
PACE is built to connect the world of descriptive data tools to the actual data processing platforms (where all that data stuff takes place!). 

It's designed to make sure your data governance can follow this pattern:
> Various **data consumers** (1), should only be shown **a representation of data** (2) that is **tailored to who they are and what they're allowed to see** (3), regardless of **the data catalog** (4) in which they explore and find data, and regardless of the **data processing platform** (5) on which they consume the data.

### Definitions
1. **Data Consumers**  
   The end-user that uses data for a specific purpose, i.e. a Data Analyst or Data Scientist
2. **Representation of the data**  
   End-users should not see the raw data, but a representation of these data. This way the access to the raw data can be
   limited to those that administer these data.
3. **Tailored to who they are and what they're allowed to see**  
   Classically this is about authentication and authorization. The difference here, is that these are used as inputs for
   the actual representation of the data, instead of classical RBAC.
4. **The Data Catalog**  
   Data catalogs are used as a central marketplace for producers and consumers of data. Therefore, a system that is able
   to create representations of data, should be able to integrate with data catalogs.
5. **The Data Processing Platform**  
   Data processing platforms often have various responsibilities, but it at least includes a storage layer (e.g. data
   lake / data warehouse) and an interfacing layer to the storage layer. This is where producers store data and where
   consumers use data.

### Solution

To solve this, PACE focuses on creating representations of data (e.g. by generating views), based on so-called **Data Policies**.

A [**Data Policy**](https://github.com/getstrm/pace/blob/alpha/protos/getstrm/pace/api/entities/v1alpha/entities.proto)
is a structured (human-defined but machine-readable) document, that aims at capturing how source data should be presented to various
principals (i.e. a data accessor), and which transformations should be applied to the data, to create a representation
of the source data on the data processing platform.

Data Policies are constructed by retrieving the data schema (the structure of the data) from either a data catalog or a
data processing platform. Next, various rule sets can be created, that determine how source data is transformed and/or
filtered to create a representation of data on the processing platform.
Defining rule sets is a cooperation between various teams: data consumers, data producers, and most important, the legal
team.

> Want to learn more about how to facilitate this cooperation between various teams? Navigate
> to https://pace.getstrm.com to see how we can help you!

