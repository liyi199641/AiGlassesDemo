package com.lw.ai.glasses.ui.base.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.lw.top.lib_core.data.model.response.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val TAG = "BaseViewModel"

abstract class BaseViewModel : ViewModel() {
    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    protected val _error = MutableStateFlow<ApiResult.Error?>(null)
    val error: StateFlow<ApiResult.Error?> = _error.asStateFlow()

    protected val _isEmpty = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()


    protected fun <T> launchOperation(
        operationBlock: suspend () -> ApiResult<T?>,
        onSuccess: (data: T?) -> Unit = {},
        onEmpty: () -> Unit = {},
        onError: (errorResult: ApiResult.Error) -> Unit = {},
        checkEmptyCondition: (T?) -> Boolean = { data -> data == null || (data is Collection<*> && data.isEmpty()) || (data is Map<*, *> && data.isEmpty()) },
        showLoading: Boolean = true
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _isLoading.value = true
            }
            // 清除之前的状态
            _error.value = null
            _isEmpty.value = false

            try {
                when (val result = operationBlock()) {
                    is ApiResult.Success -> {
                        if (checkEmptyCondition(result.data)) {
                            _isEmpty.value = true
                            onEmpty()
                        } else {
                            result.data?.let { onSuccess(it) }
                            ?: run {
                                _isEmpty.value = true
                                onEmpty()
                                LogUtils.dTag(
                                    TAG,
                                    "Data was null in Success but not considered empty by checkEmptyCondition."
                                )
                            }
                        }
                    }

                    is ApiResult.Error -> {
                        _error.value = result
                        onError(result)
                    }

                    is ApiResult.Empty -> {
                        _isEmpty.value = true
                        onEmpty()
                    }

                    is ApiResult.Loading -> {
                        if (!showLoading) _isLoading.value = true
                    }
                }
            } catch (e: Exception) {
                LogUtils.eTag(TAG, e)
                val errorResult = ApiResult.Error(
                    e,
                    e.message ?: "An unexpected error occurred in operationBlock."
                )
                _error.value = errorResult
                onError(errorResult)
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }

}