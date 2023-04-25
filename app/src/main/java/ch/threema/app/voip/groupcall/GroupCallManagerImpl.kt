/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.*
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.service.GroupCallService
import ch.threema.app.voip.groupcall.service.GroupCallServiceConnection
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.base.utils.Base64
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.api.SfuToken
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData.Companion.GCK_LENGTH
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.data.status.GroupCallStatusDataModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.*
import kotlin.math.max

private val logger = LoggingUtil.getThreemaLogger("GroupCallManagerImpl")

@WorkerThread
class GroupCallManagerImpl(
	private val context: Context,
	private val databaseService: DatabaseServiceNew,
	private val groupService: GroupService,
	private val contactService: ContactService,
	private val preferenceService: PreferenceService,
	private val messageService: MessageService,
	private val groupMessagingService: GroupMessagingService,
	private val notificationService: NotificationService,
	private val sfuConnection: SfuConnection
) : GroupCallManager {
	private companion object {
		private const val ARTIFICIAL_GC_CREATE_WAIT_PERIOD_MILLIS: Long = 2000L
	}

	private val groupCallStartQueue: MutableSharedFlow<GroupCallStartMessage> = MutableSharedFlow<GroupCallStartMessage>(
		// Reasonably high buffer capacity because messages might be dropped otherwise.
		// Normally this should only be relevant when someone was offline for some time
		// and lots of group calls have been carried out in the meantime.
		extraBufferCapacity = 256,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	).apply {
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			collect { processGroupCallStart(it) }
		}
	}

	private val generalCallObservers: MutableSet<GroupCallObserver> = Collections.synchronizedSet(mutableSetOf())
	private val callObservers: MutableMap<LocalGroupId, MutableSet<GroupCallObserver>> = mutableMapOf()
	private val callRefreshTimers: MutableMap<LocalGroupId, Job> = Collections.synchronizedMap(mutableMapOf())

	// TODO(ANDR-1957): Unsure if this is guarded properly for use outside of the GC thread. There
	//  is synchronization but it's not used consistently.
	private val runningCalls: MutableMap<CallId, GroupCallDescription>
	private val chosenCalls: MutableMap<LocalGroupId, GroupCallDescription> = mutableMapOf()

	private val peekFailedCounters: PeekFailedCounter = PeekFailedCounter()

	private var serviceConnection = GroupCallServiceConnection()

	/*
	Since the GroupCallManager is a dependency of the MessageProcessor which will be started upon
	app start, this will trigger the group call refresh steps for all considered running calls on
	app start.
	 */
	init {
		runningCalls = loadPersistedRunningCalls()
		runningCalls.values
			.map { it.groupId }
			.forEach { triggerGroupCallRefreshSteps(it) }
	}

	// TODO(ANDR-1959): Test
	@WorkerThread
	override fun handleControlMessage(message: GroupCallControlMessage): Boolean {
		logger.debug("Handle control message")

		return when (message) {
			is GroupCallStartMessage -> handleGroupCallStart(message)
			else -> handleUnknownMessageType(message)
		}
	}

	@WorkerThread
	override suspend fun getAudioManager(): CallAudioManager {
		GroupCallThreadUtil.assertDispatcherThread()

		return serviceConnection.getCallAudioManager()
	}

	@AnyThread
	override fun addGroupCallObserver(group: GroupModel, observer: GroupCallObserver) {
		addGroupCallObserver(group.localGroupId, observer)
	}

	@AnyThread
	override fun addGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver) {
		synchronized(callObservers) {
			if (groupId !in callObservers) {
				callObservers[groupId] = Collections.synchronizedSet(mutableSetOf())
			}
		}
		if (callObservers[groupId]?.add(observer) == true) {
			observer.onGroupCallUpdate(chosenCalls[groupId])
		}
	}

	@AnyThread
	override fun removeGroupCallObserver(group: GroupModel, observer: GroupCallObserver) {
		removeGroupCallObserver(group.localGroupId, observer)
	}

	@AnyThread
	override fun removeGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver) {
		synchronized(callObservers) {
			callObservers[groupId]?.remove(observer)
		}
	}

	override fun addGeneralGroupCallObserver(observer: GroupCallObserver) {
		generalCallObservers.add(observer)
		observer.onGroupCallUpdate(serviceConnection.getCurrentGroupCallController()?.description)
	}

	override fun removeGeneralGroupCallObserver(observer: GroupCallObserver) {
		generalCallObservers.remove(observer)
	}

	@WorkerThread
	override suspend fun joinCall(group: GroupModel): GroupCallController? {
		GroupCallThreadUtil.assertDispatcherThread()

		val groupId = group.localGroupId

		logger.debug("Try joining call for group {}", groupId)

		val controller = getGroupCallControllerForJoinedCall(groupId)
		return if (controller != null) {
			logger.info("Call already joined")
			controller
		} else {
			getChosenCall(groupId)?.let {
				logger.info("Join existing call with id {}", it.callId)
				joinAndConfirmCall(it, groupId)
			}
		}?.also { notifyJoinedAndLeftCall(it) }
	}

	override suspend fun createCall(group: GroupModel): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		return joinCall(group) ?: run {
			// there is no group call considered running for this group. Start it!
			logger.info("Create new group call")
			createNewCall(group)
		}.also { notifyJoinedAndLeftCall(it) }
	}

	private fun notifyJoinedAndLeftCall(call: GroupCallController) {
		notifyCallObservers(call.description.groupId, call.description)
		notifyGeneralCallObservers(call.description)
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			try {
				call.callLeftSignal.await()
			} catch (e: Exception) {
				// noop
			}
			notifyGeneralCallObservers(null)
		}
	}

	@WorkerThread
	private suspend fun joinAndConfirmCall(call: GroupCallDescription, groupId: LocalGroupId): GroupCallController {
		logger.info("Join existing group call")
		GroupCallThreadUtil.assertDispatcherThread()

		return joinCall(call).also {
			// wait until the call is 'CONNECTED'
			it.connectedSignal.await()
			// always confirm the call when joining and not creating a call
			it.confirmCall()
			// If two participants almost simultaneously start a group call, only one group
			// call will be created. The other participant will join the chosen call while
			// aborting the creation of its own group call. This can lead to a race where
			// the group call start notification is shown even though the join process has
			// already started. For this case, we cancel the notification here.
			notificationService.cancelGroupCallNotification(groupId.id)
		}
	}

	@WorkerThread
	override fun abortCurrentCall() {
		logger.warn("Aborting current call")
		val controller = serviceConnection.getCurrentGroupCallController()
		if (controller != null) {
			controller.leave()
		} else {
			context.stopService(GroupCallService.getStopIntent(context))
		}
	}

	@WorkerThread
	override fun leaveCall(call: GroupCallDescription): Boolean {
		serviceConnection.getCurrentGroupCallController()?.let {
			if (it.callId == call.callId) {
				it.leave()
				return true
			}
		}
		return false
	}

	@AnyThread
	override fun isJoinedCall(call: GroupCallDescription): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.callId == call.callId
	}

	@AnyThread
	override fun hasJoinedCall(groupId: LocalGroupId): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.description?.groupId == groupId
	}

	@AnyThread
	override fun hasJoinedCall(): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.callId != null
	}

	@AnyThread
	override fun getCurrentChosenCall(groupModel: GroupModel): GroupCallDescription? {
		return chosenCalls[groupModel.localGroupId]
	}

	override fun getCurrentGroupCallController(): GroupCallController? {
		return serviceConnection.getCurrentGroupCallController()
	}

	@AnyThread
	override fun sendGroupCallStartToNewMembers(groupModel: GroupModel, newMembers: List<String>) {
		if (newMembers.isEmpty()) {
			return
		}
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			val groupCallDescription = getChosenCall(groupModel.localGroupId) ?: return@launch
			val startData = try {
				GroupCallStartData(
					ProtocolDefines.GC_PROTOCOL_VERSION.toUInt(),
					groupCallDescription.gck,
					groupCallDescription.sfuBaseUrl
				)
			} catch (e: IllegalArgumentException) {
				logger.warn("Could not create call start data", e)
				null
			}
			if (startData == null) {
				logger.warn("Could not send group call start to new members")
			} else {
				sendGroupCallStartMessage(groupModel, startData, groupCallDescription.getStartedAtDate(), newMembers.toTypedArray())
			}
		}
	}

	override fun updateAllowedCallParticipants(groupModel: GroupModel) {
		logger.debug("Update allowed call participants")
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			getGroupCallControllerForJoinedCall(groupModel.localGroupId)?.let {
				val members = groupService.getGroupIdentities(groupModel).toSet()
				it.purgeCallParticipants(members)
			}
			if (!groupService.isGroupMember(groupModel)) {
				val groupId = groupModel.localGroupId
				val callIds = getRunningCalls(groupId)
					.map { it.callId }
					.toSet()
				removeRunningCalls(callIds)
				chosenCalls.remove(groupId)
				purgeCallRefreshTimers(groupId)
			}
		}
	}

	@WorkerThread
	private suspend fun joinCall(callDescription: GroupCallDescription): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		val intent = GroupCallService.getStartIntent(context, callDescription.sfuBaseUrl, callDescription.callId, callDescription.groupId)
		logger.debug("Start Group Call Foreground Service")
		ContextCompat.startForegroundService(context, intent)
		context.bindService(intent, serviceConnection, 0)
		val controller = serviceConnection.getGroupCallController()
		logger.trace("Got controller")
		controller.description = callDescription
		attachCallDisposedHandler(controller)
		return controller
	}

	@WorkerThread
	private fun attachCallDisposedHandler(controller: GroupCallController) {
		GroupCallThreadUtil.assertDispatcherThread()

		val callId = controller.callId
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			try {
				controller.callDisposedSignal.await()
				logger.info("Call has been disposed")
			} catch (e: Exception) {
				logger.error("Call ended exceptionally", e)
			}
			// Reset service connection
			serviceConnection = GroupCallServiceConnection()
			runningCalls[callId]?.let { triggerGroupCallRefreshSteps(it.groupId) }
		}
	}

	@WorkerThread
	private suspend fun leaveCall(callId: CallId) {
		GroupCallThreadUtil.assertDispatcherThread()

		serviceConnection.getCurrentGroupCallController()?.also {
			if (it.callId == callId) {
				logger.debug("Leaving call {}", callId)
				it.leave()
				it.callDisposedSignal.await()
			} else {
				logger.info("Attempt to leave call that is not joined")
			}
		} ?: run {
			logger.info("Trying to leave call, but no call is joined")
		}
	}

	@WorkerThread
	@Throws(GroupCallException::class)
	private suspend fun createNewCall(group: GroupModel): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		val callStartData = createGroupCallStartData()
		val callId = CallId.create(group, callStartData)
		logger.debug("Created call id: {}", callId)

		val groupId = group.localGroupId
		val callDescription = GroupCallDescription(
			callStartData.protocolVersion,
			groupId,
			callStartData.sfuBaseUrl,
			callId,
			callStartData.gck,
			Date().time.toULong()
		)
		val callController = joinCall(callDescription)

		logger.debug("Got controller")

		val (startedAt, participantIds) = callController.connectedSignal.await()
		callDescription.startedAt = startedAt

		// Wait some time for another chosen call.
		// This delay is actually meant to allow a butter fingered user to cancel a call again
		// if it has been started by mistake.
		// If a call in this group is started by another group member in the meantime, this call
		// will be joined immediately instead.
		val waitPeriodMillis = when(preferenceService.skipGroupCallCreateDelay()) {
			true -> 0
			else -> ARTIFICIAL_GC_CREATE_WAIT_PERIOD_MILLIS
		}
		val chosenCall = waitForChosenCall(group, waitPeriodMillis)

		if (chosenCall != null && chosenCall.callId != callId) {
			callController.leave()
			callController.callDisposedSignal.await()
			logger.warn("There is already another chosen call for group ${group.localGroupId}")
			return joinAndConfirmCall(chosenCall, groupId)
		}

		logger.debug("Got {} participants", participantIds.size)

		if (participantIds.isNotEmpty()) {
			logger.trace("Participants: {}", participantIds)
			callController.declineCall()
			throw GroupCallException("Invalid participants list for creation of group call (must be empty)")
		} else {
			callController.confirmCall()
		}

		sendGroupCallStartMessage(group, callStartData, callDescription.getStartedAtDate(), null)
		sendCallInitAsText(callId, group, callStartData)

		addRunningCall(callDescription)
		triggerGroupCallRefreshSteps(callDescription.groupId)

		createStartedStatusMessage(
				callDescription,
				group,
				contactService.me,
				true,
				callDescription.getStartedAtDate()
		)

		return callController
	}

	/**
	 * Wait for at most [waitPeriodMillis] for another chosen call in this [group].
	 * If after this wait period no chosen call is available for this group, `null` will be returned.
	 * If another chosen call is created for this group it is returned immediately upon creation,
	 * even if the wait period has not yet expired.
	 */
	private suspend fun waitForChosenCall(group: GroupModel, waitPeriodMillis: Long): GroupCallDescription? {
		val signal = CompletableDeferred<GroupCallDescription>()

		val groupCallObserver = object : GroupCallObserver {
			override fun onGroupCallUpdate(call: GroupCallDescription?) {
				if (call != null) {
					signal.complete(call)
				}
			}
		}

		return try {
			logger.debug("Start artificial wait period before sending group call start message")
			addGroupCallObserver(group, groupCallObserver)
			withTimeout(waitPeriodMillis) {
				signal.await().also {
					logger.debug("Another chosen call has been started for this group. Stop waiting for chosen call.")
				}
			}
		} catch (e: TimeoutCancellationException) {
			logger.debug("Artificial wait period has expired without another chosen call")
			null
		} finally {
			removeGroupCallObserver(group, groupCallObserver)
		}
	}

	/**
	 * Get the {@link GroupCallController} for a joined call for a certain group.
	 *
	 * If there is no joined call, or the joined call does not match the given group, no controller
	 * will be returned.
	 *
	 * @param groupId The {@link LocalGroupId} of the group of which the controller should be obtained.
	 *
	 * @return The {@link GroupCallController} if there is a joined call for this group, {@code null} otherwise
	 */
	@WorkerThread
	private fun getGroupCallControllerForJoinedCall(groupId: LocalGroupId): GroupCallController? {
		GroupCallThreadUtil.assertDispatcherThread()

		return serviceConnection.getCurrentGroupCallController()?.let {
			if (chosenCalls[groupId]?.callId == it.callId) {
				it
			} else {
				null
			}
		}
	}

	@WorkerThread
	private fun handleGroupCallStart(message: GroupCallStartMessage): Boolean {
		groupCallStartQueue.tryEmit(message)
		// Always mark messages as processed.
		// If there where loads of sent GroupCallStartMessages while the device was offline
		// this might lead to "dropped" messages and therefore missing group call states in the
		// chat. As this affects only older messages this should not be a problem.
		return true
	}

	@WorkerThread
	private suspend fun processGroupCallStart(message: GroupCallStartMessage) {
		GroupCallThreadUtil.assertDispatcherThread()

		val group = groupService.getGroup(message)
		val groupId = group.localGroupId
		logger.info("Process group call start for group {}: {}", groupId, message.data)

		if (ProtocolDefines.GC_PROTOCOL_VERSION != message.data.protocolVersion.toInt()) {
			logger.warn("Invalid protocol version {} received. Abort handling of group call start", message.data.protocolVersion)
			return
		}

		if (isInvalidSfuBaseUrl(message.data.sfuBaseUrl)) {
			logger.warn("Invalid sfu base url: {}", message.data.sfuBaseUrl)
			return
		}

		if (hasDuplicateGck(groupId, message.data.gck)) {
			logger.warn("Duplicate gck received. Abort handling of group call start")
			return
		}

		val callId = CallId.create(group, message.data)
		val call = GroupCallDescription(
			message.data.protocolVersion,
			groupId,
			message.data.sfuBaseUrl,
			callId,
			message.data.gck,
			Date().time.toULong()
		)

		addRunningCall(call)

		val callerContactModel = contactService.getByIdentity(message.fromIdentity)
		if (callerContactModel != null) {
			val isOutbox = message.fromIdentity.equals(contactService.me.identity)
			createStartedStatusMessage(
					call,
					group,
					callerContactModel,
					isOutbox,
					message.date)
		}

		notifyGroupCallStart(group, callerContactModel)
	}

	private suspend fun notifyGroupCallStart(group: GroupModel, callerContactModel: ContactModel?) {
		val groupId = group.localGroupId

		val chosenCall = runGroupCallRefreshSteps(groupId)

		if (chosenCall == null) {
			logger.info("Group call seems not to be running any more. Not showing notifications.")
			return
		}

		// Only check this _after_ running the group call refresh steps.
		// During the refresh steps the 'ended'-status will be created if the call has already been
		// ended
		if (!ConfigUtils.isGroupCallsEnabled()) {
			logger.info("Group call is running but disabled. Not showing notifications.")
			return
		}

		if (callerContactModel == null) {
			logger.warn("Caller could not be determined. Not showing notifications.")
			return
		}

		if (isJoinedCall(chosenCall)) {
			logger.info("Call already joined. Not showing notifications.")
			return
		}

		logger.debug("Show group call notification")
		notificationService.addGroupCallNotification(group, callerContactModel)
	}

	/**
	 * Creates a status message for a started call
	 */
	private fun createStartedStatusMessage(callDescription: GroupCallDescription,
										   group: GroupModel,
										   caller: ContactModel,
										   isOutbox: Boolean,
										   startedAt: Date) {
		messageService.createGroupCallStatus(
				GroupCallStatusDataModel.createStarted(
						callDescription.callId.toString(),
						callDescription.groupId.id,
						caller.identity),
				groupService.createReceiver(group),
				caller,
				callDescription,
				isOutbox,
				startedAt
		)
	}

	/**
	 * Makes sure that - if a call of this group is joined - the chosen call is joined.
	 *
	 * If the user is currently in a group call of this group which is not the chosen call the call is
	 * exited and the chosen call is joined.
	 *
	 * @return true, if the call has been joined, false otherwise
	 */
	@WorkerThread
	private suspend fun consolidateJoinedCall(chosenCall: GroupCallDescription, groupId: LocalGroupId): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		val joinedCall = getJoinedCall()

		// TODO(ANDR-1959): This should be tested thoroughly. There could easily be timing problems
		//  because the call is left and immediately a new call is joined.
		//  There might be problems when the foreground service is already running / bound etc.
		return if (joinedCall != null && isOfGroupButNotChosenCall(groupId, joinedCall, chosenCall.callId)) {
			logger.info("Leave joined call because it is not the chosen call for group {}. Join the chosen call instead.", groupId)

			val groupController = serviceConnection.getCurrentGroupCallController()

			val microphoneActive = groupController?.microphoneActive ?: true

			leaveCall(joinedCall)

			groupService.getById(groupId.id)?.let {
				joinCall(it)?.let {
					it.microphoneActive = microphoneActive
				}
			}

			context.startActivity(
				GroupCallActivity.getJoinCallIntent(
					context,
					groupId.id,
					microphoneActive,
				)
			)
			true
		} else {
			false
		}
	}

	@WorkerThread
	private fun isOfGroupButNotChosenCall(groupId: LocalGroupId, callId: CallId, chosenCall: CallId): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		val isOfGroup = callId in getRunningCalls(groupId).map { it.callId }
		return isOfGroup && callId != chosenCall
	}

	@WorkerThread
	private suspend fun isInvalidSfuBaseUrl(baseUrl: String): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		return !(obtainToken()?.isAllowedBaseUrl(baseUrl) ?: false)
	}

	@WorkerThread
	private fun hasDuplicateGck(groupId: LocalGroupId, gck: ByteArray) = getRunningCalls(groupId)
			.any { it.gck.contentEquals(gck) }

	@WorkerThread
	private fun handleUnknownMessageType(message: GroupCallControlMessage): Boolean {
		val type = when (message) {
			is AbstractMessage -> "0x%x".format(message.type)
			else -> "???"
		}
		logger.info("Unknown group call control message type {}", type)
		return true
	}

	@WorkerThread
	private suspend fun createGroupCallStartData(): GroupCallStartData {
		GroupCallThreadUtil.assertDispatcherThread()

		val sfuToken = requireToken()
		return GroupCallStartData(
			ProtocolDefines.GC_PROTOCOL_VERSION.toUInt(),
			createGck(),
			sfuToken.sfuBaseUrl
		)
	}

	@WorkerThread
	private fun createGck(): ByteArray {
		val random = SecureRandom()
		val gck = ByteArray(GCK_LENGTH)
		random.nextBytes(gck)
		return gck
	}

	/**
	 * Create the GroupCallStartMessage and send it to the given group members. Note that the
	 * GroupCallStartMessage is never sent to group members where the group call feature mask is not
	 * set. This method assumes that the feature masks are updated and therefore does not fetch the
	 * newest feature masks from the server.
	 *
	 * The same create date is used for all messages. If a call is already running, then the call
	 * duration is subtracted from current time to match the original created at from the first
	 * group call start message.
	 *
	 * @param group the group model where the group call message is sent to
	 * @param data the group call start data
	 * @param sendTo the list of identities who receive the message; if null it is sent to all group members
	 */
	@WorkerThread
	private fun sendGroupCallStartMessage(group: GroupModel, data: GroupCallStartData, startedAt: Date, sendTo: Array<String>?) {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.debug("Send group call start message")
		val identities = (sendTo ?: groupService.getGroupIdentities(group))
			.filter { identity -> contactService.getByIdentity(identity)?.let { ThreemaFeature.canGroupCalls(it.featureMask) } ?: false }
			.toTypedArray()

		// TODO(ANDR-1766): Created at should most likely be injected in GroupMessagingService#sendMessage
		val createdAt = startedAt
		val count = groupMessagingService.sendMessage(group, identities) {
			GroupCallStartMessage(data).apply {
				date = createdAt
				messageId = it }
		}
		logger.trace("{} group call start messages sent", count)
	}

	@WorkerThread
	private fun sendCallInitAsText(callId: CallId, group: GroupModel, callStartData: GroupCallStartData) {
		if (preferenceService.isGroupCallSendInitEnabled) {
			val groupJson = JSONObject()
			groupJson.put("creator", group.creatorIdentity)
			groupJson.put("id", Base64.encodeBytes(group.apiGroupId.groupId))

			val membersJson = JSONArray()
			groupService.getGroupIdentities(group)
				.mapNotNull { contactService.getByIdentity(it) }
				.forEach {
					val member = JSONObject()
					member.put("identity", it.identity)
					member.put("publicKey", Base64.encodeBytes(it.publicKey))
					membersJson.put(member)
				}

			val json = JSONObject()
			json.put("type", "group-call")
			json.put("protocolVersion", callStartData.protocolVersion.toInt())
			json.put("group", groupJson)
			json.put("members", membersJson)
			json.put("gck", Base64.encodeBytes(callStartData.gck))
			json.put("sfuBaseUrl", callStartData.sfuBaseUrl)
			val callIdText = Utils.byteArrayToHexString(callId.bytes)
			val jsonText = json.toString(0)
			val callInit = Base64.encodeBytes(jsonText.encodeToByteArray())
			val message = "*CallId:*\n$callIdText\n\n*CallData:*\n$callInit"

			val receiver = groupService.createReceiver(group)
			ThreemaApplication.requireServiceManager().messageService
				.sendText(message, receiver)
		}
	}

	private fun notifyCallObservers(groupId: LocalGroupId, call: GroupCallDescription?) {
		synchronized(callObservers) {
			callObservers[groupId]?.forEach { it.onGroupCallUpdate(call) }
		}
		notifyGeneralCallObservers(call)
	}

	private fun notifyGeneralCallObservers(call: GroupCallDescription?) {
		synchronized(generalCallObservers) {
			generalCallObservers.forEach { it.onGroupCallUpdate(call) }
		}
	}

	/**
	 * Run the group call refresh steps and return the chosen call if present.
	 */
	private suspend fun runGroupCallRefreshSteps(groupId: LocalGroupId): GroupCallDescription? {
		// Corresponds to the Group Call Refresh Steps 1-3 and 5
		val chosenCall = getChosenCall(groupId)

		// Step 4: reschedule group call refresh steps if there is a running call
		scheduleGroupCallRefreshSteps(groupId)

		// Step 6: abort the steps (update the UI)
		if (chosenCall == null) {
			chosenCalls.remove(groupId)
			notifyCallObservers(groupId, null)
			return null
		}

		// Step 7: display chosen-call as the currently running group call within the group
		updateChosenCallsAndNotifyCallObservers(groupId, chosenCall.callId)

		// Step 8 is handled by getting the chosen call before sending a GroupCallStart and therefore
		// this step is not part in the group call refresh steps implementation here

		// Step 9
		consolidateJoinedCall(chosenCall, groupId)

		// Step 10: return chosen-call
		return chosenCall
	}

	/**
	 * Perform steps 1-3 and 5 of the group call refresh steps and return the chosen call if present.
	 *
	 * If no group call is considered running for this group, null is returned.
	 *
	 *  TODO(ANDR-1959): Tests for group call refresh steps
	 */
	@WorkerThread
	private suspend fun getChosenCall(groupId: LocalGroupId): GroupCallDescription? {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.debug("Get chosen call for group {}", groupId)
		logger.trace("runningCalls={}", runningCalls.values)
		return getConsideredRunningCalls(groupId)
			.toSet()
			.maxByOrNull { it.startedAt }
	}

	/**
	 * Initially trigger the group call refresh steps for a group.
	 *
	 * The steps will be run as soon as the executor is ready.
	 *
	 * If needed the group call refresh steps will be re-scheduled after execution.
	 */
	@WorkerThread
	private fun triggerGroupCallRefreshSteps(groupId: LocalGroupId) {
		logger.debug("Trigger group call refresh steps")
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			runGroupCallRefreshSteps(groupId)?.let {
				logger.debug("Chosen call: {}", it)
				consolidateJoinedCall(it, groupId)
			}
		}
	}

	@WorkerThread
	private fun scheduleGroupCallRefreshSteps(groupId: LocalGroupId) {
		// This is executed asynchronously in order to let the previous
		// group call refresh finish before the next attempt to re-schedule is started.
		// When rescheduling, a refresh is only scheduled if the previous refresh has finished.
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			val hasRunningCall = getRunningCalls(groupId).isNotEmpty()
			val hasScheduledRefresh = callRefreshTimers[groupId]?.isCompleted == false
			if (hasRunningCall && !hasScheduledRefresh) {
				logger.trace("Schedule group call refresh steps for group {}", groupId)
				synchronized(callRefreshTimers) {
					callRefreshTimers[groupId] =
						CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
							delay(ProtocolDefines.GC_GROUP_CALL_REFRESH_STEPS_TIMEOUT_SECONDS * 1_000)
							runGroupCallRefreshSteps(groupId)
						}
				}
			} else if (!hasRunningCall) {
				logger.trace("Group {} has no running call. Do not schedule group call refresh steps")
			} else {
				logger.trace("Group call refresh steps already scheduled for group {}", groupId)
			}
		}
	}

	@WorkerThread
	private fun getJoinedCall(): CallId? {
		return serviceConnection.getCurrentGroupCallController()?.callId
	}

	private fun updateChosenCallsAndNotifyCallObservers(groupId: LocalGroupId, callId: CallId) {
		val call = runningCalls[callId]
		if (call == null) {
			chosenCalls.remove(groupId)
		} else {
			chosenCalls[groupId] = call
		}
		notifyCallObservers(groupId, call)
		val groupModel = groupService.getById(groupId.id)
		if (call == null && groupModel != null) {
			messageService.createGroupCallStatus(
				GroupCallStatusDataModel.createEnded(callId.toString()),
				groupService.createReceiver(groupModel),
				null,
				call,
				false,
				Date()
			)
		}
	}

	/**
	 * Get the calls that are considered running.
	 *
	 * For every call that is considered running the sfu is peeked, to update the current call state.
	 *
	 * If the call is not known to the sfu it is removed from the list of running calls for this group.
	 *
	 * If there are any pending refresh timers for a removed call, those are also removed.
	 */
	@WorkerThread
	private fun getConsideredRunningCalls(groupId: LocalGroupId): Flow<GroupCallDescription> {
		GroupCallThreadUtil.assertDispatcherThread()

		val consideredRunningCalls = getRunningCalls(groupId)
		return peekCalls(consideredRunningCalls)
			.onEach { purgeRunningCalls(groupId, it) }
			.filter { isMatchingProtocolVersion(it.call) && (it.isJoined || it.isHttpOk)  }
			.onEach { it.sfuResponse?.body?.let { r -> updateCallState(it.call.callId, r) }}
			.map { it.call }
			.also { purgeCallRefreshTimers(groupId) }
	}

	private fun isMatchingProtocolVersion(call: GroupCallDescription): Boolean {
		logger.debug("Call protocol version: local={}, call={}", ProtocolDefines.GC_PROTOCOL_VERSION, call.protocolVersion)
		return if (call.protocolVersion == ProtocolDefines.GC_PROTOCOL_VERSION.toUInt()) {
			true
		} else {
			logger.warn("Local gc protocol version is {} but running call has version {}", ProtocolDefines.GC_PROTOCOL_VERSION, call.protocolVersion)
			false
		}
	}

	@WorkerThread
	private fun purgeRunningCalls(groupId: LocalGroupId, peek: PeekResult) {
		GroupCallThreadUtil.assertDispatcherThread()

		if (!peek.isJoined && !peek.isPeekFailed && (peek.isHttpNotFound || isAbandonedCall(peek))) {
			val callId = peek.call.callId
			logger.debug("Remove call {}", callId)
			removeRunningCall(callId)
			notificationService.cancelGroupCallNotification(groupId.id)
			updateChosenCallsAndNotifyCallObservers(groupId, callId)
		}
	}

	@WorkerThread
	private fun isAbandonedCall(peek: PeekResult): Boolean {
		val failedCounter = if (!peek.isJoined && !peek.isHttpOk) {
			peekFailedCounters.getAndIncrementCounter(peek.call.callId)
		} else {
			peekFailedCounters.resetCounter(peek.call.callId)
		}
		return if (failedCounter >= ProtocolDefines.GC_PEEK_FAILED_ABANDON_MIN_TRIES) {
			// Note: we cannot use peek.call.getRunningSince() because we need an actual timestamp
			// not relative to the system uptime.
			val runningSince = max(Date().time - peek.call.startedAt.toLong(), 0L)
			runningSince >= ProtocolDefines.GC_PEEK_FAILED_ABANDON_MIN_CALL_AGE_MILLIS
		} else {
			false
		}
	}

	@WorkerThread
	private fun purgeCallRefreshTimers(groupId: LocalGroupId) {
		if (getRunningCalls(groupId).isEmpty()) {
			synchronized(callRefreshTimers) {
				callRefreshTimers.remove(groupId)?.cancel("purgeCallRefreshTimers")
			}
		}
	}

	@WorkerThread
	private fun peekCalls(calls: Collection<GroupCallDescription>): Flow<PeekResult> {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.trace("Peek calls {}", calls.map { it.callId })
		return calls.asFlow()
			.map(this::peekCall)
			.flowOn(GroupCallThreadUtil.DISPATCHER)
	}

	@WorkerThread
	private suspend fun peekCall(call: GroupCallDescription): PeekResult {
		GroupCallThreadUtil.assertDispatcherThread()

		val isJoined = isJoinedCall(call)
		return try {
			PeekResult(
				call,
				peekCall(call, 1, false),
				isJoined = isJoined
			).also {
				logger.debug("Got peek result: {}", it.sfuResponse?.body)
			}
		} catch (e: SfuException) {
			logger.error("Could not peek call", e)
			PeekResult(
				call,
				sfuResponse = null,
				isPeekFailed = true,
				isJoined = isJoined
			)
		}
	}

	@WorkerThread
	private suspend fun peekCall(call: GroupCallDescription, retriesOnInvalidToken: Int, forceTokenRefresh: Boolean): PeekResponse {
		GroupCallThreadUtil.assertDispatcherThread()

		val token = requireToken(forceTokenRefresh)
		val peekResponse = sfuConnection.peek(token, call.sfuBaseUrl, call.callId)
		return if (peekResponse.statusCode == HTTP_STATUS_TOKEN_INVALID && retriesOnInvalidToken > 0) {
			logger.info("Retry peeking with refreshed token")
			peekCall(call, retriesOnInvalidToken - 1, true)
		} else {
			peekResponse
		}
	}

	@WorkerThread
	private suspend fun obtainToken(forceTokenRefresh: Boolean = false): SfuToken? {
		return try {
			requireToken(forceTokenRefresh)
		} catch (e: SfuException) {
			logger.error("Could not obtain sfu token", e)
			null
		}
	}

	@WorkerThread
	private suspend fun requireToken(forceTokenRefresh: Boolean = false): SfuToken {
		// TODO(ANDR-2090): Use a timeout according to protocol
		return sfuConnection.obtainSfuToken(forceTokenRefresh)
	}

	/**
	 * Get all calls for the specified group, that are currently considered running.
	 *
	 * If there are multiple calls considered running for this group only the call with the highest
	 * create timestamp is included. Calls with lower timestamps are removed from known calls since those are no longer considered running.
	 *
	 * In principle there might be multiple calls that were created at exactly the same time. Therefore
	 * a collection of calls is returned.
	 */
	@WorkerThread
	private fun getRunningCalls(groupId: LocalGroupId): Set<GroupCallDescription> {
		return synchronized(runningCalls) {
			runningCalls.values
				.filter { it.groupId == groupId }
				.toSet()
		}
	}

	/**
	 * Add the call to the list of considered running calls
	 */
	@WorkerThread
	private fun addRunningCall(call: GroupCallDescription) {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.debug("Add running call {}", call)
		synchronized(runningCalls) {
			runningCalls[call.callId] = call
			persistRunningCall(call)
		}
	}

	@WorkerThread
	private fun removeRunningCall(callId: CallId) {
		removeRunningCalls(setOf(callId))
	}

	@WorkerThread
	private fun removeRunningCalls(callIds: Set<CallId>) {
		GroupCallThreadUtil.assertDispatcherThread()

		return synchronized(runningCalls) {
			callIds.forEach { callId ->
				val removedCall = runningCalls.remove(callId).also {
					logger.debug("call removed: {}, id={}", it != null, callId)
				}
				removedCall?.let { removePersistedRunningCall(it) }
			}
		}
	}

	@WorkerThread
	private fun persistRunningCall(call: GroupCallDescription) {
		databaseService.groupCallModelFactory.createOrUpdate(call.toGroupCallModel())
	}

	@WorkerThread
	private fun removePersistedRunningCall(call: GroupCallDescription) {
		databaseService.groupCallModelFactory.delete(call.toGroupCallModel())
	}

	@WorkerThread
	private fun loadPersistedRunningCalls(): MutableMap<CallId, GroupCallDescription> {
		return databaseService.groupCallModelFactory.all
			.map { it.toGroupCallDescription() }
			.associateBy { it.callId }
			.toMutableMap()
	}

	@WorkerThread
	private fun updateCallState(callId: CallId, peekResponse: PeekResponseBody) {
		GroupCallThreadUtil.assertDispatcherThread()

		synchronized(runningCalls) {
			runningCalls[callId]?.let {
				it.maxParticipants = peekResponse.maxParticipants
				it.startedAt = peekResponse.startedAt
				it.setEncryptedCallState(peekResponse.encryptedCallState)
				logger.debug("Update call state {}", it)
			}
		}
	}
}

private data class PeekResult (
	val call: GroupCallDescription,
	val sfuResponse: PeekResponse?,
	val isJoined: Boolean = false,
	val isPeekFailed: Boolean = false
) {
	val isHttpOk: Boolean
		get() = !isPeekFailed && sfuResponse?.isHttpOk == true

	val isHttpNotFound: Boolean
		get() = !isPeekFailed && sfuResponse?.isHttpNotFound == true
}
