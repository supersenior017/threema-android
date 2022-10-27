/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

package ch.threema.app.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerView;

import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.VideoUtil;
import ch.threema.app.video.VideoTimelineThumbnailTask;
import ch.threema.base.utils.LoggingUtil;

import static com.google.android.exoplayer2.C.TIME_END_OF_SOURCE;

public class VideoEditView extends FrameLayout implements DefaultLifecycleObserver, VideoTimelineThumbnailTask.VideoTimelineListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VideoEditView");

	private static final int MOVING_NONE = 0;
	private static final int MOVING_LEFT = 1;
	private static final int MOVING_RIGHT = 2;
	private static final String THUMBNAIL_THREAD_NAME = "TimelineThumbs";
	private static final int MARKER_MOVE_MESSAGE_QUEUE_ID = 771294;
	private static final int MARKER_MOVE_UPDATE_FREQUENCY_MS = 200;

	private Context context;
	private int targetHeight, calculatedWidth;
	private Paint borderPaint, arrowPaint, dashPaint, progressPaint, dimPaint;
	private int arrowWidth, arrowHeight, borderWidth;
	private int offsetLeft = 0, offsetRight = 0, touchTargetWidth;
	private long videoCurrentPosition = 0L, videoFileSize = 0L, clippedStartTimeMs, clippedEndTimeMs;
	private MediaItem videoItem;
	private int isMoving = MOVING_NONE;
	private boolean isClipped = false;
	private GridLayout timelineGridLayout;
	private PlayerView videoView;
	private ExoPlayer videoPlayer;
	private com.google.android.exoplayer2.MediaItem videoSourceMediaItem;
	private DefaultMediaSourceFactory mediaSourceFactory;

	private TextView startTimeTextView, endTimeTextView, sizeTextView;
	private Thread thumbnailThread;
	private final Handler progressHandler = new Handler();
	private final Handler markerMoveHandler = new Handler(Looper.getMainLooper());
	private final List<Rect> exclusionRects = new ArrayList<>();

	public VideoEditView(Context context) {
		this(context, null);
	}

	public VideoEditView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public VideoEditView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		this.context = context;
		this.targetHeight = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_item_size);
		this.arrowWidth = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_arrow_width);
		this.arrowHeight = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_arrow_height);
		this.borderWidth = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_border_width);
		int progressWidth = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_progress_width);

		this.touchTargetWidth = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_touch_target_width);

		((LifecycleOwner)context).getLifecycle().addObserver(this);

		LayoutInflater.from(context).inflate(R.layout.view_video_edit, this, true);

		this.mediaSourceFactory = new DefaultMediaSourceFactory(context);

		this.timelineGridLayout = findViewById(R.id.video_timeline);
		this.videoView = findViewById(R.id.video_view);
		this.startTimeTextView = findViewById(R.id.start);
		this.endTimeTextView = findViewById(R.id.end);
		this.sizeTextView = findViewById(R.id.size);

		this.borderPaint = new Paint();

		this.borderPaint.setStyle(Paint.Style.STROKE);
		this.borderPaint.setColor(Color.WHITE);
		this.borderPaint.setAntiAlias(true);
		this.borderPaint.setStrokeWidth(this.borderWidth);

		this.dimPaint = new Paint();

		this.dimPaint.setStyle(Paint.Style.FILL);
		this.dimPaint.setColor(context.getResources().getColor(R.color.dark_background_dim));
		this.dimPaint.setAntiAlias(false);
		this.dimPaint.setStrokeWidth(0);

		this.arrowPaint = new Paint();

		this.arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		this.arrowPaint.setColor(Color.WHITE);
		this.arrowPaint.setAntiAlias(true);
		this.arrowPaint.setStrokeWidth(this.borderWidth);

		this.dashPaint = new Paint();

		this.dashPaint.setStyle(Paint.Style.STROKE);
		this.dashPaint.setColor(Color.WHITE);
		this.dashPaint.setAntiAlias(true);
		this.dashPaint.setStrokeWidth(this.borderWidth);
		this.dashPaint.setPathEffect(new DashPathEffect(new float[]{3, 8}, 0));

		this.progressPaint = new Paint();

		this.progressPaint.setStyle(Paint.Style.STROKE);
		this.progressPaint.setColor(Color.WHITE);
		this.progressPaint.setAntiAlias(true);
		this.progressPaint.setStrokeWidth(progressWidth);

		initVideoView();
	}

	@SuppressLint("ClickableViewAccessibility")
	private void initVideoView() {
		this.videoPlayer = VideoUtil.getExoPlayer(context);
		this.videoPlayer.setPlayWhenReady(false);
		this.videoPlayer.addListener(new Player.Listener() {
			@Override
			public void onPlaybackStateChanged(int playbackState) {
				Player.Listener.super.onPlaybackStateChanged(playbackState);
				updateProgressBar();
			}
		});

		this.videoView.setPlayer(videoPlayer);
		this.videoView.setControllerHideOnTouch(true);
		this.videoView.setControllerShowTimeoutMs(1500);
		this.videoView.setControllerAutoShow(true);
		this.videoView.setOnTouchListener((v, event) -> onTouchEvent(event));
	}

	@SuppressLint("DrawAllocation")
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		final int timelineMarginTop = this.timelineGridLayout.getTop();
		final int timelineMarginBottom = this.timelineGridLayout.getBottom();
		final int timelineMarginLeft = this.timelineGridLayout.getLeft();
		final int timelineMarginRight = this.timelineGridLayout.getRight();

		exclusionRects.add(new Rect(0, timelineMarginTop - arrowHeight, timelineMarginLeft + touchTargetWidth, timelineMarginBottom + arrowHeight));
		exclusionRects.add(new Rect(timelineMarginRight - touchTargetWidth, timelineMarginTop - arrowHeight, right, timelineMarginBottom + arrowHeight));

		ViewCompat.setSystemGestureExclusionRects(this, exclusionRects);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		int left = this.timelineGridLayout.getLeft() + this.offsetLeft;
		int right = this.timelineGridLayout.getRight() - this.offsetRight;
		int top = this.timelineGridLayout.getTop();
		int bottom = this.timelineGridLayout.getBottom();

		// draw rectangle
		canvas.drawLine(left, top, left, bottom, this.borderPaint);
		canvas.drawLine(right, top, right, bottom, this.borderPaint);

		Path path = new Path();
		path.moveTo(left, top);
		path.lineTo(right, top);
		canvas.drawPath(path, dashPaint);

		path = new Path();
		path.moveTo(left, bottom);
		path.lineTo(right, bottom);
		canvas.drawPath(path, dashPaint);

		// draw arrows
		path = new Path();
		path.setFillType(Path.FillType.EVEN_ODD);
		path.moveTo(left, bottom);
		path.lineTo(left + arrowWidth, bottom);
		path.lineTo(left, bottom + arrowHeight);
		path.close();
		canvas.drawPath(path, this.arrowPaint);

		path = new Path();
		path.setFillType(Path.FillType.EVEN_ODD);
		path.moveTo(right, bottom);
		path.lineTo(right - arrowWidth, bottom);
		path.lineTo(right, bottom + arrowHeight);
		path.close();
		canvas.drawPath(path, this.arrowPaint);

		path = new Path();
		path.setFillType(Path.FillType.EVEN_ODD);
		path.moveTo(left, top);
		path.lineTo(left + arrowWidth, top);
		path.lineTo(left, top - arrowHeight);
		path.close();
		canvas.drawPath(path, this.arrowPaint);

		path = new Path();
		path.setFillType(Path.FillType.EVEN_ODD);
		path.moveTo(right, top);
		path.lineTo(right - arrowWidth, top);
		path.lineTo(right, top - arrowHeight);
		path.close();
		canvas.drawPath(path, this.arrowPaint);

		if (videoItem != null) {
			if (videoItem.getStartTimeMs() > 0) {
				canvas.drawRect(new Rect(this.timelineGridLayout.getLeft(), top, left, bottom), this.dimPaint);
			}

			if (videoItem.getEndTimeMs() != MediaItem.TIME_UNDEFINED) {
				canvas.drawRect(new Rect(right, top, this.timelineGridLayout.getRight(), bottom), this.dimPaint);
			}

			if (videoItem.getDurationMs() != 0 && videoCurrentPosition > videoItem.getStartTimeMs() && videoCurrentPosition < videoItem.getEndTimeMs()) {
				int offset = (int) (this.timelineGridLayout.getWidth() * videoCurrentPosition / videoItem.getDurationMs()) + this.timelineGridLayout.getLeft();

				path = new Path();
				path.moveTo(offset, top);
				path.lineTo(offset, bottom);
				canvas.drawPath(path, progressPaint);
			}
		}
	}

	private void resetClipping() {
		if (isClipped) {
			videoPlayer.pause();
			videoPlayer.setMediaItem(videoSourceMediaItem);
			isClipped = false;
		}
		videoCurrentPosition = 0;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		int x = (int) event.getX();
		int y = (int) event.getY();

		int left = this.timelineGridLayout.getLeft() + this.offsetLeft;
		int right = this.timelineGridLayout.getRight() - this.offsetRight;

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				clippedStartTimeMs = videoItem.getStartTimeMs();
				clippedEndTimeMs = videoItem.getEndTimeMs();

				if (y >= (this.timelineGridLayout.getTop() - arrowHeight) && y <= (this.timelineGridLayout.getBottom() + arrowHeight)) {
					if (x >= (left - touchTargetWidth) && x <= (left + touchTargetWidth)) {
						logger.debug("start moving left: {}", x);
						isMoving = MOVING_LEFT;
						return true;
					} else if (x >= (right - touchTargetWidth) && x <= (right + touchTargetWidth)) {
						logger.debug("start moving right: {}", x);
						isMoving = MOVING_RIGHT;
						return true;
					}
				}
				isMoving = MOVING_NONE;
				break;
			case MotionEvent.ACTION_MOVE:
				logger.debug("moving {}", x);
				if (isMoving != MOVING_NONE) {
					int oldOffsetLeft = offsetLeft;
					int oldOffsetRight = offsetRight;

					switch (isMoving) {
						case MOVING_LEFT:
							logger.debug("moving left to: {}", x);
							offsetLeft = x - this.timelineGridLayout.getLeft();

							if (offsetLeft < 0) {
								offsetLeft = 0;
							} else if (x > (right - touchTargetWidth)) {
								offsetLeft = right - this.timelineGridLayout.getLeft() - touchTargetWidth;
							}

							clippedStartTimeMs = getVideoPositionFromTimelinePosition(offsetLeft);

							if (oldOffsetLeft != offsetLeft) {
								if (!markerMoveHandler.hasMessages(MARKER_MOVE_MESSAGE_QUEUE_ID)) {
									resetClipping();
									videoPlayer.seekTo(clippedStartTimeMs);
									markerMoveHandler.sendMessageDelayed(markerMoveHandler.obtainMessage(MARKER_MOVE_MESSAGE_QUEUE_ID), MARKER_MOVE_UPDATE_FREQUENCY_MS);
									updateStartAndEnd(clippedStartTimeMs, clippedEndTimeMs);
									invalidate();
								}
							}
							break;
						case MOVING_RIGHT:
							logger.debug("moving right to: {}", x);
							offsetRight = this.timelineGridLayout.getRight() - x;

							if (offsetRight < 0) {
								offsetRight = 0;
							} else if (x < (left + touchTargetWidth)) {
								offsetRight = this.timelineGridLayout.getRight() - (left + touchTargetWidth);
							}

							clippedEndTimeMs = getVideoPositionFromTimelinePosition(timelineGridLayout.getWidth() - offsetRight);

							if (oldOffsetRight != offsetRight) {
								if (!markerMoveHandler.hasMessages(MARKER_MOVE_MESSAGE_QUEUE_ID)) {
									resetClipping();
									videoPlayer.seekTo(clippedEndTimeMs);
									markerMoveHandler.sendMessageDelayed(markerMoveHandler.obtainMessage(MARKER_MOVE_MESSAGE_QUEUE_ID), MARKER_MOVE_UPDATE_FREQUENCY_MS);
									updateStartAndEnd(clippedStartTimeMs, clippedEndTimeMs);
									invalidate();
								}
							}
							break;
					}
					return true;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				markerMoveHandler.removeCallbacksAndMessages(null);

				if (isMoving == MOVING_LEFT || isMoving == MOVING_RIGHT) {
					videoItem.setStartTimeMs(getVideoPositionFromTimelinePosition(offsetLeft));
					videoItem.setEndTimeMs(getVideoPositionFromTimelinePosition(this.timelineGridLayout.getWidth() - offsetRight));

					isMoving = MOVING_NONE;

					updateStartAndEnd(videoItem.getStartTimeMs(), videoItem.getEndTimeMs());
					preparePlayer();

					return true;
				}
		}
		return super.onTouchEvent(event);
	}

	@SuppressLint("StaticFieldLeak")
	@UiThread
	public void setVideo(MediaItem mediaItem) {
		int numColumns = calculateNumColumns();

		if (numColumns <= 0 || numColumns > 64) {
			numColumns = GridLayout.UNDEFINED;
		}

		this.videoItem = mediaItem;

		if (thumbnailThread != null && thumbnailThread.isAlive()) {
			thumbnailThread.interrupt();
		}

		this.timelineGridLayout.setUseDefaultMargins(false);
		this.timelineGridLayout.removeAllViewsInLayout();

		GridLayout.Spec rowSpec = GridLayout.spec(0, 1, 1);
		for (int i = 0; i < numColumns; i++) {
			GridLayout.Spec colSpec = GridLayout.spec(i, 1, 1);

			FrameLayout frameLayout = new FrameLayout(context);
			frameLayout.setLayoutParams(new ViewGroup.LayoutParams(calculatedWidth, targetHeight));
			ImageView imageView = new ImageView(context);
			imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			imageView.setAdjustViewBounds(false);
			imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
			imageView.setScaleY(-1);
			imageView.setTag(i);
			frameLayout.addView(imageView);
			GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
			layoutParams.rowSpec = rowSpec;
			layoutParams.columnSpec = colSpec;
			layoutParams.width = calculatedWidth;
			layoutParams.height = targetHeight;

			this.timelineGridLayout.addView(frameLayout, layoutParams);
		}

		try {
			this.timelineGridLayout.setColumnCount(numColumns);
		} catch (IllegalArgumentException e) {
			logger.debug("Invalid column count. Num columns {}", numColumns);
			return;
		}

		if (numColumns != GridLayout.UNDEFINED) {
			thumbnailThread = new Thread(
			new VideoTimelineThumbnailTask(context,
				videoItem,
				numColumns,
				calculatedWidth,
				targetHeight,
				this), THUMBNAIL_THREAD_NAME);

			if (isAttachedToWindow() && context != null) {
				thumbnailThread.start();
				videoSourceMediaItem = com.google.android.exoplayer2.MediaItem.fromUri(videoItem.getUri());
				preparePlayer();
			}
		}
	}

	private void preparePlayer() {
		if (videoPlayer != null && videoSourceMediaItem != null) {
			long startPosition = videoItem.getStartTimeMs() * 1000;
			long endPosition = (videoItem.getEndTimeMs() == videoItem.getDurationMs() || videoItem.getEndTimeMs() == 0 || videoItem.getEndTimeMs() == MediaItem.TIME_UNDEFINED) ?
							TIME_END_OF_SOURCE :
							videoItem.getEndTimeMs() * 1000;

			logger.debug("startPosition: " + startPosition + " endPosition: " + endPosition);

			videoPlayer.setMediaItem(videoSourceMediaItem);
			ClippingMediaSource clippingSource = new ClippingMediaSource(mediaSourceFactory.createMediaSource(videoSourceMediaItem),
					startPosition,
					endPosition);
			isClipped = true;

			if (videoPlayer.isLoading() || videoPlayer.isPlaying()) {
				videoPlayer.stop();
				videoPlayer.clearMediaItems();
			}

			videoPlayer.setMediaSource(clippingSource);
			videoPlayer.setPlayWhenReady(false);
			videoPlayer.prepare();
		}
	}

	@MainThread
	private int calculateNumColumns() {
		if (context != null) {
			int timelineMargin = context.getResources().getDimensionPixelSize(R.dimen.video_timeline_marginLeftRight);
			int timelineWidth = getWidth() - (2 * timelineMargin);

			int approximateColumns = timelineWidth / targetHeight;

			if (approximateColumns > 0) {
				calculatedWidth = timelineWidth / approximateColumns;

				return approximateColumns;
			}
		}
		return 0;
	}

	@MainThread
	private void updateStartAndEnd(long startTime, long endTime) {
		startTimeTextView.setText(LocaleUtil.formatTimerText(startTime, true));
		endTimeTextView.setText(LocaleUtil.formatTimerText(endTime, true));

		if (videoFileSize > 0L) {
			long croppedDurationMs = endTime - startTime;

			long size = videoFileSize * croppedDurationMs / videoItem.getDurationMs();
			sizeTextView.setText(Formatter.formatFileSize(context, size));
		}
	}

	@MainThread
	private void updateProgressBar() {
		videoCurrentPosition = videoPlayer == null ? 0 : videoPlayer.getCurrentPosition() + videoItem.getStartTimeMs();

		invalidate();

		// Remove scheduled updates.
		progressHandler.removeCallbacks(updateProgressAction);
		// Schedule an update if necessary.
		int playbackState = videoPlayer == null ? Player.STATE_IDLE : videoPlayer.getPlaybackState();
		if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
			long delayMs;
			if (videoPlayer != null && videoPlayer.getPlayWhenReady() && playbackState == Player.STATE_READY) {
				delayMs = 100;
			} else {
				delayMs = 1000;
			}
			progressHandler.postDelayed(updateProgressAction, delayMs);
		} else if (playbackState != Player.STATE_ENDED) {
			videoCurrentPosition = 0;
			invalidate();
		}
	}

	private final Runnable updateProgressAction = new Runnable() {
		@Override
		public void run() {
			updateProgressBar();
		}
	};

	private long getVideoPositionFromTimelinePosition(int timelinePosition) {
		if (this.timelineGridLayout.getWidth() != 0) {
			return timelinePosition * videoItem.getDurationMs() / this.timelineGridLayout.getWidth();
		}
		return 0;
	}

	private int getTimelinePositionFromVideoPosition(long videoCurrentPosition) {
		if (this.timelineGridLayout.getWidth() != 0) {
			return (int) (videoCurrentPosition * this.timelineGridLayout.getWidth() / videoItem.getDurationMs());
		}
		return 0;
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		if (thumbnailThread != null && thumbnailThread.isAlive()) {
			thumbnailThread.interrupt();
		}

		if (videoView != null) {
			if (videoView.getPlayer() != null) {
				videoView.setPlayer(null);
			}
		}

		if (videoPlayer != null) {
			videoPlayer.stop();
			videoPlayer.release();
		}

		this.context = null;
	}

	@Override
	public boolean setThumbnail(int column, Bitmap thumbnail) {
		if (isAttachedToWindow()) {
			RuntimeUtil.runOnUiThread(() -> {
				if (isAttachedToWindow()) {
					ImageView imageView = findViewWithTag(column);
					if (imageView != null) {
						imageView.setImageBitmap(thumbnail);
					}
				}
			});
			return true;
		}
		return false;
	}

	@Override
	public void onMetadataReady() {
		// works with file URIs only
		String path = FileUtil.getRealPathFromURI(ThreemaApplication.getAppContext(), videoItem.getUri());
		if (path != null) {
			File f = new File(path);
			videoFileSize = f.length();
		}

		offsetLeft = VideoEditView.this.getTimelinePositionFromVideoPosition(videoItem.getStartTimeMs());
		offsetRight = timelineGridLayout.getWidth() - VideoEditView.this.getTimelinePositionFromVideoPosition(videoItem.getEndTimeMs());

		RuntimeUtil.runOnUiThread(() -> {
			if (isAttachedToWindow()) {
				updateStartAndEnd(videoItem.getStartTimeMs(), videoItem.getEndTimeMs());
			}
		});
	}

	@Override
	public void onError(String errorMessage) {
		logger.info("Unable to get video thumbnails. Reason: {}", errorMessage);
	}
}
