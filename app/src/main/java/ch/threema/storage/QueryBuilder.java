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

package ch.threema.storage;

import net.sqlcipher.database.SQLiteQueryBuilder;

public class QueryBuilder extends SQLiteQueryBuilder {
	private int whereCount = 0;
	@Override
	public void appendWhere(CharSequence inWhere) {
		inWhere = "(" + inWhere + ")";
		if(this.whereCount > 0) {
			//append a AND
			inWhere = " AND " + inWhere;
		}
		this.whereCount++;
		super.appendWhere(inWhere);
	}
}
