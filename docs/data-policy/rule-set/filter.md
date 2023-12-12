---
description: Condition based row filtering
---

# Filter

## Introduction

When you have dataset that includes, for example, both large and small transactions, you might only want the _Fraud and Risk_ [`Principal`](../principals.md) to be able to see these large transactions. We defined row based filtering. Each `Filter` is one of two types, `Generic Filter` or `Retention Filter`.

### Generic Filter

A `Generic Filter` contains a list of `Conditions` and each condition consists of a list of `Principals` and the actual condition.

Similar to [`Field Transform`](field-transform.md), the list of `Principal` defines to which groups of users the `Filter` must be applied. The condition is a _SQL expression_ that should match the specified [`Processing Platform`](../../reference/integrations/processing-platform-integrations/)'s syntax. In contrast to `Field Transforms`, where each transform is defined for exactly one field, the filter conditions can contain logic regarding multiple fields. If the condition evaluates to true, the set of `Principal` is allowed to view the data, else the rows are omitted in the resulting view.

### Retention Filter

A `Retention Filter` is used to filter data based on a date field. For this filter we require that you have a `timestamp` field in your source dataset. In the `Retention Filter` you have to specify this timestamp `Field`, corresponding to one of the fields in your `Schema`. In the list of `Conditions` you specify a list of `Principals` like in the `Generic Filter`, and a `Period` in days. The resulting check then is whether or not the difference between the current timestamp and the timestamp field is smaller than the defined period. Thus filtering all records that are 'too old'. If the `Period` is left empty, the retention will be infinite for the corresponding `Principals`.

For both types of `Filter` holds that they should at least contain one `Condition` without any `Principal`, defined as last item in the list of conditions. This `Condition` acts as the default or fallback filter.

{% hint style="info" %}
Note that the order of the Condition in the policy matters. That is, if you are a member of multiple `Principal` groups, for each `Condition`, the filter with the first intersection with your `Principal` groups will be applied.
{% endhint %}

## Example Filter

{% tabs %}
{% tab title="YAML" %}
{% code lineNumbers="true" %}
```yaml
filters:
  - generic_filter:
      conditions:
        - principals:
            - group: "F&R"
          condition: "true"
        - principals: []
          condition: "age > 18"
  - generic_filter:
      conditions:
        - principals:
            - group: "MKTNG"
          condition: "true"
        - principals:
            - group: "F&R"
            - group: "ANALYSIS"
          condition: "transactionAmount >= 1000"
        - principals: []
          condition: "transactionAmount < 1000"
  - retention_filter:
      field:
        name_parts:
          - ts
        required: true
        type: timestamp
      conditions:
        - principals:
            - group: "MKTNG"
          period:
            days: 2
        - principals: 
            - group: "F&R"
        - principals: [] 
          period:
            days: 1

```
{% endcode %}
{% endtab %}

{% tab title="JSON" %}
{% code lineNumbers="true" %}
```json
{
  "filters": [
    {
      "generic_filter": {
        "conditions": [
          {
            "principals": [
              {
                "group": "F&R"
              }
            ],
            "condition": "true"
          },
          {
            "principals": [],
            "condition": "age > 18"
          }
        ]
      }
    },
    {
      "generic_filter": {
        "conditions": [
          {
            "principals": [
              {
                "group": "MKTNG"
              }
            ],
            "condition": "true"
          },
          {
            "principals": [
              {
                "group": "F&R"
              },
              {
                "group": "ANALYSIS"
              }
            ],
            "condition": "transactionAmount >= 1000"
          },
          {
            "principals": [],
            "condition": "transactionAmount < 1000"
          }
        ]
      }
    },
    {
      "retention_filter": {
        "field": {
          "name_parts": [
            "ts"
          ],
          "required": true,
          "type": "timestamp"
        },
        "conditions": [
          {
            "principals": [
              {
                "group": "MKTNG"
              }
            ],
            "period": {
              "days": 2
            }
          },
          {
            "principals": [
              {
                "group": "F&R"
              }
            ]
          },
          {
            "principals": [],
            "period": {
              "days": 1
            }
          }
        ]
      }
    }
  ]
}

```
{% endcode %}
{% endtab %}
{% endtabs %}

## Example Results

{% tabs %}
{% tab title="RAW Data" %}
| transactionId | transactionAmount | age | ts                                     |
| ------------- | ----------------- | --- | -------------------------------------- |
| 1             | 100               | 16  | 2023-11-09 18:19:45.759                |
| 2             | 1000              | 20  | 2023-11-10 00:19:26.104                |
| 3             | 5000              | 24  | <p>2023-11-11 </p><p>09:01:27.423 </p> |
| 4             | 50                | 24  | 2023-11-11 10:26:14.912                |
| 5             | 2000              | 17  | 2023-11-12 16:29:10.296                |
| 6             | 75                | 28  | 2023-11-13 08:12:11.921                |
| 7             | 3000              | 25  | 2023-11-12 19:11:35.823                |


{% endtab %}
{% endtabs %}

Let's assume the current timestamp equals `2023-11-13 08:29:14.123`. This yields the follow output per set of `Principals`

{% tabs %}
{% tab title="[ F&R ]" %}
| transactionId | transactionAmount | age | ts                       |
| ------------- | ----------------- | --- | ------------------------ |
| 2             | 1000              | 20  | 2023-11-10 00:19:26.104  |
| 3             | 5000              | 24  | 2023-11-11 09:01:27.423  |
| 5             | 2000              | 17  | 2023-11-12 16:29:10.296  |
| 7             | 3000              | 25  | 2023-11-12 19:11:35.823  |
{% endtab %}

{% tab title="[ MKTING ]" %}
| transactionId | transactionAmount | age | ts                       |
| ------------- | ----------------- | --- | ------------------------ |
| 3             | 5000              | 24  | 2023-11-11 09:01:27.423  |
| 4             | 50                | 24  | 2023-11-11 10:26:14.912  |
| 6             | 75                | 28  | 2023-11-13 08:12:11.921  |
| 7             | 3000              | 25  | 2023-11-12 19:11:35.823  |
{% endtab %}

{% tab title="[ ANALYSIS ]" %}
| transactionId | transactionAmount | age | ts                                     |
| ------------- | ----------------- | --- | -------------------------------------- |
| 3             | 5000              | 24  | <p>2023-11-11 </p><p>09:01:27.423 </p> |
| 7             | 300               | 25  | 2023-11-12 19:11:35.823                |
{% endtab %}

{% tab title="[ COMP ]" %}
| transactionId | transactionAmount | age |                         |
| ------------- | ----------------- | --- | ----------------------- |
| 6             | 75                | 28  | 2023-11-13 08:12:11.921 |
{% endtab %}

{% tab title="[ MKTNG, F&R ]" %}
| transactionId | transactionAmount | age |                                        |
| ------------- | ----------------- | --- | -------------------------------------- |
| 3             | 5000              | 24  | <p>2023-11-11 </p><p>09:01:27.423 </p> |
| 4             | 50                | 24  | 2023-11-11 10:26:14.912                |
| 5             | 2000              | 17  | 2023-11-12 16:29:10.296                |
| 6             | 75                | 28  | 2023-11-13 08:12:11.921                |
| 7             | 3000              | 25  | 2023-11-12 19:11:35.823                |
{% endtab %}
{% endtabs %}
