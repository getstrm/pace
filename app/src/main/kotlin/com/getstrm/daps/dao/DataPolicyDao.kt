import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.jooq.generated.tables.Policies.Companion.POLICIES
import com.getstrm.jooq.generated.tables.records.PoliciesRecord
import com.google.protobuf.util.JsonFormat
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

@Component
class DataPolicyDao(
    private val jooq: DSLContext,
) {
    fun listDataPolicies(organizationId: String): List<DataPolicy> = jooq.select()
        .from(POLICIES)
        .where(POLICIES.ORGANIZATION_ID.eq(organizationId))
        .fetchInto(POLICIES)
        .map { it.toApiDataPolicy() }

    fun upsertDataPolicy(dataPolicy: DataPolicy, organizationId: String, context: DSLContext = jooq): DataPolicy {
        val oldPolicy = with(dataPolicy.info) {
            getDataPolicy(title, organizationId, version, context)?.also {
                deactivateDataPolicy(title, organizationId, version, updateTime.toOffsetDateTime(), context)
            }
        }
        val updateTimestamp = OffsetDateTime.now()
        val updatedPolicy = dataPolicy.toBuilder()
            .setId(oldPolicy?.id ?: UUID.randomUUID().toString())
            .setInfo(
                dataPolicy.info.toBuilder()
                    .setUpdateTime(updateTimestamp.toTimestamp())
                    .setCreateTime(oldPolicy?.info?.createTime ?: updateTimestamp.toTimestamp())
                    .setOrganizationId(organizationId)
                    .build()
            ).build()

        context.newRecord(POLICIES).apply {
            this.policy = updatedPolicy.toJsonb()
            this.id = updatedPolicy.id
            this.updatedAt = updateTimestamp
            this.createdAt = updatedPolicy.info.createTime.toOffsetDateTime()
            this.active = true
            this.title = updatedPolicy.info.title
            this.description = updatedPolicy.info.description
            this.version = updatedPolicy.info.version
            this.organizationId = organizationId
        }.also {
            it.store()
        }

        return updatedPolicy
    }

    private fun deactivateDataPolicy(title: String, organizationId: String, version: String, updateTime: OffsetDateTime, context: DSLContext = jooq) {
        context.update(POLICIES)
            .set(POLICIES.ACTIVE, false)
            .where(POLICIES.TITLE.eq(title))
            .and(POLICIES.ORGANIZATION_ID.eq(organizationId))
            .and(POLICIES.VERSION.eq(version))
            .and(POLICIES.UPDATED_AT.eq(updateTime))
            .execute()
    }

    private fun getDataPolicy(title: String, organizationId: String, version: String, context: DSLContext = jooq): DataPolicy? {
        return context.select()
            .from(POLICIES)
            .where(POLICIES.TITLE.eq(title))
            .and(POLICIES.ORGANIZATION_ID.eq(organizationId))
            .and(POLICIES.VERSION.eq(version))
            .orderBy(POLICIES.UPDATED_AT.desc())
            .limit(1)
            .fetchOneInto(POLICIES)
            .toApiDataPolicy()
    }

    fun getLatestDataPolicy(id: String, context: DSLContext = jooq): DataPolicy? {
        return context.select()
            .from(POLICIES)
            .where(POLICIES.ID.eq(id))
            .orderBy(POLICIES.UPDATED_AT.desc())
            .limit(1)
            .fetchOneInto(POLICIES)
            .toApiDataPolicy()
    }
}

fun PoliciesRecord?.toApiDataPolicy(): DataPolicy? = this?.policy?.let {
    with(DataPolicy.newBuilder()) {
        JsonFormat.parser().ignoringUnknownFields().merge(it.data(), this)
        build()
    }
}
