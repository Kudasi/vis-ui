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

package com.kotcrab.vis.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.Timer.Task;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.Focusable;
import com.kotcrab.vis.ui.VisUI;

import java.lang.StringBuilder;

/**
 * A single-line text input field.
 * <p>
 * The preferred height of a text field is the height of the {@link TextFieldStyle#font} and {@link TextFieldStyle#background}.
 * The preferred width of a text field is 150, a relatively arbitrary size.
 * <p>
 * The text field will copy the currently selected text when ctrl+c is pressed, and paste any text in the clipboard when ctrl+v is
 * pressed. Clipboard functionality is provided via the {@link Clipboard} interface. Currently there are two standard
 * implementations, one for the desktop and one for Android. The Android clipboard is a stub, as copy & pasting on Android is not
 * supported yet.
 * <p>
 * The text field allows you to specify an {@link OnscreenKeyboard} for displaying a softkeyboard and piping all key events
 * generated by the keyboard to the text field. There are two standard implementations, one for the desktop and one for Android.
 * The desktop keyboard is a stub, as a softkeyboard is not needed on the desktop. The Android {@link OnscreenKeyboard}
 * implementation will bring up the default IME.
 * @author mzechner
 * @author Nathan Sweet
 * @author Kotcrab
 */
public class VisTextField extends Widget implements Disableable, Focusable {
	static protected final char ENTER_DESKTOP = '\r';
	static protected final char ENTER_ANDROID = '\n';
	static private final char BACKSPACE = 8;
	static private final char TAB = '\t';
	static private final char DELETE = 127;
	static private final char BULLET = 8226;
	static private final Vector2 tmp1 = new Vector2();
	static private final Vector2 tmp2 = new Vector2();
	static private final Vector2 tmp3 = new Vector2();
	protected final FloatArray glyphAdvances = new FloatArray(), glyphPositions = new FloatArray();
	protected String text;
	protected int cursor, selectionStart;
	protected boolean hasSelection;
	protected boolean writeEnters;
	protected CharSequence displayText;
	protected float textHeight, textOffset;
	VisTextFieldStyle style;
	InputListener inputListener;
	TextFieldListener listener;
	TextFieldFilter filter;
	OnscreenKeyboard keyboard = new DefaultOnscreenKeyboard();
	boolean focusTraversal = true, onlyFontChars = true, disabled, rightAligned;
	boolean passwordMode;
	float renderOffset;
	boolean cursorOn = true;
	long lastBlink;
	KeyRepeatTask keyRepeatTask = new KeyRepeatTask();
	KeyTypedRepeatTask keyTypedRepeatTask = new KeyTypedRepeatTask();
	float keyRepeatInitialTime = 0.4f;
	float keyRepeatTime = 0.05f;
	private String messageText;
	private Clipboard clipboard;
	private float selectionX, selectionWidth;
	private StringBuilder passwordBuffer;
	private char passwordCharacter = BULLET;
	private int visibleTextStart, visibleTextEnd;
	private int maxLength = 0;
	private float blinkTime = 0.32f;
	private ClickListener clickListener;

	// --

	private boolean drawBorder;
	private boolean inputValid = true;

	public VisTextField () {
		this("", VisUI.getSkin().get(VisTextFieldStyle.class));
	}

	public VisTextField (String text) {
		this(text, VisUI.getSkin().get(VisTextFieldStyle.class));
	}

	public VisTextField (String text, String styleName) {
		this(text, VisUI.getSkin().get(styleName, VisTextFieldStyle.class));
	}

	public VisTextField (String text, VisTextFieldStyle style) {
		setStyle(style);
		clipboard = Gdx.app.getClipboard();
		initialize();
		setText(text);
		setSize(getPrefWidth(), getPrefHeight());
	}

	protected void initialize () {
		writeEnters = false;
		addListener(inputListener = createInputListener());
		addListener(clickListener = new ClickListener());
		addListener(new FocusListener() {
			@Override
			public void keyboardFocusChanged (FocusEvent event, Actor actor, boolean focused) {
				if(focused == false) {
					keyTypedRepeatTask.cancel();
					keyRepeatTask.cancel();
				}
			}
		});
	}

