package com.my.kizzy.feature_settings.language

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.SYSTEM_DEFAULT
import com.my.kizzy.preference.getLanguageConfig
import com.my.kizzy.preference.getLanguageDesc
import com.my.kizzy.preference.getLanguageNumber
import com.my.kizzy.preference.languages
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.preference.PreferenceSingleChoiceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Language(
    onBackPressed: () -> Unit,
    updateLocaleLanguage: (String) -> Unit
) {
    var locale by remember { mutableIntStateOf(getLanguageNumber()) }

    fun changeLanguage(index: Int) {
        locale = index
        Prefs[Prefs.LANGUAGE] = locale
        updateLocaleLanguage(getLanguageConfig())
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.language),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    BackButton(onClick = onBackPressed)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item(key = "follow_system") {
                PreferenceSingleChoiceItem(
                    text = stringResource(R.string.follow_system),
                    selected = locale == SYSTEM_DEFAULT
                ) { changeLanguage(SYSTEM_DEFAULT) }
            }
            for (language in languages) {
                item(key = "lang_${language.key}") {
                    PreferenceSingleChoiceItem(
                        text = getLanguageDesc(language.key),
                        selected = locale == language.key
                    ) {
                        changeLanguage(language.key)
                    }
                }
            }
        }
    }
}