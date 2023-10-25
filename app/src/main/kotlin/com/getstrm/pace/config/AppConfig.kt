package com.getstrm.pace.config

import com.getstrm.pace.exceptions.ExceptionHandlerInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties(ProcessingPlatformConfiguration::class, CatalogsConfiguration::class)
class AppConfig {
    @GrpcGlobalServerInterceptor
    fun exceptionInterceptor(): ExceptionHandlerInterceptor {
        return ExceptionHandlerInterceptor(false)
    }
}
