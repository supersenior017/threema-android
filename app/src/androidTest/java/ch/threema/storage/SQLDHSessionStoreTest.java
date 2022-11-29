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

package ch.threema.storage;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import androidx.test.core.app.ApplicationProvider;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.stores.DHSessionStoreException;

public class SQLDHSessionStoreTest {

	private static final byte[] DATABASE_KEY = "dummyKey".getBytes(StandardCharsets.UTF_8);
	private static final int NUM_RANDOM_RUNS = 20;

	private String tempDbFileName;
	private SQLDHSessionStore store;
	private DHSession initiatorDHSession;
	private DHSession responderDHSession;

	@Before
	public void setup() {
		tempDbFileName = "threema-fs-test-" + System.currentTimeMillis() + ".db";
		store = new SQLDHSessionStore(ApplicationProvider.getApplicationContext(), DATABASE_KEY, tempDbFileName);
	}

	@After
	public void tearDown() {
		store.close();
		store = null;
		ApplicationProvider.getApplicationContext().deleteDatabase(tempDbFileName);
	}

	public void createSessions() {
		// Alice is the initiator (= us)
		this.initiatorDHSession = new DHSession(
			DummyUsers.getContactForUser(DummyUsers.BOB),
			DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
		);

		// Bob gets an init message from Alice with her ephemeral public key
		this.responderDHSession = new DHSession(
			this.initiatorDHSession.getId(),
			this.initiatorDHSession.getMyEphemeralPublicKey(),
			DummyUsers.getContactForUser(DummyUsers.ALICE),
			DummyUsers.getIdentityStoreForUser(DummyUsers.BOB)
		);
	}

	@Test
	public void testStoreInitiatorSession() throws DHSessionStoreException, DHSession.MissingEphemeralPrivateKeyException {
		// Assume that we are Alice = the initiator, and Bob is the responder
		createSessions();

		// Delete any stored initiator session to start with a clean slate
		store.deleteAllDHSessions(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity());
		Assert.assertNull(this.store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()));

		// Insert an initiator DH session in 2DH mode
		Assert.assertNotNull(this.initiatorDHSession.getMyRatchet2DH());
		Assert.assertNull(this.initiatorDHSession.getMyRatchet4DH());
		store.storeDHSession(this.initiatorDHSession);

