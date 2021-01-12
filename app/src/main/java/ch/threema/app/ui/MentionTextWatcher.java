/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import java.util.concurrent.CopyOnWriteArrayList;

public class MentionTextWatcher implements TextWatcher {
	private final EditText editText;
	private final CharSequence hint;
	private int maxLines;
	private final CopyOnWriteArrayList<ReplacementSpan> spansToRemove = new CopyOnWriteArrayList<>();

	public MentionTextWatcher(EditText editor) {
		editText = editor;

		hint = editText.getHint();
		maxLines = editText.getMaxLines();

		editText.addTextChangedListener(this);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//		logger.debug("beforeTextChanged " + s + " start: " + start + " count: " + count + " after: " + after);

		if (count == 1) {
			int end = start + count;
			Editable editableText = editText.getEditableText();
			ReplacementSpan[] list = editableText.getSpans(start, end, ReplacementSpan.class);

			for (ReplacementSpan span : list) {
				int spanStart = editableText.getSpanStart(span);
				int spanEnd = editableText.getSpanEnd(span);
				if ((spanStart < end) && (spanEnd > start)) {
					spansToRemove.add(span);
				}
			}
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
//		logger.debug("onTextChanged " + s + " start: " + start + " count: " + count );

	}

	@Override
	public void afterTextChanged(Editable s) {
//		logger.debug("afterTextChanged " + s);

		Editable editableText = editText.getEditableText();

		for (ReplacementSpan span : spansToRemove) {
			int start = editableText.getSpanStart(span);
			int end = editableText.getSpanEnd(span);

			editableText.removeSpan(span);

			if (start != end) {
				editableText.delete(start, end);
			}
		}
		spansToRemove.clear();

		// workaround to keep hint ellipsized on the first line
		if (s.length() > 0) {
			editText.setHint(null);
			editText.setMaxLines(maxLines);
		} else {
			editText.setMaxLines(1);
			editText.setHint(this.hint);
		}
	}

	public void setMaxLines(int maxLines) {
		this.maxLines = maxLines;
	}
}
