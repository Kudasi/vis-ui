/*
 * Copyright 2014-2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kotcrab.vis.runtime.data;

import com.badlogic.gdx.utils.Array;

/**
 * Data class for Layer. Does not extend {@link EntityData} because layer is not an entity.
 * @author Kotcrab
 * @see EntityGroup
 */
@Deprecated
public class LayerData {
	public String name;
	public Array<EntityData> entities = new Array<EntityData>();

	public LayerData () {
	}

	public LayerData (String name) {
		this.name = name;
	}
}
