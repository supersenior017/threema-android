/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class ClientInfo extends Converter {
	private static final Logger logger = LoggerFactory.getLogger(ClientInfo.class);

	// Top level keys
	private final static String DEVICE = "device";
	private final static String OS = "os";
	private final static String OS_VERSION = "osVersion";
	private final static String APP_VERSION = "appVersion";
	private final static String IS_WORK = "isWork";
	private final static String IN_APP_LOGO = "inAppLogo";
	private final static String PUSH_TOKEN = "pushToken";
	private final static String CONFIGURATION = "configuration";
	private final static String CAPABILITIES = "capabilities";

	// Configuration keys
	private final static String VOIP_ENABLED = "voipEnabled";
	private final static String VOIP_FORCE_TURN = "voipForceTurn";
	private final static String LARGE_SINGLE_EMOJI = "largeSingleEmoji";
	private final static String SHOW_INACTIVE_IDS = "showInactiveIDs";

	// Capabilities keys
	private final static String MAX_GROUP_SIZE = "maxGroupSize";
	private final static String MAX_FILE_SIZE = "maxFileSize";
	private final static String DISTRIBUTION_LISTS = "distributionLists";
	private final static String IMAGE_FORMAT = "imageFormat";
	private final static String MDM = "mdm";

	// Image format keys
	private final static String FORMAT_AVATAR = "avatar";
	private final static String FORMAT_THUMBNAIL = "thumbnail";

	// MDM keys
	private final static String DISABLE_ADD_CONTACT = "disableAddContact";
	private final static String DISABLE_CREATE_GROUP = "disableCreateGroup";
	private final static String DISABLE_SAVE_TO_GALLERY = "disableSaveToGallery";
	private final static String DISABLE_EXPORT = "disableExport";
	private final static String DISABLE_MESSAGE_PREVIEW = "disableMessagePreview";
	private final static String DISABLE_CALLS = "disableCalls";
	private final static String READONLY_PROFILE = "readonlyProfile";

	public static MsgpackObjectBuilder convert(@NonNull Context appContext,
	                                           @Nullable String pushToken) throws ConversionException {
		// Services
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			throw new ConversionException("Could not get service manager");
		}

		PreferenceService preferenceService;
		LicenseService licenseService;

		try {
			preferenceService = serviceManager.getPreferenceService();
			licenseService = serviceManager.getLicenseService();
		} catch (Exception e) {
			logger.error("Exception", e);
			throw new ConversionException("Services not available");
		}

		final MsgpackObjectBuilder data = new MsgpackObjectBuilder();
		data.put(DEVICE, Build.MODEL);
		data.put(OS, "android");
		data.put(OS_VERSION, Build.VERSION.RELEASE);
		data.put(APP_VERSION, ConfigUtils.getFullAppVersion(appContext));
		data.maybePut(PUSH_TOKEN, pushToken);

		// Work stuff
		if (ConfigUtils.isWorkBuild()) {
			data.put(IS_WORK, true);
			// Disabled for now
			//data.maybePut(IN_APP_LOGO, AppLogo.convert(
			//	preferenceService.getAppLogo(ConfigUtils.THEME_LIGHT),
			//	preferenceService.getAppLogo(ConfigUtils.THEME_DARK)
			//));
		} else {
			data.put(IS_WORK, false);
		}

		// Configuration
		final MsgpackObjectBuilder config = new MsgpackObjectBuilder();
		if (!ConfigUtils.isCallsEnabled(appContext, preferenceService, licenseService)) {
			config.put(VOIP_ENABLED, false);
		}
		if (preferenceService.getForceTURN()) {
			config.put(VOIP_FORCE_TURN, true);
		}
		if (!ConfigUtils.isBiggerSingleEmojis(appContext)) {
			config.put(LARGE_SINGLE_EMOJI, false);
		}
		config.put(SHOW_INACTIVE_IDS, preferenceService.showInactiveContacts());

		// Capabilities
		final MsgpackObjectBuilder capabilities = new MsgpackObjectBuilder();
		capabilities.put(MAX_GROUP_SIZE, appContext.getResources().getInteger(R.integer.max_group_size));
		capabilities.put(MAX_FILE_SIZE, ThreemaApplication.MAX_BLOB_SIZE);
		capabilities.put(DISTRIBUTION_LISTS, true);

		// Image format
		final MsgpackObjectBuilder imageFormat = new MsgpackObjectBuilder();
		imageFormat.put(FORMAT_AVATAR, "image/png");
		imageFormat.put(FORMAT_THUMBNAIL, "image/jpeg");
		capabilities.put(IMAGE_FORMAT, imageFormat);

		// MDM Flags
		if (ConfigUtils.isWorkRestricted()) {
			final MsgpackObjectBuilder mdm = new MsgpackObjectBuilder();
			if (AppRestrictionUtil.isAddContactDisabled(appContext)) {
				mdm.put(DISABLE_ADD_CONTACT, true);
			}
			if (AppRestrictionUtil.isCreateGroupDisabled(appContext)) {
				mdm.put(DISABLE_CREATE_GROUP, true);
			}
			if (AppRestrictionUtil.isSaveToGalleryDisabled(appContext)) {
				mdm.put(DISABLE_SAVE_TO_GALLERY, true);
			}
			if (AppRestrictionUtil.isExportDisabled(appContext)) {
				mdm.put(DISABLE_EXPORT, true);
			}
			if (AppRestrictionUtil.isMessagePreviewDisabled(appContext)) {
				mdm.put(DISABLE_MESSAGE_PREVIEW, true);
			}
			// TODO account for new th_calls_policy restriction
			if (AppRestrictionUtil.isCallsDisabled(appContext)) {
				mdm.put(DISABLE_CALLS, true);
			}
			if (AppRestrictionUtil.isReadonlyProfile(appContext)) {
				mdm.put(READONLY_PROFILE, true);
			}
			capabilities.put(MDM, mdm);
		}

		data.put(CONFIGURATION, config);
		data.put(CAPABILITIES, capabilities);
		return data;
	}
}
