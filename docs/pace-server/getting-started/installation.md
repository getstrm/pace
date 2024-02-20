---
description: Learn how to run PACE in your local environment
---

# Installation

There are several ways to run PACE – including starting the Spring Boot application from your IDE – but the easiest way to run it, is via Docker. This way, no JVM is needed, and a REST interface to PACE is included out of the box (whereas the standalone Spring Boot application only exposes a [gRPC interface](https://grpc.io/)).

This document focuses on getting PACE up and running on your local machine. We will discuss other environments – such as [Kubernetes](kubernetes-deployment.md) – elsewhere in the documentation.

#### Prerequisites

Before you get started, make sure you've installed the following tools:

* [Docker](https://www.docker.com/)
* [PostgreSQL](https://www.postgresql.org/) (or run it with Docker, as shown in the Docker Compose setup below)
* [CLI for PACE](https://github.com/getstrm/cli)

{% hint style="warning" %}
The relative location of the files created in the rest of this document can be seen as titles in the respective code blocks.
{% endhint %}

#### Create a basic PACE configuration

The following config is used for configuring your PACE application. If you use your own Postgres instance, modify accordingly.

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

{% hint style="info" %}
If you're not familiar with JVM / Spring Boot applications, [`hikari`](https://github.com/brettwooldridge/HikariCP) is a widely adopted connection pooling library.
{% endhint %}

#### Create a Docker Compose setup

Create the following `docker-compose.yaml` in the parent directory of where you created `config/application.yaml`.

{% hint style="info" %}
Extra Spring Boot configuration is configured to be read from an\*\*`/app/config`\*\*volume. Be sure to add all relevant Spring Boot configuration files there. The below setup mounts the config dir to this volume.
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
    image: ghcr.io/getstrm/pace:1.0.0-alpha.7
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

To start pace, execute `docker compose up` in the directory containing your `docker-compose.yaml` file. You should see the Spring Boot startup logs, which will end with `Started PaceApplicationKt [...]` , if all went well.

Next up, you can start interacting with your PACE instance, by accessing the [REST interface](../../reference/api-reference/rest.md), [gRPC interface](https://github.com/getstrm/pace/tree/alpha/protos) or through the [CLI](https://github.com/getstrm/cli). Here's an example listing the available Data Catalog integrations through the REST interface:

{% tabs %}
{% tab title="CLI" %}
```shell-session
pace list catalogs -o table
 ID                       TYPE

 COLLIBRA-testdrive   COLLIBRA
 datahub-on-dev        DATAHUB
```
{% endtab %}

{% tab title="curl" %}
```sh
curl --silent http://localhost:9090/catalogs | jq
{
  "catalogs": [
    {
      "id": "COLLIBRA-testdrive",
      "type": "COLLIBRA",
      "databases": [],
      "tags": []
    },
    {
      "id": "datahub-on-dev",
      "type": "DATAHUB",
      "databases": [],
      "tags": []
    }
  ]
}
```
{% endtab %}
{% endtabs %}

Since we have not configured any catalogs, the result is not particularly interesting, though it confirms that your PACE instance is running correctly.

In order to put your PACE instance to good use, you'll need to add at least one [Processing Platform](connect-a-processing-platform.md). Optionally connect it to a [Data Catalog](connect-a-data-catalog.md), in order to use table definitions from the data catalog as a starting point for defining policies.
