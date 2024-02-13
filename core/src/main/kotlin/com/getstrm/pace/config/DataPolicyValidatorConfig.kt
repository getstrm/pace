package com.getstrm.pace.config

data class DataPolicyValidatorConfig(
    val skipCheckPrincipals: Boolean = false,
)

data class DataPolicyValidatorConfigDsl(
    var skipCheckPrincipals: Boolean = false,
) {
    fun build() = DataPolicyValidatorConfig(skipCheckPrincipals)
}
