/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.app.managers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.Charsets;
import org.slf4j.Logger;

import java.util.Date;
import java.util.Locale;

import ch.threema.app.BuildFlavor;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.BackupChatService;
import ch.threema.app.backuprestore.BackupChatServiceImpl;
import ch.threema.app.backuprestore.BackupRestoreDataService;
import ch.threema.app.backuprestore.csv.BackupRestoreDataServiceImpl;
import ch.threema.app.emojis.EmojiRecent;
import ch.threema.app.emojis.EmojiService;
import ch.threema.app.emojis.search.EmojiSearchIndex;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.processors.MessageAckProcessor;
import ch.threema.app.processors.MessageProcessor;
import ch.threema.app.services.ActivityService;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.ApiServiceImpl;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.AvatarCacheServiceImpl;
import ch.threema.app.services.BrowserDetectionService;
import ch.threema.app.services.BrowserDetectionServiceImpl;
import ch.threema.app.services.CacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ContactServiceImpl;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationServiceImpl;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DeadlineListServiceImpl;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.DeviceServiceImpl;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.DistributionListServiceImpl;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.DownloadServiceImpl;
import ch.threema.app.services.FileService;
import ch.threema.app.services.FileServiceImpl;
import ch.threema.app.services.GroupMessagingService;
import ch.threema.app.services.GroupMessagingServiceImpl;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.GroupServiceImpl;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.IdListServiceImpl;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.LifetimeServiceImpl;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.LocaleServiceImpl;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.NotificationServiceImpl;
import ch.threema.app.services.PinLockService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.PreferenceServiceImpl;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.RingtoneServiceImpl;
import ch.threema.app.services.SensorService;
import ch.threema.app.services.SensorServiceImpl;
import ch.threema.app.services.ServerAddressProviderService;
import ch.threema.app.services.ServerAddressProviderServiceImpl;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.SynchronizeContactsServiceImpl;
import ch.threema.app.services.SystemScreenLockService;
import ch.threema.app.services.SystemScreenLockServiceImpl;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.UserServiceImpl;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.services.WallpaperServiceImpl;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.ballot.BallotServiceImpl;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.services.group.GroupInviteServiceImpl;
import ch.threema.app.services.group.GroupJoinResponseService;
import ch.threema.app.services.group.GroupJoinResponseServiceImpl;
import ch.threema.app.services.group.IncomingGroupJoinRequestService;
import ch.threema.app.services.group.IncomingGroupJoinRequestServiceImpl;
import ch.threema.app.services.group.OutgoingGroupJoinRequestService;
import ch.threema.app.services.group.OutgoingGroupJoinRequestServiceImpl;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceSerial;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.services.messageplayer.MessagePlayerServiceImpl;
import ch.threema.app.stores.AuthTokenStore;
import ch.threema.app.stores.DatabaseContactStore;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.threemasafe.ThreemaSafeServiceImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DeviceIdUtil;
import ch.threema.app.utils.ForwardSecurityStatusSender;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.groupcall.GroupCallManagerImpl;
import ch.threema.app.voip.groupcall.sfu.SfuConnection;
import ch.threema.app.voip.groupcall.sfu.SfuConnectionImpl;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.webclient.manager.WebClientServiceManager;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionService;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.connection.ThreemaConnection;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.SQLDHSessionStore;
import ch.threema.storage.models.MessageState;

public class ServiceManager {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ServiceManager");

	private final ThreemaConnection connection;
	private final IdentityStore identityStore;
	private final MasterKey masterKey;
	private final PreferenceStoreInterface preferenceStore;
	private final UpdateSystemService updateSystemService;

	private final CacheService cacheService;

	private DatabaseContactStore contactStore;
	private APIConnector apiConnector;
	private MessageQueue messageQueue;
	private ContactService contactService;
	private UserService userService;

	private MessageService messageService;

