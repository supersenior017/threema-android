/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.services.ballot;

import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

public interface BallotMatrixService {

	interface Participant {
		boolean hasVoted();
		String getIdentity();
		int getPos();
	}

	interface Choice
	{
		BallotChoiceModel getBallotChoiceModel();
		boolean isWinner();
		int getVoteCount();
		int getPos();
	}

	interface DataKeyBuilder {
		String build(Participant p, Choice c);
	}
	Participant createParticipant(String identity);
	Choice createChoice(BallotChoiceModel choiceModel);

	BallotMatrixService addVote(BallotVoteModel ballotVoteModel) throws ThreemaException;

	BallotMatrixData finish();
}
