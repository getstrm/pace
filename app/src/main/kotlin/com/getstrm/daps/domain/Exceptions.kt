package com.getstrm.daps.domain

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import io.grpc.Status
import io.strmprivacy.grpc.common.server.NotFoundException
import io.strmprivacy.grpc.common.server.StrmStatusException
import toJson

class ProcessingPlatformNotFoundException(id: String) : NotFoundException("Processing Platform", id)
class ProcessingPlatformConfigurationError(description: String) : StrmStatusException(Status.INVALID_ARGUMENT, description)
class ProcessingPlatformTableNotFound(id: String, type: DataPolicy.ProcessingPlatform.PlatformType, tableName: String) :
    StrmStatusException(Status.NOT_FOUND, "ProcessingPlatform $type:$id has no table named $tableName")
class ProcessingPlatformExecuteException(id: String, message: String) :
    StrmStatusException(Status.INVALID_ARGUMENT, "Platform $id caused issue $message")
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
