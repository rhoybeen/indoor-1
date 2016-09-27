package com.bupt.indoorPosition.model;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.bupt.indoorPosition.bean.Beacon;
import com.bupt.indoorPosition.bean.BeaconInfo;
import com.bupt.indoorPosition.bean.IndoorRecord;
import com.bupt.indoorPosition.bean.InspectDisplay;
import com.bupt.indoorPosition.bean.InspectedBeacon;
import com.bupt.indoorPosition.bean.Inspection;
import com.bupt.indoorPosition.bean.Inspector;
import com.bupt.indoorPosition.bean.KeepAliveLocation;
import com.bupt.indoorPosition.bean.LocalizationBeacon;
import com.bupt.indoorPosition.bean.Neighbor;
import com.bupt.indoorPosition.bean.Sim;
import com.bupt.indoorPosition.bean.Speed;
import com.bupt.indoorPosition.dao.DBManager;
import com.bupt.indoorPosition.location.LocationProvider;
import com.bupt.indoorPosition.uti.Global;
import com.bupt.indoorPosition.uti.HttpUtil;
import com.bupt.indoorPosition.uti.BeaconUtil;
import com.bupt.indoorPosition.uti.JsonUtil;
import com.bupt.indoorPosition.uti.Constants;
import com.bupt.indoorPosition.uti.MessageUtil;
import com.bupt.indoorPosition.uti.SignalUtil;
import com.bupt.indoorpostion.MainActivity;
import com.bupt.indoorpostion.R;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import android.R.integer;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.widget.Toast;

public class ModelService {

	public static Sim getPhoneInfo(TelephonyManager telephonyManager) {

		CellLocation location = telephonyManager.getCellLocation();
		String phoneType = new String();
		if (location instanceof GsmCellLocation) {
			phoneType = "GSM";
		} else {
			phoneType = "CDMA";
		}

		String imei = telephonyManager.getDeviceId();
		String imsi = telephonyManager.getSubscriberId();
		String phoneName = android.os.Build.MODEL; // 手机型号
		String operatingSystem = android.os.Build.VERSION.RELEASE;// 操作系统
		// mtype = SignalUtil.convertNetType(telephonyManager.getNetworkType());
		String phoneNumber = telephonyManager.getLine1Number(); // 手机号码，有的可得，有的不可得
		Sim sim = new Sim(imei, imsi, phoneName, operatingSystem, phoneType, phoneNumber);
		Log.d("text", "操作系统" + operatingSystem + "手机类型" + phoneType + "手机号码" + phoneNumber);
		return sim;

		//
		// String phoneInfo = "Product: " + android.os.Build.PRODUCT;
		// phoneInfo += ", CPU_ABI: " + android.os.Build.CPU_ABI;
		// phoneInfo += ", TAGS: " + android.os.Build.TAGS;
		// phoneInfo += ", VERSION_CODES.BASE: "
		// + android.os.Build.VERSION_CODES.BASE;
		// phoneInfo += ", MODEL: " + android.os.Build.MODEL;
		// phoneInfo += ", SDK: " + android.os.Build.VERSION.SDK;
		// phoneInfo += ", VERSION.RELEASE: " +
		// android.os.Build.VERSION.RELEASE;
		// phoneInfo += ", DEVICE: " + android.os.Build.DEVICE;
		// phoneInfo += ", DISPLAY: " + android.os.Build.DISPLAY;
		// phoneInfo += ", BRAND: " + android.os.Build.BRAND;
		// phoneInfo += ", BOARD: " + android.os.Build.BOARD;
		// phoneInfo += ", FINGERPRINT: " + android.os.Build.FINGERPRINT;
		// phoneInfo += ", ID: " + android.os.Build.ID;
		// phoneInfo += ", MANUFACTURER: " + android.os.Build.MANUFACTURER;
		// phoneInfo += ", USER: " + android.os.Build.USER;
		// Log.d("系统os.build获取信息", phoneInfo);
		//

	}

	public static boolean updateDb(Context context, Sim sim) {
		String url = context.getString(R.string.hostUrl) + "/location/beaconList";

		// String response = HttpUtil.getResponseString(url,
		// JsonUtil.serilizeJavaObject(sim));

		Map<String, String> params = new HashMap<String, String>();
		params.put("simString", JsonUtil.serilizeJavaObject(sim));
		params.put("city", LocationProvider.getCity());
		params.put("province", LocationProvider.getProvince());
		Map<String, Object> response = HttpUtil.post(url, params);
		Log.d("sim上传结果", "response:" + response);

		if (response == null || ((Integer) (response.get("status"))) == null
				|| ((Integer) (response.get("status"))) < 0) {
			context.sendBroadcast(MessageUtil.getServerResponseBundle(response,
					Constants.ACTIONURL.MAIN_ACTIVITY_ACTION, Constants.INTENT_TYPE.NONE));
			return false;
		}

		List<InspectedBeacon> list = JsonUtil.convertListFromMap(response, "list", InspectedBeacon.class);
		DBManager dbManager = new DBManager(context);
		dbManager.refreshBeacon(list);
		return true;
	}

