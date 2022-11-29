/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.services.ContactService
import ch.threema.app.services.PreferenceService
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.AvatarConverterUtil
import ch.threema.app.utils.ColorUtil
import ch.threema.app.utils.ContactUtil
import ch.threema.storage.models.ContactModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the avatars from the database or create the default avatars. The results of the loaded bitmaps will be cached by glide (if possible).
 */
class ContactAvatarFetcher(
    context: Context,
    private val contactService: ContactService?,
    private val contactAvatarConfig: AvatarCacheServiceImpl.ContactAvatarConfig,
    private val preferenceService: PreferenceService?
    ) : AvatarFetcher(context) {

    private val contactDefaultAvatar: VectorDrawableCompat? by lazy { VectorDrawableCompat.create(context.resources, R.drawable.ic_contact, null) }
    private val contactBusinessAvatar: VectorDrawableCompat? by lazy { VectorDrawableCompat.create(context.resources, R.drawable.ic_business, null) }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val contactModel = contactAvatarConfig.model
        val highRes = contactAvatarConfig.options.highRes
        // Show profile picture from contact (if set)
        val profilePicReceive: Boolean
        // Show default avatar
        val defaultAvatar: Boolean
        // Return default avatar if no other avatar is found
        val returnDefaultIfNone: Boolean
        when (contactAvatarConfig.options.defaultAvatarPolicy) {
            AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK -> {
                profilePicReceive = true
                defaultAvatar = false
                returnDefaultIfNone = true
            }
            AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR -> {
                profilePicReceive = true
                defaultAvatar = false
                returnDefaultIfNone = false
            }
            AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR -> {
                profilePicReceive = false
                defaultAvatar = true
                returnDefaultIfNone = true
            }
            AvatarOptions.DefaultAvatarPolicy.RESPECT_SETTINGS -> {
                profilePicReceive = preferenceService?.profilePicReceive == true
                defaultAvatar = false
                returnDefaultIfNone = true
            }
        }
        val backgroundColor = getBackgroundColor(contactAvatarConfig.options)

        val avatar = if (defaultAvatar) {
            buildDefaultAvatar(contactModel, highRes, backgroundColor)
        } else {
            loadContactAvatar(contactModel, highRes, profilePicReceive, returnDefaultIfNone, backgroundColor)
        }

        callback.onDataReady(avatar)
    }

    private fun loadContactAvatar(contactModel: ContactModel?, highRes: Boolean, profilePicReceive: Boolean, returnDefaultIfNone: Boolean, backgroundColor: Int): Bitmap? {
        if (contactModel == null) {
            return buildDefaultAvatar(null, highRes, backgroundColor)
        }

        // try profile picture
        if (profilePicReceive) {
            getProfilePicture(contactModel, highRes)?.let {
                return it
            }
        }

        // try local saved avatar
        getLocallySavedAvatar(contactModel, highRes)?.let {
            return it
        }

        // try android contact picture
        getAndroidContactAvatar(contactModel, highRes)?.let {
            return it
        }

        return if (returnDefaultIfNone) buildDefaultAvatar(contactModel, highRes, backgroundColor) else null
    }

    private fun getProfilePicture(contactModel: ContactModel, highRes: Boolean): Bitmap? {
        try {
            val result = fileService?.getContactPhoto(contactModel)
            if (result != null && !highRes) {
                return AvatarConverterUtil.convert(this.context.resources, result)
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    private fun getLocallySavedAvatar(contactModel: ContactModel, highRes: Boolean): Bitmap? {
        return try {
            var result = fileService?.getContactAvatar(contactModel)
            if (result != null && !highRes) {
                result = AvatarConverterUtil.convert(this.context.resources, result)
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun getAndroidContactAvatar(contactModel: ContactModel, highRes: Boolean): Bitmap? {
        if (ContactUtil.isChannelContact(contactModel) || AndroidContactUtil.getInstance().getAndroidContactUri(contactModel) == null) {
            return null
        }
        // regular contacts
        return try {
            var result = fileService?.getAndroidContactAvatar(contactModel)
            if (result != null && !highRes) {
                result = AvatarConverterUtil.convert(this.context.resources, result)
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun buildDefaultAvatar(contactModel: ContactModel?, highRes: Boolean, backgroundColor: Int): Bitmap {
        val color = contactService?.getAvatarColor(contactModel)
            ?: ColorUtil.getInstance().getCurrentThemeGray(context)
        val drawable = if (ContactUtil.isChannelContact(contactModel)) contactBusinessAvatar else contactDefaultAvatar
        return if (highRes) {
            buildDefaultAvatarHighRes(drawable, color, backgroundColor)
        } else {
            buildDefaultAvatarLowRes(drawable, color)
        }
    }

}
