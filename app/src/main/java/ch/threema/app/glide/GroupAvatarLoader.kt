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
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.AvatarCacheServiceImpl
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey

class GroupAvatarLoader(private val context: Context) : ModelLoader<AvatarCacheServiceImpl.GroupAvatarConfig, Bitmap> {
    private val groupService = ThreemaApplication.getServiceManager()?.groupService
    private val preferenceService = ThreemaApplication.getServiceManager()?.preferenceService

    override fun buildLoadData(config: AvatarCacheServiceImpl.GroupAvatarConfig, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(config), GroupAvatarFetcher(context, groupService, config, preferenceService))
    }

    override fun handles(model: AvatarCacheServiceImpl.GroupAvatarConfig) = true
}
