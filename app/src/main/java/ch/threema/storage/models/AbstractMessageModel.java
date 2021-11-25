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

package ch.threema.storage.models;


import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.MessageDataInterface;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.BallotDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.ImageDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;
import ch.threema.storage.models.data.status.StatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public abstract class AbstractMessageModel {
	/** The message id, unique per type. */
	public static final String COLUMN_ID = "id";
	/** The message uid, unique globally. */
	public static final String COLUMN_UID = "uid";
	/** The chat protocol message id assigned by the sender. */
	public static final String COLUMN_API_MESSAGE_ID = "apiMessageId";
	/** Identity of the conversation partner. */
	public static final String COLUMN_IDENTITY = "identity";
	/** Message direction. true = outgoing, false = incoming. */
	public static final String COLUMN_OUTBOX = "outbox";
	/** Message type. */
	public static final String COLUMN_TYPE = "type";
	/** Correlation ID. */
	public static final String COLUMN_CORRELATION_ID = "correlationId";
	/** Message body. */
	public static final String COLUMN_BODY= "body";
	/** Message caption. */
	public static final String COLUMN_CAPTION= "caption";
	/** Whether this message has been read by the receiver. */
	public static final String COLUMN_IS_READ = "isRead";
	/** Whether this message has been saved to the internal database. */
	public static final String COLUMN_IS_SAVED = "isSaved";
	/** The message state. */
	public static final String COLUMN_STATE = "state";
	/** When the message was created. */
	public static final String COLUMN_CREATED_AT = "createdAtUtc";
	/** When the message was accepted by the server. */
	public static final String COLUMN_POSTED_AT = "postedAtUtc";
	/** When the message was last modified. */
	public static final String COLUMN_MODIFIED_AT = "modifiedAtUtc";
	/** Whether this message is a status message. */
	public static final String COLUMN_IS_STATUS_MESSAGE = "isStatusMessage";
	/** Whether this message was saved to the message queue. */
	public static final String COLUMN_IS_QUEUED = "isQueued";
	/** API message id of quoted message, if any. */
	public static final String COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID = "quotedMessageId";
	/** contents type of message - may be different from type */
	public static final String COLUMN_MESSAGE_CONTENTS_TYPE = "messageContentsType";
	/** message flags that affect delivery receipt behavior etc. - carried over from AbstractMessage */
	public static final String COLUMN_MESSAGE_FLAGS = "messageFlags";
	/** When the message was delivered. */
	public static final String COLUMN_DELIVERED_AT = "deliveredAtUtc";
	/** When the message was read. */
	public static final String COLUMN_READ_AT = "readAtUtc";

	private int id;
	private String uid;
	private String apiMessageId;
	private String identity;
	private boolean outbox;
	private MessageType type;
	private String correlationId;
	private String body;
	private boolean isRead;
	private boolean isSaved;
	private MessageState state;
	private Date postedAt;
	private Date createdAt;
	private Date deliveredAt;
	private Date readAt;
	private Date modifiedAt;
	private boolean isStatusMessage;
	private boolean isQueued;
	private String caption;
	private String quotedMessageId;
	private @MessageContentsType int messageContentsType;
	private int messageFlags;

	AbstractMessageModel() {
	}

	AbstractMessageModel(boolean isStatusMessage) {
		this.isStatusMessage = isStatusMessage;
	}

	/**
	 * Return The message id, unique per message type.
	 */
	public int getId() {
		return id;
	}

	public AbstractMessageModel setId(int id) {
		this.id = id;
		return this;
	}

	/**
	 * Return the message uid, globally unique.
	 */
	public String getUid() {
		return this.uid;
	}

	public boolean isStatusMessage() {
		return this.isStatusMessage;
	}

	public AbstractMessageModel setIsStatusMessage(boolean is) {
		this.isStatusMessage = is;
		return this;
	}

	public AbstractMessageModel setUid(String uid) {
		this.uid = uid;
		return this;
	}

	/**
	 * Return the associated identity: Either the recipient
	 * (for outgoing messages) or the sender (for incoming messages).
	 */
	public String getIdentity() {
		return identity;
	}

	public AbstractMessageModel setIdentity(String identity) {
		this.identity = identity;
		return this;
	}

	public boolean isOutbox() {
		return outbox;
	}

	public AbstractMessageModel setOutbox(boolean outbox) {
		this.outbox = outbox;
		if (outbox) {
			// Outgoing messages can't be unread
			this.isRead = true;
		}
		return this;
	}

	public MessageType getType() {
		return type;
	}

	public AbstractMessageModel setType(MessageType type) {
		this.type = type;
		return this;
	}

	public String getBody() {
		return body;
	}

	public AbstractMessageModel setBodyAndQuotedMessageId(String body) {
		// extract body and ApiMessageId from quote
		if (QuoteUtil.isQuoteV2(body)) {
			QuoteUtil.addBodyAndQuotedMessageId(this, body);
		} else {
			setBody(body);
			setQuotedMessageId(null);
		}
		return this;
	}

	public AbstractMessageModel setBody(String body) {
		this.body = body;
		return this;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public AbstractMessageModel setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
		return this;
	}

	public boolean isRead() {
		return this.outbox || this.isRead;
	}

	public AbstractMessageModel setRead(boolean read) {
		isRead = read;
		return this;
	}

	public boolean isSaved() {
		return this.isSaved;
	}

	public AbstractMessageModel setSaved(boolean saved) {
		this.isSaved = saved;
		return this;
	}

	public MessageState getState() {
		return state;
	}

	public AbstractMessageModel setState(MessageState state) {
		this.state = state;
		return this;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public AbstractMessageModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Date getModifiedAt() {
		return modifiedAt;
	}

	public AbstractMessageModel setModifiedAt(Date modifiedAt) {
		this.modifiedAt = modifiedAt;
		if (getState() == MessageState.DELIVERED) {
			this.deliveredAt = modifiedAt;
		} else if (getState() == MessageState.READ) {
			this.readAt = modifiedAt;
		}
		return this;
	}

	@Nullable
	public Date getPostedAt(boolean fallbackToCreateDate) {
		if (this.postedAt != null) {
			return this.postedAt;
		} else if (fallbackToCreateDate && this.createdAt != null) {
			return this.createdAt;
		}
		return null;
	}

	@Nullable
	public Date getPostedAt() {
		return this.getPostedAt(true);
	}

	public AbstractMessageModel setPostedAt(Date postedAt) {
		this.postedAt = postedAt;
		return this;
	}

	@Nullable
	public Date getDeliveredAt() { return this.deliveredAt; }

	public AbstractMessageModel setDeliveredAt(Date deliveredAt) {
		this.deliveredAt = deliveredAt;
		return this;
	}

	@Nullable
	public Date getReadAt() { return this.readAt; }

	public AbstractMessageModel setReadAt(Date readAt) {
		this.readAt = readAt;
		return this;
	}

	public int getMessageFlags() {
		return messageFlags;
	}

	public AbstractMessageModel setMessageFlags(int messageFlags) {
		this.messageFlags = messageFlags;
		return this;
	}

	/**
	 * Return the chat protocol message id assigned by the sender.
	 */
	@Nullable
	public String getApiMessageId() {
		return apiMessageId;
	}

	public AbstractMessageModel setApiMessageId(String apiMessageId) {
		this.apiMessageId = apiMessageId;
		return this;
	}

	private MessageDataInterface dataObject;

	public @NonNull LocationDataModel getLocationData() {
		if(this.dataObject == null) {
			this.dataObject = LocationDataModel.create(this.getBody());
		}
		return (LocationDataModel)this.dataObject;
	}

	public void setLocationData(LocationDataModel locationData) {
		this.setType(MessageType.LOCATION);
		this.setBody(locationData.toString());
		this.dataObject = locationData;
	}

	public @NonNull VideoDataModel getVideoData() {
		if(this.dataObject == null) {
			this.dataObject = VideoDataModel.create(this.getBody());
		}
		return (VideoDataModel)this.dataObject;
	}

	public void setVideoData(VideoDataModel videoDataModel) {
		this.setType(MessageType.VIDEO);
		this.setBody(videoDataModel.toString());
		this.dataObject = videoDataModel;
	}

	public @NonNull AudioDataModel getAudioData() {
		if(this.dataObject == null) {
			this.dataObject = AudioDataModel.create(this.getBody());
		}
		return (AudioDataModel)this.dataObject;
	}

	public void setAudioData(AudioDataModel audioDataModel) {
		this.setType(MessageType.VOICEMESSAGE);
		this.setBody(audioDataModel.toString());
		this.dataObject = audioDataModel;
	}

	public void setVoipStatusData(VoipStatusDataModel statusDataModel) {
		this.setType(MessageType.VOIP_STATUS);
		this.setBody(StatusDataModel.convert(statusDataModel));
		this.dataObject = statusDataModel;
	}

	public @NonNull VoipStatusDataModel getVoipStatusData() {
		if(this.dataObject == null) {
			this.dataObject = StatusDataModel.convert(this.getBody());
		}
		return (VoipStatusDataModel)this.dataObject;
	}

	public void setImageData(ImageDataModel imageDataModel) {
		this.setType(MessageType.IMAGE);
		this.setBody(imageDataModel.toString());
		this.dataObject = imageDataModel;
	}

	public @NonNull ImageDataModel getImageData() {
		if(this.dataObject == null) {
			this.dataObject = ImageDataModel.create(this.getBody());
		}
		return (ImageDataModel)this.dataObject;
	}

	public @NonNull BallotDataModel getBallotData() {
		if(this.dataObject == null) {
			this.dataObject = BallotDataModel.create(this.getBody());
		}
		return (BallotDataModel)this.dataObject;
	}

	public void setBallotData(BallotDataModel ballotDataModel) {
		this.setType(MessageType.BALLOT);
		this.setBody(ballotDataModel.toString());
		this.dataObject = ballotDataModel;
	}

	public @NonNull FileDataModel getFileData() {
		if(this.dataObject == null) {
			this.dataObject = FileDataModel.create(this.getBody());
		}
		return (FileDataModel)this.dataObject;
	}

	public void setFileData(FileDataModel fileDataModel) {
		this.setType(MessageType.FILE);
		this.setBody(fileDataModel.toString());
		this.dataObject = fileDataModel;
	}

	/**
	 * Call this to update the body field with the data model stuff
	 */
	public AbstractMessageModel writeDataModelToBody() {
		if (this.dataObject != null) {
			this.setBody(this.dataObject.toString());
		}
		return this;
	}

	public boolean isAvailable() {
		switch (this.getType()) {
			case IMAGE:
				return this.isOutbox() || this.getImageData().isDownloaded();
			case VIDEO:
				return this.isOutbox() || this.getVideoData().isDownloaded();
			case VOICEMESSAGE:
				return this.isOutbox() || this.getAudioData().isDownloaded();
			case FILE:
				return this.isOutbox() || this.getFileData().isDownloaded();
		}
		return true;
	}


	public boolean isQueued() {
		return isQueued;
	}

	public AbstractMessageModel setIsQueued(boolean isQueued) {
		this.isQueued = isQueued;
		return this;
	}

	public String getCaption() {
		switch (this.getType()) {
			case FILE:
				return this.getFileData().getCaption();
			case LOCATION:
				return TestUtil.empty(this.getLocationData().getPoi()) ?
						this.getLocationData().getAddress() :
						"*" + this.getLocationData().getPoi() + "*\n" + this.getLocationData().getAddress();
			default:
				return this.caption;
		}
	}

	public AbstractMessageModel setCaption(String caption) {
		this.caption = caption;
		return this;
	}

	public String getQuotedMessageId() {
		return quotedMessageId;
	}

	public AbstractMessageModel setQuotedMessageId(String quotedMessageId) {
		this.quotedMessageId = quotedMessageId;
		return this;
	}

	public @MessageContentsType int getMessageContentsType() {
		return messageContentsType;
	}

	public AbstractMessageModel setMessageContentsType(@MessageContentsType int messageContentsType) {
		this.messageContentsType = messageContentsType;
		return this;
	}

	/**
	 * TODO: evil code!
	 * @param sourceModel
	 */
	public void copyFrom(AbstractMessageModel sourceModel) {
		//copy all objects
		this.dataObject = sourceModel.dataObject;
		this
				.setCorrelationId(sourceModel.getCorrelationId())
				.setSaved(sourceModel.isSaved())
				.setIsQueued(sourceModel.isQueued())
				.setState(sourceModel.getState())
				.setModifiedAt(sourceModel.getModifiedAt())
				.setDeliveredAt(sourceModel.getDeliveredAt())
				.setReadAt(sourceModel.getReadAt())
				.setBody(sourceModel.getBody())
				.setCaption(sourceModel.getCaption())
				.setQuotedMessageId(sourceModel.getQuotedMessageId())
				;
	}
}
