# Containerized dbt module

The requirements for running the PACE dbt module inside a container are:

* JVM Runtime (Java 17 or later)
* The PACE dbt module jar file (can be retrieved from a release's **Assets** on the PACE [Releases page](https://github.com/getstrm/pace/releases) on GitHub)

Therefore, you can easily add PACE dbt to your container image, by ensuring that these prerequisites are met. You can modify your Dockerfile, in order to initiate a multi-stage build process, where a JVM container image will be used as a base to copy the JVM from.

An example of what a Dockerfile could look like is shown below:

```Dockerfile
FROM eclipse-temurin:21-jre-jammy as jvm

FROM python:slim

RUN mkdir /app
WORKDIR /app

# Copy the JVM runtime
COPY --from=jvm /opt/java/openjdk /opt/java/openjdk
COPY dbt.jar /app/dbt.jar

# Set the environment variables and add the JVM to the PATH
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
```
