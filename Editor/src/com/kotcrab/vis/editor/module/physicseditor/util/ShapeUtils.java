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

package com.kotcrab.vis.editor.module.physicseditor.util;

import com.badlogic.gdx.math.Vector2;
import com.kotcrab.vis.editor.module.physicseditor.models.RigidBodyModel;
import com.kotcrab.vis.editor.module.physicseditor.models.ShapeModel;

/**
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 */
public class ShapeUtils {
	public static ShapeModel getShape (RigidBodyModel model, Vector2 v) {
		for (ShapeModel shape : model.getShapes()) {
			if (shape.getVertices().contains(v, true)) return shape;
		}

		return null;
	}
}
