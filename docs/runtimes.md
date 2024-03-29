# Runtimes

PACE supports the following runtimes:

* **PACE server application:** a continuous running ([Spring Boot](https://spring.io/projects/spring-boot)) application that is connected to your Data Catalog and Processing Platform. This is meant to be instructed through the API to perform specific actions, such as create a view based on a Data Policy.
* **Standalone dbt module:** a module that includes the same core functionality that the Spring Boot application offers, but is meant to be short-lived, within the scope of its task. It is used to extend a dbt project, in order read the `manifest.json` and create Data Policies based on the models in the dbt project.