	public static boolean updateLocalization(Context context) {
		String url = context.getString(R.string.hostUrl) + "/location/localization";

		Map<String, String> params = new HashMap<String, String>();
		params.put("city", LocationProvider.getCity());
		params.put("province", LocationProvider.getProvince());
		Map<String, Object> response = HttpUtil.post(url, params);
		if (response == null || ((Integer) (response.get("status"))) == null
				|| ((Integer) (response.get("status"))) < 0) {
			context.sendBroadcast(MessageUtil.getServerResponseBundle(response,
					Constants.ACTIONURL.MAIN_ACTIVITY_ACTION, Constants.INTENT_TYPE.NONE));
			return false;
		}
		List<LocalizationBeacon> list = JsonUtil.convertListFromMap(response, "list", LocalizationBeacon.class);
		DBManager dbManager = new DBManager(context);
		dbManager.refreshLocalization(list);
		return true;
	}

	/**
	 * 
	 * @param beaconSet
	 * @param newBeacon
	 *            newBeacon.getMac()保证不是Null
	 */
	public static void updateBeacon(Context context, Set<Beacon> beaconSet, Beacon newBeacon) {
		// Log.d("modelService", "enter");
		if (beaconSet.contains(newBeacon)) {
			// mac 地址存在，只需要更新
			// Log.d("modelService", "exist");
			Beacon oBeacon = BeaconUtil.get(beaconSet, newBeacon.getMac());
			oBeacon.setRssi(newBeacon.getRssi());
			oBeacon.setTxPower(newBeacon.getTxPower());
			oBeacon.setDistance(newBeacon.getDistance());
		} else {
			// Log.d("modelService", "not exist");
			String newMac = newBeacon.getMac();
			DBManager dbManager = new DBManager(context);
			if (dbManager.isContainBeacon(newMac)) {
				// 合法mac
				beaconSet.add(new Beacon(newMac, newBeacon.getRssi(), newBeacon.getTxPower(), newBeacon.getDistance()));
			}
		}
	}

	// 同理 设置对应localization
	public static void updateBeaconForLocal(Context context, Set<Beacon> beaconSet, Beacon newBeacon,
			Set<Beacon> beaconMap) {
		// Log.d("modelService", "enter");
		// 定位所需最新接受处beacon列表

		if (beaconSet.contains(newBeacon)) {
			// mac 地址存在，只需要更新
			// Log.d("modelService", "exist");
			Beacon oBeacon = BeaconUtil.get(beaconSet, newBeacon.getMac());
			oBeacon.setRssi(newBeacon.getRssi());
			oBeacon.setTxPower(newBeacon.getTxPower());
			oBeacon.setDistance(newBeacon.getDistance());
			// 更新定位使用set
			Beacon listBeacon = BeaconUtil.get(beaconMap, newBeacon.getMac());
			ArrayList<Integer> list = listBeacon.getDislist();
			list.add(newBeacon.getDistance());
			listBeacon.setDislist(list);

		} else {
			// Log.d("modelService", "not exist");
			String newMac = newBeacon.getMac();
			DBManager dbManager = new DBManager(context);
			if (dbManager.isContainLocalization(newMac)) {
				// 合法mac
				List<Integer> list = dbManager.LocalizationXY(newMac);
				beaconSet.add(new Beacon(newMac, newBeacon.getRssi(), newBeacon.getTxPower(), newBeacon.getDistance(),
						list.get(0).intValue(), list.get(1).intValue()));
				ArrayList<Integer> newlist = new ArrayList<Integer>();
				newlist.add(newBeacon.getDistance());
				beaconMap.add(new Beacon(newMac, newBeacon.getRssi(), newBeacon.getTxPower(), newBeacon.getDistance(),
						list.get(0).intValue(), list.get(1).intValue(), newlist));
			}
		}
	}

	public static Beacon getPosition(Context context, Set<Beacon> beaconSet) {
		Log.d("PositionGet", "have got");
		return BeaconUtil.getMax(beaconSet);
	}

	public static void writeDebugInfo(Context context, Set<Beacon> beaconSet, String uuidFK) {
		DBManager dbManager = new DBManager(context);
		dbManager.insertBeaconDebug(beaconSet, uuidFK);
	}