	protected InputListener createInputListener () {
		return new TextFieldClickListener();
	}

	protected int letterUnderCursor (float x) {
		x -= renderOffset + textOffset;
		int index = glyphPositions.size - 1;
		float[] glyphPositions = this.glyphPositions.items;
		for (int i = 0, n = this.glyphPositions.size; i < n; i++) {
			if (glyphPositions[i] > x) {
				index = i - 1;
				break;
			}
		}
		return Math.max(0, index);
	}

	protected boolean isWordCharacter (char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
	}

	protected int[] wordUnderCursor (int at) {
		String text = this.text;
		int start = at, right = text.length(), left = 0, index = start;
		for (; index < right; index++) {
			if (!isWordCharacter(text.charAt(index))) {
				right = index;
				break;
			}
		}
		for (index = start - 1; index > -1; index--) {
			if (!isWordCharacter(text.charAt(index))) {
				left = index + 1;
				break;
			}
		}
		return new int[]{left, right};
	}

	int[] wordUnderCursor (float x) {
		return wordUnderCursor(letterUnderCursor(x));
	}

	boolean withinMaxLength (int size) {
		return maxLength <= 0 || size < maxLength;
	}

	public int getMaxLength () {
		return this.maxLength;
	}

	public void setMaxLength (int maxLength) {
		this.maxLength = maxLength;
	}

	/**
	 * When false, text set by {@link #setText(String)} may contain characters not in the font, a space will be displayed instead.
	 * When true (the default), characters not in the font are stripped by setText. Characters not in the font are always stripped
	 * when typed or pasted.
	 */
	public void setOnlyFontChars (boolean onlyFontChars) {
		this.onlyFontChars = onlyFontChars;
	}

	/**
	 * Returns the text field's style. Modifying the returned style may not have an effect until
	 * {@link #setStyle(VisTextFieldStyle)} is called.
	 */
	public VisTextFieldStyle getStyle () {
		return style;
	}

	public void setStyle (VisTextFieldStyle style) {
		if (style == null) throw new IllegalArgumentException("style cannot be null.");
		this.style = style;
		textHeight = style.font.getCapHeight() - style.font.getDescent() * 2;
		invalidateHierarchy();
	}

	protected void calculateOffsets () {
		float visibleWidth = getWidth();
		if (style.background != null)
			visibleWidth -= style.background.getLeftWidth() + style.background.getRightWidth();

		// Check if the cursor has gone out the left or right side of the visible area and adjust renderoffset.
		float position = glyphPositions.get(cursor);
		float distance = position - Math.abs(renderOffset);
		if (distance <= 0) {
			if (cursor > 0)
				renderOffset = -glyphPositions.get(cursor - 1);
			else
				renderOffset = 0;
		} else if (distance > visibleWidth) {
			renderOffset -= distance - visibleWidth;
		}

		// calculate first visible char based on render offset
		visibleTextStart = 0;
		textOffset = 0;
		float start = Math.abs(renderOffset);
		int glyphCount = glyphPositions.size;
		float[] glyphPositions = this.glyphPositions.items;
		float startPos = 0;
		for (int i = 0; i < glyphCount; i++) {
			if (glyphPositions[i] >= start) {
				visibleTextStart = i;
				startPos = glyphPositions[i];
				textOffset = startPos - start;
				break;
			}
		}

		// calculate last visible char based on visible width and render offset
		visibleTextEnd = Math.min(displayText.length(), cursor + 1);
		for (; visibleTextEnd <= displayText.length(); visibleTextEnd++) {
			if (glyphPositions[visibleTextEnd] - startPos > visibleWidth) break;
		}
		visibleTextEnd = Math.max(0, visibleTextEnd - 1);

		// calculate selection x position and width
		if (hasSelection) {
			int minIndex = Math.min(cursor, selectionStart);
			int maxIndex = Math.max(cursor, selectionStart);
			float minX = Math.max(glyphPositions[minIndex], startPos);
			float maxX = Math.min(glyphPositions[maxIndex], glyphPositions[visibleTextEnd]);
			selectionX = minX;
			selectionWidth = maxX - minX;
		}

		if (rightAligned) {
			textOffset = visibleWidth - (glyphPositions[visibleTextEnd] - startPos);
			if (hasSelection) selectionX += textOffset;
		}
	}

