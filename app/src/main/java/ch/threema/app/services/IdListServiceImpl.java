/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.services;

import android.content.Context;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.storage.models.ContactModel;

public class IdListServiceImpl implements IdListService {
	private final Object lock = new Object();
	private String[] ids;
	private final String uniqueListName;
	private final PreferenceService preferenceService;

	public IdListServiceImpl(String uniqueListName, PreferenceService preferenceService) {
		this.uniqueListName = uniqueListName;
		this.preferenceService = preferenceService;
		this.ids = preferenceService.getList(this.uniqueListName);
	}

	@Override
	public boolean has(String id) {
		if(this.ids != null) {
			synchronized (this.lock) {
				return Arrays.asList(this.ids).contains(id);
			}
		}
		return false;
	}

	@Override
	public void remove(String id) {
		if(this.ids != null) {
			synchronized (this.lock) {
				List<String> idList = Arrays.asList(this.ids);
				if(idList.contains(id)) {
					String[] newIdentities = new String[idList.size()-1];
					int pos = 0;
					for(String other: idList) {
						if(other != null && !other.equals(id)) {
							newIdentities[pos++] = other;
						}
					}
					this.preferenceService.setList(this.uniqueListName, newIdentities);
					this.ids = newIdentities;
				}
			}
		}
	}

	@Override
	public void add(String id) {
		if(this.ids != null && (id != null && id.length() > 0)) {
			synchronized (this.lock) {
				List<String> idList = Arrays.asList(this.ids);
				if(!idList.contains(id)) {
					this.ids = Arrays.copyOf(this.ids, this.ids.length + 1);
					this.ids[ids.length-1] = id;
					this.preferenceService.setList(this.uniqueListName, ids);
				}
			}
		}
	}

	@Override
	public void toggle(Context context, final ContactModel contactModel) {
		String id = contactModel.getIdentity();

		if (this.has(id)) {
			this.remove(id);
			Toast.makeText(context, context.getString(R.string.contact_now_unblocked), Toast.LENGTH_SHORT).show();
			ListenerManager.contactListeners.handle(new ListenerManager.HandleListener<ContactListener>() {
				@Override
				public void handle(ContactListener listener) {
					listener.onModified(contactModel);
				}
			});
		} else {
			IdListServiceImpl.this.add(contactModel.getIdentity());
			Toast.makeText(context, context.getString(R.string.contact_now_blocked), Toast.LENGTH_SHORT).show();
			ListenerManager.contactListeners.handle(new ListenerManager.HandleListener<ContactListener>() {
				@Override
				public void handle(ContactListener listener) {
					listener.onModified(contactModel);
				}
			});
		}
	}

	@Override
	public synchronized String[] getAll() {
		return this.ids;
	}

	@Override
	public void addAll(String[] ids) {
		this.ids = ids;
		this.preferenceService.setList(this.uniqueListName, this.ids);
	}

	@Override
	public void removeAll() {
		this.ids = new String[0];
		this.preferenceService.setList(this.uniqueListName, this.ids);
	}
}
