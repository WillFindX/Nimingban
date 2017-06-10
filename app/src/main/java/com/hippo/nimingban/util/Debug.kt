/*
 * Copyright 2017 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE")

package com.hippo.nimingban.util

import com.hippo.nimingban.BuildConfig

/*
 * Created by Hippo on 6/10/2017.
 */

inline fun debug(value: Boolean) = if (BuildConfig.DEBUG) check(value) { "Debug failed." } else Unit

inline fun debug(value: Boolean, lazyMessage: () -> Any) = if (BuildConfig.DEBUG) check(value, lazyMessage) else Unit