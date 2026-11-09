/*

 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LoggerEntryPoint.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************

*/

package com.my.kizzy.feature_logs.di

import com.my.kizzy.domain.interfaces.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LoggerEntryPoint {
    fun logger(): Logger
}
