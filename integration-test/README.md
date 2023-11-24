# Integration tests for PACE development

## Fat app.jar
To execute the integration tests you need a fat jar of the PACE app
in this directory.

    make app.jar

You don't *have to* explicitly execute this step. `app.jar` is one of the dependencies of the `all` target.

## Create PostgreSQL containers
Start 2 docker PostgreSQL containers.

    docker-compose up --wait

## Execute all the tests

    make all
