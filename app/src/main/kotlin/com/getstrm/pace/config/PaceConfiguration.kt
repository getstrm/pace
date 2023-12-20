package com.getstrm.pace.config

import com.getstrm.pace.grpc.ExceptionHandlerInterceptor
import com.getstrm.pace.grpc.ValidationInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
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
}
