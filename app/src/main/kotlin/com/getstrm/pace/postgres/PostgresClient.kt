package com.getstrm.pace.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.POSTGRES
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.domain.Group
import com.getstrm.pace.domain.ProcessingPlatform
import com.getstrm.pace.domain.Table
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class PostgresClient(
    override val id: String,
    config: PostgresConfig,
) : ProcessingPlatform {
    constructor(config: PostgresConfig) : this(
        config.id,
        config,
    )

    init {

        val databaseURL = config.getJdbcUrl()

        val connection = DriverManager.getConnection(databaseURL, config.userName, config.password)
        val create = DSL.using(connection, SQLDialect.POSTGRES)
        create.meta().schemas.forEach{
            it.tables.forEach {
                println("table: ${it.name}")
            }
        }

    }

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    override suspend fun listTables(): List<Table> {
        TODO()
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        TODO()
    }

    override val type = POSTGRES

    override suspend fun listGroups(): List<Group> {
        TODO()
    }
}

class PostgresTable(
    override val fullName: String,
) : Table() {
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
