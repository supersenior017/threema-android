/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.domain.protocol.api.work;

import java.util.ArrayList;
import java.util.List;

public class WorkDirectory {
	public final List<WorkDirectoryContact> workContacts = new ArrayList<>();
	public final int totalRecord;
	public final int pageSize;
	public final WorkDirectoryFilter currentFilter;

	/**
	 * Can be null
	 */
	public final WorkDirectoryFilter nextFilter;

	/**
	 * Can be null
	 */
	public final WorkDirectoryFilter previousFilter;

	public WorkDirectory(int totalRecord,
						 int pageSize,
						 WorkDirectoryFilter currentFilter,
						 WorkDirectoryFilter nextFilter,
						 WorkDirectoryFilter previousFilter) {
		this.totalRecord = totalRecord;
		this.pageSize = pageSize;
		this.nextFilter = nextFilter;
		this.previousFilter = previousFilter;
		this.currentFilter = currentFilter;
	}
}
