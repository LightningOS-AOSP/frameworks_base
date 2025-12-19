/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.panels.shared.model.LunarisQsSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SysUISingleton
class TileSpacingConfig(
    private val context: Context,
) {
    private val _spacing = MutableStateFlow(readSpacing())
    val spacing: StateFlow<LunarisQsSpacing> = _spacing

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _spacing.value = readSpacing()
            }
        }

    companion object {
        const val SETTING_TILE_GAP = "ax_qs_tile_gap"
        const val SETTING_SIDE_PADDING = "ax_qs_side_padding"
        const val SETTING_TOP_PADDING = "ax_qs_top_padding"

        const val DEFAULT_TILE_GAP_DP = 20
        const val DEFAULT_SIDE_PADDING_DP = 24
        const val DEFAULT_TOP_PADDING_DP = 8

        const val MIN_TILE_GAP_DP = 8
        const val MAX_TILE_GAP_DP = 28
        const val MIN_SIDE_PADDING_DP = 8
        const val MAX_SIDE_PADDING_DP = 40
        const val MIN_TOP_PADDING_DP = 0
        const val MAX_TOP_PADDING_DP = 32
    }

    init {
        val resolver = context.contentResolver
        listOf(
                SETTING_TILE_GAP,
                SETTING_SIDE_PADDING,
                SETTING_TOP_PADDING,
            )
            .forEach { key ->
                resolver.registerContentObserver(
                    Settings.Secure.getUriFor(key),
                    false,
                    observer,
                )
            }
    }

    private fun readSpacing(): LunarisQsSpacing {
        val resolver = context.contentResolver
        return LunarisQsSpacing(
            tileGap =
                Settings.Secure.getInt(resolver, SETTING_TILE_GAP, DEFAULT_TILE_GAP_DP)
                    .coerceIn(MIN_TILE_GAP_DP, MAX_TILE_GAP_DP),
            sidePadding =
                Settings.Secure.getInt(resolver, SETTING_SIDE_PADDING, DEFAULT_SIDE_PADDING_DP)
                    .coerceIn(MIN_SIDE_PADDING_DP, MAX_SIDE_PADDING_DP),
            topPadding =
                Settings.Secure.getInt(resolver, SETTING_TOP_PADDING, DEFAULT_TOP_PADDING_DP)
                    .coerceIn(MIN_TOP_PADDING_DP, MAX_TOP_PADDING_DP),
        )
    }

    fun setSpacing(spacing: LunarisQsSpacing) {
        setTileGap(spacing.tileGap)
        setSidePadding(spacing.sidePadding)
        setTopPadding(spacing.topPadding)
    }

    fun setTileGap(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(MIN_TILE_GAP_DP, MAX_TILE_GAP_DP)
        Settings.Secure.putInt(context.contentResolver, SETTING_TILE_GAP, clamped)
        _spacing.value = _spacing.value.copy(tileGap = clamped)
    }

    fun setSidePadding(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(MIN_SIDE_PADDING_DP, MAX_SIDE_PADDING_DP)
        Settings.Secure.putInt(context.contentResolver, SETTING_SIDE_PADDING, clamped)
        _spacing.value = _spacing.value.copy(sidePadding = clamped)
    }

    fun setTopPadding(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(MIN_TOP_PADDING_DP, MAX_TOP_PADDING_DP)
        Settings.Secure.putInt(context.contentResolver, SETTING_TOP_PADDING, clamped)
        _spacing.value = _spacing.value.copy(topPadding = clamped)
    }

    fun getHorizontalSpacingDp(): Dp = _spacing.value.tileGap.dp

    fun getVerticalSpacingDp(): Dp = _spacing.value.tileGap.dp

    fun resetToDefaults() {
        setSpacing(
            LunarisQsSpacing(
                tileGap = DEFAULT_TILE_GAP_DP,
                sidePadding = DEFAULT_SIDE_PADDING_DP,
                topPadding = DEFAULT_TOP_PADDING_DP,
            ),
        )
    }
}

@Composable
fun rememberTileSpacing(config: TileSpacingConfig): State<LunarisQsSpacing> =
    config.spacing.collectAsState()
