/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

package ch.threema.app.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.adapters.UserListAdapter;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.storage.models.ContactModel;

public class UserListFragment extends RecipientListFragment {
	@Override
	protected boolean isMultiSelectAllowed() {
		return multiSelect;
	}

	@Override
	protected String getBundleName() {
		return "UserListState";
	}

	@Override
	protected int getEmptyText() {
		return R.string.no_matching_contacts;
	}

	@Override
	protected int getAddIcon() {
		return R.drawable.ic_person_add_outline;
	}

	@Override
	protected int getAddText() {
		return R.string.menu_add_contact;
	}

	@Override
	protected Intent getAddIntent() {
		Intent intent = new Intent(getActivity(), AddContactActivity.class);
		intent.putExtra(AddContactActivity.EXTRA_ADD_BY_ID, true);

		return intent;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
		new AsyncTask<Void, Void, List<ContactModel>>() {
			@Override
			protected List<ContactModel> doInBackground(Void... voids) {
				if (ConfigUtils.isWorkBuild()) {
					return Functional.filter(contactService.getAll(false, false), new IPredicateNonNull<ContactModel>() {
						@Override
						public boolean apply(@NonNull ContactModel type) {
							return !type.isWork();
						}
					});
				} else {
					return contactService.getAll(false, false);
				}
			}

			@Override
			protected void onPostExecute(List<ContactModel> contactModels) {
				adapter = new UserListAdapter(
					activity,
					contactModels,
					null,
					checkedItemPositions,
					contactService,
					blacklistService,
					hiddenChatsListService
				);
				setListAdapter(adapter);
				if (listInstanceState != null) {
					if (isAdded() && getView() != null && getActivity() != null) {
						getListView().onRestoreInstanceState(listInstanceState);
					}
					listInstanceState = null;
					restoreCheckedItems(checkedItemPositions);
				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
