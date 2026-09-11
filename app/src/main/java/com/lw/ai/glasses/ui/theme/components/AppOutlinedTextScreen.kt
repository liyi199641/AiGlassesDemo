

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * 自定义的 OutlinedTextField (String 版本)，支持自定义颜色和强制布局方向。
 *
 * @param value 输入框中显示的 [String] 文本。
 * @param onValueChange 当输入服务更新文本时触发的回调。
 * @param modifier 可选的 [Modifier]。
 * @param enabled 是否启用。
 * @param readOnly 是否只读。
 * @param textStyle 文本样式。
 * @param label 可选标签。
 * @param placeholder 可选占位符。
 * @param leadingIcon 可选前置图标。
 * @param trailingIcon 可选后置图标。
 * @param prefix 可选前缀。
 * @param suffix 可选后缀。
 * @param supportingText 可选支持文本。
 * @param isError 是否处于错误状态。
 * @param visualTransformation 视觉转换。
 * @param keyboardOptions 键盘选项。
 * @param keyboardActions 键盘操作。
 * @param singleLine 是否单行。
 * @param maxLines 最大行数。
 * @param minLines 最小行数。
 * @param interactionSource 交互源。
 * @param shape 形状。
 * @param customBorderColor 未聚焦且无错误时的边框颜色。默认为 MaterialTheme.colorScheme.outline。
 * @param customFocusedBorderColor 聚焦时的边框颜色。默认为 MaterialTheme.colorScheme.primary。
 * @param customErrorBorderColor 错误状态时的边框颜色。默认为 MaterialTheme.colorScheme.error。
 * @param customContainerColor 文本字段容器的背景颜色。默认为透明。
 * @param forceLayoutDirection 强制布局方向 (Ltr/Rtl)。默认为 null (继承)。
 * @param textAlign 文本对齐方式。若为 Rtl 且此参数为 null，则默认为 TextAlign.End。
 * @param colors [TextFieldColors] 对象，用于更细致的颜色控制，会覆盖部分自定义颜色参数。
 */
@OptIn(FlowPreview::class)
@Composable
fun AppOutlinedTextScreen(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    customBorderColor: Color = MaterialTheme.colorScheme.outline,
    customFocusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    customErrorBorderColor: Color = MaterialTheme.colorScheme.error,
    customContainerColor: Color = Color.Transparent,
    forceLayoutDirection: LayoutDirection? = null,
    textAlign: TextAlign? = null,
    colors: TextFieldColors? = null // 改为可空，以便我们可以构建默认值
) {

    LaunchedEffect(value, onValueChangeFinished) {
        if (onValueChangeFinished != null) {
            snapshotFlow { value }
                .debounce(1000L)
                .collect {
                    if (it.isNotEmpty()) {
                        onValueChangeFinished()
                    }
                }
        }
    }

    val currentLayoutDirection = LocalLayoutDirection.current
    val effectiveLayoutDirection = forceLayoutDirection ?: currentLayoutDirection
    val effectiveTextStyle = textStyle.copy(
        textAlign = textAlign ?: textStyle.textAlign
    )


    // 如果用户没有提供完整的 `colors` 对象，我们才使用自定义颜色参数构建它
    val textFieldColors = colors ?: OutlinedTextFieldDefaults.colors(
        focusedContainerColor = customContainerColor,
        unfocusedContainerColor = customContainerColor,
        disabledContainerColor = customContainerColor.copy(alpha = 0.5f), // 示例：禁用时半透明
        errorContainerColor = customContainerColor, // 错误时也使用自定义背景色
        focusedBorderColor = customFocusedBorderColor,
        unfocusedBorderColor = customBorderColor,
        disabledBorderColor = customBorderColor, // Material 默认的禁用透明度
        errorBorderColor = customErrorBorderColor
    )

    CompositionLocalProvider(LocalLayoutDirection provides effectiveLayoutDirection) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = effectiveTextStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = textFieldColors
        )
    }
}

@Composable
fun AppOutlinedTextScreen(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    customBorderColor: Color = MaterialTheme.colorScheme.outline,
    customFocusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    customErrorBorderColor: Color = MaterialTheme.colorScheme.error,
    customContainerColor: Color = Color.Transparent,
    forceLayoutDirection: LayoutDirection? = null,
    textAlign: TextAlign? = null,
    colors: TextFieldColors? = null
) {
    val currentLayoutDirection = LocalLayoutDirection.current
    val effectiveLayoutDirection = forceLayoutDirection ?: currentLayoutDirection

    val effectiveTextStyle = textStyle.copy(
        textAlign = textAlign ?: textStyle.textAlign
    )

    val textFieldColors = colors ?: OutlinedTextFieldDefaults.colors(
        focusedContainerColor = customContainerColor,
        unfocusedContainerColor = customContainerColor,
        disabledContainerColor = customContainerColor.copy(alpha = 0.5f),
        errorContainerColor = customContainerColor,
        focusedBorderColor = customFocusedBorderColor,
        unfocusedBorderColor = customBorderColor,
        disabledBorderColor = customBorderColor,
        errorBorderColor = customErrorBorderColor
    )

    CompositionLocalProvider(LocalLayoutDirection provides effectiveLayoutDirection) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = effectiveTextStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = textFieldColors
        )
    }
}