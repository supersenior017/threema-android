/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

package ch.threema.app.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.BaseBundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.widget.Toast;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.Person;
import androidx.core.content.LocusIdCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.MainActivity;
import ch.threema.app.activities.RecipientListActivity;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;

import static androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED;

public final class ShortcutUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ShortcutUtil");

	private static final int MAX_SHARE_TARGETS = 100; // we recommend that you publish only four distinct shortcuts to improve their visual appearance in the launcher. https://developer.android.com/guide/topics/ui/shortcuts/best-practices

	public static final int TYPE_NONE = 0;
	public static final int TYPE_CHAT = 1;
	public static final int TYPE_CALL = 2;

	private static final Object dynamicShortcutLock = new Object();

	private static final String DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY = "ch.threema.app.category.DYNAMIC_SHORTCUT_SHARE_TARGET"; // do not use BuildConfig.APPLICATION_ID
	private static final String KEY_RECENT_UIDS = "recent_uids";

	private static class CommonShortcutInfo {
		Intent intent;
		@Nullable
		Bitmap bitmap;
		String longLabel;
		String shortLabel;
		String uniqueId;
	}

	/*****************************************************************************************************************/

	@WorkerThread
	public static void createPinnedShortcut(MessageReceiver<? extends AbstractMessageModel> messageReceiver, int type) {
		ShortcutInfoCompat shortcutInfoCompat = getPinnedShortcutInfo(messageReceiver, type);

		if (shortcutInfoCompat != null) {
			if (ShortcutManagerCompat.requestPinShortcut(getContext(), shortcutInfoCompat, null)) {
				Toast.makeText(getContext(), R.string.add_shortcut_success, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getContext(), R.string.add_shortcut_error, Toast.LENGTH_SHORT).show();
				logger.info("Failed to add shortcut");
			}
		}
	}

	@WorkerThread
	public static void updatePinnedShortcut(MessageReceiver<? extends AbstractMessageModel> messageReceiver) {
		String uniqueId = messageReceiver.getUniqueIdString();

		if (!TestUtil.empty(uniqueId)) {
			List<ShortcutInfoCompat> matchingShortcuts = new ArrayList<>();

			for (ShortcutInfoCompat shortcutInfo : ShortcutManagerCompat.getShortcuts(getContext(), FLAG_MATCH_PINNED)) {
				if (shortcutInfo.getId().equals(TYPE_CHAT + uniqueId)) {
					matchingShortcuts.add(getPinnedShortcutInfo(messageReceiver, TYPE_CHAT));
				} else if (shortcutInfo.getId().equals(TYPE_CALL + uniqueId)) {
					matchingShortcuts.add(getPinnedShortcutInfo(messageReceiver, TYPE_CALL));
				}
			}

			if (matchingShortcuts.size() > 0) {
				ShortcutManagerCompat.updateShortcuts(getContext(), matchingShortcuts);
			}
		}
	}

	@WorkerThread
	public static void deletePinnedShortcut(String uniqueIdString) {
		if (!TestUtil.empty(uniqueIdString)) {
			List<ShortcutInfoCompat> shortcutInfos = ShortcutManagerCompat.getShortcuts(getContext(), FLAG_MATCH_PINNED);

			if (shortcutInfos.size() > 0) {
				for (ShortcutInfoCompat shortcutInfo : shortcutInfos) {
					String shortcutId = shortcutInfo.getId();
					if (!TestUtil.empty(shortcutId)) {
						// ignore first character which represents the type indicator
						if (shortcutId.substring(1).equals(uniqueIdString)) {
							ShortcutManagerCompat.removeLongLivedShortcuts(getContext(), Collections.singletonList(shortcutInfo.getId()));
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Delete all pinned shortcuts associated with our app
	 */
	@WorkerThread
	public static void deleteAllPinnedShortcuts() {
		List<ShortcutInfoCompat> shortcutInfos = ShortcutManagerCompat.getShortcuts(getContext(), FLAG_MATCH_PINNED);

		if (shortcutInfos.size() > 0) {
			List<String> shortcutIds = new ArrayList<>();

			for (ShortcutInfoCompat shortcutInfoCompat : shortcutInfos) {
				shortcutIds.add(shortcutInfoCompat.getId());
			}
			try {
				ShortcutManagerCompat.removeLongLivedShortcuts(getContext(), shortcutIds);
			} catch (IllegalStateException e) {
				logger.error("Failed to remove shortcuts.", e);
			}
		}
	}

	@NonNull
	private static CommonShortcutInfo getCommonShortcutInfo(@NonNull MessageReceiver<? extends AbstractMessageModel> messageReceiver, int type) {
		CommonShortcutInfo commonShortcutInfo = new CommonShortcutInfo();

		Bitmap bitmap = messageReceiver.getNotificationAvatar();

		if (type == TYPE_CALL) {
			commonShortcutInfo.intent = getCallShortcutIntent();
			IntentDataUtil.addMessageReceiverToIntent(commonShortcutInfo.intent, messageReceiver);
			if (messageReceiver instanceof ContactMessageReceiver) {
				// backwards compatibility
				commonShortcutInfo.intent.putExtra(VoipCallService.EXTRA_CONTACT_IDENTITY, ((ContactMessageReceiver) messageReceiver).getContact().getIdentity());
			}
			commonShortcutInfo.longLabel = String.format(getContext().getString(R.string.threema_call_with), messageReceiver.getDisplayName());
			VectorDrawableCompat phoneDrawable = VectorDrawableCompat.create(getContext().getResources(), R.drawable.ic_phone_locked, getContext().getTheme());
			Bitmap phoneBitmap = AvatarConverterUtil.getAvatarBitmap(phoneDrawable, Color.BLACK, getContext().getResources().getDimensionPixelSize(R.dimen.shortcut_overlay_size));
			commonShortcutInfo.bitmap = bitmap != null ? BitmapUtil.addOverlay(bitmap, phoneBitmap, getContext().getResources().getDimensionPixelSize(R.dimen.call_shortcut_shadow_offset)) : null;
		} else {
			commonShortcutInfo.intent = getChatShortcutIntent();
			IntentDataUtil.addMessageReceiverToIntent(commonShortcutInfo.intent, messageReceiver);
			commonShortcutInfo.longLabel = String.format(getContext().getString(R.string.chat_with), messageReceiver.getDisplayName());
			commonShortcutInfo.bitmap = bitmap;
		}
		commonShortcutInfo.shortLabel = messageReceiver.getShortName();
		commonShortcutInfo.uniqueId = messageReceiver.getUniqueIdString();

		return commonShortcutInfo;
	}

	@Nullable
	public static ShortcutInfoCompat getPinnedShortcutInfo(MessageReceiver<? extends AbstractMessageModel> messageReceiver, int type) {
		CommonShortcutInfo commonShortcutInfo = getCommonShortcutInfo(messageReceiver, type);

		try {
			Person person = null;
			if (messageReceiver instanceof ContactMessageReceiver) {
				person = ConversationNotificationUtil.getPerson(getContactService(), ((ContactMessageReceiver) messageReceiver).getContact(), messageReceiver.getDisplayName());
			}

			ShortcutInfoCompat.Builder shortcutInfoCompatBuilder = new ShortcutInfoCompat.Builder(getContext(), type + commonShortcutInfo.uniqueId)
				.setShortLabel(commonShortcutInfo.shortLabel)
				.setLongLabel(commonShortcutInfo.longLabel)
				.setIntent(commonShortcutInfo.intent)
				.setLongLived(true);

			if (commonShortcutInfo.bitmap != null) {
				shortcutInfoCompatBuilder.setIcon(IconCompat.createWithBitmap(commonShortcutInfo.bitmap));
			}

			if (person != null) {
				shortcutInfoCompatBuilder.setPerson(person);
			}

			return shortcutInfoCompatBuilder.build();
		} catch (IllegalArgumentException e) {
			logger.error("Exception", e);
		}
		return null;
	}

	private static Intent getChatShortcutIntent() {
		Intent intent = new Intent(getContext(), ComposeMessageActivity.class);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setAction(Intent.ACTION_MAIN);

		return intent;
	}

	private static Intent getCallShortcutIntent() {
		Intent intent = new Intent(getContext(), CallActivity.class);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setAction(Intent.ACTION_MAIN);
		intent.putExtra(CallActivity.EXTRA_CALL_FROM_SHORTCUT, true);
		intent.putExtra(VoipCallService.EXTRA_IS_INITIATOR, true);
		intent.putExtra(VoipCallService.EXTRA_CALL_ID, -1L);

		return intent;
	}

	private static Intent getShareTargetShortcutIntent(MessageReceiver<? extends AbstractMessageModel> messageReceiver) {
		Intent intent = new Intent(getContext(), RecipientListActivity.class);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setAction(Intent.ACTION_DEFAULT);
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);

		return intent;
	}

	/*****************************************************************************************************************/

	/**
	 * Try publishing the most recent chats as dynamic shortcuts to be shown as share targets or in a launcher popup
	 * You may want to call deleteAllShareTargetShortcuts() before adding new dynamic shortcuts as they are limited
	 */
	@WorkerThread
	public static void publishRecentChatsAsShareTargets() {
		if (ThreemaApplication.getServiceManager() == null) {
			return;
		}

		PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		if (preferenceService == null || !preferenceService.isDirectShare()) {
			return;
		}

		if (ShortcutManagerCompat.isRateLimitingActive(getContext())) {
			logger.info("Shortcuts are currently rate limited. Exiting");
			return;
		}

		ConversationService conversationService = null;
		try {
			conversationService = ThreemaApplication.getServiceManager().getConversationService();
		} catch (ThreemaException e) {
			return;
		}

		if (conversationService == null) {
			return;
		}

		if (BackupService.isRunning() || RestoreService.isRunning()) {
			logger.info("Backup / Restore is running. Exiting");
			return;
		}

		final ConversationService.Filter filter = new ConversationService.Filter() {
			@Override
			public boolean onlyUnread() {
					return false;
				}

			@Override
			public boolean noDistributionLists() {
					return false;
				}

			@Override
			public boolean noHiddenChats() {
					return true;
				}

			@Override
			public boolean noInvalid() {
					return true;
				}
		};

		final List<ConversationModel> conversations = conversationService.getAll(false, filter);

		synchronized (dynamicShortcutLock) {
			final int numPublishableConversations = Math.min(conversations.size(), Math.min(ShortcutManagerCompat.getMaxShortcutCountPerActivity(getContext()), MAX_SHARE_TARGETS));

			final List<ShortcutInfoCompat> shareTargetShortcuts = new ArrayList<>();
			final List<String> publishedRecentChatsUids = new ArrayList<>();
			for (int i = 0; i < numPublishableConversations; i++) {
				ShortcutInfoCompat shortcutInfoCompat = getShareTargetShortcutInfo(conversations.get(i), i);
				if (shortcutInfoCompat != null) {
					shareTargetShortcuts.add(shortcutInfoCompat);
					publishedRecentChatsUids.add(shortcutInfoCompat.getId());
				}
			}

			if (shareTargetShortcuts.isEmpty()) {
				logger.info("No recent chats to publish sharing targets for");
				return;
			}

			if (Arrays.equals(preferenceService.getList(KEY_RECENT_UIDS), publishedRecentChatsUids.toArray(new String[0]))) {
				logger.info("Recent chats unchanged. Not updating sharing targets");
				return;
			}

			preferenceService.setList(KEY_RECENT_UIDS, publishedRecentChatsUids.toArray(new String[0]));

			try {
				ShortcutManagerCompat.setDynamicShortcuts(getContext(), shareTargetShortcuts);
				logger.info("Published most recent {} conversations as sharing target shortcuts", numPublishableConversations);
			} catch (Exception e) {
				logger.error("Failed setting dynamic shortcuts list ", e);
			}
		}
	}

	/**
	 * Delete all dynamic shortcuts associated with our app.
	 */
	@WorkerThread
	public static void deleteAllShareTargetShortcuts() {
		synchronized (dynamicShortcutLock) {
			List<ShortcutInfoCompat> shortcutInfos = ShortcutManagerCompat.getDynamicShortcuts(getContext());

			if (shortcutInfos.size() > 0) {
				List<String> shortcutIds = new ArrayList<>();

				for (ShortcutInfoCompat shortcutInfoCompat : shortcutInfos) {
					shortcutIds.add(shortcutInfoCompat.getId());
				}
				try {
					ShortcutManagerCompat.removeLongLivedShortcuts(getContext(), shortcutIds);
				} catch (IllegalStateException e) {
					logger.error("Failed to remove shortcuts.", e);
				}
			}
		}
	}

	/**
	 * Delete dynamic shortcut associated with provided message receiver
	 */
	@WorkerThread
	public static void deleteShareTargetShortcut(String uniqueIdString) {
		synchronized (dynamicShortcutLock) {
			ShortcutManagerCompat.removeLongLivedShortcuts(getContext(), Collections.singletonList(uniqueIdString));
		}
	}

	/**
	 * Retrieve a bundle with the extras supplied with a shortcut specified by its shortcutId
	 * @param shortcutId ID of the shortcut to retrieve extras from. The ID equals the MessageReceiver's uniqueId string
	 * @return A BaseBundle containing the extras identifying the MessageReceiver
	 */
	@Nullable
	public static BaseBundle getShareTargetExtrasFromShortcutId(@NonNull String shortcutId) {
		synchronized (dynamicShortcutLock) {
			List<ShortcutInfoCompat> shortcutInfos = ShortcutManagerCompat.getDynamicShortcuts(getContext());

			if (shortcutInfos.size() > 0) {
				for (ShortcutInfoCompat shortcutInfoCompat : shortcutInfos) {
					if (shortcutId.equals(shortcutInfoCompat.getId())) {
						return shortcutInfoCompat.getExtras();
					}
				}
			}
			return null;
		}
	}

	@Nullable
	@WorkerThread
	private static ShortcutInfoCompat getShareTargetShortcutInfo(@NonNull ConversationModel conversationModel, int rank) {
		MessageReceiver messageReceiver = conversationModel.getReceiver();

		if (messageReceiver == null) {
			return null;
		}

		Person person = null;
		if (messageReceiver instanceof ContactMessageReceiver) {
			person = ConversationNotificationUtil.getPerson(getContactService(), ((ContactMessageReceiver) messageReceiver).getContact(), messageReceiver.getDisplayName());
		}

		List<Person> persons = new ArrayList<>();
		if (messageReceiver instanceof GroupMessageReceiver) {
			try {
				Collection<ContactModel> contactModels = ThreemaApplication.getServiceManager().getGroupService().getMembers(conversationModel.getGroup());
				for(ContactModel contactModel: contactModels) {
					persons.add(ConversationNotificationUtil.getPerson(getContactService(), contactModel, NameUtil.getDisplayNameOrNickname(contactModel, true)));
				}
			} catch (Exception ignore) {}
		}

		if (messageReceiver.getNotificationAvatar() != null && !TestUtil.empty(messageReceiver.getDisplayName())) {
			try {
				ShortcutInfoCompat.Builder shortcutInfoCompatBuilder = new ShortcutInfoCompat.Builder(getContext(), messageReceiver.getUniqueIdString())
					.setIcon(IconCompat.createWithBitmap(messageReceiver.getNotificationAvatar()))
					.setIntent(getShareTargetShortcutIntent(messageReceiver))
					.setShortLabel(messageReceiver.getShortName() != null ? messageReceiver.getShortName() : messageReceiver.getDisplayName())
					.setLongLabel(messageReceiver.getDisplayName())
					.setActivity(new ComponentName(getContext(), MainActivity.class))
					.setExtras(putShareTargetExtras(messageReceiver))
					.setLongLived(true)
					.setRank(rank)
					.setIsConversation()
					.setLocusId(new LocusIdCompat(messageReceiver.getUniqueIdString()))
					.setCategories(Collections.singleton(DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY));

				if (person != null) {
					shortcutInfoCompatBuilder.setPerson(person);
				}

				if (persons.size() > 0) {
					shortcutInfoCompatBuilder.setPersons(persons.toArray(new Person[0]));
				}

				return shortcutInfoCompatBuilder.build();
			} catch (Exception e) {
				logger.debug("Unable to build shortcut", e);
			}
		}
		return null;
	}

	@NonNull
	private static PersistableBundle putShareTargetExtras(MessageReceiver<? extends AbstractMessageModel> messageReceiver) {
		PersistableBundle persistableBundle = new PersistableBundle();

		switch (messageReceiver.getType()) {
			case MessageReceiver.Type_CONTACT:
				persistableBundle.putString(ThreemaApplication.INTENT_DATA_CONTACT, ((ContactMessageReceiver) messageReceiver).getContact().getIdentity());
				break;
			case MessageReceiver.Type_GROUP:
				persistableBundle.putInt(ThreemaApplication.INTENT_DATA_GROUP, ((GroupMessageReceiver) messageReceiver).getGroup().getId());
				break;
			case MessageReceiver.Type_DISTRIBUTION_LIST:
				persistableBundle.putLong(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, ((DistributionListMessageReceiver) messageReceiver).getDistributionList().getId());
				break;
			default:
				break;
		}

		return persistableBundle;
	}

	private static Context getContext() {
		return ThreemaApplication.getAppContext();
	}

	private static ContactService getContactService() {
		try {
			return Objects.requireNonNull(ThreemaApplication.getServiceManager()).getContactService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return null;
	}
}
