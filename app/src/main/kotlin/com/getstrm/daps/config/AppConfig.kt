package com.getstrm.daps.config

import io.strmprivacy.grpc.common.server.ExceptionHandlerInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties(ProcessingPlatformConfiguration::class, CatalogsConfiguration::class)
class AppConfig {

    @GrpcGlobalServerInterceptor
    fun exceptionInterceptor(): ExceptionHandlerInterceptor {
        // Todo: re-implement or use @GrpcAdvice with @GrpcExceptionHandler instead after removing the kotlin-grpc-common dependency
        return ExceptionHandlerInterceptor(false)
    }
}
