package com.getstrm.pace.config

import com.getstrm.pace.grpc.ExceptionHandlerInterceptor
import com.getstrm.pace.grpc.ValidationInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ProcessingPlatformConfiguration::class,
    AppConfiguration::class,
    GlobalTransformsConfiguration::class,
    )
class PaceConfiguration {
    @GrpcGlobalServerInterceptor
    fun exceptionInterceptor() = ExceptionHandlerInterceptor(false)

    @GrpcGlobalServerInterceptor
    fun validationInterceptor() = ValidationInterceptor()
}
