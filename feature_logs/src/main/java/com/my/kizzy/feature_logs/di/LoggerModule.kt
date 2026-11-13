/*

 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LoggerModule.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************

*/

package com.my.kizzy.feature_logs.di

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.feature_logs.KLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {

    @Binds
    @Singleton
    abstract fun bindLogger(kLogger: KLogger): Logger
}
