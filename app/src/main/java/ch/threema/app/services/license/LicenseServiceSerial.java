/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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

package ch.threema.app.services.license;

import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.domain.protocol.api.APIConnector;

public class LicenseServiceSerial extends LicenseServiceThreema<SerialCredentials> {

	public LicenseServiceSerial(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
		super(apiConnector, preferenceService, deviceId);
	}

	@Override
	public boolean hasCredentials() {
		return !TestUtil.empty(this.preferenceService.getSerialNumber());
	}

	@Override
	protected  APIConnector.CheckLicenseResult checkLicense(SerialCredentials credentials, String deviceId) throws Exception {
		return this.apiConnector.checkLicense(credentials.licenseKey, deviceId);
	}

	@Override
	protected void saveCredentials(SerialCredentials credentials) {
		this.preferenceService.setSerialNumber(credentials.licenseKey);
	}

	@Override
	public SerialCredentials loadCredentials() {
		String licenseKey = this.preferenceService.getSerialNumber();

		if(!TestUtil.empty(licenseKey)) {
			return new SerialCredentials(licenseKey);
		}
		return null;
	}
}
