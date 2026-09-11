package com.lw.ai.glasses.ui.appsettings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
import com.lw.ai.glasses.utils.titleRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var localBaseUrlInput by remember(uiState.localEnvironmentBaseUrl) {
        mutableStateOf(uiState.localEnvironmentBaseUrl)
    }
    var localWsInput by remember(uiState.localEnvironmentWsUrl) {
        mutableStateOf(uiState.localEnvironmentWsUrl)
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding(),
        ) {
            item {
                SettingsSectionTitle(stringResource(R.string.auto_connect_ai_settings))
                Text(
                    text = stringResource(R.string.auto_connect_ai_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_connect_ai_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Switch(
                        checked = uiState.autoConnectAi,
                        onCheckedChange = viewModel::updateAutoConnectAi,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SettingsSectionTitle(stringResource(R.string.environment_switch))
                Text(
                    text = stringResource(R.string.environment_switch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(GlassesConstant.ServerEnvironment.entries) { env ->
                val isCustomEnv = env == GlassesConstant.ServerEnvironment.CUSTOM
                val isSelected = uiState.selectedEnvironment == env
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCustomEnv) {
                                    viewModel.saveLocalEnvironment(
                                        localBaseUrl = localBaseUrlInput,
                                        localWsUrl = localWsInput,
                                    )
                                } else {
                                    viewModel.updateEnvironment(env)
                                }
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                if (isCustomEnv) {
                                    viewModel.saveLocalEnvironment(
                                        localBaseUrl = localBaseUrlInput,
                                        localWsUrl = localWsInput,
                                    )
                                } else {
                                    viewModel.updateEnvironment(env)
                                }
                            },
                        )
                        Text(
                            text = stringResource(env.titleRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (isCustomEnv && isSelected) {
                        val scrollOnFocus = Modifier
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                        OutlinedTextField(
                            value = localBaseUrlInput,
                            onValueChange = { localBaseUrlInput = it },
                            label = { Text(stringResource(R.string.local_base_address)) },
                            placeholder = { Text(stringResource(R.string.local_base_url_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, bottom = 8.dp)
                                .then(scrollOnFocus),
                        )
                        OutlinedTextField(
                            value = localWsInput,
                            onValueChange = { localWsInput = it },
                            label = { Text(stringResource(R.string.local_ws_address)) },
                            placeholder = { Text(stringResource(R.string.local_ws_url_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, bottom = 8.dp)
                                .then(scrollOnFocus),
                        )
                        TextButton(
                            onClick = {
                                viewModel.saveLocalEnvironment(
                                    localBaseUrl = localBaseUrlInput,
                                    localWsUrl = localWsInput,
                                )
                            },
                            modifier = Modifier.padding(start = 40.dp),
                        ) {
                            Text(stringResource(R.string.save_local_environment))
                        }
                    }
                }
            }

            if (uiState.selectedEnvironment == GlassesConstant.ServerEnvironment.CUSTOM) {
                item {
                    Spacer(modifier = Modifier.height(280.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
