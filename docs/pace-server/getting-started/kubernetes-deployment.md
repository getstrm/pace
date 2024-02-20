---
description: Run PACE on your Kubernetes cluster
---

# Kubernetes Deployment

PACE includes Kubernetes awareness and support through the [Spring Cloud Kubernetes](https://docs.spring.io/spring-cloud-kubernetes/reference/index.html) project. Application properties can be loaded through ConfigMaps or Secrets, as documented there. On this page, we show our preferred way of deploying PACE on Kubernetes. Adjust this to your own (Kubernetes) setup and way of working as needed.

{% hint style="warning" %}
As a prerequisite, a Postgres DB instance is required, reachable from your Kubernetes cluster, ideally with its own PACE database and user.
{% endhint %}

## Kubernetes spec files

You may want to create a new namespace for PACE first:

```bash
kubectl create namespace pace
```

In a directory of your choice, create the following three spec files. (Again, adjust to your own setup, e.g. using Terraform or Kustomize.)

### Application configuration

In the [Quickstart](quickstart.md), we showed how the Spring Boot application configuration file was mounted as a volume in the PACE Docker container using Docker compose. For Kubernetes, you could use a similar approach. We suggest creating a [Kubernetes Secret](https://kubernetes.io/docs/concepts/configuration/secret/) containing the entire application configuration yaml as `stringData`, for example:

{% code title="pace-config-secret.yaml" %}
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pace
  namespace: pace
type: Opaque
stringData:
  application.yaml: |-
    spring:
      datasource:
        # The Postgres instance configured here should be accessible from your PACE deployment.
        url: jdbc:postgresql://pace-postgres-postgresql.pace.svc.cluster.local:5432/postgres
        hikari:
          username: postgres
          password: postgres
    app:
      processing-platforms:
        databricks:
          - id: "dbr-pace"
            workspaceHost: "https://dbc-xxx-xxx.cloud.databricks.com/"
            accountHost: "https://accounts.cloud.databricks.com"
            accountId: "4ca..."
            clientId: "877..."
            clientSecret: "dos..."
            warehouseId: "3bd..."
```
{% endcode %}

### Deployment

With the application configuration secret in place, you can create a [Kubernetes deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) with a spec similar to the following:

{% code title="pace-deployment.yaml" %}
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pace
  labels:
    app: pace
spec:
  # PACE is currently stateless, so you could increase the amount of replicas if desired.
  replicas: 1
  selector:
    matchLabels:
      app: pace
  template:
    metadata:
      name: pace
      labels:
        app: pace
    spec:
      containers:
        - name: pace
          # You may prefer to use a specific version with a pull policy of IfNotPresent.
          image: ghcr.io/getstrm/pace:latest-alpha
          imagePullPolicy: Always
          env:
            # Here we tell PACE where to look for the application configuration.
            # The path should match the volume mount below, and end with a slash.
            - name: SPRING_CONFIG_IMPORT
              value: "file:///etc/config/"
          ports:
            - containerPort: 8080
              name: actuator
            - containerPort: 9090
              name: json-grpc-proxy
            - containerPort: 50051
              name: grpc
          volumeMounts:
            # The volume mount for the configuration secret.
            - name: config
              mountPath: /etc/config
              readOnly: true
      restartPolicy: Always
      volumes:
        # We create a volume for the secret. This will result in a file called
        # application.yaml under the volume mount directory, i.e. /etc/config/application.yaml.
        - name: config
          secret:
            secretName: pace
            items:
              - key: application.yaml
                path: application.yaml
```
{% endcode %}

As you can see, we chose the approach of mounting the configuration yaml from the secret as a file and setting the `spring.config.import` property through the `SPRING_CONFIG_IMPORT` env variable, matching the volume mount's directory. You can use or combine the other approaches [supported by Spring](https://docs.spring.io/spring-cloud-kubernetes/reference/property-source-config.html). We prefer this way, as we can simply keep using YAML.

### Service

You will probably want to expose the PACE deployment through a [Kubernetes service](https://kubernetes.io/docs/concepts/services-networking/service/), similar to the following:

{% code title="pace-service.yaml" %}
```yaml
apiVersion: v1
kind: Service
metadata:
  name: pace
spec:
  selector:
    app: pace
  ports:
    - port: 8080
      targetPort: 8080
      name: actuator
    - port: 9090
      targetPort: 9090
      name: json-grpc-proxy
    - port: 50051
      targetPort: 50051
      name: grpc
  type: NodePort
```
{% endcode %}

## Applying the configuration

Now all you need to start PACE is to apply these specs:

```bash
kubectl apply --namespace pace -f pace-config-secret.yaml -f pace-deployment.yaml -f pace-service.yaml
```

Or simply `kubectl apply --namespace pace -f .` if these are the only files in your current directory.

You can now interact with PACE, for example through [port forwards](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/) or tools like [Telepresence](https://github.com/telepresenceio/telepresence).

An example port forward to the JSON gRPC proxy endpoint:

```bash
kubectl port-forward service/pace 9090:9090 --namespace pace
```

After which you may retrieve the configured processing platforms like so:

```bash
$ curl localhost:9090/v1alpha/processing-platforms
{"processingPlatforms":[{"platformType":"DATABRICKS","id":"dbr-pace"}]}
```

## Cleanup

Execute `kubectl delete --namespace pace -f .`  from the directory containing the spec files, or simply delete the namespace itself with `kubectl delete namespace pace` , if applicable to your setup.

{% hint style="info" %}
If you miss any Kubernetes-related functionality, please reach out to us!
{% endhint %}
