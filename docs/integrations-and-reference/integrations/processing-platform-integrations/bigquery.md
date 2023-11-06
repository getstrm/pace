---
description: Connecting PACE to a BigQuery instance
---

# BigQuery

PACE translates Data Policies to authorized views. This requires certain privileges. Additionally, a user group mapping table is required, since BigQuery doesn't offer a way to retrieve user groups natively in (authorized) views. Follow the below sections to connect PACE to a BigQuery instance.

In a standard GPC deployment, PACE's integration will look like this:&#x20;

<figure><img src="../../../.gitbook/assets/STRM-PACE-in-GCP-biquery" alt=""><figcaption></figcaption></figure>

{% hint style="info" %}
PACE creates authorized views so that users of these views do not need read access to the underlying data, which would defeat the purpose of controlling access and policies through PACE Data Policies.
{% endhint %}

## Service account creation and privileges

We advice creating a new dedicated service account to connect PACE to your BigQuery environment. Also create a JSON key for this account and make sure to store it securely.

The easiest way to configure the required privileges is to grant the PACE service account the `DataOwner` role for all source and target datasets, as well as the `JobUser` role, but a more fine-grained setup is possible:

* Grant the service account the `JobUser` role on the BigQuery project you want the PACE queries to run in (i.e. the create view queries/jobs).
* Grant the permissions required for creating authorized views (see also [https://cloud.google.com/bigquery/docs/authorized-views](https://cloud.google.com/bigquery/docs/authorized-views)):
  * `bigquery.tables.create` on the **target** datasets.
  * `bigquery.tables.getData` on the **source** datasets.
  * `bigquery.datasets.get` and `bigquery.datasets.update` on the **source** datasets.

## User group mapping table

To keep track of the group [principals.md](../../../data-policy/principals.md "mention") a user belongs to, create a table (or view) with the following **string** fields: `userEmail` and `userGroup`.

A user may appear in multiple rows, one per group. Changes to this table will immediately affect the query results on PACE authorized views for the modified users.

## PACE application properties

After following the above steps, provide the corresponding [configuration](../../../getting-started/example-configuration-file.md) to the PACE application for each BigQuery instance you want to connect with. For example:

```yaml
app:
  processing-platforms:
    bigquery:
      - id: "bigquery-dev"
        projectId: "my-google-cloud-project"
        userGroupsTable: "my-other-cloud-project.config_dataset.user_groups"
        serviceAccountJsonKey: |
          {
            "type": "service_account",
            "project_id": "my-google-cloud-project",
            "private_key_id": "xxxx",
            "private_key": "-----BEGIN PRIVATE KEY-----\nxxxx\n-----END PRIVATE KEY-----\n",
            "client_email": "pace-user@my-google-cloud-project.iam.gserviceaccount.com",
            "client_id": "1234",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/pace-user%40my-google-cloud-project.iam.gserviceaccount.com",
            "universe_domain": "googleapis.com"
          }
```

The properties are expected to contain the following:

* `id`: an arbitrary identifier unique within your organization for the specific platform (BigQuery).
* `projectId`: the google cloud project id where PACE should execute its queries (may differ from source/target datasets).
* `userGroupsTable`: the full name of the table containing the user group mapping, i.e. `<project>.<dataset>.<table>`.
* `serviceAccountJsonKey`: the JSON key created for the service account to be used by PACE.

