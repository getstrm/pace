# Policy and Contract Engine

[//]: # (TODO add shields from shields.io for build status, app version, Maven artifact [if we're going to publish to Sonatype], and Docker Image version)

![pace-logo](./assets/pace-logo.svg)

> PACE is the _Policy and Contract Engine_, whose goal is to ensure that data is only used by those allowed, the way it
was intended to be used.

## Motivation and problem

Ever wondered if you're allowed to use data within your company? Use RBAC to prevent access to certain data by certain
people? How does a legal department enforce data is used exactly how the agreements state that it should be used?

After talking to many different companies, ranging from small to enterprise, we learned that it is **hard** to solve the
problem described by the questions above. The problem, from the perspective of the Data Consumer can be laid out as
follows:

> Various **data consumers** (1), should only be shown **a representation of data** (2) that is **tailored to who they
are and what they're allowed to see** (3), regardless of **the data catalog** (4) on which they explore and find data,
> and regardless of the **data processing platform** (5) on which they consume the data.

1. **Data Consumers**  
   The end-user that uses data for a specific purpose, i.e. a Data Analyst or Data Scientist
2. **Representation of the data**  
   End-users should not see the raw data, but a representation of these data. This way the access to the raw data can be limited to those that administer these data.
3. **Tailored to who they are and what they're allowed to see**  
   Classically this is about authentication and authorization. The difference here, is that these are used as inputs for the actual representation of the data, instead of classical RBAC.
4. **The Data Catalog**  
   Data catalogs are used as a central marketplace for producers and consumers of data. Therefore, a system that is able to create representations of data, should be able to integrate with data catalogs.
5. **The Data Processing Platform**  
   Data processing platforms often have various responsibilities, but it at least includes a storage layer (e.g. data lake / data warehouse) and an interfacing layer to the storage layer. This is where producers store data and where consumers use data.

## Solution

So, where and how does PACE solve these issues? PACE focuses on creating representations of data (e.g. by generating views), based on so-called **Data Policies**.

A [**Data Policy**](protos/getstrm/api/data_policies/v1alpha/entities_v1alpha.proto) is a structured (machine-readable) document, that aims at capturing how source data should be presented to various principals (i.e. a data accessor), and which transformations should be applied to the data, to create a representation of the source data on the hosting data processing platform.

Data Policies are constructed by retrieving the data schema (the structure of the data) from either a data catalog or a data processing platform. Next, various rule sets can be created, that determine how source data is transformed and/or filtered to create a representation of data on the processing platform.
Defining rule sets is a cooperation between various teams: data consumers, data producers, and most important, the legal team.

> Want to learn more about how to facilitate this cooperation between various teams? Navigate to https://pace.getstrm.com to see how we can help you

### Architecture
[//]: # (TODO add architecture image here)

## Installation
[//]: # (TODO add installation and setup details here or refer to the docs)


