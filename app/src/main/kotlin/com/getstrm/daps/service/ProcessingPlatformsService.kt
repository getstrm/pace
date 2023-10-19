import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesRequest
import com.getstrm.daps.config.AppConfig
import com.getstrm.daps.domain.ProcessingPlatformInterface
import org.springframework.stereotype.Component

@Component
class ProcessingPlatformsService(
    private val appConfig: AppConfig,
    private val tokensDao: TokensDao,
) {
    val platforms: Map<String, ProcessingPlatformInterface> = appConfig.processingPlatformConfig.mapNotNull { config ->
        when (config.type) {
            DataPolicy.ProcessingPlatform.PlatformType.DATABRICKS -> DatabricksClient(config)
            DataPolicy.ProcessingPlatform.PlatformType.SNOWFLAKE -> SnowflakeClient(config, tokensDao)
            DataPolicy.ProcessingPlatform.PlatformType.BIGQUERY -> BigQueryClient(config)
            else -> null
        }
    }.associateBy { it.id }

    suspend fun listGroups(platformId: String): List<Group> =
        platforms[platformId]?.listGroups() ?: throw ProcessingPlatformNotFoundException(platformId)

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatformInterface =
        (platforms[dataPolicy.platform.id] ?: throw ProcessingPlatformNotFoundException(dataPolicy.platform.id)).also {
            if (it.type != dataPolicy.platform.platformType) {
                throw ProcessingPlatformConfigurationError(
                    """Platform type in DataPolicy ${dataPolicy.platform.platformType} does not 
                        |correspond with configured platform ${dataPolicy.platform.id} of type ${it.type}
                        |
                    """.trimMargin(),
                )
            }
        }

    suspend fun listProcessingPlatformTables(request: ListProcessingPlatformTablesRequest): List<Table> =
        (platforms[request.platform.id] ?: throw ProcessingPlatformNotFoundException(request.platform.id)).listTables()

    suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): List<Group> =
        (platforms[request.platform.id] ?: throw ProcessingPlatformNotFoundException(request.platform.id)).listGroups()

    suspend fun createBarePolicy(platform: DataPolicy.ProcessingPlatform?, tableName: String): DataPolicy {
        val processingPlatformInterface = platforms[platform!!.id] ?: throw ProcessingPlatformNotFoundException(platform.id)
        val table = processingPlatformInterface.createTable(tableName)
        return table.toDataPolicy(DataPolicy.ProcessingPlatform.newBuilder().setId(platform.id).setPlatformType(processingPlatformInterface.type).build())
    }
}
