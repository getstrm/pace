---
description: Learn how to run PACE in your environment
---

# Installation

There are several ways to run PACE (e.g. by starting the Spring Boot application), but the easiest way to run it, is via Docker. This ensures that you don't need a JVM in order to run the application, and this also includes a REST interface to PACE (whereas the Spring Boot application only exposes a [gRPC interface](https://grpc.io/)).

This document focuses on getting PACE up and running on your local machine. We'll discuss different environments, such as [Kubernetes](kubernetes-deployment.md) elsewhere in the documentation.

#### Prerequisites

Before you get started, make sure you've installed the following tools:

* [Docker](https://www.docker.com/)
* [PostgreSQL](https://www.postgresql.org/) (optionally, this can also be ran from a Docker container)
* CLI for PACE

{% hint style="warning" %}
The relative location of the files created in the rest of this document can be seen as titles in the respective code blocks.
{% endhint %}

#### Create a basic PACE configuration

The following config is used for configuring your PACE application.

{% code title="config/application.yaml" lineNumbers="true" fullWidth="false" %}
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/postgres
    hikari:
      username: postgres
      password: postgres
      schema: public
```
{% endcode %}

#### Create a Docker Compose setup

Create the following `docker-compose.yaml` in the parent directory of where you created `config/application.yaml`.

{% hint style="info" %}
Extra Spring Boot configuration is configured to be read from **`/app/config`**. Be sure to add all relevant Spring Boot configuration files there.
{% endhint %}

{% code title="docker-compose.yaml" lineNumbers="true" %}
```yaml
version: '3.5'

services:
  postgres:
    container_name: pace_postgres
    image: postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      PGDATA: /data/postgres
    volumes:
      - postgres:/data/postgres
    ports:
      - "5432:5432"
    networks:
      - postgres

  pace:
    container_name: pace
    # you may want to update the image to the latest version
    image: ghcr.io/getstrm/pace:1.0.0-alpha.4
    volumes:
      - ./config/:/app/config/

    ports:
      - "8080:8080" # Spring Boot
      - "9090:9090" # Envoy JSON / gRPC Proxy
      - "50051:50051" # gRPC
    networks:
      - postgres

networks:
  postgres:
    driver: bridge

volumes:
  postgres:

```
{% endcode %}

#### Start interacting with your PACE instance

Next up, you can start interacting with your PACE instance, by accessing the [REST interface](../reference/api-reference.md), [gRPC interface](https://github.com/getstrm/pace/tree/alpha/protos) or through the [CLI](https://github.com/getstrm/cli). Here's an example listing the available Data Catalog integrations through the REST interface:

```sh
curl http://localhost:9090/catalogs
{"catalogs":[]}
```

Of course this is a silly example, as we have not configured any catalogs, though it confirms that your PACE instance is correctly running.

In order to put your PACE instance to good use, you'll need to add at least one [Processing Platform](connect-a-processing-platform.md). Optionally connect it to a [Data Catalog](connect-a-data-catalog.md), in order to use table definitions from the data catalog as a starting point for defining policies.
