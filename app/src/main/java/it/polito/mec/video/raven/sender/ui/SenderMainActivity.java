package it.polito.mec.video.raven.sender.ui;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.PrintWriter;
import java.io.StringWriter;

import it.polito.mec.video.raven.R;


public class SenderMainActivity extends AppCompatActivity {

    private ViewPagerAdapter mAdapter;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerExceptionHandler();
        setContentView(R.layout.activity_sender);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        ViewPager viewPager = (ViewPager) findViewById(R.id.view_pager);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        mAdapter.addFrag(new PreviewFragment(),"tab1");
        mAdapter.addFrag(new Page2Fragment(), "tab2");
        viewPager.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "My wakelook");
        wakeLock.acquire();
    }

    @Override
    protected void onPause() {
        wakeLock.release();
        super.onPause();
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
*/

    public static void registerExceptionHandler(){
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
    }

    private static class ExceptionHandler implements java.lang.Thread.UncaughtExceptionHandler {
        private final String LINE_SEPARATOR = "\n";
        public static final String LOG_TAG = ExceptionHandler.class.getSimpleName();

        @SuppressWarnings("deprecation")
        public void uncaughtException(Thread thread, Throwable exception) {
            StringWriter stackTrace = new StringWriter();
            exception.printStackTrace(new PrintWriter(stackTrace));

            StringBuilder errorReport = new StringBuilder();
            errorReport.append(stackTrace.toString());

            Log.e(LOG_TAG, errorReport.toString());

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }
}
