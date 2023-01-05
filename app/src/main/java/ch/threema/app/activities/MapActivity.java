/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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

package ch.threema.app.activities;

import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LAT;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LNG;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_NAME;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_PROVIDER;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.locationpicker.NearbyPoiUtil;
import ch.threema.app.locationpicker.Poi;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.LocationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.data.LocationDataModel;

public class MapActivity extends ThreemaActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MapActivity");

	private static final String DIALOG_TAG_ENABLE_LOCATION_SERVICES = "lss";

	private static final int REQUEST_CODE_LOCATION_SETTINGS = 22229;
	private static final int PERMISSION_REQUEST_LOCATION = 49;

	private static final int MAX_POI_COUNT = 50;

	// URLs for Threema Map server
	public static final String MAP_STYLE_URL = "https://map.threema.ch/styles/streets/style.json";

	private MapView mapView;
	private MapboxMap mapboxMap;
	private FrameLayout parentView;
	private Style mapStyle;

	private LocationManager locationManager;
	private LocationComponent locationComponent;

	private LatLng markerPosition;
	private String markerName, markerProvider;

	private PreferenceService preferenceService;

	private int insetTop = 0;

	private boolean isShowingExternalLocation = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (BuildConfig.DEBUG) {
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
					.detectAll()
					.penaltyLog()
					.build());
		}

		ConfigUtils.configureActivityTheme(this);

		setContentView(R.layout.activity_map);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
		getWindow().setStatusBarColor(Color.TRANSPARENT);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// we want dark icons, i.e. a light status bar
			getWindow().getDecorView().setSystemUiVisibility(
					getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}

		try {
			preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		} catch (Exception e) {
			logger.error("Exception", e);
			finish();
			return;
		}
		if (preferenceService == null) {
			finish();
			return;
		}
		if (BuildConfig.DEBUG && preferenceService.getPoiServerHostOverride() != null) {
			Toast.makeText(this, "Using POI host override", Toast.LENGTH_SHORT).show();
		}

		parentView = findViewById(R.id.coordinator);
		mapView = findViewById(R.id.map);

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) {
			finish();
			return;
		}

		mapView.onCreate(savedInstanceState);

		ViewCompat.setOnApplyWindowInsetsListener(parentView, new OnApplyWindowInsetsListener() {
			@Override
			public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
				insetTop = insets.getSystemWindowInsetTop();
				return insets;
			}
		});

		Intent intent = getIntent();

		if (intent.hasExtra(INTENT_DATA_LOCATION_LAT) || intent.hasExtra(INTENT_DATA_LOCATION_LNG)) {
			markerPosition = new LatLng(
				intent.getDoubleExtra(INTENT_DATA_LOCATION_LAT, 0),
				intent.getDoubleExtra(INTENT_DATA_LOCATION_LNG, 0));
			markerName = intent.getStringExtra(INTENT_DATA_LOCATION_NAME);
			markerProvider = intent.getStringExtra(INTENT_DATA_LOCATION_PROVIDER);
			isShowingExternalLocation = false;
		} else if (intent.getData() != null) {
			LocationDataModel locationData = GeoLocationUtil.getLocationDataFromGeoUri(intent.getData());
			if (locationData != null) {
				markerPosition = new LatLng(locationData.getLatitude(), locationData.getLongitude());
				isShowingExternalLocation = true;
			} else {
				Toast.makeText(this, R.string.cannot_display_location, Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

		initUi();
		initMap();
	}

	private void initUi() {
		findViewById(R.id.coordinator).setVisibility(View.VISIBLE);
		findViewById(R.id.center_map).setOnClickListener((it -> zoomToCenter()));
		Chip openChip = findViewById(R.id.open_chip);
		Chip shareChip = findViewById(R.id.share_chip);
		if (isShowingExternalLocation) {
			shareChip.setOnClickListener((it -> shareLocation()));
			openChip.setVisibility(View.GONE);
		} else {
			openChip.setOnClickListener((it -> openExternal()));
			shareChip.setVisibility(View.GONE);
		}
		TextView locationName = findViewById(R.id.location_name);
		TextView locationCoordinates = findViewById(R.id.location_coordinates);

		locationName.setText(markerName);
		locationCoordinates.setText(String.format(Locale.US, "%f, %f", markerPosition.getLatitude(), markerPosition.getLongitude()));
	}

	private void openExternal() {
		Intent intent = Intent.createChooser(new Intent(
			Intent.ACTION_VIEW,
			GeoLocationUtil.getLocationUri(markerPosition.getLatitude(), markerPosition.getLongitude(), markerName, markerProvider)
		), getString(R.string.open_in_maps_app));

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			// Don't allow opening location recursively
			intent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{getComponentName()});
		}

		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			SingleToast.getInstance().showShortText(getString(R.string.no_app_for_location));
		}
	}

	/**
	 * Share the currently displayed location within threema.
	 */
	private void shareLocation() {
		Intent intent = new Intent(this, RecipientListBaseActivity.class);

		intent.setAction(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_STREAM, GeoLocationUtil.getLocationUri(markerPosition.getLatitude(), markerPosition.getLongitude(), "", ""));
		startActivity(intent);
	}

	private void initMap() {
		mapView.getMapAsync(new OnMapReadyCallback() {
			@Override
			public void onMapReady(@NonNull MapboxMap mapboxMap1) {
				mapboxMap = mapboxMap1;
				mapboxMap.setStyle(new Style.Builder().fromUrl(MAP_STYLE_URL), new Style.OnStyleLoaded() {
					@Override
					public void onStyleLoaded(@NonNull Style style) {
						// Map is set up and the style has loaded. Now you can add data or make other mapView adjustments
						mapStyle = style;

						if (checkLocationEnabled(locationManager)) {
							setupLocationComponent(style);
						}
						mapboxMap.addMarker(getMarker(markerPosition, markerName, markerProvider));

						int marginTop = getResources().getDimensionPixelSize(R.dimen.map_compass_margin_top) + insetTop;
						int marginRight = getResources().getDimensionPixelSize(R.dimen.map_compass_margin_right);

						mapboxMap.getUiSettings().setCompassMargins(0, marginTop, marginRight, 0);

						moveCamera(markerPosition, false, -1);
						mapView.postDelayed(new Runnable() {
							@Override
							public void run() {
								moveCamera(markerPosition, true, 15);
							}
						}, 1200);

						showNearbyPOIs(markerPosition);
					}
				});
			}
		});
	}

	@SuppressLint("StaticFieldLeak")
	private void showNearbyPOIs(LatLng markerPosition) {
		new AsyncTask<LatLng, Void, List<MarkerOptions>>() {
			@Override
			protected List<MarkerOptions> doInBackground(LatLng... latLngs) {
				LatLng latLng = latLngs[0];
				List<Poi> pois = new ArrayList<>();
				NearbyPoiUtil.getPOIs(latLng, pois, MAX_POI_COUNT, preferenceService);

				List<MarkerOptions> markerOptions = new ArrayList<>();
				for (Poi poi: pois) {
					markerOptions.add(new MarkerOptions()
							.position(poi.getLatLng())
							.title(poi.getName())
							.setIcon(LocationUtil.getMarkerIcon(MapActivity.this, poi))
							.setSnippet(poi.getDescription()));
				}

				return markerOptions;
			}

			@Override
			protected void onPostExecute(List<MarkerOptions> markerOptions) {
				if (markerOptions.size() > 0) {
					mapboxMap.addMarkers(markerOptions);
				}
			}
		}.execute(markerPosition);
	}

	@SuppressLint("MissingPermission")
	private void setupLocationComponent(Style style) {
		logger.debug("setupLocationComponent");

		locationComponent = mapboxMap.getLocationComponent();
		locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(this, style).build());
		locationComponent.setCameraMode(CameraMode.NONE);
		locationComponent.setRenderMode(RenderMode.COMPASS);
		locationComponent.setLocationComponentEnabled(true);
	}

	@Override
	protected void onStart() {
		logger.debug("onStart");
		super.onStart();
		if (mapView != null) {
			mapView.onStart();
		}
	}

	@Override
	public void onResume() {
		logger.debug("onResume");
		super.onResume();
		mapView.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mapView.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		mapView.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapView.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mapView.onDestroy();
	}

	private boolean checkLocationEnabled(LocationManager locationManager) {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		}
		return false;
	}

	private boolean requestLocationEnabled(LocationManager locationManager) {
		if (ConfigUtils.requestLocationPermissions(this, null, PERMISSION_REQUEST_LOCATION)) {
			if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				setupLocationComponent(mapStyle);
				return true;
			}
			GenericAlertDialog.newInstance(R.string.your_location, R.string.location_services_disabled, R.string.yes, R.string.no).show(getSupportFragmentManager(), DIALOG_TAG_ENABLE_LOCATION_SERVICES);
			return false;
		}
		return false;
	}

	@SuppressLint("MissingPermission")
	private void zoomToCenter() {
		if (requestLocationEnabled(locationManager)) {
			locationComponent.setLocationComponentEnabled(true);
			Location location = locationComponent.getLastKnownLocation();
			// TODO: Wait for a fix if there's no last known location
			if (location != null) {
				moveCamera(new LatLng(location.getLatitude(), location.getLongitude()), true, -1);
			}
		}
	}

	private void moveCamera(LatLng latLng, boolean animate, int zoomLevel) {
		long time = System.currentTimeMillis();
		logger.debug("moveCamera to " + latLng.toString());

		mapboxMap.cancelTransitions();
		mapboxMap.addOnCameraIdleListener(new MapboxMap.OnCameraIdleListener() {
			@Override
			public void onCameraIdle() {
				mapboxMap.removeOnCameraIdleListener(this);
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						logger.debug("camera has been moved. Time in ms = " + (System.currentTimeMillis() - time));
					}
				});
			}
		});

		CameraUpdate cameraUpdate = zoomLevel != -1 ?
				CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel) :
				CameraUpdateFactory.newLatLng(latLng);

		if (animate) {
			mapboxMap.animateCamera(cameraUpdate);
		} else {
			mapboxMap.moveCamera(cameraUpdate);
		}
	}

	private MarkerOptions getMarker(LatLng latLng, String name, String provider) {
		Bitmap bitmap = BitmapUtil.getBitmapFromVectorDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_map_center_marker), null);

		return new MarkerOptions()
				.position(latLng)
				.title(name)
				.setIcon(IconFactory.getInstance(this).fromBitmap(LocationUtil.moveMarker(bitmap)))
				.setSnippet(provider);
	}

	@Override
	public void onYes(String tag, Object data) {
		if (DIALOG_TAG_ENABLE_LOCATION_SERVICES.equals(tag)) {
			startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_LOCATION_SETTINGS);
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		// do nothing
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_LOCATION_SETTINGS) {
			zoomToCenter();
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (requestCode == PERMISSION_REQUEST_LOCATION) {
				requestLocationEnabled(locationManager);
			}
		}
	}
}
