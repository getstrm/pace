package com.getstrm.pace.domain

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import io.grpc.Status
import io.strmprivacy.grpc.common.server.NotFoundException
import io.strmprivacy.grpc.common.server.StrmStatusException
import org.jooq.impl.ParserException
import toJson

class ProcessingPlatformNotFoundException(id: String) : NotFoundException("Processing Platform", id)
class CatalogNotFoundException(id: String) : NotFoundException("Data Catalog", id)
class CatalogDatabaseNotFoundException(id: String) : NotFoundException("Data Catalog Database", id)
class CatalogSchemaNotFoundException(id: String) : NotFoundException("Data Catalog Schema", id)
class CatalogTableNotFoundException(id: String) : NotFoundException("Data Catalog Table", id)

class ProcessingPlatformConfigurationError(description: String) : StrmStatusException(Status.INVALID_ARGUMENT, description)
class ProcessingPlatformTableNotFound(id: String, type: DataPolicy.ProcessingPlatform.PlatformType, tableName: String) :
    StrmStatusException(Status.NOT_FOUND, "ProcessingPlatform $type:$id has no table named $tableName")
class ProcessingPlatformExecuteException(id: String, message: String) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Platform $id caused issue $message")
class InvalidDataPolicyAbsentSourceRef() :
    StrmStatusException(Status.INVALID_ARGUMENT, "DataPolicy has no source ref")
class InvalidDataPolicyAbsentPlatformId() :
    StrmStatusException(Status.INVALID_ARGUMENT, "DataPolicy has no platform.id")
class InvalidDataPolicyUnknownGroup(principals: Set<String>) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Unknown groups: ${principals.joinToString()}")
class InvalidDataPolicyNonEmptyLastFieldTransform(transform: DataPolicy.RuleSet.FieldTransform.Transform) :
    StrmStatusException(Status.INVALID_ARGUMENT, "FieldTransform ${transform.toJson()} does not have an empty principals list as last field")
class InvalidDataPolicyMissingAttribute(att: DataPolicy.Attribute) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Attribute ${att.toJson()} does not exist in attribute list")
class InvalidDataPolicyEmptyFieldTransforms(transform: DataPolicy.RuleSet.FieldTransform) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Attribute ${transform.toJson()} has no transforms")
class InvalidDataPolicyOverlappingPrincipals(transform: DataPolicy.RuleSet.FieldTransform) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Attribute ${transform.toJson()} has overlapping principals")
class InvalidDataPolicyOverlappingAttributes(ruleSet: DataPolicy.RuleSet) :
    StrmStatusException(Status.INVALID_ARGUMENT, "RuleSet ${ruleSet.toJson()} has overlapping attributes")

class SqlParseException(statement: String, cause: ParserException) : StrmStatusException(
    Status.INVALID_ARGUMENT,
    "SQL Statement [$statement] is invalid, please verify it's syntax. Details: ${cause.sql()}"
)
