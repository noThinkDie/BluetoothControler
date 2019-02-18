package bluetooth.cw.com.bluetoothcontroler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow()
                .setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        findViewById(R.id.central).setOnClickListener(this);
        findViewById(R.id.periphera).setOnClickListener(this);
    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //当点击不同的menu item 是执行不同的操作
        switch (id) {
            case R.id.action_exit:
                onBackPressed();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.central:
                startActivity(new Intent(MainActivity.this, CentralActivity.class));
                break;
            case R.id.periphera:
                //Intent intentOne = new Intent(this, PeripheraActivity.class);
                //startService(intentOne);
                startActivity(new Intent(MainActivity.this, PeripheraActivity.class));
                //startActivity(new Intent(MainActivity.this, ControlerActivity.class));
                break;
        }
    }
}
