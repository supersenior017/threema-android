/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

package ch.threema.app.webclient.converter;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.localcrypto.MasterKeyLockedException;

/**
 * A converter converts arbitrary data to MessagePack representation.
 */
@AnyThread
public abstract class Converter {

	private static ServiceManager serviceManager = null;

	@Nullable
	protected static ServiceManager getServiceManager() {
		if (serviceManager == null) {
			serviceManager = ThreemaApplication.getServiceManager();
		}
		return serviceManager;
	}

	protected static IdListService getBlackListService() {
		return getServiceManager().getBlackListService();
	}

	protected static ContactService getContactService() throws ConversionException {
		try {
			return getServiceManager().getContactService();
		} catch (NullPointerException | MasterKeyLockedException | FileSystemNotPresentException e) {
			throw new ConversionException(e.toString());
		}
	}

	protected static DeadlineListService getHiddenChatListService() throws ConversionException {
		try {
			return getServiceManager().getHiddenChatsListService();
		}
		catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}
	protected static Context getContext() {
		return getServiceManager().getContext();
	}

	protected static GroupService getGroupService() throws ConversionException {
		try {
			return getServiceManager().getGroupService();
		} catch (NullPointerException | MasterKeyLockedException | NoIdentityException | FileSystemNotPresentException e) {
			throw new ConversionException(e.toString());
		}
	}

	protected static DistributionListService getDistributionListService() throws ConversionException {
		try {
			return getServiceManager().getDistributionListService();
		} catch (NullPointerException | MasterKeyLockedException | NoIdentityException | FileSystemNotPresentException e) {
			throw new ConversionException(e.toString());
		}
	}

	protected static PreferenceService getPreferenceService()  throws ConversionException {
		try {
			return getServiceManager().getPreferenceService();
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}

	protected static FileService getFileService()  throws ConversionException {
		try {
			return getServiceManager().getFileService();
		} catch (NullPointerException | FileSystemNotPresentException e) {
			throw new ConversionException(e.toString());
		}
	}
}
