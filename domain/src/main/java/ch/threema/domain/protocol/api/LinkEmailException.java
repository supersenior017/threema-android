/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.domain.protocol.api;

import ch.threema.base.ThreemaException;

/**
 * Exception that may get thrown if there is an error while linking an e-mail address to an identity.
 * The message is intended to be displayed to the user and is usually already localized.
 */
public class LinkEmailException extends ThreemaException {

	public LinkEmailException(String msg) {
		super(msg);
	}

	public LinkEmailException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
