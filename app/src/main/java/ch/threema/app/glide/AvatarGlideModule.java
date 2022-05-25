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

package ch.threema.app.glide;

import android.content.Context;
import android.graphics.Bitmap;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import androidx.annotation.NonNull;
import ch.threema.app.services.AvatarCacheServiceImpl;

@GlideModule
public class AvatarGlideModule extends AppGlideModule {

	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.prepend(AvatarCacheServiceImpl.ContactAvatarConfig.class, Bitmap.class, new ContactAvatarModelLoaderFactory(context));
		registry.prepend(AvatarCacheServiceImpl.GroupAvatarConfig.class, Bitmap.class, new GroupAvatarModelLoaderFactory(context));
		registry.prepend(AvatarCacheServiceImpl.DistributionListAvatarConfig.class, Bitmap.class, new DistributionListAvatarModelLoaderFactory(context));
	}

}