	private QRCodeService qrCodeService;
	private FileService fileService;
	private PreferenceService preferencesService;
	private LocaleService localeService;
	private DeviceService deviceService;
	private LifetimeService lifetimeService;
	private AvatarCacheService avatarCacheService;
	private LicenseService licenseService;
	private BackupRestoreDataService backupRestoreDataService;
	private GroupService groupService;
	private @Nullable GroupInviteService groupInviteService;
	private @Nullable
	GroupJoinResponseService groupJoinResponseService;
	IncomingGroupJoinRequestService incomingGroupJoinRequestService;
	OutgoingGroupJoinRequestService outgoingGroupJoinRequestService;
	private GroupMessagingService groupMessagingService;

	private MessageAckProcessor messageAckProcessor;
	private LockAppService lockAppService;
	private ActivityService activityService;
	private ApiService apiService;
	private ConversationService conversationService;
	private NotificationService notificationService;
	private SynchronizeContactsService synchronizeContactsService;
	private SystemScreenLockService systemScreenLockService;

	private IdListService blackListService, excludedSyncIdentitiesService, profilePicRecipientsService;
	private DeadlineListService mutedChatsListService, hiddenChatListService, mentionOnlyChatsListService;
	private DistributionListService distributionListService;
	private MessageProcessor messageProcessor;
	private MessagePlayerService messagePlayerService = null;
	private DownloadServiceImpl downloadService;
	private BallotService ballotService;
	private WallpaperService wallpaperService;
	private ThreemaSafeService threemaSafeService;
	private RingtoneService ringtoneService;
	private BackupChatService backupChatService;
	private final DatabaseServiceNew databaseServiceNew;
	private SensorService sensorService;
	private VoipStateService voipStateService;
	private GroupCallManager groupCallManager;
	private SfuConnection sfuConnection;
	private BrowserDetectionService browserDetectionService;
	private ConversationTagServiceImpl conversationTagService;
	private ServerAddressProviderService serverAddressProviderService;

	private WebClientServiceManager webClientServiceManager;

	private DHSessionStoreInterface dhSessionStore;
	private ForwardSecurityMessageProcessor forwardSecurityMessageProcessor;
	private SymmetricEncryptionService symmetricEncryptionService;

	private EmojiService emojiService;

	public ServiceManager(ThreemaConnection connection,
						  DatabaseServiceNew databaseServiceNew,
						  IdentityStore identityStore,
						  PreferenceStoreInterface preferenceStore,
						  MasterKey masterKey,
						  UpdateSystemService updateSystemService)
	{
		this.cacheService = new CacheService();

		this.connection = connection;
		this.preferenceStore = preferenceStore;
		this.identityStore = identityStore;
		this.masterKey = masterKey;
		this.databaseServiceNew = databaseServiceNew;
		this.updateSystemService = updateSystemService;
	}

	private DatabaseContactStore getContactStore() {
		if (this.contactStore == null) {
			this.contactStore = new DatabaseContactStore(
					this.getAPIConnector(),
					this.getPreferenceService(),
					this.databaseServiceNew,
					this.getBlackListService(),
					this.getExcludedSyncIdentitiesService()
			);
		}

		return this.contactStore;
	}

