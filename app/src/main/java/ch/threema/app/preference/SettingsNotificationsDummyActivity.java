/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.preference;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.ThreemaApplication;

// Frontend to call the app's notifications settings directly from notification or system settings
public class SettingsNotificationsDummyActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (ThreemaApplication.getServiceManager() != null && ThreemaApplication.getServiceManager().getUserService() != null && ThreemaApplication.getServiceManager().getUserService().getIdentity() != null) {
			Intent intent = new Intent(this, SettingsActivity.class);
			intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsNotificationsFragment.class.getName());
			intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
			startActivity(intent);
		}
		finish();
	}
}
