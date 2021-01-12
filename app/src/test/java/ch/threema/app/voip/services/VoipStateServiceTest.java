/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.voip.services;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.voip.listeners.VoipMessageListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.base.ThreemaException;
import ch.threema.client.MessageQueue;
import ch.threema.client.voip.VoipCallAnswerData;
import ch.threema.client.voip.VoipCallAnswerMessage;
import ch.threema.client.voip.VoipCallHangupData;
import ch.threema.client.voip.VoipCallHangupMessage;
import ch.threema.client.voip.VoipCallOfferData;
import ch.threema.client.voip.VoipCallOfferMessage;
import ch.threema.storage.models.ContactModel;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LogUtil.class, SystemClock.class})
@SuppressWarnings("FieldCanBeLocal")
public class VoipStateServiceTest {
	// Mocks
	private Context mockContext;
	private NotificationManager mockNotificationManager;
	private AudioManager mockAudioManager;
	private ContactService mockContactService;
	private MessageService mockMessageService;
	private RingtoneService mockRingtoneService;
	private PreferenceService mockPreferenceService;
	private MessageQueue mockMessageQueue;
	private LifetimeService mockLifetimeService;

	// Service
	private VoipStateService service;

	@Before
	public void setUp() {
		// Mock context
		this.mockContext = PowerMockito.mock(Context.class);
		this.mockNotificationManager = PowerMockito.mock(NotificationManager.class);
		this.mockAudioManager = PowerMockito.mock(AudioManager.class);
		when(this.mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
				.thenReturn(mockNotificationManager);
		when(this.mockContext.getSystemService(Context.AUDIO_SERVICE))
				.thenReturn(mockAudioManager);

		// Mock services
		this.mockContactService = PowerMockito.mock(ContactService.class);
		this.mockMessageService = PowerMockito.mock(MessageService.class);
		this.mockRingtoneService = PowerMockito.mock(RingtoneService.class);
		this.mockPreferenceService = PowerMockito.mock(PreferenceService.class);
		this.mockLifetimeService = PowerMockito.mock(LifetimeService.class);

		// Set up return values for contact service
		when(this.mockContactService.getByIdentity("INVALID")).thenReturn(null);
		when(this.mockContactService.getByIdentity("AAAAAAAA")).thenReturn(new ContactModel("AAAAAAAA", new byte[] { 1, 2, 3 }));
		when(this.mockContactService.getByIdentity("BBBBBBBB")).thenReturn(new ContactModel("BBBBBBBB", new byte[] { 2, 3, 4 }));

		// Set up return values for preference service
		when(this.mockPreferenceService.isVoipEnabled()).thenReturn(true);

		// Mock message queue
		this.mockMessageQueue = PowerMockito.mock(MessageQueue.class);

		// Static mocks
		mockStatic(LogUtil.class);
		mockStatic(SystemClock.class);

		// Instantiate service
		this.service = new VoipStateService(
				this.mockContactService,
				this.mockRingtoneService,
				this.mockPreferenceService,
				this.mockMessageService,
				this.mockMessageQueue,
				this.mockLifetimeService,
				this.mockContext
		);
	}

	@Test
	public void callCounterIncrement() {
		// Initially at 0
		assertEquals(0, service.getCallState().getIncomingCallCounter());

		// Increment when ringing
		service.setStateRinging(1);
		assertEquals(1, service.getCallState().getIncomingCallCounter());

		// Don't increment when state didn't change
		service.setStateRinging(1);
		assertEquals(1, service.getCallState().getIncomingCallCounter());

		// Increment again when state changed
		service.setStateIdle();
		assertEquals(1, service.getCallState().getIncomingCallCounter());
		service.setStateRinging(1);
		assertEquals(2, service.getCallState().getIncomingCallCounter());
	}

	/**
	 * Test the entire state lifecycle.
	 */
	@Test
	public void callStateSetting() {
		service.setStateIdle();
		assertTrue(service.getCallState().isIdle());

		service.setStateRinging(1);
		assertTrue(service.getCallState().isRinging());

		service.setStateInitializing(1);
		assertTrue(service.getCallState().isInitializing());

		service.setStateCalling(1);
		assertTrue(service.getCallState().isCalling());

		service.setStateDisconnecting(1);
		assertTrue(service.getCallState().isDisconnecting());

		service.setStateIdle();
		assertTrue(service.getCallState().isIdle());
	}

	@Test
	public void callDuration() {
		// Initially null
		assertNull(service.getCallDuration());

		// Stays null until call is started
		service.setStateRinging(1);
		service.setStateInitializing(1);
		assertNull(service.getCallDuration());

		// Counts up from 0
		when(SystemClock.elapsedRealtime()).thenReturn(1000L);
		service.setStateCalling(1);
		assertEquals(Integer.valueOf(0), service.getCallDuration());
		when(SystemClock.elapsedRealtime()).thenReturn(3100L);
		assertEquals(Integer.valueOf(2), service.getCallDuration());
		when(SystemClock.elapsedRealtime()).thenReturn(13100L);
		assertEquals(Integer.valueOf(12), service.getCallDuration());

		// Resets on disconnect
		service.setStateDisconnecting(1);
		assertNull(service.getCallDuration());
		when(SystemClock.elapsedRealtime()).thenReturn(15000L);
		service.setStateCalling(1);
		assertEquals(Integer.valueOf(0), service.getCallDuration());
	}

	@Test
	@SuppressWarnings("ConstantConditions")
	public void initiatorFlag() {
		// Flag is null initially
		assertNull(service.isInitiator());

		// It can be set by external code
		service.setInitiator(true);
		assertTrue(service.isInitiator());

		// When the state machine goes back to idle, the initiator flag must be reset
		service.setStateRinging(1);
		assertTrue(service.isInitiator());
		service.setStateIdle();
		assertNull(service.isInitiator());
	}

	/**
	 * Offers with no data are ignored and do not change the state.
	 */
	@Test
	public void handleOfferNullData() {
		// Offer message with no data
		final VoipCallOfferMessage msg = new VoipCallOfferMessage();
		msg.setFromIdentity("AAAAAAAA");
		msg.setToIdentity("BBBBBBBB");

		// Handling should not change the state
		assertTrue(service.getCallState().isIdle());
		assertTrue(service.handleCallOffer(msg));
		assertTrue(service.getCallState().isIdle());
	}

	/**
	 * Offers with no data are ignored and do not change the state.
	 */
	@Test
	public void handleOfferInvalidContact() {
		// Offer message with no data
		final VoipCallOfferMessage msg = this.createOfferMessage();
		msg.setFromIdentity("INVALID");

		// Handling should not change the state
		assertTrue(service.getCallState().isIdle());
		assertTrue(service.handleCallOffer(msg));
		assertTrue(service.getCallState().isIdle());
	}

	/**
	 * If a call is already active, an offer will be rejected.
	 */
	@Test
	public void handleOfferBusy() throws ThreemaException {
		final VoipCallOfferMessage offer = this.createOfferMessage();

		final String[] states = new String[] { "ringing", "initializing", "calling", "disconnecting" };
		final long callId = 1;
		for (String state : states) {
			final ArgumentCaptor<Byte> reasonCaptor = ArgumentCaptor.forClass(Byte.class);

			// Partially mock service
			VoipStateService serviceSpy = spy(service);

			// Handle offer in non-idle state
			switch (state) {
				case "ringing":
					serviceSpy.setStateRinging(callId);
					break;
				case "initializing":
					serviceSpy.setStateInitializing(callId);
					break;
				case "calling":
					serviceSpy.setStateCalling(callId);
					break;
				case "disconnecting":
					serviceSpy.setStateDisconnecting(callId);
					break;
				default:
					// Not supported
					fail("Unsupported state: " + state);
			}

			assertTrue(serviceSpy.handleCallOffer(offer));

			// Capture reject reason
			verify(serviceSpy, times(1))
				.sendRejectCallAnswerMessage(
					any(ContactModel.class),
					anyLong(),
					reasonCaptor.capture(),
					anyBoolean()
				);
			assertEquals(
				Byte.valueOf(VoipCallAnswerData.RejectReason.BUSY),
				reasonCaptor.getValue()
			);
		}
	}

	/**
	 * Create an offer message.
	 */
	private VoipCallOfferMessage createOfferMessage(@Nullable Long callId) {
		final VoipCallOfferMessage msg = new VoipCallOfferMessage();
		msg.setFromIdentity("AAAAAAAA");
		msg.setToIdentity("BBBBBBBB");
		final VoipCallOfferData msgData = new VoipCallOfferData();
		final VoipCallOfferData.OfferData data = new VoipCallOfferData.OfferData()
			.setSdpType("offer")
			.setSdp("mocked");
		msgData.setOfferData(data);
		if (callId != null) {
			msgData.setCallId(callId);
		}
		msg.setData(msgData);
		return msg;
	}

	private VoipCallOfferMessage createOfferMessage() {
		return createOfferMessage(null);
	}

	/**
	 * Reject a call while another call is active.
	 *
	 * The call ID in the reject message should correspond to the incoming call,
	 * not to the current call.
	 */
	@Test
	public void rejectCallWhileCalling() throws ThreemaException {
		long currentCallId = 1;
		long interruptingCallId = 2;

		// Initially, no call ID
		assertEquals(0L, service.getCallState().getCallId());

		// Start call
		service.setStateInitializing(currentCallId);
		assertEquals(currentCallId, service.getCallState().getCallId());
		service.setStateCalling(currentCallId);
		assertEquals(currentCallId, service.getCallState().getCallId());

		// Partially mock service
		final VoipStateService serviceSpy = spy(this.service);

		// Incoming offer
		final VoipCallOfferMessage msg = this.createOfferMessage(interruptingCallId);
		assertTrue(serviceSpy.handleCallOffer(msg));

		// Capture reject call ID
		final ArgumentCaptor<Long> callIdCaptor = ArgumentCaptor.forClass(Long.class);
		verify(
			serviceSpy,
			times(1))
			.sendRejectCallAnswerMessage(
				any(ContactModel.class),
				callIdCaptor.capture(),
				anyByte(),
				anyBoolean()
			);
		assertEquals((Long)interruptingCallId, callIdCaptor.getValue());
	}

	/**
	 * The call ID in an answer message should be validated.
	 */
	@Test
	public void validateCallIdAnswer() {
		// Start outgoing call
		service.setInitiator(true);

		// Detect message handling
		final AtomicBoolean answerHandled = new AtomicBoolean(false);
		VoipListenerManager.messageListener.clear();
		VoipListenerManager.messageListener.add(new VoipMessageListener() {
			@Override
			public void onAnswer(String identity, VoipCallAnswerData data) {
				answerHandled.set(true);
			}

			@Override
			public boolean handle(String identity) {
				return true;
			}
		});

		// Create answer
		final VoipCallAnswerMessage answer = new VoipCallAnswerMessage();
		answer.setFromIdentity("AAAAAAAA");
		answer.setToIdentity("BBBBBBBB");
		final VoipCallAnswerData msgData = new VoipCallAnswerData();
		msgData.setAction(VoipCallAnswerData.Action.REJECT);
		msgData.setRejectReason(VoipCallAnswerData.RejectReason.UNKNOWN);

		// Process answer with valid call ID
		service.setStateInitializing(1);
		msgData.setCallId(1);
		answer.setData(msgData);
		answerHandled.set(false);
		service.handleCallAnswer(answer);
		assertTrue("Answer should have been handled", answerHandled.get());

		// Process answer with invalid call ID
		service.setStateInitializing(1);
		msgData.setCallId(2);
		answer.setData(msgData);
		answerHandled.set(false);
		service.handleCallAnswer(answer);
		assertFalse("Answer should not have been handled", answerHandled.get());

		// Process answer with missing call ID
		service.setStateInitializing(1);
		msgData.setCallId(0);
		answer.setData(msgData);
		answerHandled.set(false);
		service.handleCallAnswer(answer);
		assertTrue("Answer should have been handled", answerHandled.get());
	}

	/**
	 * The call ID in a hangup message should be validated.
	 */
	@Test
	public void validateCallIdHangup() {
		// Mock call notifications
		VoipStateService service = spy(this.service);
		doNothing().when(service).cancelCallNotification(anyString());

		// Create hangup
		final VoipCallHangupMessage msg = new VoipCallHangupMessage();
		msg.setFromIdentity("AAAAAAAA");
		msg.setToIdentity("BBBBBBBB");

		// Process hangup with valid call ID
		service.setStateInitializing(1);
		service.setStateCalling(1);
		msg.setData(new VoipCallHangupData().setCallId(1));
		service.handleRemoteCallHangup(msg);
		assertTrue("Hangup should have been handled", service.getCallState().isIdle());

		// Process hangup with invalid call ID
		service.setStateInitializing(1);
		service.setStateCalling(1);
		msg.setData(new VoipCallHangupData().setCallId(2));
		service.handleRemoteCallHangup(msg);
		assertTrue("Hangup should not have been handled", service.getCallState().isCalling());

		// Process hangup with missing call ID
		service.setStateInitializing(1);
		service.setStateCalling(1);
		// As callee
		service.setInitiator(false);
		msg.setData(new VoipCallHangupData());
		service.handleRemoteCallHangup(msg);
		assertTrue("Hangup should not have been handled", service.getCallState().isCalling());
		// As caller
		service.setInitiator(true);
		msg.setData(new VoipCallHangupData());
		service.handleRemoteCallHangup(msg);
		assertTrue("Hangup should have been handled", service.getCallState().isIdle());
	}
}
