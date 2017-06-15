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

package com.hippo.nimingban.scene

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hippo.nimingban.scene.ui.SceneUi
import com.hippo.stage.Scene

/*
 * Created by Hippo on 6/12/2017.
 */

abstract class UiScene : Scene() {

  internal var ui: SceneUi? = null

  abstract fun createUi(): SceneUi

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
    val ui = createUi()
    this.ui = ui
    return ui.create(inflater, container)
  }

  override fun onAttachView(view: View) {
    super.onAttachView(view)
    ui?.attach()
  }

  override fun onStart() {
    super.onStart()
    ui?.start()
  }

  override fun onResume() {
    super.onResume()
    ui?.resume()
  }

  override fun onPause() {
    super.onPause()
    ui?.pause()
  }

  override fun onStop() {
    super.onStop()
    ui?.stop()
  }

  override fun onDetachView(view: View) {
    super.onDetachView(view)
    ui?.detach()
  }

  override fun onDestroyView(view: View) {
    super.onDestroyView(view)
    ui?.destroy()
    ui = null
  }
}