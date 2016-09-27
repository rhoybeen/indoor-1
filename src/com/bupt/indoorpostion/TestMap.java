package com.bupt.indoorpostion;

import com.sails.engine.SAILS;
import com.sails.engine.SAILSMapView;
import com.sails.engine.core.model.GeoPoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import com.bupt.indoorPosition.bean.Beacon;
import com.bupt.indoorPosition.model.ModelService;
import com.bupt.indoorPosition.uti.BeaconUtil;
import com.sails.engine.MapViewPosition;
import com.sails.engine.core.model.BoundingBox;
import com.sails.engine.overlay.ListOverlay;
import com.sails.engine.overlay.Marker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class TestMap extends Activity {

	static SAILS mSails;
	static SAILSMapView mSailsMapView;
	ImageView zoomin;
	ImageView zoomout;
	ImageView lockcenter;
	EditText editText1;
	EditText editText2;
	Button locationButton;
	Button clearButton;
	TextView locationTextView;
	Vibrator mVibrator;
	Spinner floorList;
	ArrayAdapter<String> adapter;
	byte zoomSav = 0;

	int flag = 0;
	GeoPoint geoPointCenter;
	GeoPoint geoPointLocationLB;
	GeoPoint geoPointLocationRT;
	BoundingBox boundingBox;
	MapViewPosition mapViewPositionBase;

	private Set<Beacon> beaconSet;
	private BluetoothAdapter bAdapter;

	private Timer findLostBeaconTimer;
	private Handler handler;

	// 重启BLE扫描计时
	private int scanCount = 0;
	private int localizationCount = 0;
	// 蓝牙没有反应计时
	private int bleNoReactCount = 0;
	private long timeZero;
	private int count = 0;
	private Timer LocalizationTimer;
	private Timer GetBluetoothDeviceTimer;
	private Set<Beacon> beaconMap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.maptest);

		handler = new MAHandler();

		zoomin = (ImageView) findViewById(R.id.zoomin);
		zoomout = (ImageView) findViewById(R.id.zoomout);
		lockcenter = (ImageView) findViewById(R.id.lockcenter);
		locationButton = (Button) findViewById(R.id.locationButton);
		clearButton = (Button) findViewById(R.id.clearButton);

		locationTextView = (TextView) findViewById(R.id.locationText);
		floorList = (Spinner) findViewById(R.id.spinner);

		initComponent();

		zoomin.setOnClickListener(controlListener);
		zoomout.setOnClickListener(controlListener);
		lockcenter.setOnClickListener(controlListener);
		locationButton.setOnClickListener(controlListener);
		clearButton.setOnClickListener(controlListener);

		// new a SAILS engine.
		mSails = new SAILS(this);
		// set location mode.
		mSails.setMode(SAILS.BLE_GFP_IMU);
		// set floor number sort rule from descending to ascending.
		mSails.setReverseFloorList(true);
		// create location change call back.

		// new and insert a SAILS MapView from layout resource.
		mSailsMapView = new SAILSMapView(this);
		((FrameLayout) findViewById(R.id.SAILSMap)).addView(mSailsMapView);
		// configure SAILS map after map preparation finish.
		mSailsMapView.post(new Runnable() {
			@Override
			public void run() {
				// please change token and building id to your own building
				// project in cloud.
				mSails.loadCloudBuilding("ef608be1ea294e3ebcf6583948884a2a", "57e381af08920f6b4b0004a0",
						new SAILS.OnFinishCallback() {
							@Override
							public void onSuccess(String response) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										mapViewInitial();
									}
								});

							}

							@Override
							public void onFailed(String response) {
								Toast t = Toast.makeText(getBaseContext(),
										"Load cloud project fail, please check network connection.",
										Toast.LENGTH_SHORT);
								t.show();
							}
						});
			}
		});
		LocalizationTimer = new Timer();
		LocalizationTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				// TODO Auto-generated method
				// stub

				List<Integer> list1 = ModelService.localizationFunc(beaconSet);
				List<Integer> list2 = ModelService.localizationFunc1(beaconSet);
				List<Integer> list3 = ModelService.threePointLocalization(beaconSet);
			//	Log.d("666", list1.get(0)+" "+list1.get(1));
				Message msg = new Message();
				Bundle b = new Bundle();
				msg.what = 0x01;
				b.putInt("list1x", list1.get(0));
				b.putInt("list1y", list1.get(1));
				b.putInt("list2x", list2.get(0));
				b.putInt("list2y", list2.get(1));
				b.putInt("list3x", list3.get(0));
				b.putInt("list3y", list3.get(1));
				msg.setData(b);
				handler.sendMessage(msg);
				synchronized (beaconSet) {
					beaconSet.clear();
				}
				beaconMap.clear();
				localizationCount += 1;
				if (localizationCount % 5 == 0) {
					bleRestart();
				}
			}
		}, 3000, 5000);
	}

	void mapViewInitial() {
		// establish a connection of SAILS engine into SAILS MapView.
		mSailsMapView.setSAILSEngine(mSails);

		// set location pointer icon.
		mSailsMapView.setLocationMarker(R.drawable.circle, R.drawable.arrow, null, 35);

		// set location marker visible.
		mSailsMapView.setLocatorMarkerVisible(true);

		// load first floor map in package.
		mSailsMapView.loadFloorMap(mSails.getFloorNameList().get(0));

		// Auto Adjust suitable map zoom level and position to best view
		// position.
		mSailsMapView.autoSetMapZoomAndView();

		// design some action in floor change call back.
		mSailsMapView.setOnFloorChangedListener(new SAILSMapView.OnFloorChangedListener() {
			@Override
			public void onFloorChangedBefore(String floorName) {
				// get current map view zoom level.
				zoomSav = mSailsMapView.getMapViewPosition().getZoomLevel();
			}

			@Override
			public void onFloorChangedAfter(final String floorName) {
				Runnable r = new Runnable() {
					@Override
					public void run() {
						// check is locating engine is start and current brows
						// map is in the locating floor or not.
						if (mSails.isLocationEngineStarted() && mSailsMapView.isInLocationFloor()) {
							// change map view zoom level with animation.
							mSailsMapView.setAnimationToZoom(zoomSav);
						}
					}
				};
				new Handler().postDelayed(r, 1000);

				int position = 0;
				for (String mS : mSails.getFloorNameList()) {
					if (mS.equals(floorName))
						break;
					position++;
				}
				floorList.setSelection(position);
			}
		});

		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mSails.getFloorDescList());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		floorList.setAdapter(adapter);
		floorList.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (!mSailsMapView.getCurrentBrowseFloorName().equals(mSails.getFloorNameList().get(position)))
					mSailsMapView.loadFloorMap(mSails.getFloorNameList().get(position));
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});
	}

	View.OnClickListener controlListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (v == zoomin) {
				// set map zoomin function.
				mSailsMapView.zoomIn();
			} else if (v == zoomout) {
				// set map zoomout function.
				mSailsMapView.zoomOut();
			} else if (v == lockcenter) {

			} else if (v == locationButton) {
				drawPosition(1,1);
			} else if (v == clearButton) {
				mSailsMapView.setRotationAngle(0);
				mSailsMapView.autoSetMapZoomAndView();
				mSailsMapView.getOverlays().clear();
				mSailsMapView.redraw();
				flag = 0;
			}
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		mSailsMapView.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSailsMapView.onPause();
	}

	@Override
	protected void onDestroy() {
		if (bAdapter != null)
			bAdapter.stopLeScan(mLeScanCallback);
		if (findLostBeaconTimer != null)
			findLostBeaconTimer.cancel();
		if (LocalizationTimer != null)
			LocalizationTimer.cancel();
		if (GetBluetoothDeviceTimer != null)
			GetBluetoothDeviceTimer.cancel();
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		finish();
		super.onBackPressed();
		((FrameLayout) findViewById(R.id.SAILSMap)).removeAllViews();
	}

	private void initComponent() {
		beaconSet = new HashSet<Beacon>();
		beaconMap = new HashSet<Beacon>();
		// 打开蓝牙
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(TestMap.this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			// finish();
		}
		Log.d("bluetooth", "ok");
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bAdapter = bluetoothManager.getAdapter();
		bAdapter.enable();

		timeZero = System.currentTimeMillis();
		if (bAdapter != null) {
			if (!bAdapter.isEnabled())
				bAdapter.enable();
			Log.d("bluetooth", "start scaning");
			bAdapter.startLeScan(mLeScanCallback);
			// startTime = new Timestamp(System.currentTimeMillis());
			//
			// positionTimer = new Timer();
			// positionTimer.schedule(new TimerTask() {
			//
			// @Override
			// public void run() {
			//
			// }
			// }, 3000, 1000);
			findLostBeaconTimer = new Timer();
			findLostBeaconTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					synchronized (beaconSet) {
						int invalidBeacon = BeaconUtil.scanLostBeacon(beaconSet);
						Beacon max = BeaconUtil.getMax(beaconSet);
						// Log.i("traindataactivity", max == null ? "无" :
						// max.getMac());
						// Bundle b = new Bundle();
						// b.putString("beacon", max == null ? "无" :
						// max.getMac());
						// Message msg = new Message();
						// msg.setData(b);
						// msg.what = 0x01;
						// handler.sendMessage(msg);

						// 每20* Beacon.TRANSMIT_PERIOD的时间重启一次BLE
						int restartThreshold = 20;
						int noReactThreashold = 1;
						int total = beaconSet.size();
						if (bleNoReactCount == noReactThreashold - 1) {
							/**
							 * 蓝牙在noReactThreashold*TRANSMIT_PERIOD时间内没有触发任何回调，
							 * 针对小米等机型，可能通过重启一次蓝牙来缓解这种问题。
							 * 对于华为等机型，蓝牙回调非常频繁，在有Beacon的范围内,
							 * noReactThreashold可能永远保持为零
							 */
							bleRestart();
							Log.i("ScansService", "蓝牙未响应重启");
						} else {
							if (total == 0 || invalidBeacon == total || scanCount == restartThreshold - 1) {
								/**
								 * 蓝牙有响应,但可能没有潜在位置的Beacon可能没有被更新。
								 * 没有检测到蓝牙，或者蓝牙全部失效，或者时间达到restartThreshold
								 * *TRANSMIT_PERIOD ， 重启一次蓝牙
								 */
								bleRestart();
								Log.i("ScansService", "beacon失效或周期性重启");

							}
						}
						bleNoReactCount = (bleNoReactCount + 1) % noReactThreashold;
						scanCount = (scanCount + 1) % restartThreshold;
					}
				}
			}, 3000, Beacon.TRANSMIT_PERIOD);
		}
	}

	/**
	 * 对于小米手机，每个Beacon可能只会被扫描一次，此时需要重启扫描
	 */
	private void bleRestart() {
		bAdapter.stopLeScan(mLeScanCallback);
		bAdapter.startLeScan(mLeScanCallback);
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			// Log.d("callback", device.getAddress() + "\n" + rssi);
			// int sec = (int) ((System.currentTimeMillis() - timeZero) / 1000);
			// Log.e("onLeScan", "第" + sec + "s ,发现" + device.getAddress() +
			// "\n有"
			// + beaconSet.size() + "个");
			if (device.getAddress() != null && rssi <= 0) {
				// if(device.getAddress().equals("98:7B:F3:72:23:C5")){
				// System.out.println(123456789);
				// }
				bleNoReactCount = 0;
				int txPower = BeaconUtil.getBeaconTxPower(scanRecord);
				if (device.getAddress().equals("98:7B:F3:72:23:C5")) {
					System.out.println(123456789 + " txPower " + txPower);
				}
				// 针对某些没有设置txpower的蓝牙芯片，设置默认的参考发射功率
				if (txPower > 0) {
					txPower = -60;
				}
				int dis = BeaconUtil.calculateAccuracyForLocalization(txPower, rssi);
				if (device.getAddress().equals("80:30:DC:0D:F6:0F")) {
					// String newMac = device.getAddress();
					// DBManager dbManager = new
					// DBManager(IndoorLocationActivity.this);
					// if (dbManager.isContainLocalization(newMac)) {
					// Log.d("genxinshujufordistance",
					// "" + dis + " " + device.getAddress() + " " + rssi + " " +
					// "txPower:" + txPower);
				}
				ModelService.updateBeaconForLocal(TestMap.this, beaconSet,
						new Beacon(device.getAddress(), rssi, txPower, dis), beaconMap);

			}
		}
	};

	// 显示当前定位信息
	private class MAHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			if (msg.what == 0x01) {
				Bundle b = msg.getData();
			locationTextView.setText(b.getInt("list3x")+" "+b.getInt("list3y"));
				
				mSailsMapView.getOverlays().clear();
				mSailsMapView.redraw();
				drawPosition(b.getInt("list3x"),b.getInt("list3y"));
			}
		}
	}
	
	public void drawPosition(int x,int y) {
		MapViewPosition mapViewPosition = mSailsMapView.getMapViewPosition();
		if (flag == 0) {
			mapViewPositionBase = mSailsMapView.getMapViewPosition();
			geoPointCenter = mapViewPositionBase.getCenter();
			geoPointLocationLB = mSailsMapView.getProjection().fromPixels(425, 915);
			geoPointLocationRT = mSailsMapView.getProjection().fromPixels(655, 687);
		}
		GeoPoint geoPointCenter2 = mapViewPosition.getCenter();
		byte zoomLevel = mapViewPosition.getZoomLevel();
		GeoPoint geoPointNow = new GeoPoint(
				geoPointLocationLB.latitude
						- (geoPointLocationLB.latitude - geoPointLocationRT.latitude) / 16.8 * x,
				geoPointLocationLB.longitude
						- (geoPointLocationLB.longitude - geoPointLocationRT.longitude) / 16.8 * y);

		ListOverlay listOverlay = new ListOverlay();
		Marker marker = new Marker(geoPointNow,
				Marker.boundCenterBottom(getResources().getDrawable(R.drawable.start_point)));
		listOverlay.getOverlayItems().add(marker);

		mSailsMapView.getOverlays().add(listOverlay);
		mSailsMapView.redraw();
		flag++;
	}
}