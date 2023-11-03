package com.getstrm.pace.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.POSTGRES
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.domain.Group
import com.getstrm.pace.domain.ProcessingPlatform
import com.getstrm.pace.domain.Table
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class PostgresClient(
    override val id: String,
    private val config: PostgresConfig,
) : ProcessingPlatform {
    constructor(config: PostgresConfig) : this(
        config.id,
        config,
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    private val jooq = DSL.using(
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.getJdbcUrl()
                username = config.userName
                password = config.password
            }),
        SQLDialect.POSTGRES
    )

    override val type = POSTGRES

    override suspend fun listTables(): List<Table> = jooq.meta().tables
        .filter { !schemasToIgnore.contains(it.schema?.name) }
        .map { PostgresTable(config.database, it) }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        // SELECT rolname, rolcanlogin isuser FROM pg_roles WHERE
        //   pg_has_role( (select session_user), oid, 'member');

        TODO()
    }

    override suspend fun listGroups(): List<Group> {
        val result = withContext(Dispatchers.IO) {
            jooq.select(DSL.field("oid", Int::class.java), DSL.field("rolname", String::class.java))
                .from(DSL.table("pg_roles"))
                .where(
                    DSL.field("rolcanlogin").notEqual(true),
                    DSL.field("rolname").notLike("pg_%")
                )
                .fetch()
        }

        return result.map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }
    }

    companion object {
        // These are built-in Postgres schemas that we don't want to list tables from.
        private val schemasToIgnore = listOf("information_schema", "pg_catalog")
    }
}

class PostgresTable(
    database: String,
    val table: org.jooq.Table<*>
) : Table() {
    override val fullName: String = "$database.${table.schema?.name}.${table.name}"

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
        TODO("Not yet implemented")
    }
}

/*
Connect to a database SERVER, not a specific database
https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java


create user paceso with encrypted password 'paceso';
grant all privileges on database paceso to paceso;

create user mark with encrypted password 'mark';
create user fin with encrypted password 'fin';
create role marketing;
create role finance;
create role analytics;
grant marketing to mark;
grant analytics to mark;
grant finance to fin;

!-- list groups
select rolname from pg_roles where not rolcanlogin and rolname not like 'pg_%';
  rolname
-----------
 marketing
 finance
 analytics

!-- groups of current session user
SELECT rolname, rolcanlogin isuser FROM pg_roles WHERE
   pg_has_role( (select session_user), oid, 'member');

  rolname  | isuser
-----------+--------
 mark      | t
 marketing | f
 analytics | f
 */