	/**
	 * 
	 * @param context
	 * @param record
	 * @param neighbors
	 * @param beaconSet
	 * @param debug
	 * @param prePosition
	 * @return Beacon 当前位置对应的蓝牙设备
	 */
	public static Beacon recordIndoorData(Context context, IndoorRecord record, List<Neighbor> neighbors,
			Set<Beacon> beaconSet, BeaconInfo beaconinfo, Speed speed, boolean debug) {
		// Log.d("recordIndoorData", "neighbor size " + neighbors.size());
		Log.d("recordIndoorData", "record" + record.getSignalStrength());
		Beacon position = getPosition(context, beaconSet);
		if (position == null) {
			// 不做记录 return position;
			Log.d("NullPosition", "have not got");
			position = new Beacon("暂无设备", 99, 99, 99);
			return position;
		}
		if (record != null && !SignalUtil.isValidStrength(record.getSignalStrength())) {
			// 不做记录 return position;
			Log.e("invalid signalStrength", record.getSignalStrength() + " dbm");
			position = new Beacon("暂无设备", 99, 99, 99);
			return position;
		}

		if (record != null && (!SignalUtil.isValidRsrp(record.getRsrp()) || !SignalUtil.isValidRsrq(record.getRsrq())
				|| !SignalUtil.isValidSinr(record.getSinr()))) {
			Log.e("invalid rsrp&rsrq&sinr", record.getRsrp() + " dbm" + record.getRsrq() + "   " + record.getSinr());
			position = new Beacon("暂无设备", 99, 99, 99);
			return position;
		}

		// Log.d("recordIndoorData", "position " + position.getRssi());
		record.setPosition(position.getMac());

		String uuid = BeaconUtil.getUUID();
		speed.setUuidFK(uuid);

		beaconinfo.setMac(position.getMac());
		beaconinfo.setUuidFK(uuid);
		beaconinfo.setBeaconRssi(position.getRssi());
		beaconinfo.setbTxPower(position.getTxPower());
		beaconinfo.setDistance(position.getDistance());
		// record.setBeaconRssi(position.getRssi());
		// record.setBTxPower(position.getTxPower());
		record.setTime(new Timestamp(System.currentTimeMillis()));
		record.setUuid(uuid);
		// record.setDistance(position.getDistance());
		// Log.d("recordIndoorData", record.getUuid());
		DBManager dbManager = new DBManager(context);
		dbManager.insertIndoorRecord(record);
		dbManager.insertBeaconInfo(beaconinfo);
		if (neighbors != null)
			dbManager.insertNeighborRecord(neighbors, record.getUuid());
		if (debug) {
			writeDebugInfo(context, beaconSet, uuid);
		}
		return position;
	}

