/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import ch.threema.app.R
import ch.threema.app.camera.VideoEditView
import ch.threema.app.ui.BigFileView
import ch.threema.app.ui.MediaItem
import ch.threema.app.utils.BitmapUtil.FLIP_HORIZONTAL
import ch.threema.app.utils.BitmapUtil.FLIP_VERTICAL
import ch.threema.base.utils.LoggingUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import pl.droidsonroids.gif.GifImageView

private val logger = LoggingUtil.getThreemaLogger("BigMediaFragment")

class BigMediaFragment : Fragment() {
    private var mediaItem: MediaItem? = null
    private var viewPager: ViewPager2? = null
    private lateinit var bigFileView: BigFileView
    private lateinit var bigImageView: ImageView
    private lateinit var bigGifImageView: GifImageView
    private lateinit var videoEditView: VideoEditView
    private lateinit var bigProgressBar: ProgressBar
    private var bottomElemHeight: Int = 0
    private var isVideo = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_big_media, container, false).apply {
            bigFileView = findViewById(R.id.big_file_view)
            bigImageView = findViewById(R.id.preview_image)
            bigGifImageView = findViewById(R.id.gif_image)
            videoEditView = findViewById(R.id.video_edit_view)
            bigProgressBar = findViewById(R.id.progress)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showBigMediaItem()
    }

    override fun onResume() {
        super.onResume()

        if (isVideo) {
            showBigVideo(mediaItem ?: return)
        }
    }

    override fun onPause() {
        super.onPause()

        if (isVideo) {
            videoEditView.releasePlayer()
        }
    }

    fun setMediaItem(mediaItem: MediaItem) {
        this.mediaItem = mediaItem
        isVideo = mediaItem.type == MediaItem.TYPE_VIDEO || mediaItem.type == MediaItem.TYPE_VIDEO_CAM
    }

    // Change to drag listener here
    fun setViewPager(viewPager: ViewPager2?) {
        this.viewPager = viewPager
    }

    private fun setBottomElemHeight(bottomElemHeight: Int) {
        this.bottomElemHeight = bottomElemHeight
    }

    fun showBigMediaItem() {
        logger.debug("showBigMediaItem")
        if (lifecycle.currentState == Lifecycle.State.INITIALIZED || lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }

        val item = mediaItem ?: return


        when (item.type) {
            MediaItem.TYPE_IMAGE, MediaItem.TYPE_IMAGE_CAM, MediaItem.TYPE_GIF -> {
                showBigImage(item)
            }
            MediaItem.TYPE_VIDEO, MediaItem.TYPE_VIDEO_CAM -> {
                // nothing to do as the video gets initialized in onResume
            }
            else -> {
                if (!this::bigFileView.isInitialized) {
                    return
                }
                showBigFile(item)
            }
        }
    }

    fun updateFilename() {
        if (bigFileView.visibility == View.VISIBLE) {
            bigFileView.setFilename(mediaItem?.filename)
        }
    }

    fun updateVideoPlayerSound() {
        if (isVideo) {
            if (mediaItem?.isMuted == true) {
                videoEditView.mutePlayer()
            } else {
                videoEditView.unmutePlayer()
            }
        }
    }

    fun updateSendAsFileState() {
        if (isVideo) {
            videoEditView.updateSendAsFileState()
        }
    }

    private fun showBigFile(item: MediaItem) {
        this.bigGifImageView.visibility = View.GONE
        this.bigImageView.visibility = View.GONE
        this.videoEditView.visibility = View.GONE
        this.bigFileView.visibility = View.VISIBLE
        this.bigFileView.setPadding(0, 0, 0, bottomElemHeight)
        this.bigFileView.setMediaItem(item)
    }

    private fun showBigVideo(item: MediaItem) {
        this.bigFileView.visibility = View.GONE
        this.bigImageView.visibility = View.GONE
        this.bigGifImageView.visibility = View.GONE
        this.videoEditView.visibility = View.VISIBLE
        this.videoEditView.setOnTimelineDragListener(object :
            VideoEditView.OnTimelineDragListener {
            override fun onTimelineDragStart() {
                viewPager?.isUserInputEnabled = false
            }

            override fun onTimelineDragStop() {
                viewPager?.isUserInputEnabled = true
            }
        })
        this.videoEditView.doOnLayout {
            this.videoEditView.setVideo(item)
        }
        this.videoEditView.requestLayout()
    }

    private fun showBigImage(item: MediaItem) {
        bigImageView.visibility = View.VISIBLE
        bigFileView.visibility = View.GONE
        videoEditView.visibility = View.GONE
        if (item.type == MediaItem.TYPE_GIF) {
            bigProgressBar.visibility = View.GONE
            bigImageView.visibility = View.GONE
            try {
                bigGifImageView.setImageURI(item.uri)
                bigGifImageView.visibility = View.VISIBLE
            } catch (e: Exception) {
                // may crash with a SecurityException on some exotic devices
                logger.error("Error setting GIF", e)
            }
        } else {
            val flipHorizontal =
                (item.rotation in setOf(90, 270) && item.flip and FLIP_VERTICAL == FLIP_VERTICAL)
                    || (item.rotation in setOf(0, 180) && item.flip and FLIP_HORIZONTAL == FLIP_HORIZONTAL)
            val flipVertical =
                (item.rotation in setOf(90, 270) && item.flip and FLIP_HORIZONTAL == FLIP_HORIZONTAL)
                    || (item.rotation in setOf(0, 180) && item.flip and FLIP_VERTICAL == FLIP_VERTICAL)
            bigImageView.rotationX = if (flipVertical) 180f else 0f
            bigImageView.rotationY = if (flipHorizontal) 180f else 0f

            Glide.with(context ?: return).load(item.uri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .apply(RequestOptions.fitCenterTransform())
                .transform(Rotate(item.rotation), FitCenter())
                .error(R.drawable.ic_baseline_broken_image_200)
                .into(bigImageView)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(mediaItem: MediaItem, bottomElemHeight: Int) =
            BigMediaFragment().apply {
                setMediaItem(mediaItem)
                setBottomElemHeight(bottomElemHeight)
            }
    }
}
