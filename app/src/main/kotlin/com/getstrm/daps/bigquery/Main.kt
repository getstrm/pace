package io.strmprivacy.management.data_policy_service.bigquery

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.daps.bigquery.BigQueryClient
import toYaml

suspend fun main() {
    val bigQueryClient = BigQueryClient(
        id = "bigquery",
        serviceAccountKeyJson = "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"stream-machine-development\",\n" +
                "  \"private_key_id\": \"063e6b77eddafd1c3c9b1348b5173cecf3fa174f\",\n" +
                "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDeUGjOmr5h7grg\n+YGBf4iSyKBLKhKikepKiAGAfz90e+H3XAqes1VxwGAlYsLktC46GCo7FyZUX7T0\npc0dUjBwh4vATUdjXFO3SG6FDikpIGEQ25pOMcLBA3huBMMU8nyPtHfKOFJpWXZZ\npLOAz6yc0/f+zcz7JsP3KFzOxPh2zZtPM1IFRP0BaAJCzv0kywSnN5TOyJK+H63e\n29Jgyf0emw4JO/ggdkysYh6EuyH44V9tkelVOKSLIFJ32FOQDTJhTxmmFbJIg6qw\naJygNnUt8a5IzRyqd9ZbPr+O6reZhFeexhjABvuu7B0X7Z7qyBiamLhMaW/dlmJX\nJ/F26W8xAgMBAAECggEACTOJyvdTpuj70UjyZ8I4DF86ZzIEGG9yo4gSi9d4cGFK\nPns0Q1JH2I/uSs3WJDIi8aubX9u67eYSLsgH/80tjRLHIXvxVvb0zhK05FPsNzQM\nYG6+abschPGYU/Flg6HvruD4zklbe9nEkLxE47F0wv7w9j5dXA3EMaAdiz9Sy1Pu\nbXhisnSsHsTCPxB+JNKMRSXZ55mdRRWt4Msqgt1EhTAhHkaCLrSNoZGp8PoZOQEZ\nMjSzCIetXCJHDlp0Cs11Dd1clC22iTagY/MalihdkyfdM4FSBmFv+EbHg9heC/Y5\nXmiC3P/HYVEVqo9NyLjTDmBFT3hylaNlA8sYpN/WGwKBgQD+/uow1fw+dSkIcEoz\nKzO55XFsZiS6up4DHfHLOJdGh017ILgV0W8+sZX4m54nMaolMiXhF6goAzam2XX6\nINxNOSUBY2wZjbtDGl8jVP7NHJ6LtmfF3OjzpaX+XtJJ8+MiwGOPjK/JmX63HpL8\nG0Q9+RHfmpKhtRTvPhdJXTtvWwKBgQDfMIuOTO5ezprmBQkWTAMQFSU9FQT4jicF\nQ7e+MzbISoMy40PmWMX/GoccFP7ChO6hyHrHnf+sijZJjDcua8sfQ2aAfzm9EnHN\nmfRIVY5qP4dK1hwlbKK4E9tPi48GOZazChfbf0ETTrfRIGHSzKBUdDHLXVi2FXF8\nPB2vy31NYwKBgQDfLB+vajkAOO4WsqBeNDtrQYKJisQuoVHWDIkogXj0g0qurq4u\nekRQrIBDO3+pcfAl6cP5QwkrK9TTJpP4vHXTKGZY1rkvjDoOuq/1blgrEBc61APy\nyisfwySKglat3sQ2EAeTBWB8otiiUCH4f6y0SJ76AoC/Aos18DPVQ8HW7QKBgEmx\nVrLyldY58401Zm21RWGfCb+kXBLRpPKpDFdBw7nYWH+J6JZ7A00a7jeIeyGxELXc\nYyXb9lp6/DfGk0XBk7zL3WFaIK9cErVnOvBTR4WvWjWMgpicyRFshQI7u0q74xVU\npNH1r3/3gtwLDCG1LM8V2ociWDMu21zA4LB1yPavAoGARV1pg7Y8kQwYFflTWj2v\nUZ+7HarAy8KHLss1yUpbZJF2y0ReWwBun9yy6wJ6HwzbbCywHktuvD6pTD9wh3tE\nU7cG9rsprI5fzvaRVpfOYV1UPkG6HeO7+FYTfALcG6cDpPowrirPmCy4YDuPQs0i\nY4Od038EfZPyu8jBCtBAuSk=\n-----END PRIVATE KEY-----\n\",\n" +
                "  \"client_email\": \"dps-bigquery-poc@stream-machine-development.iam.gserviceaccount.com\",\n" +
                "  \"client_id\": \"104066739856134154650\",\n" +
                "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/dps-bigquery-poc%40stream-machine-development.iam.gserviceaccount.com\",\n" +
                "  \"universe_domain\": \"googleapis.com\"\n" +
                "}\n",
        projectId = "stream-machine-development",
        userGroupsTable = "stream-machine-development.user_groups.user_groups"
    )
    println(bigQueryClient.listGroups())
    bigQueryClient.listTables().forEach { table ->
        println(table.toDataPolicy(DataPolicy.ProcessingPlatform.getDefaultInstance()).toYaml())
    }
}
