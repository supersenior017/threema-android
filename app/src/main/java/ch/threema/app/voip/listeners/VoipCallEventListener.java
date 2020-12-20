/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

package ch.threema.app.voip.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * Events that happen before, during and after a call
 * (e.g. a rejected call or a missed call).
 */
public interface VoipCallEventListener {

	/**
	 * @param peerIdentity
	 */
	@AnyThread void onRinging(String peerIdentity);

	/**
	 * A call was successfully started (meaning that it was accepted and that the connection has
	 * been established successfully).
	 *
	 * @param peerIdentity The identity of the peer.
	 * @param outgoing Whether this is an outgoing call (initiated by us).
	 */
	@AnyThread void onStarted(String peerIdentity, boolean outgoing);

	/**
	 * A call was finished.
	 *
	 * @param peerIdentity The identity of the peer.
	 * @param outgoing Whether this is an outgoing call (initiated by us).
	 * @param duration The duration of the call in seconds.
	 */
	@AnyThread void onFinished(@NonNull String peerIdentity, boolean outgoing, int duration);

	/**
	 * A call was rejected.
	 *
	 * @param peerIdentity The identity of the peer.
	 * @param outgoing Whether the rejected call was an outgoing call (initiated by us).
	 * @param reason The reject reason. The meaning can be determined using the
	 *     `VoipCallAnswerData.RejectReason` class.
	 */
	@AnyThread void onRejected(String peerIdentity, boolean outgoing, byte reason);

	/**
	 * An incoming call was missed or failed to be established.
	 *
	 * @param peerIdentity The identity of the peer.
	 * @param accepted Whether the call was accepted by the user or not.
	 */
	@AnyThread void onMissed(String peerIdentity, boolean accepted);

	/**
	 * An outgoing call was aborted or failed to be established.
	 *
	 * @param peerIdentity The identity of the peer.
	 */
	@AnyThread void onAborted(String peerIdentity);
}
