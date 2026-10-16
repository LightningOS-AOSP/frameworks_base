/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs

import android.content.Context
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.flags.SystemExitRestarter
import com.android.systemui.fragments.FragmentService
import com.android.systemui.qs.composefragment.QSFragmentCompose
import com.android.systemui.qs.flags.MistQsMode
import com.android.systemui.settings.UserTracker
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider

@SysUISingleton
class QSFragmentStartable
@Inject
constructor(
    private val fragmentService: FragmentService,
    private val userTracker: UserTracker,
    private val restarter: SystemExitRestarter,
    @Main private val mainExecutor: Executor,
    private val qsFragmentComposeProvider: Provider<QSFragmentCompose>,
) : CoreStartable {
    private val userChangedCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                if (MistQsMode.readEnabled(userContext, newUser) != MistQsMode.isEnabled) {
                    restarter.restartSystemUI("Quick Settings design changed for selected user")
                }
            }
        }

    override fun start() {
        userTracker.addCallback(userChangedCallback, mainExecutor)
        fragmentService.addFragmentInstantiationProvider(
            QSFragmentCompose::class.java,
            qsFragmentComposeProvider,
        )
    }
}

@Module
interface QSFragmentStartableModule {
    @Binds
    @IntoMap
    @ClassKey(QSFragmentStartable::class)
    fun bindsQSFragmentStartable(startable: QSFragmentStartable): CoreStartable
}
