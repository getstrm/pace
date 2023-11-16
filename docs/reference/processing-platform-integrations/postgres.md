# PostgreSQL 
This might be the same as the Pace database, but can also be a different one.

* `id` The identifier of this PostgreSQL database in Pace.
* `hostName` The hostname of the PostgreSQL database server.
* `port` The tcp port number of the database server.
* `userName` Connection details.
* `password`
* `database`

Example:

```yaml
app:
  processing-platforms:
    postgres:
      - id: pace-postgres
        hostName: localhost
        userName: ...
        password: ...
        database: ...
        port: 5432
```
