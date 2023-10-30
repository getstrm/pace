---
description: Condition based row filtering
---

# Filter

## Introduction

When you have dataset that includes, for example, both large and small transactions, you might only want the _Fraud and Risk_ [`Principal`](../principals.md) to be able to see these large transactions. We defined row based filtering. Each `Filter` contains a list of `Condition` and each condition consists of a list of `Principal` and the actual condition.

Similar to [`Field Transform`](field-transform.md), the list of `Principal` defines to which groups of users the `Filter` must be applied. The condition is a _SQL expression_ that should match the specified [`Processing Platform`](../../reference/processing-platform-integrations/)'s syntax. If the condition evaluates to true, the set of `Principal` is allowed to view the data, else the rows are omitted in the resulting view.

Every `Filter` should at least contain one `Condition` without any `Principal`, defined as last item in the list. This `Condition` acts as the default or fallback filter.

{% hint style="info" %}
Note that the order of the Condition in the policy matters. That is, if you are a member of multiple `Principal` groups, for each `Condition`, the filter with the first intersection with your `Principal` groups will be applied.
{% endhint %}

## Example Filter

```yaml
filters:
  - conditions:
    - principals: [ "F&R" ]
      condition: "true"
    - principals: []
      condition: "age > 18"
  - conditions:
    - principals: [ "MKTNG" ]
      condition: "true"
    - principals: [ "F&R", "ANALYSIS" ]
      condition: "transactionAmount >= 1000"
    - principals: []
      condition: "transactionAmount < 1000"
```

## Example Results

{% tabs %}
{% tab title="RAW Data" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 1             | 100               | 16  |
| 2             | 1000              | 20  |
| 3             | 5000              | 24  |
| 4             | 50                | 24  |
| 5             | 2000              | 17  |


{% endtab %}

{% tab title="[ F&R ]" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 2             | 1000              | 20  |
| 3             | 5000              | 24  |
| 5             | 2000              | 17  |
{% endtab %}

{% tab title="[ MKTING ]" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 2             | 1000              | 20  |
| 3             | 5000              | 24  |
| 4             | 50                | 24  |
{% endtab %}

{% tab title="[ ANALYSIS ]" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 2             | 1000              | 20  |
| 3             | 5000              | 24  |
{% endtab %}

{% tab title="[ COMP ]" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 4             | 50                | 24  |
{% endtab %}

{% tab title="[ MKTNG, F&R ]" %}
| transactionId | transactionAmount | age |
| ------------- | ----------------- | --- |
| 1             | 100               | 16  |
| 2             | 1000              | 20  |
| 3             | 5000              | 24  |
| 4             | 50                | 24  |
| 5             | 2000              | 17  |
{% endtab %}
{% endtabs %}
