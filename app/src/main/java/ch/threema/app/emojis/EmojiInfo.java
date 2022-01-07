/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.emojis;

/**
 * This class contains metadata about an emoji.
 *
 * Generated by Threema emoji-tools.
 */
public class EmojiInfo {
	public final String emojiSequence;
	public final byte diversityFlag;
	public final String[] diversities;
	public final byte genderFlag;
	public final String[] genders;
	public final byte displayFlag;

	public EmojiInfo(String emojiSequence,
	                 byte diversityFlag, String[] diversities,
                     byte genderFlag, String[] genders,
                     byte displayFlag) {
		this.emojiSequence = emojiSequence;
		this.diversityFlag = diversityFlag;
		this.diversities = diversities;
		this.genderFlag = genderFlag;
		this.genders = genders;
		this.displayFlag = displayFlag;
	}
}
