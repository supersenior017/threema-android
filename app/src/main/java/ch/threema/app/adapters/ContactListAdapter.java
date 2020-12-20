/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

package ch.threema.app.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.ContactModel;

public class ContactListAdapter extends FilterableListAdapter implements SectionIndexer {
	private static final Logger logger = LoggerFactory.getLogger(ContactListAdapter.class);

	private static final int MAX_RECENTLY_ADDED_CONTACTS = 3;

	private final ContactService contactService;
	private final PreferenceService preferenceService;
	private final IdListService blackListIdentityService;
	private final Context context;

	public static final int VIEW_TYPE_NORMAL = 0;
	public static final int VIEW_TYPE_COUNT = 2;

	private static final String PLACEHOLDER_BLANK_HEADER = " ";
	private static final String PLACEHOLDER_CHANNELS = "\uffff";
	private static final String PLACEHOLDER_RECENTLY_ADDED = "\u0001";
	private static final String CHANNEL_SIGN = "\u002a";
	private static final String RECENTLY_ADDED_SIGN = "+";

	private List<ContactModel> values, ovalues, recentlyAdded = new ArrayList<>();
	private ContactListFilter contactListFilter;
	private AvatarListener avatarListener;
	private Bitmap defaultContactImage;
	private final HashMap<String, Integer> alphaIndexer = new HashMap<String, Integer>();
	private final HashMap<Integer, String> positionIndexer = new HashMap<Integer, String>();
	private String[] sections;
	private Integer[] counts;
	private LayoutInflater inflater;
	private Collator collator;

	public interface AvatarListener {
		void onAvatarClick(View view, int position);
		boolean onAvatarLongClick(View view, int position);
	}

	public ContactListAdapter(@NonNull Context context, @NonNull List<ContactModel> values, ContactService contactService, PreferenceService preferenceService, IdListService blackListIdentityService, AvatarListener avatarListener) {
		super(context, R.layout.item_contact_list, (List<Object>) (Object) values);

		this.values = updateRecentlyAdded(values);
		this.ovalues = this.values;
		this.contactService = contactService;
		this.context = context;
		this.preferenceService = preferenceService;
		this.blackListIdentityService = blackListIdentityService;
		this.defaultContactImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact);
		this.avatarListener = avatarListener;
		this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		this.collator = Collator.getInstance();
		this.collator.setStrength(Collator.PRIMARY);

