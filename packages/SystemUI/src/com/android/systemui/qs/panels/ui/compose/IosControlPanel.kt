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

import android.content.Context
import android.database.ContentObserver
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LargeTileContent
import com.android.systemui.qs.panels.ui.compose.infinitegrid.TileDefaults
import com.android.systemui.qs.panels.ui.compose.infinitegrid.getTileIcon
import com.android.systemui.qs.panels.ui.compose.infinitegrid.largeTilePadding
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IosInternetTile(
            tileVM = internetTile,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
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
    IosAospTile(tileVM = tileVM, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosBluetoothTile(tileVM: TileViewModel? = null, modifier: Modifier = Modifier) {
    IosAospTile(tileVM = tileVM, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosAospTile(tileVM: TileViewModel?, modifier: Modifier = Modifier) {
    if (tileVM == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.05f))
        )
        return
    }

    DisposableEffect(tileVM) {
        val token = Any()
        tileVM.startListening(token)
        onDispose { tileVM.stopListening(token) }
    }

    val context = LocalContext.current
    val state by tileVM.state.collectAsStateWithLifecycle(initialValue = tileVM.currentState)
    val uiState = remember(state) { state.toUiState(context.resources) }
    val icon = remember(state) { state.toIconProvider() }
    val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = true)
    val haptic = LocalHapticFeedback.current

    val bgColor by animateColorAsState(
        targetValue = colors.background,
        animationSpec = tween(300),
        label = "IosTileBg",
    )

    val rawSecondary = uiState.secondaryLabel.trim()
    val resolvedSecondaryLabel = when {
        rawSecondary.isNotEmpty() -> rawSecondary
        state.state == Tile.STATE_ACTIVE -> {
            val defaultBtLabel = context.getString(R.string.quick_settings_bluetooth_label)
            val defaultInternetLabel = context.getString(R.string.quick_settings_internet_label)
            val defaultWifiLabel = context.getString(R.string.quick_settings_wifi_label)
            if (uiState.label != defaultBtLabel && uiState.label != defaultInternetLabel && uiState.label != defaultWifiLabel) {
                context.getString(R.string.quick_settings_connected)
            } else {
                context.getString(R.string.switch_bar_on)
            }
        }
        state.state == Tile.STATE_INACTIVE -> context.getString(R.string.switch_bar_off)
        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    tileVM.mainClick(null)
                },
                onLongClick = if (uiState.handlesSettingsClick) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tileVM.settingsClick(null)
                    }
                } else null,
            )
            .largeTilePadding(isDualTarget = false),
        contentAlignment = Alignment.CenterStart,
    ) {
        LargeTileContent(
            label = uiState.label,
            secondaryLabel = resolvedSecondaryLabel,
            iconProvider = { getTileIcon(icon) },
            sideDrawable = uiState.sideDrawable,
            colors = colors,
            squishiness = { 1f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
