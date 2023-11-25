package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.util.toJooqField
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * A fully embedded H2 client for (basic) evaluation of data policies on sample data.
 */
class H2Client {

    private val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:;DATABASE_TO_UPPER=false"
            username = "sa"
            password = ""
            maximumPoolSize = 1
        }
    )
    val jooq: DSLContext = DSL.using(dataSource.connection, SQLDialect.H2)

    fun insertCSV(dataPolicy: DataPolicy, csv: String, tableName: String) {
        val jooqFields = dataPolicy.source.fieldsList.map { it.toJooqField() }
        jooq.createTable(tableName).columns(jooqFields).execute()
        // Get an actual instance of this table
        val table = jooq.meta().getTables(tableName).first()
        jooq.loadInto(table)
            .loadCSV(csv.byteInputStream(), "utf-8")
            .fieldsCorresponding()
            .execute()
    }

    fun close() {
        dataSource.close()
    }
}
