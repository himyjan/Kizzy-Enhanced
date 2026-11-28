/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CreditScreenState.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_about.about

import androidx.compose.runtime.Immutable
import com.my.kizzy.domain.model.Contributor

@Immutable
sealed interface CreditScreenState {
    data object Loading : CreditScreenState
    data class Error(val error: String?) : CreditScreenState
    data class LoadingCompleted(val contributors: List<Contributor>) : CreditScreenState
}