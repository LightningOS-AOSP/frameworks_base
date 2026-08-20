/*
 * Copyright (C) 2026 MistOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.panels.ui.compose

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import android.service.quicksettings.Tile
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.animation.Expandable

@Composable
fun IosControlPanel(
    modifier: Modifier = Modifier,
    internetTile: TileViewModel? = null,
    btTile: TileViewModel? = null,
) {
    val context = LocalContext.current
    val cr = context.contentResolver

    fun readEnabled(): Boolean = try {
        Settings.System.getIntForUser(
            cr, "qs_ios_control_panel", 0, UserHandle.USER_CURRENT
        ) == 1
    } catch (_: Exception) { false }

    var enabled by remember { mutableStateOf(readEnabled()) }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { enabled = readEnabled() }
        }
        try {
            cr.registerContentObserver(
                Settings.System.getUriFor("qs_ios_control_panel"),
                false, observer, UserHandle.USER_ALL,
            )
        } catch (_: Exception) {}
        onDispose { cr.unregisterContentObserver(observer) }
    }

    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
        exit  = shrinkVertically(tween(250)) + fadeOut(tween(250)),
        modifier = modifier,
    ) {
        IosControlPanelContent(internetTile, btTile)
    }
}

@Composable
private fun IosControlPanelContent(
    internetTile: TileViewModel? = null,
    btTile: TileViewModel? = null
) {
    val context = LocalContext.current
    val sessionManager = remember {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    var isMusicPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        var controllerCallback: MediaController.Callback? = null
        var currentController: MediaController? = null

        val logic = object {
            fun refreshActiveSessions() {
                val sessions = try {
                    sessionManager?.getActiveSessions(null) ?: emptyList()
                } catch (_: Exception) { emptyList<MediaController>() }

                val active = sessions.firstOrNull { ctrl ->
                    val ps = ctrl.playbackState?.state
                    ps != null && ps != PlaybackState.STATE_NONE && ps != PlaybackState.STATE_STOPPED && ps != PlaybackState.STATE_ERROR
                }

                if (active == null) {
                    controllerCallback?.let { cb -> currentController?.unregisterCallback(cb) }
                    currentController = null
                    controllerCallback = null
                    isMusicPlaying = false
                    return
                }

                if (currentController != active) {
                    controllerCallback?.let { cb -> currentController?.unregisterCallback(cb) }
                    currentController = active
                    val cb = object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            refreshActiveSessions()
                        }
                        override fun onSessionDestroyed() {
                            refreshActiveSessions()
                        }
                    }
                    active.registerCallback(cb)
                    controllerCallback = cb
                }

                isMusicPlaying = active != null
            }
        }

        logic.refreshActiveSessions()

        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener {
            logic.refreshActiveSessions()
        }
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, null)
        } catch (_: Exception) {}

        onDispose {
            try { sessionManager?.removeOnActiveSessionsChangedListener(sessionListener) }
            catch (_: Exception) {}
            controllerCallback?.let { cb -> currentController?.unregisterCallback(cb) }
        }
    }

    val panelHeight = 160.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
            if (isMusicPlaying) {
                val pagerState = rememberPagerState { 2 }
                LaunchedEffect(isMusicPlaying) {
                    if (!isMusicPlaying) pagerState.animateScrollToPage(0)
                }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        pageSpacing = 12.dp,
                        beyondViewportPageCount = 1,
                    ) { page ->
                        when (page) {
                            0 -> IosMusicPlayer(modifier = Modifier.fillMaxSize())
                            1 -> IosConnectivityTilesPage(
                                modifier = Modifier.fillMaxSize(),
                                internetTile = internetTile,
                                btTile = btTile
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(2) { index ->
                            val isSelected = pagerState.currentPage == index
                            val dotSize by animateDpAsState(
                                targetValue = if (isSelected) 6.dp else 5.dp,
                                animationSpec = tween(200),
                                label = "DotSize$index",
                            )
                            val dotColor by animateColorAsState(
                                targetValue = if (isSelected)
                                    Color.White.copy(alpha = 0.9f)
                                else
                                    Color.White.copy(alpha = 0.3f),
                                animationSpec = tween(200),
                                label = "DotColor$index",
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(dotSize)
                                    .clip(RoundedCornerShape(50))
                                    .background(dotColor)
                            )
                        }
                    }
                }
            } else {
                IosConnectivityTilesPage(
                    modifier = Modifier.fillMaxSize(),
                    internetTile = internetTile,
                    btTile = btTile
                )
            }
        }

        IosVerticalBrightnessSlider(
            modifier = Modifier.weight(0.7f).fillMaxHeight().widthIn(max = 68.dp)
        )
        IosVerticalVolumeSlider(
            modifier = Modifier.weight(0.7f).fillMaxHeight().widthIn(max = 68.dp)
        )
    }
}

@Composable
private fun IosConnectivityTilesPage(
    modifier: Modifier = Modifier,
    internetTile: TileViewModel? = null,
    btTile: TileViewModel? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IosInternetTile(
            tileVM = internetTile,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        IosBluetoothTile(
            tileVM = btTile,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosInternetTile(tileVM: TileViewModel? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    var connectedSsid by remember {
        mutableStateOf(
            wifiManager?.connectionInfo?.ssid
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                ?.removeSurrounding("\"")
        )
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION ||
                    action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val raw = wifiManager?.connectionInfo?.ssid
                    connectedSsid = raw
                        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                        ?.removeSurrounding("\"")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (tileVM != null) {
        DisposableEffect(tileVM) {
            val token = Any()
            tileVM.startListening(token)
            onDispose { tileVM.stopListening(token) }
        }

        val state by tileVM.state.collectAsStateWithLifecycle(initialValue = tileVM.currentState)
        val isActive = state.state == Tile.STATE_ACTIVE

        val sublabel = state.secondaryLabel
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: if (isActive) (connectedSsid ?: "On") else "Off"

        IosConnectivityTile(
            icon = if (isActive) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            label = state.label?.toString() ?: "Internet",
            sublabel = sublabel,
            isActive = isActive,
            modifier = modifier,
            onClick = { expandable -> tileVM.mainClick(expandable) },
            onLongClick = { expandable -> tileVM.settingsClick(expandable) },
        )
        return
    }

    var wifiEnabled by remember { mutableStateOf(wifiManager?.isWifiEnabled == true) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val s = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN,
                    )
                    wifiEnabled = s == WifiManager.WIFI_STATE_ENABLED ||
                                  s == WifiManager.WIFI_STATE_ENABLING
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        onDispose { context.unregisterReceiver(receiver) }
    }

    IosConnectivityTile(
        label      = "Internet",
        sublabel   = if (wifiEnabled) (connectedSsid ?: "Wi\u2011Fi") else "Off",
        icon       = if (wifiEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
        isActive   = wifiEnabled,
        modifier   = modifier,
        onClick    = { _ ->
            val newState = !wifiEnabled
            wifiEnabled = newState
            try {
                @Suppress("DEPRECATION")
                wifiManager?.setWifiEnabled(newState)
            } catch (_: Exception) {}
        },
        onLongClick = null,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosBluetoothTile(tileVM: TileViewModel? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val btAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    var connectedBtDevice by remember {
        mutableStateOf(
            try {
                btAdapter?.bondedDevices
                    ?.firstOrNull { it.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }
                    ?.name
            } catch (_: Exception) { null }
        )
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED ||
                    action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED ||
                    action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    connectedBtDevice = try {
                        btAdapter?.bondedDevices
                            ?.firstOrNull { it.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }
                            ?.name
                    } catch (_: Exception) { null }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (tileVM != null) {
        DisposableEffect(tileVM) {
            val token = Any()
            tileVM.startListening(token)
            onDispose { tileVM.stopListening(token) }
        }

        val state by tileVM.state.collectAsStateWithLifecycle(initialValue = tileVM.currentState)
        val isActive = state.state == Tile.STATE_ACTIVE

        val sublabel = state.secondaryLabel
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: if (isActive) (connectedBtDevice ?: "On") else "Off"

        IosConnectivityTile(
            icon = if (isActive) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            label = state.label?.toString() ?: "Bluetooth",
            sublabel = sublabel,
            isActive = isActive,
            modifier = modifier,
            onClick = { expandable -> tileVM.mainClick(expandable) },
            onLongClick = { expandable -> tileVM.settingsClick(expandable) },
        )
        return
    }

    var btEnabled by remember { mutableStateOf(btAdapter?.isEnabled == true) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val s = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR,
                    )
                    btEnabled = s == BluetoothAdapter.STATE_ON ||
                                s == BluetoothAdapter.STATE_TURNING_ON
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    IosConnectivityTile(
        label       = "Bluetooth",
        sublabel    = if (btEnabled) (connectedBtDevice ?: "On") else "Off",
        icon        = if (btEnabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
        isActive    = btEnabled,
        modifier    = modifier,
        onClick     = { _ ->
            val newState = !btEnabled
            btEnabled = newState
            try {
                @Suppress("DEPRECATION")
                if (newState) btAdapter?.enable() else btAdapter?.disable()
            } catch (_: Exception) {}
        },
        onLongClick = null,
    )
}

/**
 * A single iOS-style connectivity tile (Wi-Fi / Bluetooth).
 *
 * Colors are intentionally aligned with AOSP QS [TileDefaults]:
 *  - Active   bg : MaterialTheme.colorScheme.primary      (same as activeTileColors)
 *  - Inactive bg : LocalAndroidColorScheme.surfaceEffect1  (same as inactiveTileColors)
 *  - Icon tint  : onPrimary (active) / onSurface (inactive)
 *  - Icon bg    : transparent (matches AOSP inactiveTileColors.iconBackground = Transparent)
 */
