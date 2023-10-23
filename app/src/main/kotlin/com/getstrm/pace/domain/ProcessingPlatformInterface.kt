package com.getstrm.pace.domain

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy

interface ProcessingPlatformInterface {

    // platform id
    val id: String
    val type: DataPolicy.ProcessingPlatform.PlatformType
    suspend fun listGroups(): List<Group>
    suspend fun listTables(): List<Table>
    suspend fun applyPolicy(dataPolicy: DataPolicy)

    suspend fun createTable(tableName: String): Table =
        listTables().find { it.fullName == tableName } ?: throw ProcessingPlatformTableNotFound(id, type, tableName)
}

data class Group(val id: String, val name: String, val description: String? = null)

abstract class Table {
    abstract val fullName: String

    abstract suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy

    override fun toString(): String = "${javaClass.simpleName}(fullName=$fullName)"
}