	@Override
	public void draw (Batch batch, float parentAlpha) {
		Stage stage = getStage();
		boolean focused = stage != null && stage.getKeyboardFocus() == this;

		final BitmapFont font = style.font;
		final Color fontColor = (disabled && style.disabledFontColor != null) ? style.disabledFontColor
				: ((focused && style.focusedFontColor != null) ? style.focusedFontColor : style.fontColor);
		final Drawable selection = style.selection;
		final Drawable cursorPatch = style.cursor;
		Drawable background = (disabled && style.disabledBackground != null) ? style.disabledBackground
				: ((focused && style.focusedBackground != null) ? style.focusedBackground : style.background);

		// vis
		if (!disabled && clickListener.isOver() && style.backgroundOver != null) background = style.backgroundOver;

		Color color = getColor();
		float x = getX();
		float y = getY();
		float width = getWidth();
		float height = getHeight();

		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
		float bgLeftWidth = 0;
		if (background != null) {
			background.draw(batch, x, y, width, height);
			bgLeftWidth = background.getLeftWidth();
		}

		float textY = getTextY(font, background);
		calculateOffsets();

		if (focused && hasSelection && selection != null) {
			drawSelection(selection, batch, font, x + bgLeftWidth, y + textY);
		}

		float yOffset = font.isFlipped() ? -textHeight : 0;
		if (displayText.length() == 0) {
			if (!focused && messageText != null) {
				if (style.messageFontColor != null) {
					font.setColor(style.messageFontColor.r, style.messageFontColor.g, style.messageFontColor.b,
							style.messageFontColor.a * parentAlpha);
				} else
					font.setColor(0.7f, 0.7f, 0.7f, parentAlpha);
				BitmapFont messageFont = style.messageFont != null ? style.messageFont : font;
				messageFont.draw(batch, messageText, x + bgLeftWidth, y + textY + yOffset);
			}
		} else {
			font.setColor(fontColor.r, fontColor.g, fontColor.b, fontColor.a * parentAlpha);
			drawText(batch, font, x + bgLeftWidth, y + textY + yOffset);
		}
		if (drawBorder && focused && !disabled) {
			blink();
			if (cursorOn && cursorPatch != null) {
				drawCursor(cursorPatch, batch, font, x + bgLeftWidth, y + textY);
			}
		}

		// vis
		if (inputValid == false)
			style.errorBorder.draw(batch, getX(), getY(), getWidth(), getHeight());
		else if (drawBorder && style.focusBorder != null)
			style.focusBorder.draw(batch, getX(), getY(), getWidth(), getHeight());

	}

	protected float getTextY (BitmapFont font, Drawable background) {
		float height = getHeight();
		float textY = textHeight / 2 + font.getDescent();
		if (background != null) {
			float bottom = background.getBottomHeight();
			textY = (int) (textY + (height - background.getTopHeight() - bottom) / 2 + bottom);
		} else {
			textY = (int) (textY + height / 2);
		}
		return textY;
	}

	/** Draws selection rectangle * */
	protected void drawSelection (Drawable selection, Batch batch, BitmapFont font, float x, float y) {
		selection.draw(batch, x + selectionX + renderOffset, y - textHeight - font.getDescent(), selectionWidth,
				textHeight + font.getDescent() / 2);
	}

	protected void drawText (Batch batch, BitmapFont font, float x, float y) {
		font.draw(batch, displayText, x + textOffset, y, visibleTextStart, visibleTextEnd);
	}

	protected void drawCursor (Drawable cursorPatch, Batch batch, BitmapFont font, float x, float y) {
		cursorPatch.draw(batch, x + textOffset + glyphPositions.get(cursor) - glyphPositions.items[visibleTextStart] - 1, y
				- textHeight - font.getDescent(), cursorPatch.getMinWidth(), textHeight + font.getDescent() / 2);
	}

	void updateDisplayText () {
		BitmapFont font = style.font;
		String text = this.text;
		int textLength = text.length();

		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < textLength; i++) {
			char c = text.charAt(i);
			buffer.append(font.containsCharacter(c) ? c : ' ');
		}
		String newDisplayText = buffer.toString();

