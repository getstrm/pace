package com.getstrm.pace.dbt

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration
@RegisterReflectionForBinding(classes = [DbtPolicy::class, DbtModel::class, Column::class])
class NativeConfig