/**
 * A single iOS-style connectivity tile (Wi-Fi / Bluetooth).
 *
 * Colors are intentionally aligned with AOSP QS [TileDefaults]:
 *  - Active   bg : MaterialTheme.colorScheme.primary      (same as activeTileColors)
 *  - Inactive bg : LocalAndroidColorScheme.surfaceEffect1  (same as inactiveTileColors)
 *  - Icon tint  : onPrimary (active) / onSurface (inactive)
 *  - Icon bg    : transparent (matches AOSP inactiveTileColors.iconBackground = Transparent)
 *
 * [onClick] receives a valid [Expandable] backed by the Compose layout, so
 * calling [TileViewModel.mainClick] with it properly launches the Internet /
 * Bluetooth dialog with the correct transition animation (same as AOSP tiles).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosConnectivityTile(
    label       : String,
    sublabel    : String,
    icon        : ImageVector,
    isActive    : Boolean,
    onClick     : (Expandable) -> Unit,
    onLongClick : ((Expandable) -> Unit)?,
    modifier    : Modifier = Modifier,
) {
    val aosp = LocalAndroidColorScheme.current
    val activeBg   = MaterialTheme.colorScheme.primary
    val inactiveBg = aosp.surfaceEffect1

    val bgColor by animateColorAsState(
        targetValue   = if (isActive) activeBg else inactiveBg,
        animationSpec = tween(300),
        label         = "TileBg_$label",
    )

    val iconTint by animateColorAsState(
        targetValue   = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label         = "TileIconTint_$label",
    )

    val labelColor by animateColorAsState(
        targetValue   = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label         = "TileLabel_$label",
    )
    val sublabelColor by animateColorAsState(
        targetValue   = if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label         = "TileSublabel_$label",
    )

    val haptic = LocalHapticFeedback.current

    val expandable = remember { Expandable(mutableSetOf()) }
    val tileShape  = RoundedCornerShape(32.dp)
    val controller = rememberExpandableController(
        color = bgColor,
        shape = tileShape,
    )

    Expandable(
        expandable = expandable,
        controller = controller,
        modifier   = modifier,
        useModifierBasedImplementation = true,
    ) { registeredExpandable ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(tileShape)
                .background(bgColor)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick(registeredExpandable)
                    },
                    onLongClick = onLongClick?.let { action ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            action(registeredExpandable)
                        }
                    },
                )
                .padding(horizontal = 16.dp, vertical = 0.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier         = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState   = icon,
                        animationSpec = tween(200),
                        label         = "IconCrossfade_$label",
                    ) { ic ->
                        Icon(
                            imageVector        = ic,
                            contentDescription = label,
                            tint               = iconTint,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            color      = labelColor,
                        ),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = sublabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color    = sublabelColor,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
