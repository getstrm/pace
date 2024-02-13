# Running PACE dbt module in a container

The requirements for running PACE dbt module inside a container are:
* JVM Runtime (Java 17 or later)
* The PACE dbt module jar file (can be retrieved from the PACE releases on GitHub)

An example of what a Dockerfile could look like is shown below:

```Dockerfile
FROM eclipse-temurin:21-jre-jammy as jvm

FROM python:slim

RUN mkdir /app
WORKDIR /app

# Copy the JVM runtime
COPY --from=jvm /opt/java/openjdk /opt/java/openjdk

# Set the environment variables and add the JVM to the PATH
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
```
