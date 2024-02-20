---
description: Connecting PACE to a BigQuery instance
---

# BigQuery

PACE translates Data Policies to authorized views. This requires certain privileges.  For the use of Principals, we offer two options. The first (and easy) way, using a user group mapping table. The second way by leveraging the Google Groups, Roles and Permissions from IAM. Follow the below sections to connect PACE to a BigQuery instance.

In a standard GCP deployment, PACE's integration will look like this:&#x20;

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

{% hint style="warning" %}
The table must be located in the same region where the PACE views are created.
{% endhint %}

#### PACE application properties

After following the above steps, provide the corresponding [configuration](../../../pace-server/getting-started/example-configuration-file.md) to the PACE application for each BigQuery instance you want to connect with. For example:

```yaml
app:
  processing-platforms:
    bigquery:
      - id: "bigquery-dev"
        project-id: "my-google-cloud-project"
        user-groups-table: "my-google-cloud-project.config_dataset.user_groups"
        service-account-json-key: |
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

## Google IAM extensions

We offer two extensions for Principal checks: `Google IAM Sync` and `Google IAM Check`. The `Sync` extension provides an automated way of keeping the user mapping table in sync, whereas the `Check` extension is a standalone check that leverages all IAM groups, roles and permissions for your Google project. Both are described in more detail below.&#x20;

### Prerequisites

The extensions share a lot of prerequisites. Follow the steps below to configure your Google project properly.

First of all, we need to "trust" the `Google Auth Library`. This requires a super-admin account as you need to login to the [admin console](https://admin.google.com/ac/owl/list?tab=configuredApps). Click Add App and select based on Client-ID.\
The corresponding app-id is `764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com`. \
Complete the wizard to make it a trusted app.

Next up, make sure that the following APIs are enabled:

* [Admin SDK](https://console.cloud.google.com/apis/library/admin.googleapis.com)
* [Cloud Functions](https://console.cloud.google.com/apis/library/cloudfunctions.googleapis.com)
* [Cloud Identity](https://console.cloud.google.com/apis/library/cloudidentity.googleapis.com)
* [Secret Manager](https://console.cloud.google.com/apis/library/secretmanager.googleapis.com)
* [Cloud Resource Manager](https://console.cloud.google.com/apis/library/cloudresourcemanager.googleapis.com) (`Check` extension only)
* [Cloud Asset](https://console.cloud.google.com/apis/library/cloudasset.googleapis.com) (`Check` extension only)

The BigQuery IAM Extensions make use of the `Application Default Credentials` for Google. You need to create OAuth credentials for a Desktop application in the Google Cloud Console:

* Go to the [APIs & Services console](https://console.cloud.google.com/apis/credentials), make sure you select the correct project
* Click on `Create Credentials` and select `OAuth client ID`
* Select `Desktop application` as the application type
* Click on `Create` and download the credentials file

The extension is managed by [Terraform](https://www.terraform.io/). In order to create the Terraform resources, log in locally as the super-admin account with the `--client-id-file` flag set to the OAuth credentials file and the `--scopes` flag with the following scopes: `https://www.googleapis.com/auth/admin.directory.rolemanagement`, `https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly` `https://www.googleapis.com/auth/cloud-platform`

for example:

```bash
gcloud auth application-default login \
 --client-id-file=<path/to/credentials/file.json> \
 --scopes=https://www.googleapis.com/auth/admin.directory.rolemanagement,https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly,https://www.googleapis.com/auth/cloud-platform
```

After login set the quota project you want to use:

```bash
gcloud auth application-default set-quota-project <YOUR_PROJECT>
```

### Google IAM Sync Extension

The `Sync` extension requires the following environment variables to be set. We recommend to use an `.envrc` like below:

```bash
export TF_VAR_region="<REGION>"
export TF_VAR_project="<PROJECT>"
export TF_VAR_organization_id="<ORGANIZATION_ID>"
export TF_VAR_customer_id="<CUSTOMER_ID>"
export TF_VAR_scheduler_region="<SCHEDULER_REGION>"
export TF_VAR_cron_schedule="<CRON_SCHEDULE>"
```

* `REGION` is the region where your data lives
* `PROJECT` is the project where your data lives
* `ORGANIZATION_ID` for example: `12345689012`
* `CUSTOMER_ID` is the customer-id of the organization in the [Google admin console](https://admin.google.com/ac/accountsettings).
* `SCHEDULER_REGION` is the region where the cloud function will be deployed. This could potentially be the same as the `region` variable, but cloud scheduler is not available in all regions. Check if your region is available [here](https://cloud.google.com/about/locations).
* `CRON_SCHEDULE` is the schedule for the cloud scheduler in cron format. For example, `0 0 * * *` would invoke every day at midnight.

#### PACE application properties

Unless you have defined differently, the pace application follows the [user mappings table config](bigquery.md#pace-application-properties) with `user-groups-table: "my-google-cloud-project.user_groups.user_groups_view"`. The view that is created is an authorized view that will only return the groups of the user in session.

### Google IAM Check Extension

The `Check` extension requires only four environment variables. We recommend using an `.envrc`:

```bash
export TF_VAR_region="<REGION>"
export TF_VAR_project="<PROJECT>"
export TF_VAR_organization_id="<ORGANIZATION_ID>"
export TF_VAR_customer_id="<CUSTOMER_ID>"
```

* `REGION` is the region where your data lives
* `PROJECT` is the project where your data lives
* `ORGANIZATION_ID` for example: `12345689012`
* `CUSTOMER_ID` is the customer-id of the organization in the [Google admin console](https://admin.google.com/ac/accountsettings).

#### PACE application properties

Compared to the user mapping table, the only difference is that you must set the `use-iam-check-extension` parameter to `true`. The `user-groups-table` can then be left empty.

```yaml
app:
  processing-platforms:
    bigquery:
      - id: "bigquery-dev"
        project-id: "my-google-cloud-project"
        use-iam-check-extension: true
        service-account-json-key: |
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

#### Data policy

In order to leverage the three different IAM types, you need to indicate the type of principal.

<table><thead><tr><th width="151">Principal</th><th>Example (in YAML)</th></tr></thead><tbody><tr><td>Groups</td><td><pre><code>- principals:
    - group: tg1@strmprivacy.io
</code></pre></td></tr><tr><td>Roles</td><td><pre><code>- principals:
    - role: roles/apigee.analyticsAgent
</code></pre></td></tr><tr><td>Permissions</td><td><pre><code>- principals:
    - permission: bigquery.datasets.get
</code></pre></td></tr></tbody></table>

## Request Access

If you are interested in using the IAM groups, roles and permissions as Principals for your data policies,  [let us know!](mailto:pace@getstrm.com?subject=BigQuery%20IAM%20Extension)
