/*
 * Copyright (C) 2024-2025 The Lunaris AOSP
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
import android.service.quicksettings.Tile.STATE_ACTIVE
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.dagger.SysUISingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TileShapeStyle {
    ROUNDED,
    ROUNDED_RECTANGLE,
    ROUNDED_WHEN_ACTIVE,
    RECTANGULAR_WHEN_ACTIVE,
}

@SysUISingleton
class TileShapeConfig(
    private val context: Context,
) {
    private val _shapeStyle = MutableStateFlow(readShapeStyle())
    val shapeStyle: StateFlow<TileShapeStyle> = _shapeStyle

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _shapeStyle.value = readShapeStyle()
            }
        }

    companion object {
        const val SETTING_KEY = "ax_qs_tile_style"
        const val ROUNDED_RADIUS_DP = 50
        const val ROUNDED_RECT_RADIUS_DP = 28
    }

    init {
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTING_KEY),
            false,
            observer,
        )
    }

    private fun readShapeStyle(): TileShapeStyle =
        TileShapeStyle
            .values()
            .getOrElse(
                Settings.Secure.getInt(context.contentResolver, SETTING_KEY, 0),
            ) { TileShapeStyle.ROUNDED }

    fun setShapeStyle(style: TileShapeStyle) {
        _shapeStyle.value = style
        Settings.Secure.putInt(context.contentResolver, SETTING_KEY, style.ordinal)
    }

    fun getEffectiveStyle(isActive: Boolean): TileShapeStyle =
        when (_shapeStyle.value) {
            TileShapeStyle.ROUNDED_WHEN_ACTIVE ->
                if (isActive) TileShapeStyle.ROUNDED else TileShapeStyle.ROUNDED_RECTANGLE
            TileShapeStyle.RECTANGULAR_WHEN_ACTIVE ->
                if (isActive) TileShapeStyle.ROUNDED_RECTANGLE else TileShapeStyle.ROUNDED
            else -> _shapeStyle.value
        }

    fun getCornerRadius(
        tileWidth: Dp,
        tileHeight: Dp,
        isActive: Boolean = false,
    ): Dp =
        when (getEffectiveStyle(isActive)) {
            TileShapeStyle.ROUNDED -> tileWidth / 2
            TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
            else -> tileWidth / 2
        }

    fun getIconTileShape(
        tileSize: Dp,
        isActive: Boolean = false,
    ): RoundedCornerShape =
        RoundedCornerShape(
            when (getEffectiveStyle(isActive)) {
                TileShapeStyle.ROUNDED -> tileSize / 2
                TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
                else -> tileSize / 2
            },
        )

    fun getLargeTileShape(
        tileWidth: Dp,
        tileHeight: Dp,
        isActive: Boolean = false,
    ): RoundedCornerShape =
        RoundedCornerShape(
            when (getEffectiveStyle(isActive)) {
                TileShapeStyle.ROUNDED -> tileHeight / 2
                TileShapeStyle.ROUNDED_RECTANGLE -> ROUNDED_RECT_RADIUS_DP.dp
                else -> tileHeight / 2
            },
        )
}

@Composable
fun rememberTileShapeConfig(config: TileShapeConfig): State<TileShapeStyle> =
    config.shapeStyle.collectAsState()

fun isTileActive(tileState: Int): Boolean = tileState == STATE_ACTIVE
