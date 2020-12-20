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

import ch.threema.app.R;
import ch.threema.app.activities.GroupAddActivity;
import ch.threema.app.adapters.GroupListAdapter;
import ch.threema.app.services.GroupService;
import ch.threema.storage.models.GroupModel;

public class GroupListFragment extends RecipientListFragment {
	@Override
	protected boolean isMultiSelectAllowed() {
		return multiSelect;
	}

	@Override
	protected String getBundleName() {
		return "GroupListState";
	}

	@Override
	protected int getEmptyText() {
		return R.string.no_matching_groups;
	}

	@Override
	protected int getAddIcon() {
		return R.drawable.ic_group_outline;
	}

	@Override
	protected int getAddText() {
		return R.string.title_addgroup;
	}

	@Override
	protected Intent getAddIntent() {
		return new Intent(getActivity(), GroupAddActivity.class);
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
		new AsyncTask<Void, Void, List<GroupModel>>() {
			@Override
			protected List<GroupModel> doInBackground(Void... voids) {
				return groupService.getAll(new GroupService.GroupFilter() {
					@Override
					public boolean sortingByDate() {
						return false;
					}

					@Override
					public boolean sortingByName() {
						return true;
					}

					@Override
					public boolean sortingAscending() {
						return true;
					}

					@Override
					public boolean withDeleted() {
						return false;
					}

					@Override
					public boolean withDeserted() { return false; }

				});
			}

			@Override
			protected void onPostExecute(List<GroupModel> groupModels) {
				adapter = new GroupListAdapter(
					activity,
					groupModels,
					checkedItemPositions,
					groupService
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