		setupIndexer();
	}

	private List<ContactModel> updateRecentlyAdded(List<ContactModel> all) {
		ArrayList<ContactModel> recents = new ArrayList<>();
		Date recentlyAddedDate = new Date(System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS);

		for (ContactModel contactModel : all) {
			if (contactModel != null && contactModel.getDateCreated() != null && recentlyAddedDate.before(contactModel.getDateCreated()) && !ContactUtil.isChannelContact(contactModel)) {
				recents.add(contactModel);
			}
		}

		if (recents.size() > 0) {
			// filter latest
			Collections.sort(recents, (o1, o2) -> o2.getDateCreated().compareTo(o1.getDateCreated()));
			this.recentlyAdded = recents.subList(0, Math.min(recents.size() , MAX_RECENTLY_ADDED_CONTACTS));

			all.removeAll(this.recentlyAdded);
			all.addAll(0, this.recentlyAdded);
		} else {
			this.recentlyAdded.clear();
		}
		return all;
	}

	public void updateData(@NonNull List<ContactModel> all) {
		setNotifyOnChange(false);
		this.values = updateRecentlyAdded(all);
		this.ovalues = this.values;
		setupIndexer();
		setNotifyOnChange(true);
		notifyDataSetChanged();
	}

	private boolean containsKeyLocaleAware(String newKey) {
		for (String key : alphaIndexer.keySet()) {
			if (collator.equals(key, newKey)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get Unicode-aware index character for headers and thumbscroller
	 * @param input Input string
	 * @return Unicode character at beginning of input
	 */
	private String getIndexCharacter(String input) {
		try {
			int codePoint = Character.codePointAt(input, 0);
			return input.substring(0, Character.charCount(codePoint)).toUpperCase();
		} catch (Exception e) {
			return input.substring(0, 1).toUpperCase();
		}
	}

	private void setupIndexer() {
		int size = values.size();
		String firstLetter, sortingValue;

		alphaIndexer.clear();
		positionIndexer.clear();

		// create index for fast scroll
		for (int i = 0; i < size; i++) {
			ContactModel c = values.get(i);

			if (c == null) {
				// this case only happens if setupList() is called on
				// values that already have headers added
				values.remove(i);
				i--;
				size--;
				continue;
			}

			firstLetter = getInitial(c, false);

			if (PLACEHOLDER_BLANK_HEADER.equals(firstLetter) ||
					PLACEHOLDER_CHANNELS.equals(firstLetter) ||
					PLACEHOLDER_RECENTLY_ADDED.equals(firstLetter)) {
				// placeholders
				if (!alphaIndexer.containsKey(firstLetter)) {
					alphaIndexer.put(firstLetter, i);
					positionIndexer.put(i, firstLetter);
				}
			} else {
				if (!containsKeyLocaleAware(firstLetter)) {
					firstLetter = Normalizer.normalize(firstLetter, Normalizer.Form.NFD);
					alphaIndexer.put(firstLetter, i);
					positionIndexer.put(i, firstLetter);
				}
			}
		}

		// create a list from the set to sort
		ArrayList<String> sectionList = new ArrayList<String>(alphaIndexer.keySet());
		Collections.sort(sectionList, collator);
		if (sectionList.contains(PLACEHOLDER_CHANNELS)) {
			// replace channels placeholder by copyright sign AFTER sorting
			sectionList.set(sectionList.indexOf(PLACEHOLDER_CHANNELS), CHANNEL_SIGN);
			if (alphaIndexer.containsKey(PLACEHOLDER_CHANNELS)) {
				alphaIndexer.put(CHANNEL_SIGN, alphaIndexer.get(PLACEHOLDER_CHANNELS));
				alphaIndexer.remove(PLACEHOLDER_CHANNELS);
			}
		}
		sections = new String[sectionList.size()];
		sectionList.toArray(sections);

		// create array for reverse lookup
		ArrayList<Integer> countsList = new ArrayList<Integer>(positionIndexer.keySet());
		Collections.sort(countsList);
		counts = new Integer[countsList.size()];
		countsList.toArray(counts);
	}

	private String getInitial(ContactModel c, boolean afterSorting) {
		String firstLetter, sortingValue;

		sortingValue = ContactUtil.getSafeNameStringNoNickname(c, preferenceService);
		if (sortingValue.length() == 0) {
			firstLetter = PLACEHOLDER_BLANK_HEADER;
		} else {
			if (ContactUtil.isChannelContact(c)) {
				firstLetter = afterSorting ? CHANNEL_SIGN : PLACEHOLDER_CHANNELS;
			} else if (recentlyAdded != null && recentlyAdded.size() > 0 && recentlyAdded.contains(c)) {
				firstLetter = afterSorting ? RECENTLY_ADDED_SIGN : PLACEHOLDER_RECENTLY_ADDED;
			} else {
				firstLetter = getIndexCharacter(sortingValue);
			}
		}
		return firstLetter;
	}

	private static class ContactListHolder extends AvatarListItemHolder {
		TextView nameTextView;
		TextView idTextView;
		TextView nickTextView;
		VerificationLevelImageView verificationLevelView;
		ImageView blockedContactView;
		EmojiTextView initialView;
		ImageView initialImageView;
		int originalPosition;
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
		CheckableConstraintLayout itemView = (CheckableConstraintLayout) convertView;

		ContactListHolder holder;

		if (convertView == null) {
			// This a new view we inflate the new layout
			holder = new ContactListHolder();
			itemView = (CheckableConstraintLayout) inflater.inflate(R.layout.item_contact_list, parent, false);

			holder.nameTextView = itemView.findViewById(R.id.name);
			holder.idTextView = itemView.findViewById(R.id.subject);
			holder.nickTextView = itemView.findViewById(R.id.nick);
			holder.verificationLevelView = itemView.findViewById(R.id.verification_level);
			holder.avatarView = itemView.findViewById(R.id.avatar_view);
			holder.blockedContactView = itemView.findViewById(R.id.blocked_contact);
			holder.initialView = itemView.findViewById(R.id.initial);
			holder.initialImageView = itemView.findViewById(R.id.initial_image);

			itemView.setTag(holder);
			itemView.setOnCheckedChangeListener(new CheckableConstraintLayout.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked) {
					if (isChecked) {
						checkedItems.add(((ContactListHolder) checkableView.getTag()).originalPosition);
					} else {
						checkedItems.remove(((ContactListHolder) checkableView.getTag()).originalPosition);
					}
				}
			});
		} else {
			holder = (ContactListHolder) itemView.getTag();
		}

		holder.avatarView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				avatarListener.onAvatarClick(v, position);
			}
		});

		holder.avatarView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				return avatarListener.onAvatarLongClick(v, position);
			}
		});

		final ContactModel contactModel = values.get(position);
		holder.originalPosition = ovalues.indexOf(contactModel);

		String filterString = null;
		if (contactListFilter != null) {
			filterString = contactListFilter.getFilterString();
		}

		String displayName = NameUtil.getDisplayNameOrNickname(contactModel, true);

		ViewUtil.showAndSet(
				holder.nameTextView,
				highlightMatches(displayName, filterString, true));

		holder.avatarView.setContentDescription(
				ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
						ThreemaApplication.getAppContext().getString(R.string.mime_contact),
						displayName));

		AdapterUtil.styleContact(holder.nameTextView, contactModel);

		ViewUtil.showAndSet(
				holder.idTextView,
				highlightMatches(contactModel.getIdentity(), filterString, true));

		AdapterUtil.styleContact(holder.idTextView, contactModel);

		holder.verificationLevelView.setContactModel(contactModel);

		ViewUtil.show(
				holder.blockedContactView,
				blackListIdentityService != null && blackListIdentityService.has(contactModel.getIdentity()));

		if (displayName.length() > 1 && displayName.startsWith("~") && displayName.substring(1).equals(contactModel.getPublicNickName())) {
			holder.nickTextView.setText("");
		} else {
			NameUtil.showNicknameInView(holder.nickTextView, contactModel, filterString, this);
		}

		AvatarListItemUtil.loadAvatar(
				position,
				contactModel,
				this.defaultContactImage,
				this.contactService,
				holder);

		String previousInitial = PLACEHOLDER_CHANNELS;
		String currentInitial = getInitial(contactModel, true);
		if (position > 0) {
			previousInitial = getInitial(values.get(position - 1), true);
		}
		if (previousInitial != null && !previousInitial.equals(currentInitial)) {
			if (RECENTLY_ADDED_SIGN.equals(currentInitial)) {
				holder.initialView.setVisibility(View.GONE);
				holder.initialImageView.setVisibility(View.VISIBLE);
			} else {
				holder.initialView.setText(currentInitial);
				holder.initialView.setVisibility(View.VISIBLE);
				holder.initialImageView.setVisibility(View.GONE);
			}
		} else {
			holder.initialView.setVisibility(View.GONE);
			holder.initialImageView.setVisibility(View.GONE);
		}

		holder.avatarView.setBadgeVisible(contactService.showBadge(contactModel));

		return itemView;
	}

	@Override
	public int getItemViewType(int position) {
		return VIEW_TYPE_NORMAL;
	}

	@Override
	public int getViewTypeCount() {
		return VIEW_TYPE_COUNT;
	}

	@Override
	public boolean isEnabled(int position) {
		return true;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public int getPositionForSection(int section) {
		if (section < 0 || section >= sections.length) {
			return -1;
		}

		return alphaIndexer.get(sections[section]);
	}

	@Override
	public int getSectionForPosition(int position) {
		if (position < 0 || position >= values.size()) {
			return -1;
		}
		int index = Arrays.binarySearch(counts, position);

        /*
         * Consider this example: section positions are 0, 3, 5; the supplied
         * position is 4. The section corresponding to position 4 starts at
         * position 3, so the expected return value is 1. Binary search will not
         * find 4 in the array and thus will return -insertPosition-1, i.e. -3.
         * To get from that number to the expected value of 1 we need to negate
         * and subtract 2.
         */
		return index >= 0 ? index : -index - 2;
	}

	@Override
	public Object[] getSections() {
		return sections;
	}

	public class ContactListFilter extends Filter {
		String filterString = null;

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();

			if (constraint == null || constraint.length() == 0) {
				// no filtering
				filterString = null;
				results.values = ovalues;
				results.count = ovalues.size();
			} else {
				// perform filtering
				List<ContactModel> nContactList = new ArrayList<ContactModel>();
				filterString = LocaleUtil.normalize(constraint.toString());

				for (ContactModel contactModel : ovalues) {
					if (contactModel != null) {
						if ((LocaleUtil.normalize(NameUtil.getDisplayNameOrNickname(contactModel, false)).contains(filterString)) ||
							(contactModel.getIdentity().toUpperCase().contains(filterString))) {
							nContactList.add(contactModel);
						}
					}
				}
				results.values = nContactList;
				results.count = nContactList.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			values = (List<ContactModel>) results.values;
			notifyDataSetChanged();
		}

		public String getFilterString() {
			return filterString;
		}
	}

	@Override
	public Filter getFilter() {
		if (contactListFilter == null)
			contactListFilter = new ContactListFilter();

		return contactListFilter;
	}

	@Override
	public int getCount() {
		return values != null ? values.size() : 0;
	}

	@Override
	public HashSet<ContactModel> getCheckedItems() {
		HashSet<ContactModel> contacts = new HashSet<>();
		ContactModel contactModel;

		for (int position: checkedItems) {
			contactModel = ovalues.get(position);
			if (contactModel != null) {
				contacts.add(contactModel);
			}
		}
		return contacts;
	}

	@Override
	public ContactModel getClickedItem(View v) {
		if (ovalues.size() > 0) {
			return ovalues.get(getClickedItemPosition(v));
		}
		return null;
	}

	public int getClickedItemPosition(View v) {
		if (v != null && v.getTag() != null) {
			return ((ContactListAdapter.ContactListHolder) v.getTag()).originalPosition;
		}
		return 0;
	}

	public String getInitial(int position) {
		if (position < values.size() && position > 0) {
			return getInitial(values.get(position), true);
		}
		return "";
	}
}
