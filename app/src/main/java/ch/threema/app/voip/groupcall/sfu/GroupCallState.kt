/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.sfu.messages.createRandomPadding
import ch.threema.protobuf.groupcall.CallState

data class GroupCallState(
    val createdBy: ParticipantId,
    val createdAt: ULong,
    val participants: Set<ParticipantDescription>
) {
    companion object {
        fun fromProtobufBytes(bytes: ByteArray): GroupCallState {
            val state = CallState.parseFrom(bytes)
            val participants = state.participantsMap.values
                .map(GroupCallState::mapParticipant)
                .toSet()
            return GroupCallState(
                ParticipantId(state.stateCreatedBy.toUInt()),
                state.stateCreatedAt.toULong(),
                participants
            )
        }

        private fun mapParticipant(participant: CallState.Participant): ParticipantDescription {
            return when {
                participant.hasThreema() -> mapNormalParticipant(participant)
                participant.hasGuest() -> mapGuestParticipant(participant)
                else -> throw GroupCallException("Cannot map state participant")
            }
        }

        private fun mapNormalParticipant(participant: CallState.Participant): NormalParticipantDescription {
            return SimpleNormalParticipantDescription(
                ParticipantId(participant.participantId.toUInt()),
                participant.threema.identity,
                participant.threema.nickname
            )
        }

        private fun mapGuestParticipant(participant: CallState.Participant): GuestParticipantDescription {
            return SimpleGuestParticipantDescription(
                ParticipantId(participant.participantId.toUInt()),
                participant.guest.name
            )
        }
    }

    fun toProtobuf(): CallState {
        val builder = CallState.newBuilder()
            .setPadding(createRandomPadding())
            .setStateCreatedAt(createdAt.toLong())
            .setStateCreatedBy(createdBy.id.toInt())

        participants.forEach {
            val participant = CallState.Participant.newBuilder()
                .setParticipantId(it.id.id.toInt())

            if (it is NormalParticipantDescription) {
                val threema = CallState.Participant.Normal.newBuilder()
                    .setIdentity(it.identity)
                    .setNickname(it.nickname)
                    .build()
                participant.threema = threema
            } else if (it is GuestParticipantDescription) {
                val guest = CallState.Participant.Guest.newBuilder()
                    .setName(it.name)
                    .build()
                participant.guest = guest
            }
            builder.putParticipants(it.id.id.toInt(), participant.build())
        }
        return builder.build()
    }

    override fun toString(): String {
        return "GroupCallState(createdBy=$createdBy, createdAt=$createdAt, participants(${participants.size})=${participants.map { it.id }})"
    }
}
