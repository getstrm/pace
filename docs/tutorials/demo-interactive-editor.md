# Demo: interactive editor

{% hint style="info" %}
The interactive editor demo is available [here](https://console.demo.getstrm.com/data-policies/data-policy-editor).
{% endhint %}

To illustrate how PACE can help to change data for each different consumer (we call those principals), we put together an interactive demo. It illustrates how you define a policy, and what it actually does do the data.&#x20;

To load your data, click _select source_ -> _upload data._ You then see a faked data set and a different access principals.

Some suggestions of scenarios to try:

* Remove the email and round ages down for **Fraud and Risk**, hash the user ID (so they can revert it if needed, but don't have a priori access).&#x20;
* Remove any column apart from the the user ID for **Marketing** (why would they need personal data to?)&#x20;
* Hash or replace all personal data for **everyone else**

Have fun!

<figure><img src="../.gitbook/assets/Screenshot 2024-03-21 at 11.21.29.png" alt=""><figcaption></figcaption></figure>



