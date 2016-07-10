package it.polito.mec.video.raven;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

import it.polito.mec.video.raven.receiver.ui.ReceiverMainActivity;
import it.polito.mec.video.raven.sender.ui.SenderMainActivity;

public class MainActivity extends AppCompatActivity {

    private TextView mCurServerTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCurServerTV = (TextView) findViewById(R.id.cur_server_tv);

        findViewById(R.id.receiver_choice).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ReceiverMainActivity.class));
            }
        });

        findViewById(R.id.sender_choice).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SenderMainActivity.class));
            }
        });

        findViewById(R.id.settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ServersActivity.class), 1);
            }
        });

        updateCurrentServer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        updateCurrentServer();
    }

    private void updateCurrentServer(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String IPKey = getString(R.string.pref_key_server_ip);
        String portKey = getString(R.string.pref_key_server_port);
        String IP = prefs.getString(IPKey, getString(R.string.pref_server_ip_default_value));
        String port = prefs.getString(portKey, getString(R.string.pref_server_port_default_value));
        mCurServerTV.setText(String.format("SERVER\n%s:%s", IP, port));
    }

}
