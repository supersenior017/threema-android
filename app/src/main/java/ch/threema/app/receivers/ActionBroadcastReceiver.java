/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

package ch.threema.app.receivers;

import android.content.BroadcastReceiver;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public abstract class ActionBroadcastReceiver extends BroadcastReceiver {
	protected static final String TAG = "ActionBroadcastReceiver";
	private static final Logger logger = LoggingUtil.getThreemaLogger(TAG);

	protected static final int WEARABLE_CONNECTION_LINGER = 1000 * 5;

	protected MessageService messageService;
	protected LifetimeService lifetimeService;
	protected NotificationService notificationService;
	protected ContactService contactService;
	protected DistributionListService distributionListService;
	protected GroupService groupService;

	public ActionBroadcastReceiver() {
		this.instantiate();
	}

	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.messageService,
				this.lifetimeService,
				this.notificationService,
				this.contactService,
				this.distributionListService,
				this.groupService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.messageService = serviceManager.getMessageService();
				this.lifetimeService = serviceManager.getLifetimeService();
				this.notificationService = serviceManager.getNotificationService();
				this.contactService = serviceManager.getContactService();
				this.distributionListService = serviceManager.getDistributionListService();
				this.groupService = serviceManager.getGroupService();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}
}
