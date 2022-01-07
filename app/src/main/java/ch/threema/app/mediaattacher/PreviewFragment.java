/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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

package ch.threema.app.mediaattacher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;

public abstract class PreviewFragment extends Fragment implements AudioManager.OnAudioFocusChangeListener, PreviewFragmentInterface.AudioFocusActions {
	private AudioManager audioManager;
	protected MediaAttachItem mediaItem;
	protected MediaAttachViewModel mediaAttachViewModel;
	protected View rootView;
	protected boolean isChecked = false;

	public PreviewFragment(MediaAttachItem mediaItem, MediaAttachViewModel mediaAttachViewModel) {
		this.mediaItem = mediaItem;
		this.mediaAttachViewModel = mediaAttachViewModel;

		setRetainInstance(true);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		this.audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

		super.onCreate(savedInstanceState);
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_GAIN:
				resumeAudio();
				setVolume(1.0f);
				break;
			case AudioManager.AUDIOFOCUS_LOSS:
				stopAudio();
				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				pauseAudio();
				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				setVolume(0.2f);
				break;
		}
	}

	protected boolean requestFocus() {
		if (audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
			return false;
		}
		return true;
	}

	protected void abandonFocus() {
		audioManager.abandonAudioFocus(this);
	}
}

