package bluetooth.cw.com.bluetoothcontroler;

import android.app.Application;

import com.inuker.bluetooth.library.BluetoothContext;

public class MyAppcation extends Application {

	private static MyAppcation instance;

	public static Application getInstance() {
		return instance;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		CrashHandler.getInstance().init(this);
		instance = this;
		BluetoothContext.set(this);
	}

}
