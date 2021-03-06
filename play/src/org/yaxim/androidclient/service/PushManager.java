package org.yaxim.androidclient.service;

import android.app.Service;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.commands.AdHocCommandManager;
import org.jivesoftware.smackx.commands.RemoteCommand;
import org.jivesoftware.smackx.push_notifications.PushNotificationsManager;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;

import java.util.HashMap;

public class PushManager {
	private final static String TAG = "yaxim.PushManager";
	private final static String NODE = "v1-register-push";

	private String deviceId = null;
	private String pushToken = null;
	private Service service;
	private XMPPConnection connection;

	public PushManager(Service service, XMPPConnection connection) {
		this.deviceId = YaximApplication.getConfig().getPushNodeId();
		this.service = service;
		this.connection = connection;
	}

	public boolean enableAccountPush() {
		if (!connection.isAuthenticated())
			return false;
		PushNotificationsManager pnm = PushNotificationsManager.getInstanceFor(connection);
		try {
			if (!pnm.isSupported()) {
				Log.i(TAG, "push not supported by account");
				return false;
			}
			getFcmPushToken();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean disableAccountPush() {
		try {
			if (connection.isAuthenticated()) {
				Jid push_service = JidCreate.from(service.getString(R.string.push_service));
				PushNotificationsManager.getInstanceFor(connection).disableAll(push_service);
				Log.i(TAG, "Successfully disabled push for " + push_service + " on account.");
				return true;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error obtaining FCM token!");
			e.printStackTrace();
		}
		return false;
	}

	private void getFcmPushToken() {
		Log.d(TAG, "requesting FCM token");
		//FirebaseApp.initializeApp(YaximApplication.getApp());
		FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
			@Override
			public void onComplete(@NonNull Task<InstanceIdResult> task) {
				if (!task.isSuccessful()) {
					Log.w(TAG, "FCM token retrieval failed.");
					task.getException().printStackTrace();
					return;
				}
				try {
					InstanceIdResult result = task.getResult();
					pushToken = result.getToken();
					Log.d(TAG, "FCM token: " + pushToken);
				} catch (Exception e) {
					Log.e(TAG, "Error obtaining FCM token!");
					e.printStackTrace();
				}
				try {
					if (connection.isAuthenticated()) {
						Jid push_service = JidCreate.from(service.getString(R.string.push_service));
						AdHocCommandManager ahcm = AdHocCommandManager.getAddHocCommandsManager(connection);
						RemoteCommand rc = ahcm.getRemoteCommand(push_service, NODE);
						DataForm df = new DataForm(DataForm.Type.submit);
						df.addField(new FormField("type", "fcm"));
						df.addField(new FormField("node", deviceId));
						df.addField(new FormField("token", pushToken));
						rc.execute(new Form(df));
						if (rc.getForm().hasField("node") && rc.getForm().hasField("secret")) {
							String node = rc.getForm().getField("node").getFirstValue();
							String secret = rc.getForm().getField("secret").getFirstValue();
							HashMap<String, String> pub_opts = new HashMap<>();
							pub_opts.put("secret", secret);
							PushNotificationsManager.getInstanceFor(connection)
									.enable(push_service, node, pub_opts);
						}
						Log.i(TAG, "Successfully registered push for " + push_service + " on account.");
					} else
						Log.i(TAG, "Not connected to server, not registering for FCM push.");
				} catch (Exception e) {
					Log.e(TAG, "Error obtaining FCM token!");
					e.printStackTrace();
				}
			}
		});

	}
}
