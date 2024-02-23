package com.getstrm.pace.config

data class DataPolicyValidatorConfig(
    val skipCheckPrincipals: Boolean = false,
    var skipTypeCheck: Boolean = false,
)

data class DataPolicyValidatorConfigDsl(
    var skipCheckPrincipals: Boolean = false,
    var skipTypeCheck: Boolean = false,
) {
    fun build() = DataPolicyValidatorConfig(skipCheckPrincipals, skipTypeCheck)
}
