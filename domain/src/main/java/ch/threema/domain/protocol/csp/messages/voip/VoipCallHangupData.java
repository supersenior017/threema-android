/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.voip;

import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.base.utils.JSONUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VoipCallHangupData extends VoipCallData<VoipCallHangupData> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipCallHangupData");

	//region Serialization

	public static @NonNull VoipCallHangupData parse(@NonNull String jsonObjectString) throws BadMessageException {
		final JSONObject o;
		if (jsonObjectString.trim().isEmpty()) {
			// Historically, hangup messages may be empty
			o = new JSONObject();
		} else {
			try {
				o = new JSONObject(jsonObjectString);
			} catch (JSONException e) {
				logger.error("Bad VoipCallHangupData: Invalid JSON string", e);
				throw new BadMessageException("TM063", true);
			}
		}

		final VoipCallHangupData callHangupData = new VoipCallHangupData();

		try {
			final Long callId = JSONUtil.getLongOrThrow(o, KEY_CALL_ID);
			if (callId != null) {
				callHangupData.setCallId(callId);
			}
		} catch (Exception e) {
			logger.error("Bad VoipCallHangupData: Invalid Call ID", e);
			throw new BadMessageException("TM063", true);
		}

		return callHangupData;
	}

	public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(UTF_8));
	}

	private @NonNull String generateString() {
		final JSONObject o = this.buildJsonObject();
		return o.toString();
	}

	//endregion
}
