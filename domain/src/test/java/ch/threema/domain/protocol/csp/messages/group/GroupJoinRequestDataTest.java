/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.group;

import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.csp.e2e.GroupJoinRequest;

public class GroupJoinRequestDataTest {
	static final String TEST_GROUP_NAME = "GroupName";
	static final String TEST_MESSAGE = "MyMessage";
	static GroupInviteToken TEST_TOKEN_VALID;

	static {
		try {
			TEST_TOKEN_VALID = new GroupInviteToken(new byte[] { 0, 1, 2 , 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 });
		} catch (GroupInviteToken.InvalidGroupInviteTokenException e) {
			e.printStackTrace();
		}
	}

	static final GroupJoinRequest TEST_PROTOBUF_MESSAGE = GroupJoinRequest.newBuilder()
		.setToken(ByteString.copyFrom(TEST_TOKEN_VALID.get()))
		.setGroupName(TEST_GROUP_NAME)
		.setMessage(TEST_MESSAGE)
		.build();

	static void assertEqualsTestProperties(GroupJoinRequestData data) {
		Assert.assertEquals(TEST_TOKEN_VALID, data.getToken());
		Assert.assertEquals(TEST_GROUP_NAME, data.getGroupName());
		Assert.assertEquals(TEST_MESSAGE, data.getMessage());
	}

	@Test
	public void testValidData() throws BadMessageException {
		final GroupJoinRequestData data = new GroupJoinRequestData(TEST_TOKEN_VALID, TEST_GROUP_NAME, TEST_MESSAGE);
		assertEqualsTestProperties(data);
	}


	@Test
	public void testFromProtobuf() throws BadMessageException {
		final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
		final GroupJoinRequestData data = GroupJoinRequestData.fromProtobuf(rawMessage);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testToProtobufMessage() throws BadMessageException {
		final GroupJoinRequestData data = new GroupJoinRequestData(TEST_TOKEN_VALID, TEST_GROUP_NAME, TEST_MESSAGE);
		final GroupJoinRequest generatedProtobufMessage = data.toProtobufMessage();

		Assert.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
	}
}
