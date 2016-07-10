package it.polito.mec.video.raven;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mec.video.raven.sender.Util;

public class ServersActivity extends AppCompatActivity {

    private JSONArray serversJSON;
    private ListView mServersListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servers);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();*/
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                        ServersActivity.this);

                final View contentView = LayoutInflater.from(ServersActivity.this)
                        .inflate(R.layout.layout_dialog_new_server, null, false);
                alertDialogBuilder.setView(contentView);
                alertDialogBuilder.setTitle("Add new Server").setCancelable(false);
                alertDialogBuilder.setPositiveButton("Test", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Do nothing here because we override this button later to change the close behaviour.
                                //However, we still need this because on older versions of  Android unless we
                                //pass a handler the button doesn't get instantiated
                            }
                        });
                alertDialogBuilder.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });

                // create alert dialog
                final AlertDialog alertDialog = alertDialogBuilder.create();

                // show it
                alertDialog.show();

                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String serverName = ((EditText) contentView.findViewById(R.id.server_name_et))
                                .getText().toString();
                        String serverAddress = ((EditText) contentView.findViewById(R.id.server_address_et))
                                .getText().toString();
                        String serverPort = ((EditText) contentView.findViewById(R.id.server_port_et))
                                .getText().toString();
                        try {
                            validateNewServerInput(serverName, serverAddress, serverPort);
                            JSONObject obj = new JSONObject();
                            obj.put("name", serverName);
                            obj.put("address", serverAddress);
                            obj.put("port", serverPort);
                            serversJSON.put(obj);
                            commitToPreferences(serversJSON);
                            alertDialog.dismiss();
                        }catch(Exception e){
                            Toast.makeText(ServersActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                /*alertDialogBuilder
                        .setPositiveButton("Confirm",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                String serverName = ((EditText) contentView.findViewById(R.id.server_name_et))
                                        .getText().toString();
                                String serverAddress = ((EditText) contentView.findViewById(R.id.server_address_et))
                                        .getText().toString();
                                String serverPort = ((EditText) contentView.findViewById(R.id.server_port_et))
                                        .getText().toString();
                                try {
                                    validateNewServerInput(serverName, serverAddress, serverPort);
                                    JSONObject obj = new JSONObject();
                                    obj.put("name", serverName);
                                    obj.put("address", serverAddress);
                                    obj.put("port", serverPort);
                                    serversJSON.put(obj);
                                    commitToPreferences(serversJSON);
                                }catch(Exception e){
                                    Toast.makeText(ServersActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        });
                    */

            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        serversJSON = getFromPreferences();

        mServersListView = (ListView) findViewById(R.id.servers_list);
        mServersListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return serversJSON.length();
            }
            @Override
            public JSONObject getItem(int position) {
                JSONObject item = null;
                try{
                    item = serversJSON.getJSONObject(position);
                }catch (Exception e){
                    return null;
                }
                return item;
            }
            @Override
            public long getItemId(int position) {
                return 0;
            }
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                if (convertView == null){
                    convertView = LayoutInflater.from(ServersActivity.this).inflate(R.layout.layout_server_item,
                            null, false);
                }
                final JSONObject item = getItem(position);
                convertView.findViewById(R.id.cancel_server_btn).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //serversJSON.remove(position);
                        JSONArray newArray = new JSONArray();
                        for (int i=0;i<getCount(); i++){
                            if (i != position){
                                newArray.put(getItem(i));
                            }
                        }
                        serversJSON = newArray;
                        commitToPreferences(serversJSON);
                        notifyDataSetChanged();
                    }
                });
                try{
                    ((TextView) convertView.findViewById(R.id.server_name_tv)).setText(item.getString("name"));
                    ((TextView) convertView.findViewById(R.id.server_address_port_tv)).setText(
                            String.format("%s:%s", item.getString("address"), item.getString("port"))
                    );
                }
                catch(JSONException e){}
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmServer(item);
                    }
                });
                return convertView;
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        JSONArray array = getFromPreferences();
        if (array.length() == 0){
            return;
        }
        try {
            confirmServer(array.getJSONObject(0));
        }catch(JSONException e){}
    }

    @Override
    protected void onResume() {
        super.onResume();
        serversJSON = getFromPreferences();
        ((BaseAdapter) mServersListView.getAdapter()).notifyDataSetChanged();
    }



    private JSONArray getFromPreferences(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String prefsKey = getString(R.string.pref_key_saved_servers);
        String defaultValue = getString(R.string.pref_saved_servers_default_value);
        String servers = prefs.getString(prefsKey, defaultValue);
        try{
            return new JSONArray(servers);
        }
        catch(Exception e){
            Log.e("ServersActivity", "Can't retrieve servers: "+e.getMessage());
            return null;
        }
    }

    private void commitToPreferences(JSONArray array){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String prefsKey = getString(R.string.pref_key_saved_servers);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefsKey, array.toString());
        editor.commit();
    }

    private void confirmServer(JSONObject obj){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String IPKey = getString(R.string.pref_key_server_ip);
        String portKey = getString(R.string.pref_key_server_port);
        SharedPreferences.Editor editor = prefs.edit();
        try{
            editor.putString(IPKey, obj.getString("address"));
            editor.putString(portKey, obj.getString("port"));
        }
        catch(Exception e){}
        editor.commit();
        finish();
    }

    private static void validateNewServerInput(String name, String address, String port)
        throws Exception {
        if (name == null || name.isEmpty()){
            throw new Exception("Server name is empty");
        }
        if (!Util.isValidIPv4(address)){
            throw new Exception("Not a valid IP address");
        }
        try{
            Integer.parseInt(port);
        }catch(Exception e){
            throw new Exception("Wrong port format");
        }
    }

}
