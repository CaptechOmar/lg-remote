package com.github.heroslender.lgtvcontroller.ui.home

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.github.heroslender.lgtvcontroller.ui.controller.touchHaptics
import com.github.heroslender.lgtvcontroller.utils.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.model.GlideUrl
import com.github.heroslender.lgtvcontroller.ControllerTopAppBar
import com.github.heroslender.lgtvcontroller.R
import com.github.heroslender.lgtvcontroller.R.string
import com.github.heroslender.lgtvcontroller.TopAppBarAction
import com.github.heroslender.lgtvcontroller.device.DeviceControllerButton
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.device.impl.PreviewDevice
import com.github.heroslender.lgtvcontroller.domain.model.App
import com.github.heroslender.lgtvcontroller.domain.model.Input
import com.github.heroslender.lgtvcontroller.ui.ConnectedDeviceScaffold
import com.github.heroslender.lgtvcontroller.ui.TvTextInputState
import com.github.heroslender.lgtvcontroller.ui.controller.ButtonShape
import com.github.heroslender.lgtvcontroller.ui.controller.CIconButton
import com.github.heroslender.lgtvcontroller.ui.controller.CTextButton
import com.github.heroslender.lgtvcontroller.ui.controller.rememberReadoutVisible
import com.github.heroslender.lgtvcontroller.ui.icons.MyIconPack
import com.github.heroslender.lgtvcontroller.ui.icons.myiconpack.TvRemote
import com.github.heroslender.lgtvcontroller.ui.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Preview()
@Composable
fun HomePreview(
    isConnected: Boolean = true,
) {
    val device = if (isConnected) PreviewDevice else null
    val uiState = HomeUiState(
        loaded = true,
        hasTv = true,
        isConnected = isConnected,
        deviceID = device?.id,
        deviceName = device?.friendlyName,
        deviceStatus = device?.let { runBlocking { it.state.first().status } }
            ?: DeviceStatus.DISCONNECTED,
        runningApp = "netflix",
        apps = listOf(
            App(id = "youtube", name = "YouTube Yoyu YOuY asd", icon = "a"),
            App(id = "netflix", name = "Netflix", icon = "a"),
        ),
        inputs = listOf(
            Input(id = "hdmi1", name = "HDMI1", icon = "a"),
            Input(id = "hdmi2", name = "HDMI2", icon = "a", connected = true),
            Input(id = "scart", name = "SCART", icon = "a"),
        ),
        hasCapability = { true },
    )

    HomeScreen(
        uiState = uiState,
        textInputState = TvTextInputState(isKeyboardOpen = true),
        errorFlow = flow {},
        navigateToDeviceList = {},
        navigateToController = {},
        navigateToEditDevice = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    navigateToDeviceList: () -> Unit,
    navigateToController: () -> Unit,
    navigateToEditDevice: (String) -> Unit,
    navigateToSetup: () -> Unit = {},
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val textInputState by homeViewModel.tvTextInputState.collectAsState()

    HomeScreen(
        uiState = uiState,
        textInputState = textInputState,
        errorFlow = homeViewModel.errors,
        navigateToDeviceList = navigateToDeviceList,
        navigateToController = navigateToController,
        navigateToEditDevice = navigateToEditDevice,
        navigateToSetup = navigateToSetup,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    textInputState: TvTextInputState,
    errorFlow: Flow<Snackbar>,
    navigateToDeviceList: () -> Unit,
    navigateToController: () -> Unit,
    navigateToEditDevice: (String) -> Unit,
    navigateToSetup: () -> Unit = {},
) {
    // Nothing saved yet (first use): go and look for a tv instead of showing an empty remote
    LaunchedEffect(uiState.loaded, uiState.hasTv) {
        if (uiState.loaded && !uiState.hasTv) {
            navigateToSetup()
        }
    }

    ConnectedDeviceScaffold(
        errorFlow = errorFlow,
        textInputState = textInputState,
        topBar = {
            ControllerTopAppBar(
                title = uiState.deviceName ?: "",
                subtitle = buildAnnotatedString {
                    if (uiState.deviceStatus == DeviceStatus.CONNECTED) {
                        withStyle(style = SpanStyle(color = colorResource(R.color.connected))) {
                            append("● ")
                        }
                    }

                    append(stringResource(uiState.deviceStatus.nameResId))
                },
                onTitleClick = navigateToDeviceList,
                actions = {
                    TopAppBarAction(
                        imageVector = Filled.Settings,
                        contentDescription = stringResource(string.edit_button),
                        enabled = uiState.deviceID != null,
                    ) {
                        uiState.deviceID?.also { navigateToEditDevice(it) }
                    }
                },
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ControllerShortcutsCard(
                isConnected = uiState.isConnected,
                hasCapability = uiState.hasCapability,
                executeButton = uiState.executeButton,
                navigateToController = navigateToController,
                volume = uiState.volume,
            )

            AppsCard(
                apps = uiState.apps,
                tvId = uiState.deviceID.orEmpty(),
                runningApp = uiState.runningApp,
                openApp = { app ->
                    uiState.launchApp(app.id)
                }
            )

            InputsCard(
                inputs = uiState.inputs,
                runningApp = uiState.runningApp,
                openInputPicker = if (uiState.isConnected) {
                    { uiState.executeButton(DeviceControllerButton.SOURCE) }
                } else null,
                switchInput = { input ->
                    uiState.launchApp(input.id)
                }
            )
        }
    }
}

@Composable
fun ControllerShortcutsCard(
    isConnected: Boolean,
    hasCapability: (DeviceControllerButton) -> Boolean,
    executeButton: (DeviceControllerButton) -> Unit,
    navigateToController: () -> Unit,
    volume: Int? = null,
) {
    ContentCard(
        iconVector = MyIconPack.TvRemote,
        header = stringResource(string.controller_card_title),
        openCard = if (isConnected) navigateToController else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CIconButton(
                    iconId = R.drawable.baseline_power_24,
                    contentDescription = stringResource(string.power_button),
                    enabled = hasCapability(DeviceControllerButton.POWER),
                    modifier = Modifier
                        .weight(1F)
                        .aspectRatio(1F, true),
                ) {
                    executeButton(DeviceControllerButton.POWER)
                }
                CIconButton(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(string.mute_button),
                    enabled = hasCapability(DeviceControllerButton.MUTE),
                    modifier = Modifier
                        .weight(1F)
                        .aspectRatio(1F, true),
                ) {
                    executeButton(DeviceControllerButton.MUTE)
                }
                CIconButton(
                    iconId = R.drawable.baseline_home_24,
                    contentDescription = stringResource(string.home_button),
                    enabled = hasCapability(DeviceControllerButton.HOME),
                    modifier = Modifier
                        .weight(1F)
                        .aspectRatio(1F, true),
                ) {
                    executeButton(DeviceControllerButton.HOME)
                }
            }

            VolumeControls(
                hasCapability = hasCapability,
                executeButton = executeButton,
                level = volume,
            )
        }
    }
}

@Composable
fun ColumnScope.VolumeControls(
    hasCapability: (DeviceControllerButton) -> Boolean,
    executeButton: (DeviceControllerButton) -> Unit,
    modifier: Modifier = Modifier,
    level: Int? = null,
) {
    val isEnabled = hasCapability(DeviceControllerButton.VOLUME_UP)
    var downPressed by remember { mutableStateOf(false) }
    var upPressed by remember { mutableStateOf(false) }
    val showLevel = rememberReadoutVisible(downPressed || upPressed, level) && level != null
    HorizontalControls(
        centerText = if (showLevel) level.toString() else stringResource(string.volume_controls),
        centerHighlight = showLevel,
        modifier = modifier,
        enabled = isEnabled,
        topButton = {
            CTextButton(
                text = stringResource(string.volume_down_button),
                fontSize = 6.em,
                repeatOnHold = true,
                onPressedChange = { downPressed = it },
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1F)
                    .fillMaxWidth()
                    .aspectRatio(1.4F, false),
            ) {
                executeButton(DeviceControllerButton.VOLUME_DOWN)
            }
        },
        bottomButton = {
            CTextButton(
                text = stringResource(string.volume_up_button),
                fontSize = 6.em,
                repeatOnHold = true,
                onPressedChange = { upPressed = it },
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1F)
                    .fillMaxWidth()
                    .aspectRatio(1.4F, false),
            ) {
                executeButton(DeviceControllerButton.VOLUME_UP)
            }
        }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AppsCard(
    apps: List<App>,
    runningApp: String,
    openApp: (App) -> Unit,
    tvId: String = "",
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("app_order", Context.MODE_PRIVATE) }

    // The order the user chose, remembered per TV. Apps it doesn't mention keep the TV's order.
    var savedOrder by remember(tvId) {
        mutableStateOf(prefs.getString("order_$tvId", "").orEmpty().split(",").filter { it.isNotEmpty() })
    }
    val ordered = remember(apps, savedOrder) {
        apps.sortedBy { app -> savedOrder.indexOf(app.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    // Working order while dragging
    var order by remember(ordered) { mutableStateOf(ordered) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var suppressClick by remember { mutableStateOf(false) }
    var viewport by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val spacing = with(density) { 8.dp.toPx() }

    fun leftOf(index: Int): Float {
        var x = 0f
        for (i in 0 until index) x += (widths[order[i].id] ?: 0) + spacing
        return x
    }

    fun swap(a: Int, b: Int) {
        order = order.toMutableList().also { java.util.Collections.swap(it, a, b) }
        Haptics.buttonUp(context)
    }

    fun drag(dx: Float) {
        val id = draggedId ?: return
        dragOffset += dx
        while (true) {
            val index = order.indexOfFirst { it.id == id }
            val width = widths[id] ?: return
            val left = leftOf(index)
            val center = left + width / 2f + dragOffset

            val next = order.getOrNull(index + 1)
            if (next != null) {
                val nextWidth = widths[next.id] ?: 0
                if (center > left + width + spacing + nextWidth / 2f) {
                    swap(index, index + 1)
                    dragOffset -= nextWidth + spacing
                    continue
                }
            }

            val previous = order.getOrNull(index - 1)
            if (previous != null) {
                val previousWidth = widths[previous.id] ?: 0
                if (center < left - spacing - previousWidth / 2f) {
                    swap(index, index - 1)
                    dragOffset += previousWidth + spacing
                    continue
                }
            }
            break
        }
    }

    // Scroll the list while an app is held near its edges
    LaunchedEffect(draggedId) {
        val edge = with(density) { 48.dp.toPx() }
        val step = with(density) { 10.dp.toPx() }
        while (isActive && draggedId != null) {
            val id = draggedId ?: break
            val index = order.indexOfFirst { it.id == id }
            val width = widths[id] ?: 0
            val screenLeft = leftOf(index) + dragOffset - scrollState.value
            val delta = when {
                screenLeft < edge -> -step
                screenLeft + width > viewport - edge -> step
                else -> 0f
            }
            if (delta != 0f) {
                dragOffset += scrollState.scrollBy(delta)
                drag(0f)
            }
            delay(16)
        }
    }

    ScrollContentCard(
        iconVector = Filled.Widgets,
        header = stringResource(string.apps_card_title),
        scrollState = scrollState,
        onViewportWidth = { viewport = it },
    ) {
        for (appInfo in order) {
            key(appInfo.id) {
                ReorderItem(
                    left = leftOf(order.indexOf(appInfo)),
                    dragging = draggedId == appInfo.id,
                    dragOffset = dragOffset,
                    modifier = Modifier
                        .onSizeChanged { widths[appInfo.id] = it.width }
                        .pointerInput(appInfo.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    suppressClick = true
                                    draggedId = appInfo.id
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    draggedId = null
                                    dragOffset = 0f
                                    val ids = order.map { it.id }
                                    prefs.edit().putString("order_$tvId", ids.joinToString(",")).apply()
                                    savedOrder = ids
                                    scope.launch {
                                        delay(100)
                                        suppressClick = false
                                    }
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffset = 0f
                                    scope.launch {
                                        delay(100)
                                        suppressClick = false
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag(amount.x)
                                },
                            )
                        },
                ) {
                TextButton(
                    onClick = { if (!suppressClick) openApp(appInfo) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(96.dp)
                        .width(IntrinsicSize.Min)
                        .touchHaptics(),
                    colors = if (appInfo.id == runningApp) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                    contentPadding = PaddingValues(6.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(96.dp)
                    ) {
                        GlideImage(
                            model = CustomGlideUrl(appInfo.icon, appInfo.name),
                            contentDescription = appInfo.name,
                            modifier = Modifier
                                .weight(1F)
                                .aspectRatio(1F, true)
                                .clip(RoundedCornerShape(8.dp)),
                            loading = placeholder {
                                CircularProgressIndicator(
                                    color = if (appInfo.id == runningApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .weight(1F)
                                        .aspectRatio(1F, true)
                                )
                            },
                            failure = placeholder {
                                CircularProgressIndicator(
                                    color = if (appInfo.id == runningApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .weight(1F)
                                        .aspectRatio(1F, true)
                                )
                            }
                        )

                        Text(
                            text = appInfo.name,
                            modifier = Modifier,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = LocalTextStyle.current.fontSize * .8,
                                maxFontSize = LocalTextStyle.current.fontSize
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }

                }
            }
        }
    }
}

/**
 * Wraps a draggable item: while dragged it follows the finger (slightly enlarged), otherwise it
 * slides smoothly to its new slot when the order changes.
 */
@Composable
private fun ReorderItem(
    left: Float,
    dragging: Boolean,
    dragOffset: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val slide = remember { Animatable(0f) }
    var previousLeft by remember { mutableFloatStateOf(left) }
    LaunchedEffect(left) {
        if (previousLeft != left) {
            slide.snapTo(slide.value + previousLeft - left)
            previousLeft = left
            slide.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = if (dragging) dragOffset else slide.value
                val scale = if (dragging) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}

class CustomGlideUrl(
    url: String,
    val cacheName: String,
) : GlideUrl(url.ifEmpty { "Not Found" }) {
    override fun getCacheKey(): String {
        return cacheName
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun InputsCard(
    inputs: List<Input>,
    runningApp: String,
    switchInput: (Input) -> Unit,
    openInputPicker: (() -> Unit)? = null,
) {
    ScrollContentCard(
        iconVector = Filled.ElectricalServices,
        header = stringResource(string.inputs_card_title),
    ) {
        if (openInputPicker != null) {
            // Opens the TV's own input picker, to the left of the individual inputs
            TextButton(
                onClick = openInputPicker,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(96.dp)
                    .touchHaptics(),
                colors = ButtonDefaults.outlinedButtonColors(),
                contentPadding = PaddingValues(6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height(96.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sign_in),
                        contentDescription = stringResource(string.input_picker_button),
                        modifier = Modifier
                            .weight(1F)
                            .aspectRatio(1F, true)
                            .padding(12.dp),
                    )
                    Text(
                        text = stringResource(string.input_picker_button),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = LocalTextStyle.current.fontSize
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        for (appInfo in inputs) {
            TextButton(
                onClick = { switchInput(appInfo) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(96.dp)
                    .touchHaptics(),
                colors = if (appInfo.id == runningApp) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                contentPadding = PaddingValues(6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .height(96.dp)
                ) {
                    GlideImage(
                        model = CustomGlideUrl(appInfo.icon, appInfo.name),
                        contentDescription = appInfo.name,
                        modifier = Modifier
                            .weight(1F)
                            .aspectRatio(1F, true),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        loading = placeholder {
                            CircularProgressIndicator(
                                color = if (appInfo.id == runningApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .weight(1F)
                                    .aspectRatio(1F, true)
                            )
                        },
                        failure = placeholder {
                            CircularProgressIndicator(
                                color = if (appInfo.id == runningApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .weight(1F)
                                    .aspectRatio(1F, true)
                            )
                        }
                    )

                    Text(
                        text = buildAnnotatedString {
                            if (appInfo.connected) {
                                withStyle(style = SpanStyle(color = colorResource(R.color.connected))) {
                                    append("● ")
                                }
                            }

                            append(appInfo.name)
                        },
                        modifier = Modifier,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = LocalTextStyle.current.fontSize
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ScrollContentCard(
    iconVector: ImageVector,
    header: String,
    openCard: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    onViewportWidth: ((Int) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ContentCard(
        iconVector = iconVector,
        header = header,
        openCard = openCard
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onViewportWidth?.invoke(it.width) }
                .horizontalScroll(scrollState),
            content = content
        )
    }
}

@Composable
fun Card(
    iconVector: ImageVector,
    header: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    openCardIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    openCard: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = header,
                )
                Text(
                    text = header,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                if (openCard != null) {
                    Spacer(Modifier.weight(1F))

                    Icon(
                        imageVector = openCardIcon,
                        contentDescription = null,
                    )
                }
            }

            content()
        }
    }

    if (openCard != null) {
        ElevatedCard(
            modifier = modifier.padding(top = 16.dp),
            onClick = openCard,
            colors = colors,
            content = cardContent
        )
    } else {
        ElevatedCard(
            modifier = modifier.padding(top = 16.dp),
            colors = colors,
            content = cardContent
        )
    }
}

@Composable
fun ContentCard(
    iconVector: ImageVector,
    header: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    openCard: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        iconVector = iconVector,
        header = header,
        colors = colors,
        modifier = modifier,
        openCard = openCard,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            content = content
        )
    }
}

@Composable
fun ColumnScope.HorizontalControls(
    centerText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centerHighlight: Boolean = false,
    topButton: @Composable () -> Unit,
    bottomButton: @Composable () -> Unit,
) {
    Surface(
        shape = ButtonShape,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row {
            topButton()

            Text(
                text = centerText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1F),
                fontSize = if (centerHighlight) 5.em else 3.em,
                fontWeight = if (centerHighlight) androidx.compose.ui.text.font.FontWeight.Bold else null,
            )

            bottomButton()
        }
    }
}