	public APIConnector getAPIConnector() {
		if (this.apiConnector == null) {
			try {
				this.apiConnector = new APIConnector(
					ThreemaApplication.getIPv6(),
					this.getServerAddressProviderService().getServerAddressProvider(),
					ConfigUtils.isWorkBuild(),
					ConfigUtils::getSSLSocketFactory
				);
				this.apiConnector.setVersion(this.getConnection().getVersion());
				this.apiConnector.setLanguage(Locale.getDefault().getLanguage());

				if (BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.ONPREM) {
					// On Premise always requires Basic authentication
					PreferenceService preferenceService = this.getPreferenceService();
					this.apiConnector.setAuthenticator(urlConnection -> {
						if (preferenceService.getLicenseUsername() != null) {
							String auth = preferenceService.getLicenseUsername() + ":" + preferenceService.getLicensePassword();
							urlConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeBytes(auth.getBytes(Charsets.UTF_8)));
						}
					});
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}

		return this.apiConnector;
	}

	/**
	 * Start the server connection. Do not call this directly; use the LifetimeService!
	 */
	public void startConnection() throws ThreemaException {
		logger.trace("startConnection");

		String currentIdentity = this.identityStore.getIdentity();
		if (currentIdentity == null || currentIdentity.length() == 0) {
			throw new NoIdentityException();
		}

		if(this.masterKey.isLocked()) {
			throw new MasterKeyLockedException("master key is locked");
		}

		//add a message processor
		if (this.connection.getMessageProcessor() == null) {
			logger.debug("add message processor to connection");
			this.connection.setMessageProcessor(this.getMessageProcessor());
		}

		//add message ACK processor
		getMessageAckProcessor().setMessageService(this.getMessageService());
		connection.addMessageAckListener(getMessageAckProcessor());

		logger.info("Starting connection");
		this.connection.start();
	}

	public PreferenceStoreInterface getPreferenceStore() {
		return preferenceStore;
	}

	public MessageProcessor getMessageProcessor() throws ThreemaException {
		if(this.messageProcessor == null) {
			this.messageProcessor = new MessageProcessor(
					this.getMessageService(),
					this.getContactService(),
					this.getIdentityStore(),
					this.getContactStore(),
					this.getPreferenceService(),
					this.getGroupService(),
					this.getGroupJoinResponseService(),
					this.getIncomingGroupJoinRequestService(),
					this.getBlackListService(),
					this.getBallotService(),
					this.getFileService(),
					this.getNotificationService(),
					this.getVoipStateService(),
					this.getForwardSecurityMessageProcessor(),
					this.getGroupCallManager()
		);
		}
		return this.messageProcessor;
	}

	/**
	 * Stop the connection. Do not call this directly; use the LifetimeService!
	 */
	public void stopConnection() throws InterruptedException {
		logger.info("Stopping connection");
		InterruptedException interrupted = null;
		try {
			this.connection.stop();
		} catch (InterruptedException e) {
			logger.error("Interrupted while stopping connection");
			interrupted = e;
		}

		// Write message queue to file at this opportunity (we can never know when we'll get killed)
		try {
			this.getMessageService().saveMessageQueueAsync();
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		// Re-set interrupted flag
		if (interrupted != null) {
			Thread.currentThread().interrupt();
			throw interrupted;
		}
	}

	public MessageQueue getMessageQueue() {
		if (this.messageQueue == null) {
			this.messageQueue = new MessageQueue(
					this.getContactStore(),
					this.getIdentityStore(),
					this.getConnection()
			);
		}

		return this.messageQueue;
	}

	public MessageAckProcessor getMessageAckProcessor() {
		if (this.messageAckProcessor == null) {
			this.messageAckProcessor = new MessageAckProcessor();
		}

		return this.messageAckProcessor;
	}

	public UserService getUserService() {
		if (this.userService == null) {
			try {
				this.userService = new UserServiceImpl(
						this.getContext(),
						this.preferenceStore,
						this.getLocaleService(),
						this.getAPIConnector(),
						this.getIdentityStore(),
						this.getMessageQueue(),
						this.getPreferenceService());
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}

		return this.userService;
	}

	@NonNull
	public ContactService getContactService() throws MasterKeyLockedException, FileSystemNotPresentException {
		if (this.contactService == null) {
			if(this.masterKey.isLocked()) {
				throw new MasterKeyLockedException("master key is locked");
			}
			this.contactService = new ContactServiceImpl(
					this.getContext(),
					this.getContactStore(),
					this.getAvatarCacheService(),
					this.databaseServiceNew,
					this.getDeviceService(),
					this.getUserService(),
					this.getMessageQueue(),
					this.getIdentityStore(),
					this.getPreferenceService(),
					this.getBlackListService(),
					this.getProfilePicRecipientsService(),
					this.getRingtoneService(),
					this.getMutedChatsListService(),
					this.getHiddenChatsListService(),
					this.getFileService(),
					this.cacheService,
					this.getApiService(),
					this.getWallpaperService(),
					this.getLicenseService(),
					this.getAPIConnector(),
					this.getForwardSecurityMessageProcessor());
		}

		return this.contactService;
	}

	public MessageService getMessageService() throws ThreemaException {
		if (this.messageService == null) {
			this.messageService = new MessageServiceImpl(
					this.getContext(),
					this.cacheService,
					this.getMessageQueue(),
					this.databaseServiceNew,
					this.getContactService(),
					this.getFileService(),
					this.getIdentityStore(),
					this.getSymmetricEncryptionService(),
					this.getPreferenceService(),
					this.getMessageAckProcessor(),
					this.getLockAppService(),
					this.getBallotService(),
					this.getGroupService(),
					this.getApiService(),
					this.getDownloadService(),
					this.getHiddenChatsListService(),
					this.getProfilePicRecipientsService(),
					this.getBlackListService(),
					this.getForwardSecurityMessageProcessor()
			);
		}

		return this.messageService;
	}

	public PreferenceService getPreferenceService() {
		if (this.preferencesService == null) {
			this.preferencesService = new PreferenceServiceImpl(
					this.getContext(),
					this.preferenceStore
			);
		}
		return this.preferencesService;
	}

	public QRCodeService getQRCodeService() {
		if (this.qrCodeService == null) {
			this.qrCodeService = new QRCodeServiceImpl(this.getUserService());
		}

		return this.qrCodeService;
	}

	public FileService getFileService() throws FileSystemNotPresentException {
		if (this.fileService == null) {
			this.fileService = new FileServiceImpl(
					this.getContext(),
					this.masterKey,
					this.getPreferenceService()
			);
		}

		return this.fileService;
	}

	public LocaleService getLocaleService() {
		if (this.localeService == null) {
			this.localeService = new LocaleServiceImpl(this.getContext());
		}

		return this.localeService;
	}

	public ThreemaConnection getConnection() {
		return this.connection;
	}

	public DeviceService getDeviceService() {
		if(this.deviceService == null) {
			this.deviceService = new DeviceServiceImpl(this.getContext());
		}

		return this.deviceService;
	}

	public LifetimeService getLifetimeService() {
		if(this.lifetimeService == null) {
			this.lifetimeService = new LifetimeServiceImpl(this.getContext());
		}

		return this.lifetimeService;
	}

	public AvatarCacheService getAvatarCacheService() throws FileSystemNotPresentException {
		if(this.avatarCacheService == null) {
			this.avatarCacheService = new AvatarCacheServiceImpl(this.getContext());
		}

		return this.avatarCacheService;
	}

	/**
	 * @return service to backup or restore data (conversations and contacts)
	 */
	public BackupRestoreDataService getBackupRestoreDataService() throws FileSystemNotPresentException {
		if(this.backupRestoreDataService == null) {
			this.backupRestoreDataService = new BackupRestoreDataServiceImpl(
					this.getFileService());
		}

		return this.backupRestoreDataService;
	}

	public LicenseService getLicenseService() throws FileSystemNotPresentException {
		if(this.licenseService == null) {
			switch(BuildFlavor.getLicenseType()) {
				case SERIAL:
					this.licenseService = new LicenseServiceSerial(
							this.getAPIConnector(),
							this.getPreferenceService(),
							DeviceIdUtil.getDeviceId(getContext()));
					break;
				case GOOGLE_WORK:
				case HMS_WORK:
				case ONPREM:
					this.licenseService = new LicenseServiceUser(
						this.getAPIConnector(),
						this.getPreferenceService(),
						DeviceIdUtil.getDeviceId(getContext()));
					break;
				default:
					this.licenseService = new LicenseService() {
						@Override
						public String validate(Credentials credentials) {
							return null;
						}

						@Override
						public String validate(Credentials credentials, boolean allowException) {
							return null;
						}

						@Override
						public String validate(boolean allowException) {
							return null;
						}

						@Override
						public boolean hasCredentials() {
							return false;
						}

						@Override
						public boolean isLicensed() {
							return true;
						}

						@Override
						public Credentials loadCredentials() {
							return null;
						}
					};
			}

		}

		return this.licenseService;
	}

	public LockAppService getLockAppService() {
		if(null == this.lockAppService) {
			this.lockAppService = new PinLockService(
					this.getContext(),
					this.getPreferenceService(),
					this.getUserService()
			);
		}

		return this.lockAppService;
	}

	public ActivityService getActivityService() {
		if(null == this.activityService) {
			this.activityService = new ActivityService(
					this.getContext(),
					this.getLockAppService(),
					this.getPreferenceService());
		}
		return this.activityService;
	}

	public @NonNull GroupService getGroupService() throws MasterKeyLockedException, FileSystemNotPresentException {
		if(null == this.groupService) {
			this.groupService = new GroupServiceImpl(
					this.getContext(),
					this.cacheService,
					this.getApiService(),
					this.getGroupMessagingService(),
					this.getUserService(),
					this.getContactService(),
					this.databaseServiceNew,
					this.getAvatarCacheService(),
					this.getFileService(),
					this.getPreferenceService(),
					this.getWallpaperService(),
					this.getMutedChatsListService(),
					this.getHiddenChatsListService(),
					this.getRingtoneService(),
					this.getBlackListService()
			);
		}
		return this.groupService;
	}

	public @NonNull GroupInviteService getGroupInviteService() throws FileSystemNotPresentException, MasterKeyLockedException {
		if (this.groupInviteService == null) {
			this.groupInviteService = new GroupInviteServiceImpl(
				this.getUserService(),
				this.getGroupService(),
				this.getDatabaseServiceNew()
			);
		}
		return this.groupInviteService;
	}

	public @NonNull GroupJoinResponseService getGroupJoinResponseService() {
		if (this.groupJoinResponseService == null) {
			this.groupJoinResponseService = new GroupJoinResponseServiceImpl(
				this.getDatabaseServiceNew(),
				this.getMessageQueue()
			);
		}
		return this.groupJoinResponseService;
	}


	public @NonNull
	IncomingGroupJoinRequestService getIncomingGroupJoinRequestService() throws FileSystemNotPresentException, MasterKeyLockedException {
		if (this.incomingGroupJoinRequestService == null) {
			this.incomingGroupJoinRequestService = new IncomingGroupJoinRequestServiceImpl(
				this.getGroupJoinResponseService(),
				this.getGroupService(),
				this.getUserService(),
				this.databaseServiceNew
			);
		}
		return this.incomingGroupJoinRequestService;
	}


	public @NonNull
	OutgoingGroupJoinRequestService getOutgoingGroupJoinRequestService() throws MasterKeyLockedException {
		if (this.outgoingGroupJoinRequestService == null) {
			this.outgoingGroupJoinRequestService = new OutgoingGroupJoinRequestServiceImpl(
				this.getDatabaseServiceNew(),
				this.getMessageQueue()
			);
		}
		return this.outgoingGroupJoinRequestService;
	}

	public GroupMessagingService getGroupMessagingService() throws MasterKeyLockedException, FileSystemNotPresentException {
		if(null == this.groupMessagingService) {
			this.groupMessagingService = new GroupMessagingServiceImpl(
					this.getUserService(),
					this.getContactService(),
					this.getMessageQueue());
		}
		return this.groupMessagingService;
	}

	public ApiService getApiService() {
		if(null == this.apiService) {
			this.apiService = new ApiServiceImpl(ThreemaApplication.getAppVersion(), ThreemaApplication.getIPv6(), this.getAPIConnector(), new AuthTokenStore(), this.getServerAddressProviderService().getServerAddressProvider());
		}
		return this.apiService;
	}
	public DistributionListService getDistributionListService() throws MasterKeyLockedException, NoIdentityException, FileSystemNotPresentException {
		if(null == this.distributionListService) {
			this.distributionListService = new DistributionListServiceImpl(
					this.getContext(),
					this.getAvatarCacheService(),
					this.databaseServiceNew,
					this.getContactService()
			);
		}

		return this.distributionListService;
	}

	public ConversationTagService getConversationTagService() {
		if(null == this.conversationService) {
			this.conversationTagService = new ConversationTagServiceImpl(
				this.databaseServiceNew
			);
		}

		return this.conversationTagService;
	}

	public ConversationService getConversationService() throws ThreemaException {
		if(null == this.conversationService) {
			this.conversationService = new ConversationServiceImpl(
				this.cacheService,
				this.databaseServiceNew,
				this.getContactService(),
				this.getGroupService(),
				this.getDistributionListService(),
				this.getMessageService(),
				this.getHiddenChatsListService(),
				this.getConversationTagService()
			);
		}

		return this.conversationService;
	}

	public ServerAddressProviderService getServerAddressProviderService() {
		if(null == this.serverAddressProviderService) {
			this.serverAddressProviderService = new ServerAddressProviderServiceImpl(this.getPreferenceService());
		}

		return this.serverAddressProviderService;
	}

	public NotificationService getNotificationService() {
		if(this.notificationService == null) {
			this.notificationService = new NotificationServiceImpl(
					this.getContext(),
					this.getLockAppService(),
					this.getHiddenChatsListService(),
					this.getPreferenceService(),
					this.getRingtoneService()
			);
		}
		return this.notificationService;
	}

	public SynchronizeContactsService getSynchronizeContactsService() throws MasterKeyLockedException, FileSystemNotPresentException {
		if(this.synchronizeContactsService == null) {
			this.synchronizeContactsService = new SynchronizeContactsServiceImpl(
					this.getContext(),
					this.getAPIConnector(),
					this.getContactService(),
					this.getUserService(),
					this.getLocaleService(),
					this.getExcludedSyncIdentitiesService(),
					this.getPreferenceService(),
					this.getDeviceService(),
					this.getFileService(),
					this.getIdentityStore(),
					this.getBlackListService(),
					this.getLicenseService(),
					this.getApiService()
			);
		}

		return this.synchronizeContactsService;
	}

	public IdListService getBlackListService() {
		if(this.blackListService == null) {
			this.blackListService = new IdListServiceImpl("identity_list_blacklist", this.getPreferenceService());
		}
		return this.blackListService;
	}

	public DeadlineListService getMutedChatsListService() {
		if(this.mutedChatsListService == null) {
			this.mutedChatsListService = new DeadlineListServiceImpl("list_muted_chats", this.getPreferenceService());
		}
		return this.mutedChatsListService;
	}

	public DeadlineListService getHiddenChatsListService() {
		if(this.hiddenChatListService == null) {
			this.hiddenChatListService = new DeadlineListServiceImpl("list_hidden_chats", this.getPreferenceService());
		}
		return this.hiddenChatListService;
	}

	public DeadlineListService getMentionOnlyChatsListService() {
		if(this.mentionOnlyChatsListService == null) {
			this.mentionOnlyChatsListService = new DeadlineListServiceImpl("list_mention_only", this.getPreferenceService());
		}
		return this.mentionOnlyChatsListService;
	}

	public IdListService getExcludedSyncIdentitiesService() {
		if(this.excludedSyncIdentitiesService == null) {
			this.excludedSyncIdentitiesService = new IdListServiceImpl("identity_list_sync_excluded", this.getPreferenceService());
		}
		return this.excludedSyncIdentitiesService;
	}

	public UpdateSystemService getUpdateSystemService() {
		return this.updateSystemService;
	}

	public MessagePlayerService getMessagePlayerService() throws ThreemaException {
		if(this.messagePlayerService == null) {
			this.messagePlayerService = new MessagePlayerServiceImpl(
					getContext(),
					this.getMessageService(),
					this.getFileService(),
					this.getPreferenceService()
			);
		}
		return this.messagePlayerService;
	}

	public DownloadService getDownloadService() throws FileSystemNotPresentException {
		if (this.downloadService == null) {
			this.downloadService = new DownloadServiceImpl(
					this.getContext(),
					this.getFileService(),
					this.getApiService()
			);
		}
		return this.downloadService;
	}

	public BallotService getBallotService() throws NoIdentityException, MasterKeyLockedException, FileSystemNotPresentException {
		if(this.ballotService == null) {
			this.ballotService = new BallotServiceImpl(
					this.cacheService.getBallotModelCache(),
					this.cacheService.getLinkBallotModelCache(),
					this.databaseServiceNew,
					this.getUserService(),
					this.getGroupService(),
					this.getContactService(),
					this);
		}
		return this.ballotService;
	}

	public WallpaperService getWallpaperService() throws FileSystemNotPresentException {
		if(this.wallpaperService == null) {
			this.wallpaperService = new WallpaperServiceImpl(this.getContext(),
					this.getFileService(),
					this.getPreferenceService(),
					this.masterKey
			);
		}

		return this.wallpaperService;
	}

	public ThreemaSafeService getThreemaSafeService() throws FileSystemNotPresentException, MasterKeyLockedException {
		if(this.threemaSafeService == null) {
			this.threemaSafeService = new ThreemaSafeServiceImpl(this.getContext(), this.getPreferenceService(), this.getUserService(), this.getContactService(), this.getLocaleService(), this.getFileService(), this.getProfilePicRecipientsService(), this.getDatabaseServiceNew(), this.getIdentityStore(), this.getAPIConnector(), this.getHiddenChatsListService());
		}

		return this.threemaSafeService;
	}

	public Context getContext() {
		return ThreemaApplication.getAppContext();
	}

	public IdentityStore getIdentityStore() {
		return this.identityStore;
	}

	public RingtoneService getRingtoneService() {
		if(this.ringtoneService == null) {
			this.ringtoneService = new RingtoneServiceImpl(this.getPreferenceService());
		}

		return this.ringtoneService;
	}

	public BackupChatService getBackupChatService() throws ThreemaException {
		if (this.backupChatService == null) {
			this.backupChatService = new BackupChatServiceImpl(
					this.getContext(),
					this.getFileService(),
					this.getMessageService(),
					this.getContactService()
			);
		}

		return this.backupChatService;
	}

	public SystemScreenLockService getScreenLockService() {
		if(this.systemScreenLockService == null) {
			this.systemScreenLockService = new SystemScreenLockServiceImpl(
					this.getContext(),
					this.getLockAppService(),
					this.getPreferenceService()
			);
		}
		return this.systemScreenLockService;
	}

	public SensorService getSensorService() {
		if (this.sensorService == null) {
			this.sensorService = new SensorServiceImpl(this.getContext());
		}
		return this.sensorService;
	}

	@NonNull
	public WebClientServiceManager getWebClientServiceManager() throws ThreemaException {
		if (this.webClientServiceManager == null) {
			this.webClientServiceManager = new WebClientServiceManager(new ServicesContainer(
				this.getContext().getApplicationContext(),
				this.getLifetimeService(),
				this.getContactService(),
				this.getGroupService(),
				this.getDistributionListService(),
				this.getConversationService(),
				this.getConversationTagService(),
				this.getMessageService(),
				this.getNotificationService(),
				this.databaseServiceNew,
				this.getBlackListService(),
				this.preferencesService,
				this.getUserService(),
				this.getHiddenChatsListService(),
				this.getFileService(),
				this.getSynchronizeContactsService(),
				this.getLicenseService(),
				this.getMessageQueue()
			));
		}
		return this.webClientServiceManager;
	}

	@NonNull
	public BrowserDetectionService getBrowserDetectionService() {
		if (this.browserDetectionService == null) {
			this.browserDetectionService = new BrowserDetectionServiceImpl();
		}
		return this.browserDetectionService;
	}

	@NonNull
	public IdListService getProfilePicRecipientsService() {
		if(this.profilePicRecipientsService == null) {
			this.profilePicRecipientsService = new IdListServiceImpl("identity_list_profilepics", this.getPreferenceService());
		}
		return this.profilePicRecipientsService;
	}

	@NonNull
	public VoipStateService getVoipStateService() throws ThreemaException {
		if (this.voipStateService == null) {
			this.voipStateService = new VoipStateService(
					this.getContactService(),
					this.getRingtoneService(),
					this.getPreferenceService(),
					this.getMessageService(),
					this.getMessageQueue(),
					this.getLifetimeService(),
					this.getContext()
			);
		}
		return this.voipStateService;
	}

	public DatabaseServiceNew getDatabaseServiceNew() {
		return this.databaseServiceNew;
	}

	public DHSessionStoreInterface getDHSessionStore() throws MasterKeyLockedException {
		if (this.dhSessionStore == null) {
			this.dhSessionStore = new SQLDHSessionStore(this.getContext(), this.masterKey.getKey());
		}
		return this.dhSessionStore;
	}

	public ForwardSecurityMessageProcessor getForwardSecurityMessageProcessor() throws MasterKeyLockedException {
		if (!ConfigUtils.isForwardSecurityEnabled()) {
			return null;
		}

		if (this.forwardSecurityMessageProcessor == null) {
			this.forwardSecurityMessageProcessor = new ForwardSecurityMessageProcessor(
				this.getDHSessionStore(),
				this.getContactStore(),
				this.getIdentityStore(),
				this.getMessageQueue(),
				(sender, rejectedApiMessageId) -> {
					this.messageService.updateMessageStateForOutgoingMessage(rejectedApiMessageId, MessageState.FS_KEY_MISMATCH, new Date());
			}
			);

			try {
				this.forwardSecurityMessageProcessor.addStatusListener(new ForwardSecurityStatusSender(
					this.getContactService(),
					this.getMessageService(),
					this.getAPIConnector()
				));
			} catch (ThreemaException e) {
				logger.error("Exception while adding FS status listener", e);
			}
		}
		return this.forwardSecurityMessageProcessor;
	}

	public @NonNull
	SymmetricEncryptionService getSymmetricEncryptionService() {
		if (symmetricEncryptionService == null) {
			symmetricEncryptionService = new SymmetricEncryptionService();
		}
		return symmetricEncryptionService;
	}

	public @NonNull
	EmojiService getEmojiService() {
		if (emojiService == null) {
			EmojiSearchIndex searchIndex = new EmojiSearchIndex(
				getContext().getApplicationContext(),
				getPreferenceService()
			);
			emojiService = new EmojiService(
				getPreferenceService(),
				searchIndex,
				new EmojiRecent(getPreferenceService())
			);
		}
		return emojiService;
	}

	public @NonNull GroupCallManager getGroupCallManager() throws ThreemaException {
		if (groupCallManager == null) {
			groupCallManager = new GroupCallManagerImpl(
				getContext().getApplicationContext(),
				getDatabaseServiceNew(),
				getGroupService(),
				getContactService(),
				getPreferenceService(),
				getMessageService(),
				getGroupMessagingService(),
				getNotificationService(),
				getSfuConnection()
			);
		}
		return groupCallManager;
	}

	public @NonNull SfuConnection getSfuConnection() {
		if (sfuConnection == null) {
			sfuConnection = new SfuConnectionImpl(
				getAPIConnector(),
				getIdentityStore(),
				ThreemaApplication.getAppVersion()
			);
		}
		return sfuConnection;
	}
}
