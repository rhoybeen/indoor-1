package com.bupt.indoorpostion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import com.bupt.indoorPosition.bean.Beacon;
import com.bupt.indoorPosition.dao.DBManager;
import com.bupt.indoorPosition.model.ModelService;
import com.bupt.indoorPosition.uti.BeaconUtil;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class IndoorLocationActivity extends Activity {

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
	
	private int width;
	private int height;
	private float density;
	private int d = 20;
	private ImageView indoorMap;
	private ImageView myLocation;
	private Button startButton;
	private Button stopButton;
	private TextView textView;
	private TextView textView1;

	private ObjectAnimator animX;
	private ObjectAnimator animY;
	
	

	private Bitmap myBitMap;
	private float myBitMap_width;
	private float myBitMap_height;
	private float myImageView_width;
	private float myImageView_height;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.indoor_localization);

		handler = new MAHandler();
		
		indoorMap = (ImageView) findViewById(R.id.map);
		myLocation = (ImageView) findViewById(R.id.myLocation);
		startButton = (Button) findViewById(R.id.start_buuton);
		stopButton = (Button) findViewById(R.id.stop_button);
		textView = (TextView) findViewById(R.id.showdetails);
		textView1 = (TextView) findViewById(R.id.TextView01);
		width = getDeviceWidth(this);
		height = getDeviceHeight(this);
		density = getResources().getDisplayMetrics().density;
		
		myBitMap=BitmapFactory.decodeResource(getResources(),R.drawable.map630jpg);
		myBitMap_width=myBitMap.getWidth();
		myBitMap_height=myBitMap.getHeight();
		
		
		animX = ObjectAnimator.ofFloat(myLocation, "scaleX", 0.6f, 1f, 0.6f);
		animX.setDuration(2000);
		animX.setRepeatCount(Animation.INFINITE);
		animX.setRepeatMode(Animation.REVERSE);
		animY = ObjectAnimator.ofFloat(myLocation, "scaleY", 0.6f, 1f, 0.6f);
		animY.setDuration(2000);
		animY.setRepeatCount(Animation.INFINITE);
		animY.setRepeatMode(Animation.REVERSE);
		initComponent();

		startButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
			
				myImageView_width=width;
				myImageView_height=myImageView_width/myBitMap_width*myBitMap_height;
				LayoutParams params=indoorMap.getLayoutParams();
				params.width=(int) myImageView_width;
				params.height=(int) myImageView_height;
				indoorMap.setLayoutParams(params);
				textView.setText(myImageView_width + " " + myImageView_height + " " + density + " " + indoorMap.getWidth() + " "
						+ indoorMap.getHeight());
				myLocation.setX((float) (indoorMap.getX()+myImageView_width * Math.random() - d * density / 2));
				myLocation.setY((float) (indoorMap.getY()+myImageView_height * Math.random() - d * density / 2));

				animX.start();
				animY.start();
			}
		});
		stopButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				animX.end();
				animY.end();
			}
		});

		LocalizationTimer = new Timer();
		LocalizationTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				// TODO Auto-generated method stub

				List<Integer> list1 = ModelService.localizationFunc(beaconSet);
				List<Integer> list2 = ModelService.localizationFunc1(beaconSet);
				List<Integer> list3 = ModelService.threePointLocalization(beaconSet);
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

		// 尝试去定时获取蓝牙设备
		// GetBluetoothDeviceTimer = new Timer();
		// GetBluetoothDeviceTimer.schedule(new TimerTask() {
		//
		// @Override
		// public void run() {
		// // TODO Auto-generated method stub
		// // for(BluetoothDevice b:bAdapter.getBondedDevices()){
		// // if(b.getAddress() == "80:30:DC:0D:F6:0F");
		// // Log.d("genxinshuju+lanyashebeisaomiao", b.getAddress()+" ");
		// // }
		// // bAdapter.stopLeScan(mLeScanCallback);
		// // bAdapter.startLeScan(mLeScanCallback);
		// }
		// }, 1000, 100);
	}

	/**
	 * 获取设备屏幕的宽
	 * 
	 * @param context
	 * @return
	 */
	public static int getDeviceWidth(Activity context) {
		Display display = context.getWindowManager().getDefaultDisplay();
		Point p = new Point();
		display.getSize(p);
		return p.x;
	}

	/** 获取屏幕的高 */
	public static int getDeviceHeight(Activity context) {
		Display display = context.getWindowManager().getDefaultDisplay();
		Point p = new Point();
		display.getSize(p);
		return p.y;
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

	private void initComponent() {
		beaconSet = new HashSet<Beacon>();
		beaconMap = new HashSet<Beacon>();
		// 打开蓝牙
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(IndoorLocationActivity.this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
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
				ModelService.updateBeaconForLocal(IndoorLocationActivity.this, beaconSet,
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

			}
		}
	}
}