		// Retrieve the session again and ensure that the details match
		Assert.assertEquals(this.initiatorDHSession, this.store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()));

		// Turn 2DH ratchets once (need to do this here, as responder sessions are always 4DH)
		this.initiatorDHSession.getMyRatchet2DH().turn();
		store.storeDHSession(this.initiatorDHSession);
		Assert.assertEquals(this.initiatorDHSession, this.store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()));

		// Now Bob sends his ephemeral public key back to Alice
		this.initiatorDHSession.processAccept(
			this.responderDHSession.getMyEphemeralPublicKey(),
			DummyUsers.getContactForUser(DummyUsers.BOB),
			DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
		);

		// initiatorDHSession has now been upgraded to 4DH - store and retrieve it again
		Assert.assertNotNull(this.initiatorDHSession.getMyRatchet4DH());
		store.storeDHSession(this.initiatorDHSession);
		DHSession bestSession = this.store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity());
		Assert.assertNotNull(bestSession);
		Assert.assertEquals(this.initiatorDHSession, bestSession);

		// Check that the private key has been discarded
		Assert.assertNull(bestSession.getMyEphemeralPrivateKey());

		// Delete initiator DH session
		store.deleteDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), this.initiatorDHSession.getId());
		Assert.assertNull(this.store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()));
	}

	@Test
	public void testStoreResponderSession() throws DHSessionStoreException {
		// Assume that we are Bob = the responder
		createSessions();

		// Store and retrieve the responder session
		store.storeDHSession(this.responderDHSession);
		Assert.assertEquals(this.responderDHSession, this.store.getBestDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity()));

		// Turn the 4DH ratchets once, store, retrieve and compare again
		Assert.assertNotNull(this.responderDHSession.getMyRatchet4DH());
		Assert.assertNotNull(this.responderDHSession.getPeerRatchet4DH());
		this.responderDHSession.getMyRatchet4DH().turn();
		this.responderDHSession.getPeerRatchet4DH().turn();
		store.storeDHSession(this.responderDHSession);
		Assert.assertEquals(this.responderDHSession, this.store.getBestDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity()));

		// Try to retrieve a responder session with a random session ID
		Assert.assertNull(this.store.getDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), new DHSessionId()));

		// Delete DH session
		store.deleteDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), this.responderDHSession.getId());
		Assert.assertNull(this.store.getBestDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity()));
	}

	@Test
	public void testDiscardRatchet() throws DHSessionStoreException {
		// Assume that we are Bob = the responder
		createSessions();

		Assert.assertNotNull(this.responderDHSession.getPeerRatchet2DH());
		Assert.assertNotNull(this.responderDHSession.getPeerRatchet4DH());

		// Store the responder session, including the 2DH ratchet
		store.storeDHSession(this.responderDHSession);

		// There should still be a 2DH ratchet at this point
		DHSession retrievedSession = store.getDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), this.responderDHSession.getId());
		Assert.assertNotNull(retrievedSession);
		Assert.assertNotNull(retrievedSession.getPeerRatchet2DH());

		// Discard the 2DH ratchet (assume Bob has received a 4DH message from Alice)
		this.responderDHSession.discardPeerRatchet2DH();
		Assert.assertNull(this.responderDHSession.getPeerRatchet2DH());

		// Store the responder session again without the 2DH ratchet
		store.storeDHSession(this.responderDHSession);

		// Ensure that the 2DH ratchet is really gone
		retrievedSession = store.getDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), this.responderDHSession.getId());
		Assert.assertNotNull(retrievedSession);
		Assert.assertNull(retrievedSession.getPeerRatchet2DH());
	}

	@Test
	public void testRaceCondition() throws DHSession.MissingEphemeralPrivateKeyException, DHSessionStoreException {
		// Repeat the test several times, as random session IDs are involved
		for (int i = 0; i < NUM_RANDOM_RUNS; i++) {
			if (i > 0) {
				tearDown();
				setup();
			}
			testRaceConditionOnce();
		}
	}

	private void testRaceConditionOnce() throws DHSession.MissingEphemeralPrivateKeyException, DHSessionStoreException {
		createSessions();

		// Alice stores the session that she initiated (still in 2DH mode)
		store.storeDHSession(this.initiatorDHSession);

		// Pretend Bob has created a (separate) DH session before he has received the Init from Alice
		DHSession raceInitiatorDHSession = new DHSession(
			DummyUsers.getContactForUser(DummyUsers.BOB),
			DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
		);

		// Alice gets the Init for Bob's new session first and processes it
		DHSession raceResponderDHSession = new DHSession(
			raceInitiatorDHSession.getId(),
			raceInitiatorDHSession.getMyEphemeralPublicKey(),
			DummyUsers.getContactForUser(DummyUsers.BOB),
			DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
		);

		store.storeDHSession(raceResponderDHSession);

		// Alice then processes the Accept from Bob and stores the session
		this.initiatorDHSession.processAccept(
			this.responderDHSession.getMyEphemeralPublicKey(),
			DummyUsers.getContactForUser(DummyUsers.BOB),
			DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
		);

		store.storeDHSession(this.initiatorDHSession);

		// At this point, there should be only one DH session with Bob from Alice's point of view,
		// and it should be the one with the lower session ID
		DHSessionId lowestSessionId;
		if (raceResponderDHSession.getId().compareTo(this.initiatorDHSession.getId()) < 0) {
			lowestSessionId = raceResponderDHSession.getId();
		} else {
			lowestSessionId = this.initiatorDHSession.getId();
		}
		DHSession bestSession = store.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity());
		Assert.assertNotNull(bestSession);
		Assert.assertEquals(lowestSessionId, bestSession.getId());
	}
}