	public static boolean uploadRecord(Context context) {
		DBManager dbManager = new DBManager(context);
		List<IndoorRecord> list = dbManager.selectAllIndoorRecord();
		List<BeaconInfo> beaconinfolist = dbManager.selectAllBeaconInfo();
		List<Speed> speedlist = dbManager.selectAllSpeedList();

		// for (IndoorRecord ir : list)
		// Log.d("upload", ir.getPosition());
		ObjectMapper objectMapper = new ObjectMapper();
		String jsonIndoorRecord = null;
		String jsonBeaconRecord = null;
		String jsonSpeed = null;
		try {
			jsonIndoorRecord = objectMapper.writeValueAsString(list);
			jsonBeaconRecord = objectMapper.writeValueAsString(beaconinfolist);
			jsonSpeed = objectMapper.writeValueAsString(speedlist);
			Log.d("上传测试啊，jsonIndoorRecord ", "" + jsonIndoorRecord);
			Log.d("上传测试啊，jsonBeaconRecord ", "" + jsonBeaconRecord);
			Log.d("上传测试啊，jsonSpeed ", "" + jsonSpeed);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// if ((json1 == null) || (json2 == null) || (json3 == null))
		// return false;
		String url = context.getString(R.string.uploadIndoorUrl);
		Map<String, String> params = new HashMap<String, String>();
		params.put("listRecord", jsonIndoorRecord);
		params.put("listBeacon", jsonBeaconRecord);
		params.put("listSpeed", jsonSpeed);

		Map<String, Object> result = HttpUtil.post(url, params);

		if (result == null || ((Integer) (result.get("status"))) == null || ((Integer) (result.get("status"))) < 0) {
			// context.sendBroadcast(MessageUtil.getServerResponseBundle(result));
			return false;
		}

		if (result != null && ((Integer) (result.get("status"))) == 1) {
			dbManager.deleteAllIndoorRecord();
			dbManager.deleteAllSpeedList();
			dbManager.deleteAllBeaconInfo();
			return true;
		}
		return false;
	}

	public static boolean uploadNeighbor(Context context) {
		DBManager dbManager = new DBManager(context);

		List<Neighbor> neighbors = dbManager.getNeighborRecord();

		// for (IndoorRecord ir : list)
		// Log.d("upload", ir.getPosition());
		ObjectMapper objectMapper = new ObjectMapper();
		String json = null;
		try {
			json = objectMapper.writeValueAsString(neighbors);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (json == null)
			return false;
		String url = context.getString(R.string.uploadNeighborUrl);
		Map<String, String> params = new HashMap<String, String>();
		params.put("neighbors", json);
		Map<String, Object> result = HttpUtil.post(url, params);
		if (result == null || ((Integer) (result.get("status"))) == null || ((Integer) (result.get("status"))) < 0) {
			// context.sendBroadcast(MessageUtil.getServerResponseBundle(result));
			return false;
		}

		if (result != null && ((Integer) (result.get("status"))) == 1) {
			dbManager.deleteAllNeighborList();
			return true;
		}
		return false;
	}

	public static boolean uploadInspection(Context context) {
		DBManager dbManager = new DBManager(context);

		List<Inspection> inspections = dbManager.selectAllInspection();

		ObjectMapper objectMapper = new ObjectMapper();
		String json = null;
		try {
			json = objectMapper.writeValueAsString(inspections);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (json == null)
			return false;
		String url = context.getString(R.string.hostUrl) + "/location/insertInspection";
		Map<String, String> params = new HashMap<String, String>();
		params.put("inspectionListString", json);
		Map<String, Object> result = HttpUtil.post(url, params);
		if (result == null || ((Integer) (result.get("status"))) == null || ((Integer) (result.get("status"))) < 0) {
			// context.sendBroadcast(MessageUtil.getServerResponseBundle(result));
			return false;
		}

		if (result != null && ((Integer) (result.get("status"))) == 1) {
			dbManager.deleteInspection();
			return true;
		}
		return false;
	}

	public static int UploadTest(IndoorRecord cellState) {
		int sendBufferSize = 8192;
		String hostIp = "115.28.164.238";
		int hostPort = 9999;

		long totalTransmited = 0;
		long totalTime = 0;
		int ul_bps = 0;
		Socket socket = null;
		String fileName = "testfile";
		Object[] returnValues = getFileStream(cellState, fileName);
		InputStream source = (InputStream) (returnValues[0]);
		String name = (String) (returnValues[1]);
		long fileLength = (Long) (returnValues[2]);

		try {

			// socket = new Socket(InetAddress.getByName(hostIp), hostPort);
			socket = new Socket(hostIp, hostPort);

			socket.setSoTimeout(10 * 1000);

			socket.setSendBufferSize(sendBufferSize);
			socket.setTrafficClass(0x08);// 高吞吐量
			socket.setPerformancePreferences(1, 1, 2);// 高带宽
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			// Log.d("上传下载速度测试", socket.toString());
			if (in.readByte() == 0x01) {
				out.writeBytes(name + "\n");
				out.flush();

			}
			// Log.d("上传下载速度测试", "9999999999999999999999999999999999999");
			byte[] buffer = new byte[8192];
			int count = source.read(buffer, 0, buffer.length);
			long time_begin = SystemClock.uptimeMillis();

			while (count != -1) {

				totalTransmited += (count + 1);
				out.write(buffer, 0, count);
				out.flush();
				count = source.read(buffer, 0, buffer.length);
				// Log.d("上传下载速度测试", "8888888888888888888888888888888888888");
			}
			socket.sendUrgentData(-1);
			long time = SystemClock.uptimeMillis();

			totalTime = time - time_begin;
			// Log.d("持续时间", "" + totalTime);
			if (totalTime != 0) {
				long UploadSpeed = (totalTransmited * 1000) / (1024 * totalTime);
				ul_bps = Integer.parseInt(String.valueOf(UploadSpeed));
				// Log.d("上传速度测试", "" + UploadSpeed);

			}
		} catch (UnknownHostException e) {

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}

		return ul_bps;

	}

	public static int DownloadTest() {
		// String host = "www.zxbupt.cn";
		InputStream is = null;
		URLConnection con = null;
		int dl_bps = 0;
		try {

			URL url = new URL("http://www.zxbupt.cn/buildingMap/download.jpg");
			;
			// final InetAddress address = InetAddress.getByName(host);

			con = url.openConnection();
			// final int contentLength = con.getContentLength();
			// final int timeout = con.getConnectTimeout();
			is = con.getInputStream();
			// InputStream is = sock.getInputStream();
			byte[] b = new byte[4096];
			int read_count, total_read = 0;
			// circumvent a little bit speed progressing of TCP
			is.read(b, 0, 1024);
			long time_begin = SystemClock.uptimeMillis();
			while ((read_count = is.read(b, 0, 4096)) != -1) {
				total_read += read_count;
				// final String str = this.getString(R.string.dl_speed,
				// total_read
				// / 1024.0
				// / ((SystemClock.uptimeMillis() - time_begin) / 1000.0));
			}

			long totalTime = SystemClock.uptimeMillis() - time_begin;
			if (totalTime != 0) {
				long DownloadSpeed = (total_read * 1000) / (1024 * totalTime);
				// Log.d("下载速度测试", "" + DownloadSpeed);
				dl_bps = Integer.parseInt(String.valueOf(DownloadSpeed));
				// speed.setUl_bps(dl_bps);

			}
		} catch (Exception e) {
			e.printStackTrace();
			// Log.debug(Util.printException(e));
			// special case common error when data is not available
		}
		return dl_bps;
	}

	public static Object[] getFileStream(IndoorRecord cellState, String fileName) {
		int size = 200 * 1024;
		String _fileName = "unknown";
		if (Constants._2G.contains(cellState.getNetworkType())) {
			size = 150 * 1024;
			_fileName = "2G";
		} else if (Constants._3G.contains(cellState.getNetworkType())) {
			size = 500 * 1024;
			_fileName = "3G";
		} else if (Constants._4G.contains(cellState.getNetworkType())) {
			size = 1000 * 1024;
			_fileName = "4G";
		}
		ByteArrayInputStream bais = new ByteArrayInputStream(new byte[size]);
		//
		return new Object[] { bais, _fileName, (long) size };
	}

	public static void speedTest(Context context, Speed speed, IndoorRecord cellState) {
		int ul = UploadTest(cellState);
		int dl = DownloadTest();
		Log.i("上行速度/下行速度 ", "" + ul + " / " + dl);
		speed.setUl_bps(ul);
		speed.setDl_bps(dl);
		DBManager dbManager = new DBManager(context);
		dbManager.insertSpeedList(speed);
	}

	public static boolean retreat(Map<String, Integer> map, Beacon currentPostion) {
		boolean tobeornottobe = true;
		if (map.containsKey(currentPostion.getMac())) {
			if (map.get(currentPostion.getMac()) > 0) {
				int a = map.get(currentPostion.getMac());
				map.put(currentPostion.getMac(), a - 1);
				tobeornottobe = false;
			} else {
				map.put(currentPostion.getMac(), 10);
				tobeornottobe = true;
			}
		} else {
			map.put(currentPostion.getMac(), 10);
		}
		return tobeornottobe;
	}

	/**
	 * 
	 * @param location
	 *            向服务器发送位置，并判断是否登录
	 * @return true 登录 false 未登录
	 */
	public static boolean keepAlive(Context context, KeepAliveLocation location) {
		String url = context.getString(R.string.hostUrl) + "/location/loginKeepAlive";

		Map<String, String> params = new HashMap<String, String>();
		params.put("keepAliveString", JsonUtil.serilizeJavaObject(location));
		Map<String, Object> response = HttpUtil.post(url, params);

		if (response == null || ((Integer) (response.get("status"))) == null
				|| ((Integer) (response.get("status"))) < 0) {
			context.sendBroadcast(MessageUtil.getServerResponseBundle(response,
					Constants.ACTIONURL.MAIN_ACTIVITY_ACTION, Constants.INTENT_TYPE.KEEP_ALIVE));
			return false;
		}

		return true;
	}

	/**
	 * 根据currentPosition,更新用于界面显示的showList
	 * 
	 * @param currentPostion
	 * @param showList
	 * @param context
	 */
	public static void updateDisplayList(Beacon currentPosition, List<InspectedBeacon> showList, Context context) {
		if (currentPosition == null)
			return;
		if (showList == null)
			return;

		if (showList.size() == 0) {
			// 重新导入值到showList
			DBManager dbManager = new DBManager(context);
			// Log.i("updateDisplayList", "target mac " +
			// currentPosition.getMac());
			List<InspectedBeacon> bl = dbManager.getBeaconInfo(currentPosition.getMac());
			// Log.i("updateDisplayList", "bl size " + bl.size());
			if (bl.size() == 0)
				return;
			for (int i = 0; i < bl.size(); i++) {

				InspectedBeacon ib = bl.get(i);
				// Log.i("updateDisplayList", "ib " + ib.getMac());
				if (ib != null) {
					if (currentPosition.getMac().equals(ib.getMac())) {
						ib.setCount(1);
					}
					showList.add(ib);
				}

			}
			return;
		}
		for (int i = 0; i < showList.size(); i++) {
			InspectedBeacon ib = showList.get(i);
			if (ib != null) {
				if (currentPosition.getMac().equals(ib.getMac())) {
					ib.setCount(ib.getCount() + 1);
					break;
				}
			}

		}

	}

	public static void insertInspection(Context context, Timestamp start, Timestamp end, int buildingId) {
		DBManager dbManager = new DBManager(context);
		List<Inspector> list = dbManager.selectAllInspector();
		if (list.size() == 0)
			return;
		String username = list.get(0).getUsername();
		int min = (int) Math.ceil((end.getTime() - start.getTime()) / (1000 * 60));
		Inspection inspection = new Inspection(-1, username, start, end, min, buildingId,
				LocationProvider.getProvince(), LocationProvider.getCity(), LocationProvider.getLongditude(),
				LocationProvider.getLatitidue());
		dbManager.insertInspection(inspection);
	}

	public static void saveCookie(Context context) {
		DBManager dbManager = new DBManager(context);
		dbManager.updateCookie(Global.cookieContainer);
		Log.i("modelService saveCookie", "cookieContainer: ");
		for (String key : Global.cookieContainer.keySet())
			System.out.println(key + " = " + Global.cookieContainer.get(key));

	}

	public static void loadCookie(Context context) {
		DBManager dbManager = new DBManager(context);
		Global.cookieContainer = dbManager.selectAllCookie();
		Log.i("modelService loadCookie", "cookieContainer: ");
		for (String key : Global.cookieContainer.keySet())
			System.out.println(key + " = " + Global.cookieContainer.get(key));

	}

	public static void removeAllInspector(Context context) {
		DBManager dbManager = new DBManager(context);
		dbManager.deleteInspector();
	}

	public static void recordPositionTrainData(Context context, String marker, Set<Beacon> beaconSet) {
		String uuid = BeaconUtil.getUUID();
		DBManager dbManager = new DBManager(context);
		dbManager.insertTrainData(beaconSet, uuid, marker);
	}

	public static String uploadPositionTrainData(Context context) {
		String message = "sorry";
		DBManager dbManager = new DBManager(context);
		List<Inspector> iss = dbManager.selectAllInspector();
		String username = null;
		if (iss != null && iss.size() > 0) {
			username = iss.get(0).getUsername();
		} else {
			return "请先登录";
		}
		List<Map<String, Object>> list = dbManager.selectAllTrainData();
		ObjectMapper objectMapper = new ObjectMapper();
		String jsonTrainData = null;
		try {
			jsonTrainData = objectMapper.writeValueAsString(list);

			Log.d("ModelService", "uploadPositionTrainData 上传" + jsonTrainData);
		} catch (Exception e) {
			// message = "解析數據有誤，上船失敗";
			e.printStackTrace();
			return "数据解析错误，上传失败";
		}

		String url = context.getString(R.string.hostUrl) + "/location/trainDataUpload";
		Map<String, String> params = new HashMap<String, String>();
		params.put("listTrainData", jsonTrainData);
		params.put("username", username);
		Map<String, Object> result = HttpUtil.post(url, params);

		if (result == null || ((Integer) (result.get("status"))) == null || ((Integer) (result.get("status"))) < 0) {
			// context.sendBroadcast(MessageUtil.getServerResponseBundle(result));
			return "服务器无响应，错误原因 " + result.get("reason");

		}
		if (result != null && ((Integer) (result.get("status"))) == 1) {
			dbManager.deleteAllTrainData();
			message = "数据上传成功！本地数据库已删除";
		}
		return message;
	}

	public static String uploadPositionXY(Context context, String marker, String X, String Y) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("marker", marker);
		params.put("X", X);
		params.put("Y", Y);
		String url = context.getString(R.string.hostUrl) + "/location/trainDataXY";
		Log.d("aaaaaaaaaaaaa", "aaaaaaaaaaaaaa");
		String message = "no";
		Map<String, Object> result = HttpUtil.post(url, params);
		if (result == null || ((Integer) (result.get("status"))) == null || ((Integer) (result.get("status"))) < 0) {
			// context.sendBroadcast(MessageUtil.getServerResponseBundle(result));
			return "服务器无响应，错误原因 " + result.get("reason");

		}
		if (result != null && ((Integer) (result.get("status"))) == 1) {
			message = "数据上传成功！本地数据库已删除";
		}
		return message;
	}

	// 定位A算法
	public static List<Integer> localizationFunc(Set<Beacon> beaconMap) {
		List<Integer> XX = new ArrayList<Integer>();
		double X[][];
		Iterator<Beacon> it = beaconMap.iterator();
		int length = beaconMap.size();
		int original[][] = new int[length][3];
		int temp = 0;
		int dealedDistance = 0;
		while (it.hasNext()) {
			Beacon b = it.next();
			// X,Y
			original[temp][0] = b.getX();
			original[temp][1] = b.getY();
			// 对每个点采集的时序数据进行高斯滤波
			if (b.getDislist().size() < 4) {
				Iterator<Integer> iterasmall = b.getDislist().iterator();

				while (iterasmall.hasNext()) {
					dealedDistance += iterasmall.next().intValue();
				}
				dealedDistance = dealedDistance / b.getDislist().size();
			} else {
				ArrayList<Integer> arlist = b.getDislist();
				Collections.sort(arlist);
				int n = Double.valueOf(Math.floor(arlist.size() * 0.6)).intValue();
				int isFirstorLast = 0;
				for (int i = 0; i < n; i++) {
					if (isFirstorLast == 0) {
						arlist.remove(0);
						isFirstorLast = 1;
					} else {
						arlist.remove(arlist.size() - 1);
						isFirstorLast = 0;
					}
				}
				Iterator<Integer> iterabig = arlist.iterator();
				while (iterabig.hasNext()) {
					dealedDistance += iterabig.next().intValue();
				}
				dealedDistance = dealedDistance / b.getDislist().size();
			}
			original[temp][2] = dealedDistance;
			temp += 1;
		}
		// Log.d("genxinshujufordistance", temp + " " + length + " " +
		// beaconMap.size());
		// if (beaconMap.get("80:30:DC:0D:F6:0F") != null)
		// Log.d("genxinshuju", "" + temp + " " +
		// beaconMap.get("80:30:DC:0D:F6:0F").size());

		// 要求的坐标为（A的转置乘上A）的逆乘上A的转置乘上B
		if (temp > 1 && temp == length) {
			double A[][] = new double[temp - 1][2];
			double AT[][] = new double[2][temp - 1];
			double B[][] = new double[temp - 1][1];
			for (int i = 0; i < temp - 1; i++) {
				A[i][0] = 2 * (original[i][0] - original[temp - 2][0]);
				A[i][1] = 2 * (original[i][1] - original[temp - 2][1]);
				B[i][0] = (original[i][0]) * (original[i][0]) - (original[temp - 2][0]) * (original[temp - 2][0])
						+ (original[i][1]) * (original[i][1]) - (original[temp - 2][1]) * (original[temp - 2][1])
						+ original[temp - 2][2] * original[temp - 2][2] - original[i][2] * original[i][2];
				AT[0][i] = 2 * (original[i][0] - original[temp - 2][0]);
				AT[1][i] = 2 * (original[i][1] - original[temp - 2][1]);
				// Log.d("genxinshujudistance", " orignal: "+original[i][2]);

			}
			double X1[][] = multiMetrixAandB(AT, A);
			double X2[][] = Mrinv(X1, 2);
			double X3[][] = multiMetrixAandB(AT, B);
			double X4[][] = multiMetrixAandB(X2, X3);
			// Log.d("genxinshujuX1", "" + X1[0][0] + " " + X1[0][1] + " " +
			// X1[1][0] + " " + X1[1][1]);
			// Log.d("genxinshujuX2", "" + X2[0][0] + " " + X2[0][1] + " " +
			// X2[1][0] + " " + X2[1][1]);
			// Log.d("genxinshujuX3", "" + X3[0][0] + " " + X3[1][0] + " " +
			// B[0][0]);
			// Log.d("genxinshujuX4", "" + X4[0][0] + " " + X4[1][0]);

			X = multiMetrixAandB(Mrinv(multiMetrixAandB(AT, A), 2), multiMetrixAandB(AT, B));

			XX.add(Double.valueOf(Math.floor(X[0][0])).intValue());
			XX.add(Double.valueOf(Math.floor(X[1][0])).intValue());
			// Log.d("genxinshuju", "" + XX[0][0] + " " + XX[1][0] + " A: " +
			// A[0][0] + " " + A[0][1] + " B: " + B[0][0]
			// + " AT: " + AT[0][0] + " " + AT[1][0]);

		} else {
			XX.add(-999);
			XX.add(-999);
		}
		return XX;

	}

	// 定位B算法
	public static List<Integer> localizationFunc1(Set<Beacon> beaconSet) {
		List<Integer> X1 = new ArrayList<Integer>();
		Iterator<Beacon> ite = beaconSet.iterator();
		int length = beaconSet.size();
		int original[][] = new int[length][3];
		int temp = 0;
		while (ite.hasNext()) {
			Beacon b = ite.next();
			original[temp][0] = b.getX();
			original[temp][1] = b.getY();
			original[temp][2] = b.getDistance();
			Log.d("genxinshujuBBBBBBB", "" + original[temp][0] + " " + original[temp][1] + " " + original[temp][2]);
			temp += 1;
		}
		Log.d("genxinshujuBBBBBBBB", "" + temp + "   " + beaconSet.size());

		// 要求的坐标为（A的转置乘上A）的逆乘上A的转置乘上B
		if (temp > 1 && temp == length) {
			double A[][] = new double[temp - 1][2];
			double AT[][] = new double[2][temp - 1];
			double B[][] = new double[temp - 1][1];
			for (int i = 0; i < temp - 1; i++) {
				A[i][0] = 2 * (original[i][0] - original[temp - 2][0]);
				A[i][1] = 2 * (original[i][1] - original[temp - 2][1]);
				B[i][0] = (original[i][0]) * (original[i][0]) - (original[temp - 2][0]) * (original[temp - 2][0])
						+ (original[i][1]) * (original[i][1]) - (original[temp - 2][1]) * (original[temp - 2][1])
						+ original[temp - 2][2] * original[temp - 2][2] - original[i][2] * original[i][2];
				AT[0][i] = 2 * (original[i][0] - original[temp - 2][0]);
				AT[1][i] = 2 * (original[i][1] - original[temp - 2][1]);

				// Log.d("genxinshuju", ""+A[i][0]+" "+A[i][1]+" "+B[i][0]+"
				// "+AT[0][i]+" "+AT[1][i]);
			}
			double X[][] = multiMetrixAandB(Mrinv(multiMetrixAandB(AT, A), 2), multiMetrixAandB(AT, B));
			X1.add(Double.valueOf(Math.floor(X[0][0])).intValue());
			X1.add(Double.valueOf(Math.floor(X[1][0])).intValue());
		} else {
			X1.add(-999);
			X1.add(-999);
		}
		return X1;

	}

	//
	//
	//
	//
	//

	// 三环定位算法
	public static List<Integer> threePointLocalization(Set<Beacon> beaconSet) {
		double x = -9999;
		double y = -9999;
		if (beaconSet.size() >= 3) {
			Beacon a = new Beacon();
			Beacon b = new Beacon();
			Beacon c = new Beacon();

			a = BeaconUtil.getMax(beaconSet);
			beaconSet.remove(a);
			b = BeaconUtil.getMax(beaconSet);
			beaconSet.remove(b);
			c = BeaconUtil.getMax(beaconSet);
			int r1 = a.getDistance();
			int x1 = a.getX();
			int y1 = a.getY();
			int r2 = b.getDistance();
			int x2 = b.getX();
			int y2 = b.getY();
			int r3 = c.getDistance();
			int x3 = c.getX();
			int y3 = c.getY();
			x = ((r1 * x2 + r2 * x1) * 1.0) / ((r1 + r2) * 1.0);
			y = ((r1 * y2 + r2 * y1) * 1.0) / ((r1 + r2) * 1.0);
			if (y1 == y2) {
				if (Math.abs(x - x3) <= r3) {
					int yy1 = y3 + Double.valueOf(Math.sqrt(r3 * r3 - (x - x3) * (x - x3))).intValue();
					int yy2 = y3 - Double.valueOf(Math.sqrt(r3 * r3 - (x - x3) * (x - x3))).intValue();
					if (Math.abs(yy1 - y1) <= Math.abs(yy2 - y1)) {
						y = yy1;
					} else {
						y = yy2;
					}
				} else {
					y = (y3 + y) / 2;
					x = (x3 + x) / 2;
				}
			} else {
				double A = -((x2 - x1) * 1.0) / ((y2 - y1) * 1.0);
				double B = -1;
				double C = (((x2 - x1) * 1.0) / ((y2 - y1) * 1.0)) * x + y;
				double D = Math.abs((A * x3 + B * y3 + C) / (Math.sqrt(A * A + B * B)));
				if (D <= r3) {
					double aa = A * A + 1;
					double bb = 2 * A * C - 2 * x3 - 2 * y3 * A;
					double cc = x3 * x3 + C * C - 2 * y3 * C + y3 * y3 - r3 * r3;
					int xxx1 = Double.valueOf((-bb + Math.sqrt(bb * bb - 4 * aa * cc)) / (2 * aa)).intValue();
					int yyy1 = Double.valueOf(A * xxx1 + C).intValue();
					int xxx2 = Double.valueOf((-bb - Math.sqrt(bb * bb - 4 * aa * cc)) / (2 * aa)).intValue();
					int yyy2 = Double.valueOf(A * xxx2 + C).intValue();
					if (Math.abs(yyy1 - y) <= Math.abs(yyy2 - y)) {
						y = yyy1;
						x = xxx1;
					} else {
						x = xxx2;
						y = yyy2;
					}
				} else {
					y = (y3 + y) / 2;
					x = (x3 + x) / 2;
				}
			}
		} else if (beaconSet.size() == 2) {
			Iterator<Beacon> ite = beaconSet.iterator();
			int rr1 = 0;
			boolean isfirst = true;
			int xx1 = 0;
			int yy1 = 0;
			int xx2 = 0;
			int yy2 = 0;
			int rr2 = 0;
			while (ite.hasNext()) {
				Beacon b = ite.next();
				if (isfirst) {
					rr1 = b.getDistance();
					xx1 = b.getX();
					yy1 = b.getY();
					isfirst = false;
				} else {
					rr2 = b.getDistance();
					xx2 = b.getX();
					yy2 = b.getY();
				}
			}
			x = ((rr1 * xx2 + rr2 * xx1) * 1.0) / ((rr1 + rr2) * 1.0);
			y = ((rr1 * yy2 + rr2 * yy1) * 1.0) / ((rr1 + rr2) * 1.0);
		} else if (beaconSet.size() == 1) {
			int rrr = 0;
			Iterator<Beacon> ite = beaconSet.iterator();
			while (ite.hasNext()) {
				Beacon b = ite.next();
				rrr = b.getDistance();
				x = b.getX();
				y = b.getY();
			}
			double du = Math.random();
			x = Math.cos(du * 360) * rrr + x;
			y = Math.sin(du * 360) * rrr + y;

		} else {

		}
		Log.d("genxinshujusanhuansanhuan", x + "   " + y);
		int lx = Double.valueOf(x).intValue();
		int ly = Double.valueOf(y).intValue();
		List<Integer> list = new ArrayList<Integer>();
		list.add(lx);
		list.add(ly);
		return list;

	}

	// 矩阵求逆运算
	public static double[][] Mrinv(double[][] a, int n) {
		int i, j, row, col, k;
		double max, temp;
		int[] p = new int[n];
		double[][] b = new double[n][n];
		for (i = 0; i < n; i++) {
			p[i] = i;
			b[i][i] = 1;
		}

		for (k = 0; k < n; k++) {
			// 找主元
			max = 0;
			row = col = i;
			for (i = k; i < n; i++)
				for (j = k; j < n; j++) {
					temp = Math.abs(b[i][j]);
					if (max < temp) {
						max = temp;
						row = i;
						col = j;
					}
				}
			// 交换行列，将主元调整到 k 行 k 列上
			if (row != k) {
				for (j = 0; j < n; j++) {
					temp = a[row][j];
					a[row][j] = a[k][j];
					a[k][j] = temp;
					temp = b[row][j];
					b[row][j] = b[k][j];
					b[k][j] = temp;
				}
				i = p[row];
				p[row] = p[k];
				p[k] = i;
			}
			if (col != k) {
				for (i = 0; i < n; i++) {
					temp = a[i][col];
					a[i][col] = a[i][k];
					a[i][k] = temp;
				}
			}
			// 处理
			for (j = k + 1; j < n; j++)
				a[k][j] /= a[k][k];
			for (j = 0; j < n; j++)
				b[k][j] /= a[k][k];
			a[k][k] = 1;

			for (j = k + 1; j < n; j++) {
				for (i = 0; i < k; i++)
					a[i][j] -= a[i][k] * a[k][j];
				for (i = k + 1; i < n; i++)
					a[i][j] -= a[i][k] * a[k][j];
			}
			for (j = 0; j < n; j++) {
				for (i = 0; i < k; i++)
					b[i][j] -= a[i][k] * b[k][j];
				for (i = k + 1; i < n; i++)
					b[i][j] -= a[i][k] * b[k][j];
			}
			for (i = 0; i < k; i++)
				a[i][k] = 0;
			a[k][k] = 1;
		}
		// 恢复行列次序；
		for (j = 0; j < n; j++)
			for (i = 0; i < n; i++)
				a[p[i]][j] = b[i][j];

		return a;
	}

	// 矩阵乘法
	public static double[][] multiMetrixAandB(double metrixA[][], double metrixB[][]) {
		double result[][] = new double[metrixA.length][metrixB[0].length];
		int x, i, j = 0;
		double tmp = 0;
		for (i = 0; i < metrixA.length; i++) {
			for (j = 0; j < metrixB[0].length; j++) {
				for (x = 0; x < metrixB.length; x++)
					tmp += metrixA[i][x] * metrixB[x][j];// 矩阵AB中a_ij的值等于矩阵A的i行和矩阵B的j列的乘积之和

				result[i][j] = tmp;
				tmp = 0; // 中间变量，每次使用后都得清零
			}
		}
		return result;
	}

}
