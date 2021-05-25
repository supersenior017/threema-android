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

package ch.threema.app.fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.backuprestore.BackupRestoreDataConfig;
import ch.threema.app.backuprestore.BackupRestoreDataService;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.filepicker.FilePickerActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.TestUtil;

import static android.app.Activity.RESULT_OK;
import static ch.threema.app.filepicker.FilePickerActivity.INTENT_DATA_DEFAULT_PATH;
import static ch.threema.app.filepicker.FilePickerActivity.INTENT_DATA_SELECT_DIRECTORY;

public class BackupDataFragment extends Fragment implements
		GenericAlertDialog.DialogClickListener,
		PasswordEntryDialog.PasswordEntryDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(BackupDataFragment.class);

	public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 441;
	private static final int REQUEST_CODE_FILE_PICKER = 7221;
	private static final int REQUEST_CODE_DOCUMENT_TREE = 7222;

	private static final int PERMISSION_REQUEST_STORAGE_DOBACKUP = 2;

	private static final String DIALOG_TAG_ENERGY_SAVING_REMINDER = "esr";
	private static final String DIALOG_TAG_DISABLE_ENERGY_SAVING = "des";
	private static final String DIALOG_TAG_PASSWORD = "pwd";
	private static final String DIALOG_TAG_PATH_INTRO = "pathintro";

	private BackupRestoreDataService backupRestoreDataService;
	private View fragmentView;
	private ExtendedFloatingActionButton fab;
	private ServiceManager serviceManager;
	private FileService fileService;
	private PreferenceService preferenceService;
	private Uri backupUri;
	private TextView pathTextView;
	private NestedScrollView scrollView;
	private MaterialButton pathChangeButton;

	private boolean launchedFromFAB = false;

	@Override
	public void onDestroyView() {
		this.fab.setOnClickListener(null);
		this.fab = null;
		this.scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
		this.scrollView = null;
		this.pathChangeButton.setOnClickListener(null);
		this.pathChangeButton = null;
		this.pathTextView = null;

		fragmentView.findViewById(R.id.info).setOnClickListener(null);
		fragmentView.findViewById(R.id.restore).setOnClickListener(null);

		this.fragmentView = null;

		super.onDestroyView();
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);

		try {
			this.serviceManager = ThreemaApplication.getServiceManager();
			this.fileService = serviceManager.getFileService();
			this.preferenceService = serviceManager.getPreferenceService();
			this.backupRestoreDataService = serviceManager.getBackupRestoreDataService();
		} catch (Exception e) {
			logger.error("Exception", e);
			getActivity().finish();
		}

		backupUri = fileService.getBackupUri();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (this.fragmentView == null) {
			this.fragmentView = inflater.inflate(R.layout.fragment_backup_data, container, false);

			fab = fragmentView.findViewById(R.id.floating);
			fab.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					launchedFromFAB = true;
					initiateBackup();
				}
			});
			fab.show();

			scrollView = fragmentView.findViewById(R.id.scroll_parent);
			scrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
				@Override
				public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
					if (v.getTop() == scrollY) {
						fab.extend();
					} else {
						fab.shrink();
					}
				}
			});

			fragmentView.findViewById(R.id.info).setOnClickListener(v -> onInfoButtonClicked(v));
			fragmentView.findViewById(R.id.restore).setOnClickListener(v -> onRestoreButtonClicked(v));

			pathChangeButton = fragmentView.findViewById(R.id.backup_path_change_btn);
			pathChangeButton.findViewById(R.id.backup_path_change_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					launchedFromFAB = false;
					showPathSelectionIntro();
				}
			});

			pathTextView = fragmentView.findViewById(R.id.backup_path);
			pathTextView.setText(
				backupUri == null ?
				getString(R.string.not_set) :
				backupUri.toString());
		}

		Date backupDate = preferenceService.getLastDataBackupDate();
		if (backupDate != null) {
			this.fragmentView.findViewById(R.id.last_backup_layout).setVisibility(View.VISIBLE);
			this.fragmentView.findViewById(R.id.intro_layout).setVisibility(View.GONE);
			((TextView) this.fragmentView.findViewById(R.id.last_backup_date)).setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), backupDate.getTime()));
		} else {
			this.fragmentView.findViewById(R.id.last_backup_layout).setVisibility(View.GONE);
			this.fragmentView.findViewById(R.id.intro_layout).setVisibility(View.VISIBLE);
		}

		return this.fragmentView;
	}

	private void showPathSelectionIntro() {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.set_backup_path, R.string.set_backup_path_intro, R.string.ok, R.string.cancel);
		dialog.setTargetFragment(this);
		dialog.show(getFragmentManager(), DIALOG_TAG_PATH_INTRO);
	}

	private void launchDocumentTree() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			try {
				Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				// undocumented APIs according to https://issuetracker.google.com/issues/72053350
				i.putExtra("android.content.extra.SHOW_ADVANCED", true);
				i.putExtra("android.content.extra.FANCY", true);
				i.putExtra("android.content.extra.SHOW_FILESIZE", true);
				startActivityForResult(i, REQUEST_CODE_DOCUMENT_TREE);
			} catch (Exception e) {
				Toast.makeText(getContext(), "Your device is missing an activity for Intent.ACTION_OPEN_DOCUMENT_TREE. Contact the manufacturer of the device.", Toast.LENGTH_SHORT).show();
				logger.info("Broken device. No Activity for Intent.ACTION_OPEN_DOCUMENT_TREE");
			}
		} else {
			Intent intent = new Intent(getContext(), FilePickerActivity.class);
			if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(backupUri.getScheme())) {
				intent.putExtra(INTENT_DATA_DEFAULT_PATH, backupUri.getPath());
			}
			intent.putExtra(INTENT_DATA_SELECT_DIRECTORY, true);
			startActivityForResult(intent, REQUEST_CODE_FILE_PICKER);
		}
	}

	private void initiateBackup() {
		if (BackupService.isRunning()) {
			//show toast
			Toast.makeText(ThreemaApplication.getAppContext(), R.string.backup_in_progress, Toast.LENGTH_SHORT).show();
		} else {
			if (backupUri == null) {
				showPathSelectionIntro();
			} else {
				checkBatteryOptimizations();
			}
		}
	}

	private void checkBatteryOptimizations() {
		if (ConfigUtils.requestStoragePermissions(getActivity(), this, PERMISSION_REQUEST_STORAGE_DOBACKUP)) {
			Intent intent = new Intent(getActivity(), DisableBatteryOptimizationsActivity.class);
			intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, getString(R.string.backup_data));
			startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
		}
	}

	private void launchDataBackup(String password, boolean includeMedia) {
		if (TestUtil.required(this.backupRestoreDataService, password)) {
			final BackupRestoreDataConfig backupRestoreDataConfig = new BackupRestoreDataConfig(password);
			backupRestoreDataConfig
					.setBackupContactAndMessages(true)
					.setBackupIdentity(true)
					.setBackupAvatars(true)
					.setBackupMedia(includeMedia)
					.setBackupThumbnails(includeMedia)
					.setBackupVideoAndFiles(includeMedia);

			Intent intent = new Intent(getActivity(), BackupService.class);
			intent.putExtra(BackupService.EXTRA_BACKUP_RESTORE_DATA_CONFIG, backupRestoreDataConfig);
			ContextCompat.startForegroundService(getActivity(), intent);
			Toast.makeText(getActivity(), R.string.backup_started, Toast.LENGTH_SHORT).show();
			getActivity().finish();
		}
	}

	private void doBackup() {
		DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
				R.string.backup_data_new,
				R.string.backup_data_password_msg,
				R.string.password_hint,
				R.string.ok,
				R.string.cancel,
				ThreemaApplication.MIN_PW_LENGTH_BACKUP,
				ThreemaApplication.MAX_PW_LENGTH_BACKUP,
				R.string.backup_password_again_summary,
				0,
				R.string.backup_data_media,
				R.string.backup_data_media_confirm);
		dialogFragment.setTargetFragment(this, 0);
		dialogFragment.show(getFragmentManager(), DIALOG_TAG_PASSWORD);
	}

	@Override
	public void onYes(String tag, String text, boolean isChecked, Object data) {
		switch (tag) {
			case DIALOG_TAG_PASSWORD:
				launchDataBackup(text, isChecked);
				break;
		}
	}

	@Override
	public void onNo(String tag) {}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_ENERGY_SAVING_REMINDER:
				doBackup();
				break;
			case DIALOG_TAG_PATH_INTRO:
				launchDocumentTree();
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_DISABLE_ENERGY_SAVING:
				doBackup();
				break;
			case DIALOG_TAG_ENERGY_SAVING_REMINDER:
				break;
			default:
				break;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_STORAGE_DOBACKUP:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					checkBatteryOptimizations();
				} else {
					if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
						ConfigUtils.showPermissionRationale(getContext(), fragmentView, R.string.permission_storage_required);
					}
				}
				break;
		}
	}

	@UiThread
	private void onInfoButtonClicked(View v) {
		SimpleStringAlertDialog.newInstance(R.string.backup_data, R.string.data_backup_explain).show(getFragmentManager(), "tse");
	}

	private void onRestoreButtonClicked(View v) {
		SimpleStringAlertDialog.newInstance(R.string.restore, R.string.restore_data_backup_explain).show(getFragmentManager(), "re");
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
				GenericAlertDialog dialog;
				dialog = GenericAlertDialog.newInstance(R.string.backup_data_title, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_ENERGY_SAVING_REMINDER);
				break;
			case REQUEST_CODE_FILE_PICKER:
				if (resultCode == RESULT_OK) {
					String path = data.getStringExtra(FilePickerActivity.EXTRA_DIRECTORY);
					if (!TestUtil.empty(path)) {
						File file = new File(path);
						DocumentFile documentFile = DocumentFile.fromFile(file);
						if (documentFile.exists() && documentFile.isDirectory() && documentFile.canWrite()) {
							backupUri = documentFile.getUri();
							preferenceService.setDataBackupUri(backupUri);
							pathTextView.setText(path);
							return;
						}
					}
					Toast.makeText(getContext(), R.string.invalid_backup_path, Toast.LENGTH_LONG).show();
				}
				break;
			case REQUEST_CODE_DOCUMENT_TREE:
				if (resultCode == RESULT_OK) {
					if (data != null) {
						Uri treeUri = data.getData();
						if (treeUri != null) {
							// Persist access permissions.
							final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
							try {
								getContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
							} catch (SecurityException e) {
								logger.error("Exception", e);
							}
							backupUri = treeUri;
							preferenceService.setDataBackupUri(treeUri);
							pathTextView.setText(treeUri.toString());

							if (launchedFromFAB) {
								checkBatteryOptimizations();
							}

							return;
						}
					}
					Toast.makeText(getContext(), "Unable to set new path", Toast.LENGTH_LONG).show();
				}
		}
	}
}
