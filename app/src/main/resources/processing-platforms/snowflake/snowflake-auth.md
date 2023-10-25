# Snowflake Authentication

For Snowflake, we've researched the best approach for machine-to-machine authentication. Public / Private key pairs
are the most suitable for this use case, and as Daps should be also usable standalone, we've decided to use this.

This doc follows along with
the [Snowflake docs regarding key pairs](https://docs.snowflake.com/en/user-guide/key-pair-auth).

Limitations:

- No file

## Creating a key pair

```
cd app/src/main/resources/snowflake
openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -out daps-snowflake-private-key.p8 -nocrypt
```

## Create a public key

```
openssl rsa -in daps-snowflake-private-key.p8 -pubout -out daps-snowflake-public-key.pub
```

## Register the public key in Snowflake

Grab the contents of the `daps-snowflake-public-key.pub`, replace the newlines with `\n`, omit the `begin` and `end`
sections, and register it in Snowflake.
This needs to be done by an `ACCOUNT_ADMIN`.

```sql
alter user user_name set rsa_public_key = '<public_key>'
```

Verify whether it's correctly registered, by checking if a fingerprint has been generated:

```sql
DESC USER user_name;
```