		if (passwordMode && font.containsCharacter(passwordCharacter)) {
			if (passwordBuffer == null) passwordBuffer = new StringBuilder(newDisplayText.length());
			if (passwordBuffer.length() > textLength) //
				passwordBuffer.setLength(textLength);
			else {
				for (int i = passwordBuffer.length(); i < textLength; i++)
					passwordBuffer.append(passwordCharacter);
			}
			displayText = passwordBuffer;
		} else
			displayText = newDisplayText;
		font.computeGlyphAdvancesAndPositions(displayText, glyphAdvances, glyphPositions);
		if (selectionStart > newDisplayText.length()) selectionStart = textLength;
	}

	private void blink () {
		long time = TimeUtils.nanoTime();
		if ((time - lastBlink) / 1000000000.0f > blinkTime) {
			cursorOn = !cursorOn;
			lastBlink = time;
		}
	}

	/** Copies the contents of this TextField to the {@link Clipboard} implementation set on this TextField. */
	public void copy () {
		if (hasSelection && !passwordMode) {
			clipboard.setContents(text.substring(Math.min(cursor, selectionStart), Math.max(cursor, selectionStart)));
		}
	}

	/**
	 * Copies the selected contents of this TextField to the {@link Clipboard} implementation set on this TextField, then removes
	 * it.
	 */
	public void cut () {
		if (hasSelection && !passwordMode) {
			copy();
			cursor = delete();
		}
	}

	/** Pastes the content of the {@link Clipboard} implementation set on this Textfield to this TextField. */
	void paste () {
		paste(clipboard.getContents(), true);
	}

	void paste (String content, boolean onlyFontChars) {
		if (content == null) return;
		StringBuilder buffer = new StringBuilder();
		int textLength = text.length();
		for (int i = 0, n = content.length(); i < n; i++) {
			if (!withinMaxLength(textLength + buffer.length())) break;
			char c = content.charAt(i);
			if (!(writeEnters && (c == ENTER_ANDROID || c == ENTER_DESKTOP))) {
				if (onlyFontChars && !style.font.containsCharacter(c)) continue;
				if (filter != null && !filter.acceptChar(this, c)) continue;
			}
			buffer.append(c);
		}
		content = buffer.toString();

		if (hasSelection) cursor = delete(false);
		text = insert(cursor, content, text);
		updateDisplayText();
		cursor += content.length();
	}

	String insert (int position, CharSequence text, String to) {
		if (to.length() == 0) return text.toString();
		return to.substring(0, position) + text + to.substring(position, to.length());
	}

	int delete () {
		return delete(true);
	}

	int delete (boolean updateText) {
		return delete(selectionStart, cursor, updateText);
	}

	int delete (int from, int to, boolean updateText) {
		int minIndex = Math.min(from, to);
		int maxIndex = Math.max(from, to);
		text = (minIndex > 0 ? text.substring(0, minIndex) : "")
				+ (maxIndex < text.length() ? text.substring(maxIndex, text.length()) : "");
		if (updateText) updateDisplayText();
		clearSelection();
		return minIndex;
	}

	/**
	 * Focuses the next TextField. If none is found, the keyboard is hidden. Does nothing if the text field is not in a stage.
	 * @param up If true, the TextField with the same or next smallest y coordinate is found, else the next highest.
	 */
	public void next (boolean up) {
		Stage stage = getStage();
		if (stage == null) return;
		getParent().localToStageCoordinates(tmp1.set(getX(), getY()));
		VisTextField textField = findNextTextField(stage.getActors(), null, tmp2, tmp1, up);
		if (textField == null) { // Try to wrap around.
			if (up)
				tmp1.set(Float.MIN_VALUE, Float.MIN_VALUE);
			else
				tmp1.set(Float.MAX_VALUE, Float.MAX_VALUE);
			textField = findNextTextField(getStage().getActors(), null, tmp2, tmp1, up);
		}
		if (textField != null) {
			textField.focusField();
			textField.setCursorPosition(textField.getText().length());
		} else
			Gdx.input.setOnscreenKeyboardVisible(false);
	}

	private VisTextField findNextTextField (Array<Actor> actors, VisTextField best, Vector2 bestCoords, Vector2 currentCoords, boolean up) {
		Window modalWindow = findModalWindow(this);

		for (int i = 0, n = actors.size; i < n; i++) {
			Actor actor = actors.get(i);
			if (actor == this) continue;
			if (actor instanceof VisTextField) {
				VisTextField textField = (VisTextField) actor;

				if (modalWindow != null) {
					Window nextFieldModalWindow = findModalWindow(textField);
					if (nextFieldModalWindow != modalWindow) continue;
				}

				if (textField.isDisabled() || !textField.focusTraversal || isActorVisibleInStage(textField) == false)
					continue;

				Vector2 actorCoords = actor.getParent().localToStageCoordinates(tmp3.set(actor.getX(), actor.getY()));
				if ((actorCoords.y < currentCoords.y || (actorCoords.y == currentCoords.y && actorCoords.x > currentCoords.x)) ^ up) {
					if (best == null
							|| (actorCoords.y > bestCoords.y || (actorCoords.y == bestCoords.y && actorCoords.x < bestCoords.x)) ^ up) {
						best = (VisTextField) actor;
						bestCoords.set(actorCoords);
					}
				}
			} else if (actor instanceof Group)
				best = findNextTextField(((Group) actor).getChildren(), best, bestCoords, currentCoords, up);
		}
		return best;
	}

	/**
	 * Checks if actor is visible in stage acknowledging parent visibility.
	 * If any parent returns false from isVisible then this method return false.
	 * True is returned when this actor and all its parent are visible.
	 */
	private boolean isActorVisibleInStage (Actor actor) {
		if (actor == null) return true;
		if (actor.isVisible() == false) return false;
		return isActorVisibleInStage(actor.getParent());
	}

	private Window findModalWindow (Actor actor) {
		if (actor == null) return null;
		if (actor instanceof Window && ((Window) actor).isModal()) return (Window) actor;
		return findModalWindow(actor.getParent());
	}

	public InputListener getDefaultInputListener () {
		return inputListener;
	}

	/** @param listener May be null. */
	public void setTextFieldListener (TextFieldListener listener) {
		this.listener = listener;
	}

	public TextFieldFilter getTextFieldFilter () {
		return filter;
	}

	/** @param filter May be null. */
	public void setTextFieldFilter (TextFieldFilter filter) {
		this.filter = filter;
	}

	/** If true (the default), tab/shift+tab will move to the next text field. */
	public void setFocusTraversal (boolean focusTraversal) {
		this.focusTraversal = focusTraversal;
	}

	/** @return May be null. */
	public String getMessageText () {
		return messageText;
	}

	/**
	 * Sets the text that will be drawn in the text field if no text has been entered.
	 * @param messageText may be null.
	 */
	public void setMessageText (String messageText) {
		this.messageText = messageText;
	}

	/** @return Never null, might be an empty string. */
	public String getText () {
		return text;
	}

	public void setText (String str) {
		if (str == null) throw new IllegalArgumentException("text cannot be null.");
		if (str.equals(text)) return;

		clearSelection();
		text = "";
		paste(str, onlyFontChars);
		cursor = 0;
	}

	public int getSelectionStart () {
		return selectionStart;
	}

	public String getSelection () {
		return hasSelection ? text.substring(Math.min(selectionStart, cursor), Math.max(selectionStart, cursor)) : "";
	}

	/** Sets the selected text. */
	public void setSelection (int selectionStart, int selectionEnd) {
		if (selectionStart < 0) throw new IllegalArgumentException("selectionStart must be >= 0");
		if (selectionEnd < 0) throw new IllegalArgumentException("selectionEnd must be >= 0");
		selectionStart = Math.min(text.length(), selectionStart);
		selectionEnd = Math.min(text.length(), selectionEnd);
		if (selectionEnd == selectionStart) {
			clearSelection();
			return;
		}
		if (selectionEnd < selectionStart) {
			int temp = selectionEnd;
			selectionEnd = selectionStart;
			selectionStart = temp;
		}

		hasSelection = true;
		this.selectionStart = selectionStart;
		cursor = selectionEnd;
	}

	public void selectAll () {
		setSelection(0, text.length());
	}

	public void clearSelection () {
		hasSelection = false;
	}

	public int getCursorPosition () {
		return cursor;
	}

	/** Sets the cursor position and clears any selection. */
	public void setCursorPosition (int cursorPosition) {
		if (cursorPosition < 0) throw new IllegalArgumentException("cursorPosition must be >= 0");
		clearSelection();
		cursor = Math.min(cursorPosition, text.length());
	}

	/** Default is an instance of {@link DefaultOnscreenKeyboard}. */
	public OnscreenKeyboard getOnscreenKeyboard () {
		return keyboard;
	}

	public void setOnscreenKeyboard (OnscreenKeyboard keyboard) {
		this.keyboard = keyboard;
	}

	public void setClipboard (Clipboard clipboard) {
		this.clipboard = clipboard;
	}

	@Override
	public float getPrefWidth () {
		return 150;
	}

	@Override
	public float getPrefHeight () {
		float prefHeight = textHeight;
		if (style.background != null) {
			prefHeight = Math.max(prefHeight + style.background.getBottomHeight() + style.background.getTopHeight(),
					style.background.getMinHeight());
		}
		return prefHeight;
	}

	public void setRightAligned (boolean rightAligned) {
		this.rightAligned = rightAligned;
	}

	public boolean isPasswordMode () {
		return passwordMode;
	}

	/**
	 * If true, the text in this text field will be shown as bullet characters.
	 * @see #setPasswordCharacter(char)
	 */
	public void setPasswordMode (boolean passwordMode) {
		this.passwordMode = passwordMode;
		updateDisplayText();
	}

	/**
	 * Sets the password character for the text field. The character must be present in the {@link BitmapFont}. Default is 149
	 * (bullet).
	 */
	public void setPasswordCharacter (char passwordCharacter) {
		this.passwordCharacter = passwordCharacter;
		if (passwordMode) updateDisplayText();
	}

	public void setBlinkTime (float blinkTime) {
		this.blinkTime = blinkTime;
	}

	public boolean isDisabled () {
		return disabled;
	}

	@Override
	public void setDisabled (boolean disabled) {
		this.disabled = disabled;
		if (disabled) FocusManager.getFocus();
	}

	protected void moveCursor (boolean forward, boolean jump) {
		int limit = forward ? text.length() : 0;
		int charOffset = forward ? 0 : -1;
		while ((forward ? ++cursor < limit : --cursor > limit) && jump) {
			if (!continueCursor(cursor, charOffset)) break;
		}
	}

	protected boolean continueCursor (int index, int offset) {
		char c = text.charAt(index + offset);
		return isWordCharacter(c);
	}

	/** Focuses this field, field must be added to stage before this method can be called */
	public void focusField () {
		if (disabled) return;
		FocusManager.getFocus(VisTextField.this);
		setCursorPosition(0);
		selectionStart = cursor;
		Stage stage = getStage();
		if (stage != null) stage.setKeyboardFocus(VisTextField.this);
		keyboard.show(true);
		hasSelection = true;
	}

	@Override
	public void focusLost () {
		drawBorder = false;
	}

	@Override
	public void focusGained () {
		drawBorder = true;
	}

	public boolean isEmpty () {
		return text.length() == 0;
	}

	public boolean isInputValid () {
		return inputValid;
	}

	public void setInputValid (boolean inputValid) {
		this.inputValid = inputValid;
	}

	/**
	 * Interface for listening to typed characters.
	 * @author mzechner
	 */
	static public interface TextFieldListener {
		public void keyTyped (VisTextField textField, char c);
	}

	/**
	 * Interface for filtering characters entered into the text field.
	 * @author mzechner
	 */
	static public interface TextFieldFilter {
		public boolean acceptChar (VisTextField textField, char c);

		static public class DigitsOnlyFilter implements TextFieldFilter {
			@Override
			public boolean acceptChar (VisTextField textField, char c) {
				return Character.isDigit(c);
			}

		}
	}

	/**
	 * An interface for onscreen keyboards. Can invoke the default keyboard or render your own keyboard!
	 * @author mzechner
	 */
	static public interface OnscreenKeyboard {
		public void show (boolean visible);
	}

	/**
	 * The default {@link OnscreenKeyboard} used by all {@link TextField} instances. Just uses
	 * {@link Input#setOnscreenKeyboardVisible(boolean)} as appropriate. Might overlap your actual rendering, so use with care!
	 * @author mzechner
	 */
	static public class DefaultOnscreenKeyboard implements OnscreenKeyboard {
		@Override
		public void show (boolean visible) {
			Gdx.input.setOnscreenKeyboardVisible(visible);
		}
	}

	static public class VisTextFieldStyle extends TextFieldStyle {
		public Drawable focusBorder;
		public Drawable errorBorder;
		public Drawable backgroundOver;

		public VisTextFieldStyle () {
		}

		public VisTextFieldStyle (BitmapFont font, Color fontColor, Drawable cursor, Drawable selection, Drawable background) {
			super(font, fontColor, cursor, selection, background);
		}

		public VisTextFieldStyle (VisTextFieldStyle style) {
			super(style);
			this.focusBorder = style.focusBorder;
			this.errorBorder = style.errorBorder;
			this.backgroundOver = style.backgroundOver;
		}
	}

	class KeyRepeatTask extends Task {
		int keycode;

		@Override
		public void run () {
			inputListener.keyDown(null, keycode);
		}
	}

	class KeyTypedRepeatTask extends Task {
		char character;
		int keycode;

		@Override
		public void run () {
			inputListener.keyTyped(null, character);
		}
	}

	/** Basic input listener for the text field */
	public class TextFieldClickListener extends ClickListener {
		@Override
		public void clicked (InputEvent event, float x, float y) {
			int count = getTapCount() % 4;
			if (count == 0) clearSelection();
			if (count == 2) {
				int[] array = wordUnderCursor(x);
				setSelection(array[0], array[1]);
			}
			if (count == 3) selectAll();
		}

		@Override
		public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
			if (!super.touchDown(event, x, y, pointer, button)) return false;
			if (pointer == 0 && button != 0) return false;
			if (disabled) return true;
			FocusManager.getFocus(VisTextField.this);
			setCursorPosition(x, y);
			selectionStart = cursor;
			Stage stage = getStage();
			if (stage != null) stage.setKeyboardFocus(VisTextField.this);
			keyboard.show(true);
			hasSelection = true;
			return true;
		}

		@Override
		public void touchDragged (InputEvent event, float x, float y, int pointer) {
			super.touchDragged(event, x, y, pointer);
			setCursorPosition(x, y);
		}

		@Override
		public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
			if (selectionStart == cursor) hasSelection = false;
			super.touchUp(event, x, y, pointer, button);
		}

		protected void setCursorPosition (float x, float y) {
			lastBlink = 0;
			cursorOn = false;
			cursor = letterUnderCursor(x);
		}

		protected void goHome (boolean jump) {
			cursor = 0;
		}

		protected void goEnd (boolean jump) {
			cursor = text.length();
		}

		@Override
		public boolean keyDown (InputEvent event, int keycode) {
			if (disabled) return false;

			lastBlink = 0;
			cursorOn = false;

			Stage stage = getStage();
			if (stage == null || stage.getKeyboardFocus() != VisTextField.this) return false;
			if (drawBorder == false) return false;

			boolean repeat = false;
			boolean ctrl = UIUtils.ctrl();
			boolean jump = ctrl && !passwordMode;

			if (ctrl) {
				if (keycode == Keys.V) {
					paste();
					return true;
				}
				if (keycode == Keys.C || keycode == Keys.INSERT) {
					copy();
					return true;
				}
				if (keycode == Keys.X || keycode == Keys.DEL) {
					cut();
					return true;
				}
				if (keycode == Keys.A) {
					selectAll();
					return true;
				}
			}

			if (UIUtils.shift()) {
				if (keycode == Keys.INSERT) paste();
				if (keycode == Keys.FORWARD_DEL && hasSelection) {
					copy();
					delete(); // cut
				}
				selection:
				{
					int temp = cursor;
					keys:
					{
						if (keycode == Keys.LEFT) {
							moveCursor(false, jump);
							repeat = true;
							break keys;
						}
						if (keycode == Keys.RIGHT) {
							moveCursor(true, jump);
							repeat = true;
							break keys;
						}
						if (keycode == Keys.HOME) {
							goHome(jump);
							break keys;
						}
						if (keycode == Keys.END) {
							goEnd(jump);
							break keys;
						}
						break selection;
					}
					if (!hasSelection) {
						selectionStart = temp;
						hasSelection = true;
					}
				}
			} else {
				// Cursor movement or other keys (kills selection).
				if (keycode == Keys.LEFT) {
					moveCursor(false, jump);
					clearSelection();
					repeat = true;
				}
				if (keycode == Keys.RIGHT) {
					moveCursor(true, jump);
					clearSelection();
					repeat = true;
				}
				if (keycode == Keys.HOME) {
					goHome(jump);
					clearSelection();
				}
				if (keycode == Keys.END) {
					goEnd(jump);
					clearSelection();
				}
			}
			cursor = MathUtils.clamp(cursor, 0, text.length());

			if (repeat) {
				scheduleKeyRepeatTask(keycode);
			}
			return true;
		}

		protected void scheduleKeyRepeatTask (int keycode) {
			if (!keyRepeatTask.isScheduled() || keyRepeatTask.keycode != keycode) {
				keyRepeatTask.keycode = keycode;
				keyRepeatTask.cancel();
				Timer.schedule(keyRepeatTask, keyRepeatInitialTime, keyRepeatTime);
			}
		}

		protected void scheduleKeyTypedRepeatTask (int keycode, char character) {
			if (!keyTypedRepeatTask.isScheduled() || keyTypedRepeatTask.character != character) {
				keyTypedRepeatTask.character = character;
				keyTypedRepeatTask.keycode = keycode;
				keyTypedRepeatTask.cancel();
				Timer.schedule(keyTypedRepeatTask, keyRepeatInitialTime, keyRepeatTime);
			}
		}

		@Override
		public boolean keyUp (InputEvent event, int keycode) {
			if (disabled) return false;
			keyRepeatTask.cancel();
			if (keyTypedRepeatTask.keycode == keycode) keyTypedRepeatTask.cancel();
			return true;
		}

		@Override
		public boolean keyTyped (InputEvent event, char character) {
			if (disabled) return false;

			Stage stage = getStage();
			if (stage == null || stage.getKeyboardFocus() != VisTextField.this) return false;
			if (drawBorder == false) return false;

			if ((character == TAB || character == ENTER_ANDROID) && focusTraversal) {
				next(UIUtils.shift());
			} else {
				boolean delete = character == DELETE;
				boolean backspace = character == BACKSPACE;
				boolean add = style.font.containsCharacter(character)
						|| (writeEnters && (character == ENTER_ANDROID || character == ENTER_DESKTOP));
				boolean remove = backspace || delete;
				if (add || remove) {
					if (hasSelection)
						cursor = delete(false);
					else {
						if (backspace && cursor > 0) {
							text = text.substring(0, cursor - 1) + text.substring(cursor--);
							renderOffset = 0;
							scheduleKeyTypedRepeatTask(event != null ? event.getKeyCode() : keyTypedRepeatTask.keycode, character);
						}
						if (delete && cursor < text.length()) {
							text = text.substring(0, cursor) + text.substring(cursor + 1);
						}
					}
					if (add && !remove) {
						// Character may be added to the text.
						boolean isEnter = character == ENTER_DESKTOP || character == ENTER_ANDROID;
						if (!isEnter) {
							if (filter != null && !filter.acceptChar(VisTextField.this, character)) return true;
						}
						if (!withinMaxLength(text.length())) return true;
						String insertion = isEnter ? "\n" : String.valueOf(character);
						text = insert(cursor++, insertion, text);
						scheduleKeyTypedRepeatTask(event != null ? event.getKeyCode() : keyTypedRepeatTask.keycode, character);
					}

					updateDisplayText();
				}
			}
			if (listener != null) listener.keyTyped(VisTextField.this, character);
			return true;
		}
	}
}
