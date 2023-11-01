# Kubernetes Deployment

<details>

<summary>Coming soon.</summary>

If you really can't wait for the official docs on getting started with PACE on Kubernetes, here's something to get you started:

1. Create a deployment with the PACE container image and expose ports
   * `8080`: a.o. Spring Boot Actuator
   * `9090`: JSON / gRPC proxy
   * `50051`: gRPC
2. Ensure that you've setup Postgres in some way for PACE to connect to.
3. Optionally create a Kubernetes service to connect to PACE.

</details>
