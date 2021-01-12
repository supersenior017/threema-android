/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.os.PowerManager;
import android.text.TextUtils;
import android.widget.SeekBar;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.app.voicemessage.VoiceRecorderActivity.MAX_VOICE_MESSAGE_LENGTH_MILLIS;

public class AudioChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(AudioChatAdapterDecorator.class);

	private static final String LISTENER_TAG = "decorator";
	private MessagePlayer audioMessagePlayer;
	private final PowerManager powerManager;
	private final PowerManager.WakeLock audioPlayerWakelock;

	public AudioChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context.getApplicationContext(), messageModel, helper);
		this.powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
		this.audioPlayerWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":AudioPlayer");
	}

	private void keepScreenOn() {
		if (audioPlayerWakelock.isHeld()) {
			keepScreenOff();
		}

		if (audioPlayerWakelock != null && !audioPlayerWakelock.isHeld()) {
			audioPlayerWakelock.acquire(MAX_VOICE_MESSAGE_LENGTH_MILLIS);
		}

		keepScreenOnUpdate();
	}

	private void keepScreenOnUpdate() {
/*		RuntimeUtil.runOnUiThread(() -> {
			try {
				helper.getFragment().getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} catch (Exception e) {
				//
			}
		});
*/	}

	private void keepScreenOff() {
		if (audioPlayerWakelock != null && audioPlayerWakelock.isHeld()) {
			audioPlayerWakelock.release();
		}

/*		RuntimeUtil.runOnUiThread(() -> {
			try {
				helper.getFragment().getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} catch (Exception e) {
				//
			}
		});
*/	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		AudioDataModel audioDataModel;
		FileDataModel fileDataModel;
		final long duration;
		final boolean isDownloaded;
		String caption = null;

		if (getMessageModel().getType() == MessageType.VOICEMESSAGE) {
			audioDataModel = this.getMessageModel().getAudioData();
			duration = audioDataModel.getDuration();
			isDownloaded = audioDataModel.isDownloaded();
		} else {
			fileDataModel = this.getMessageModel().getFileData();
			duration = fileDataModel.getDuration();
			isDownloaded = fileDataModel.isDownloaded();
			caption = fileDataModel.getCaption();
		}

		audioMessagePlayer = this.getMessagePlayerService().createPlayer(this.getMessageModel(),
			helper.getFragment().getActivity(), this.helper.getMessageReceiver());

		this.setOnClickListener(view -> {
			// no action on onClick
		}, holder.messageBlockView);

		holder.messagePlayer = audioMessagePlayer;
		holder.controller.setOnClickListener(v -> {
			int status = holder.controller.getStatus();

			switch (status) {
				case ControllerView.STATUS_READY_TO_PLAY:
				case ControllerView.STATUS_PLAYING:
				case ControllerView.STATUS_READY_TO_DOWNLOAD:
					if (holder.seekBar != null && audioMessagePlayer != null) {
						audioMessagePlayer.toggle();
					}
					break;
				case ControllerView.STATUS_PROGRESSING:
					if (getMessageModel().isOutbox() && (getMessageModel().getState() == MessageState.PENDING || getMessageModel().getState() == MessageState.SENDING)) {
						getMessageService().cancelMessageUpload(getMessageModel());
					} else {
						audioMessagePlayer.cancel();
					}
					break;
				case ControllerView.STATUS_READY_TO_RETRY:
					if (onClickRetry != null) {
						onClickRetry.onClick(getMessageModel());
					}
			}
		});

		RuntimeUtil.runOnUiThread(() -> {

			holder.controller.setNeutral();

			//reset progressbar
			updateProgressCount(holder, 0);

			if (audioMessagePlayer != null) {
				boolean isPlaying = false;
				if (holder.seekBar != null) {
					holder.seekBar.setEnabled(false);
				}

				switch (audioMessagePlayer.getState()) {
					case MessagePlayer.State_NONE:
						if (isDownloaded) {
							holder.controller.setPlay();
						} else {
							if (helper.getDownloadService().isDownloading(this.getMessageModel().getId())) {
								holder.controller.setProgressing(false);
							} else {
								holder.controller.setReadyToDownload();
							}
						}
						break;
					case MessagePlayer.State_DOWNLOADING:
					case MessagePlayer.State_DECRYPTING:
						//show loading
						holder.controller.setProgressing();
						break;
					case MessagePlayer.State_DOWNLOADED:
					case MessagePlayer.State_DECRYPTED:
						holder.controller.setPlay();
						break;
					case MessagePlayer.State_PLAYING:
						isPlaying = true;
						// fallthrough
					case MessagePlayer.State_PAUSE:
					case MessagePlayer.State_INTERRUPTED_PLAY:
						if (isPlaying) {
							holder.controller.setPause();
						} else {
							holder.controller.setPlay();
						}

						if (holder.seekBar != null) {
							holder.seekBar.setEnabled(true);
							logger.debug("SeekBar: Duration = " + audioMessagePlayer.getDuration());
							holder.seekBar.setMax(audioMessagePlayer.getDuration());
							logger.debug("SeekBar: Position = " + audioMessagePlayer.getPosition());
							updateProgressCount(holder, audioMessagePlayer.getPosition());
							holder.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
								@Override
								public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
									if (b) {
										audioMessagePlayer.seekTo(i);
									}
								}

								@Override
								public void onStartTrackingTouch(SeekBar seekBar) {
								}

								@Override
								public void onStopTrackingTouch(SeekBar seekBar) {
								}
							});
						}

						break;
				}

				audioMessagePlayer
					.addListener(LISTENER_TAG, new MessagePlayer.PlayerListener() {
						@Override
						public void onError(final String humanReadableMessage) {
							RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), humanReadableMessage, Toast.LENGTH_SHORT).show());
						}
					})

					.addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
						@Override
						public void onStart(AbstractMessageModel messageModel) {
							invalidate(holder, position);
						}

						@Override
						public void onEnd(AbstractMessageModel messageModel, boolean success, final String message, File decryptedFile) {
							if (!success) {
								RuntimeUtil.runOnUiThread(() -> {
									holder.controller.setPlay();
									if (!TestUtil.empty(message)) {
										Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
									}
								});
							}
							invalidate(holder, position);
						}
					})

					.addListener(LISTENER_TAG, new MessagePlayer.DownloadListener() {
						@Override
						public void onStart(AbstractMessageModel messageModel) {
							invalidate(holder, position);
						}

						@Override
						public void onStatusUpdate(AbstractMessageModel messageModel, int progress) {
						}

						@Override
						public void onEnd(AbstractMessageModel messageModel, boolean success, final String message) {
							if (!success) {
								RuntimeUtil.runOnUiThread(() -> {
									holder.controller.setReadyToDownload();
									if (!TestUtil.empty(message)) {
										Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
									}
								});
							}
							invalidate(holder, position);
						}
					})

					.addListener(LISTENER_TAG, new MessagePlayer.PlaybackListener() {
						@Override
						public void onPlay(AbstractMessageModel messageModel, boolean autoPlay) {
							invalidate(holder, position);
							keepScreenOn();
						}

						@Override
						public void onPause(AbstractMessageModel messageModel) {
							invalidate(holder, position);
							keepScreenOff();
						}

						@Override
						public void onStatusUpdate(AbstractMessageModel messageModel, final int pos) {
								RuntimeUtil.runOnUiThread(() -> {
									if (holder.position == position) {
										if (holder.seekBar != null) {
											holder.seekBar.setMax(holder.messagePlayer.getDuration());
										}
										updateProgressCount(holder, pos);

										// make sure pinlock is not activated while playing
										ThreemaApplication.activityUserInteract(helper.getFragment().getActivity());
										keepScreenOnUpdate();
									}
								});
						}

						@Override
						public void onStop(AbstractMessageModel messageModel) {
							RuntimeUtil.runOnUiThread(() -> {
								holder.controller.setPlay();
								updateProgressCount(holder, 0);
								invalidate(holder, position);
								keepScreenOff();
							});
						}
					});
			} else {
				//no player => no playable file
				holder.controller.setNeutral();

				if (this.getMessageModel().getState() == MessageState.SENDFAILED) {
					holder.controller.setRetry();
				}
			}

			if (this.getMessageModel().isOutbox()) {
				// outgoing message
				switch (this.getMessageModel().getState()) {
					case TRANSCODING:
						holder.controller.setTranscoding();
						break;
					case PENDING:
					case SENDING:
						holder.controller.setProgressing();
						break;
					case SENDFAILED:
						holder.controller.setRetry();
						break;
					default:
						break;
				}
			}
		});

		//do not show duration if 0
		if(duration > 0) {
			this.setDatePrefix(StringConversionUtil.secondsToString(
					duration,
					false
			), holder.dateView.getTextSize());
			this.dateContentDescriptionPreifx = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
		}

		if(holder.contentView != null) {
			//one size fits all :-)
			holder.contentView.getLayoutParams().width = helper.getThumbnailWidth();
		}

		// format caption
		if (!TextUtils.isEmpty(caption)) {
			holder.bodyTextView.setText(formatTextString(caption, this.filterString));

			LinkifyUtil.getInstance().linkify(
				(ComposeMessageFragment) helper.getFragment(),
				holder.bodyTextView,
				this.getMessageModel(),
				caption.length() < 80,
				actionModeStatus.getActionModeEnabled(),
				onClickElement);

			this.showHide(holder.bodyTextView, true);
		} else {
			this.showHide(holder.bodyTextView, false);
		}
	}

	private void updateProgressCount(final ComposeMessageHolder holder, int value) {
		if (holder != null && holder.size != null && holder.seekBar != null) {
			holder.seekBar.setProgress(value);
			holder.size.setText(StringConversionUtil.secondsToString((long) value / 1000, false));
		}
	}

}
