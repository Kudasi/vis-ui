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

package com.kotcrab.vis.editor.ui.scene.entityproperties;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.editor.scene.EditorObject;
import com.kotcrab.vis.editor.scene.MusicObject;
import com.kotcrab.vis.editor.ui.IndeterminateCheckbox;
import com.kotcrab.vis.editor.util.gdx.TableBuilder;
import com.kotcrab.vis.ui.widget.VisLabel;

import static com.kotcrab.vis.editor.ui.scene.entityproperties.Utils.setCheckBoxState;

class MusicObjectTable extends SpecificObjectTable {
	private VisLabel label;
	private IndeterminateCheckbox loopingCheck;
	private IndeterminateCheckbox playOnStartCheck;

	public MusicObjectTable (EntityProperties properties) {
		super(properties, true);
		loopingCheck = new IndeterminateCheckbox("Loop");
		playOnStartCheck = new IndeterminateCheckbox("Play on start");

		loopingCheck.addListener(properties.getSharedCheckBoxChangeListener());
		playOnStartCheck.addListener(properties.getSharedCheckBoxChangeListener());

		label = new VisLabel();
		label.setColor(Color.GRAY);

		left();
		defaults().left();
		add(TableBuilder.build(new VisLabel("Music:"), label)).row();
		add(TableBuilder.build(loopingCheck, playOnStartCheck));
	}

	@Override
	public boolean isSupported (EditorObject entity) {
		return entity instanceof MusicObject;
	}

	@Override
	public void updateUIValues () {
		Array<EditorObject> entities = properties.getEntities();

		label.setText(Utils.getCommonString(entities, "<?>", entity -> ((MusicObject) entity).getAssetPath()));
		setCheckBoxState(entities, loopingCheck, entity -> ((MusicObject) entity).isLooping());
		setCheckBoxState(entities, playOnStartCheck, entity -> ((MusicObject) entity).isPlayOnStart());
	}

	@Override
	public void setValuesToEntities () {
		Array<EditorObject> entities = properties.getEntities();
		for (EditorObject entity : entities) {
			MusicObject obj = (MusicObject) entity;

			if (loopingCheck.isIndeterminate() == false) obj.setLooping(loopingCheck.isChecked());
			if (playOnStartCheck.isIndeterminate() == false) obj.setPlayOnStart(playOnStartCheck.isChecked());
		}
	}
}
