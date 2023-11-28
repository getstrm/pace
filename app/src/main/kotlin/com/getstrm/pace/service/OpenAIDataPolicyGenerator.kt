package com.getstrm.pace.service

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.getstrm.pace.config.PluginConfiguration
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.plugins.openai.api-key")
class OpenAIDataPolicyGenerator(
    private val pluginConfiguration: PluginConfiguration
) {
    private val openai = OpenAI(
        token = pluginConfiguration.openai!!.apiKey,
//        timeout = Timeout(request = 10.seconds)
    )

    suspend fun generateYaml(instructions: String) {
        val systemMessage = ChatMessage(
            role = ChatRole.System,
            content = "Your task is to create YAML file based on a JSON Schema and instructions by the end user. Ensure that you only respond with the YAML as plain text, do not provide any other details.",
        )

        val jsonSchemaMessage = ChatMessage(
            role = ChatRole.User,
            content = jsonSchema,
        )

        val blueprintDataPolicyMessage = ChatMessage(
            role = ChatRole.User,
            content = """
                Use the following blueprint data policy as a starting point, and add a single rule set to it based on the instructions provided.
                $blueprintDataPolicy
            """.trimIndent()
        )

        val instructionsMessage = ChatMessage(
            role = ChatRole.User,
            content = """$instructions
                |
                |Make sure to return the YAML as plain text, without any formatting code blocks.
            """.trimMargin()
        )

        val request = ChatCompletionRequest(
            model = ModelId("gpt-4-1106-preview"),
            messages = listOf(
                systemMessage,
                jsonSchemaMessage,
                blueprintDataPolicyMessage,
                instructionsMessage
            ),
            seed = 42, // https://platform.openai.com/docs/guides/text-generation/reproducible-outputs
            maxTokens = 4095,
            temperature = 0.0,
            topP = 1.0,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0
        )
        log.info("Request: {}", request)
        val response = openai.chatCompletion(request)
        log.info("Response: {}", response)
        println(response.choices.first().message.messageContent)
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(OpenAIDataPolicyGenerator::class.java) }

        @Language("yaml")
        private val blueprintDataPolicy = """
metadata:
  version: 3
  description: "Users of our application"
  title: BANANACORP.USERS
platform:
  id: snowflake-connection
  platform_type: SNOWFLAKE
source:
  ref: BANANACORP.USERS
  fields:
    - name_parts:
        - email
      required: true
      type: varchar
    - name_parts:
        - username
      required: true
      type: varchar
    - name_parts:
        - organization
      required: true
      type: varchar
"""


        private val jsonSchema =
            """{${'$'}schema": "http://json-schema.org/draft-04/schema#", ${'$'}ref": "#/definitions/DataPolicy", "definitions": {"DataPolicy": {"properties": {"id": {"type": "string"}, "metadata": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Metadata", "additionalProperties": false}, "source": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Source", "additionalProperties": false}, "platform": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform", "additionalProperties": false}, "rule_sets": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet"}, "additionalProperties": false, "type": "array"}}, "additionalProperties": false, "type": "object", "title": "Data Policy"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.Field": {"properties": {"name_parts": {"items": {"type": "string"}, "type": "array", "description": "Instead of using \"nodes\", nesting can be expressed by specifying multiple name parts. A flat table (e.g. csv file) will only contain a single name part for all fields."}, "type": {"type": "string", "description": "The data type of the field."}, "required": {"type": "boolean", "description": "Whether the field is required. If not, the field may be null."}, "tags": {"items": {"type": "string"}, "type": "array"}}, "additionalProperties": false, "type": "object", "title": "Field"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.Metadata": {"properties": {"title": {"type": "string"}, "description": {"type": "string"}, "version": {"type": "integer", "description": "For new policies, the version does not need to be set. When updating a policy, the version must match the current version. The version is then automatically incremented."}, "create_time": {"type": "string", "format": "date-time"}, "update_time": {"type": "string", "format": "date-time"}, "tags": {"items": {"type": "string"}, "type": "array"}, "last_apply_time": {"type": "string", "description": "The last time the policy was applied to the target(s).", "format": "date-time"}}, "additionalProperties": false, "type": "object", "title": "Metadata"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.Principal": {"properties": {"group": {"type": "string"}}, "additionalProperties": false, "type": "object", "title": "Principal"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform": {"properties": {"platform_type": {"enum": ["PLATFORM_TYPE_UNSPECIFIED", 0, "DATABRICKS", 1, "SNOWFLAKE", 2, "BIGQUERY", 3, "POSTGRES", 4, "SYNAPSE", 5], "oneOf": [{"type": "string"}, {"type": "integer"}], "title": "Platform Type"}, "id": {"type": "string", "description": "An arbitrary but unique identifier for the platform. This matches the id from the PACE app configuration."}}, "additionalProperties": false, "type": "object", "title": "Processing Platform"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet": {"properties": {"target": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Target", "additionalProperties": false}, "field_transforms": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform"}, "additionalProperties": false, "type": "array", "description": "Zero or more field transforms. Any field for which no field transform is specified will be included as-is."}, "filters": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter"}, "additionalProperties": false, "type": "array", "description": "Zero or more filters. Records that match the conditions are included in the result. When no filters are defined, all records are always included."}}, "additionalProperties": false, "type": "object", "title": "Rule Set"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform": {"properties": {"field": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Field", "additionalProperties": false}, "transforms": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform"}, "additionalProperties": false, "type": "array", "description": "The last condition in the list must have 0 principals, as this acts as the default / else condition. Transforms should have mutually exclusive sets of principals."}}, "additionalProperties": false, "type": "object", "title": "Field Transform"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform": {"properties": {"principals": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Principal"}, "additionalProperties": false, "type": "array", "description": "The principals (e.g. groups) for which this transform will be applied."}, "regexp": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Regexp", "additionalProperties": false, "description": "Extract and optionally replace a value in a field using a regular expression."}, "identity": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Identity", "additionalProperties": false, "description": "Use the identity transform to copy a field value as-is."}, "fixed": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Fixed", "additionalProperties": false, "description": "Provide a fixed value for the field."}, "hash": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Hash", "additionalProperties": false, "description": "Hash the field value, optionally with a seed. The exact algorithm is platform-specific."}, "sql_statement": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement", "additionalProperties": false, "description": "Execute a SQL statement to transform the field value. The exact syntax is platform-specific."}, "nullify": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Nullify", "additionalProperties": false, "description": "Make the field value null."}, "detokenize": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Detokenize", "additionalProperties": false, "description": "Replaces a tokenized field value with its original value, looked up in a token source. If no value is found, the tokenized value is left as-is."}, "numeric_rounding": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding", "additionalProperties": false, "description": "Round a numeric value with the specified rounding."}}, "additionalProperties": false, "type": "object", "title": "Transform"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Detokenize": {"properties": {"token_source_ref": {"type": "string", "description": "Full reference to the token source, e.g. a fully qualified table name."}, "token_field": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Field", "additionalProperties": false, "description": "The field in the token source that contains the token. Only the name parts are required."}, "value_field": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Field", "additionalProperties": false, "description": "The field in the token source that contains the value. Only the name parts are required."}}, "additionalProperties": false, "type": "object", "title": "Detokenize"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Fixed": {"properties": {"value": {"type": "string"}}, "additionalProperties": false, "type": "object", "title": "Fixed"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Hash": {"properties": {"seed": {"type": "string"}}, "additionalProperties": false, "type": "object", "title": "Hash"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Identity": {"additionalProperties": false, "type": "object", "title": "Identity"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Nullify": {"additionalProperties": false, "type": "object", "title": "Nullify"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding": {"properties": {"ceil": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Ceil", "additionalProperties": false, "description": "Round the value to the nearest integer (e.g. 1.5 becomes 2), respecting the divisor."}, "floor": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Floor", "additionalProperties": false, "description": "Round the value down to the nearest integer (e.g. 1.5 becomes 1), respecting the divisor."}, "round": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round", "additionalProperties": false, "description": "Use regular natural rounding (e.g. 1.5 becomes 2, 1.4 becomes 1), respecting the precision."}}, "additionalProperties": false, "type": "object", "title": "Numeric Rounding"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Ceil": {"properties": {"divisor": {"type": "number", "description": "The divisor to use when applying integer division. Values \u003c 1 allow for rounding to decimal places."}}, "additionalProperties": false, "type": "object", "title": "Ceil"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Floor": {"properties": {"divisor": {"type": "number", "description": "The divisor to use when applying integer division. Values \u003c 1 allow for rounding to decimal places."}}, "additionalProperties": false, "type": "object", "title": "Floor"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round": {"properties": {"precision": {"type": "integer", "description": "The precision to use for rounding. When positive, the value is rounded to the nearest decimal place. When negative, the value is rounded to the nearest power of 10."}}, "additionalProperties": false, "type": "object", "title": "Round"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Regexp": {"properties": {"regexp": {"type": "string"}, "replacement": {"type": "string", "description": "Use dollar signs to reference capturing groups in the replacement, e.g. \"my-replacement-${'$'}1-${'$'}2\". If the replacement is left empty, the regexp match result (full match or first capturing group) is used."}}, "additionalProperties": false, "type": "object", "title": "Regexp"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement": {"properties": {"statement": {"type": "string"}}, "additionalProperties": false, "type": "object", "title": "Sql Statement"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter": {"properties": {"retention_filter": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter", "additionalProperties": false}, "generic_filter": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter", "additionalProperties": false}}, "additionalProperties": false, "type": "object", "title": "Filter"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter": {"properties": {"conditions": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter.Condition"}, "additionalProperties": false, "type": "array", "description": "The last condition in the list must have 0 principals, as this acts as the default / else condition."}}, "additionalProperties": false, "type": "object", "title": "Generic Filter"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter.Condition": {"properties": {"principals": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Principal"}, "additionalProperties": false, "type": "array", "description": "The principals (e.g. groups) that apply to this condition."}, "condition": {"type": "string", "description": "A (platform-specific) SQL expression. If it evaluates to true, the principals are allowed to access the data."}}, "additionalProperties": false, "type": "object", "title": "Condition"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter": {"properties": {"field": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Field", "additionalProperties": false, "description": "The field of type SQL date with timestamp."}, "conditions": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter.Condition"}, "additionalProperties": false, "type": "array", "description": "The last condition in the list must have 0 principals, as this acts as the default / else condition."}}, "additionalProperties": false, "type": "object", "title": "Retention Filter"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter.Condition": {"properties": {"principals": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Principal"}, "additionalProperties": false, "type": "array", "description": "The principals (e.g. groups) that apply to this condition."}, "period": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter.Period", "additionalProperties": false, "description": "The retention period for the data measured in days after creation. If empty or null, defaults to infinite retention period."}}, "additionalProperties": false, "type": "object", "title": "Condition"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter.Period": {"properties": {"days": {"type": "string", "description": "Number of days since the creation date."}}, "additionalProperties": false, "type": "object", "title": "Period"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.Source": {"properties": {"ref": {"type": "string", "description": "The ref is an identifier for the source, unique at the source platform level."}, "fields": {"items": {${'$'}ref": "#/definitions/getstrm.pace.api.entities.v1alpha.DataPolicy.Field"}, "additionalProperties": false, "type": "array", "description": "A representation of the source data schema. Nested fields are supported."}, "tags": {"items": {"type": "string"}, "type": "array"}}, "additionalProperties": false, "type": "object", "title": "Source"}, "getstrm.pace.api.entities.v1alpha.DataPolicy.Target": {"properties": {"type": {"enum": ["TARGET_TYPE_UNSPECIFIED", 0, "SQL_VIEW", 1], "oneOf": [{"type": "string"}, {"type": "integer"}], "title": "Target Type"}, "fullname": {"type": "string", "description": "The full and unique name to be used in the target platform. E.g. the view name."}}, "additionalProperties": false, "type": "object", "title": "Target"}}}
"""
    }
}
