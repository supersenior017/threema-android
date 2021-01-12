/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.actions;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;

public class TextMessageSendAction extends SendAction {
	protected static volatile TextMessageSendAction instance;
	private static final Object instanceLock = new Object();

	private TextMessageSendAction() {
		// Singleton
	}

	public static TextMessageSendAction getInstance() {
		if (instance == null) {
			synchronized (instanceLock) {
				if (instance == null) {
					instance = new TextMessageSendAction();
				}
			}
		}
		return instance;
	}

	public boolean sendTextMessage(final MessageReceiver[] allReceivers,
	                               String text,
	                               final ActionHandler actionHandler) {

		if (actionHandler == null) {
			return false;
		}

		MessageService messageService;
		try {
			messageService = this.getServiceManager().getMessageService();
		} catch (ThreemaException e) {
			actionHandler.onError(e.getMessage());
			return false;
		}

		if (messageService == null || TestUtil.empty(text)) {
			actionHandler.onError("Nothing to send");
			return false;
		}

		if (allReceivers.length < 1) {
			actionHandler.onError("No message receiver");
			return false;
		}

		/* split input text into multiple strings if necessary */
		ArrayList<String> messageTexts =
			TextUtil.splitEmojiText(text, ProtocolDefines.MAX_TEXT_MESSAGE_LEN);

		// add distribution list members to list of receivers
		final MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(allReceivers);
		final int numReceivers = resolvedReceivers.length;

		if (numReceivers > 0) {
			actionHandler.onProgress(100, 100);
			for (MessageReceiver receiver : resolvedReceivers) {
				try {
					for (String messageText : messageTexts) {
						messageService.sendText(messageText, receiver);
					}
				} catch (final Exception e) {
					actionHandler.onError(e.getMessage());
					return false;
				}
			}
			actionHandler.onCompleted();
			messageService.sendProfilePicture(resolvedReceivers);
			return true;
		}
		return false;
	}
}
