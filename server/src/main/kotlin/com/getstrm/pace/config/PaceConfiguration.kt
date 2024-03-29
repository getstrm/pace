package com.getstrm.pace.config

import com.getstrm.pace.grpc.ExceptionHandlerInterceptor
import com.getstrm.pace.grpc.ValidationInterceptor
import com.getstrm.pace.service.DataPolicyValidator
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationPropertiesScan("com.getstrm.pace.config")
class PaceConfiguration(
    private val appConfiguration: AppConfiguration,
) {
    @GrpcGlobalServerInterceptor
    fun exceptionInterceptor() =
        ExceptionHandlerInterceptor(appConfiguration.exposeApplicationExceptions)

    @GrpcGlobalServerInterceptor fun validationInterceptor() = ValidationInterceptor()

    @Bean
    fun processingPlatformsConfiguration(): ProcessingPlatformsConfiguration {
        return appConfiguration.processingPlatforms
    }

    @Bean
    fun dataPolicyValidator(): DataPolicyValidator {
        return DataPolicyValidator()
    }
}
