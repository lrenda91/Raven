package it.polito.mec.video.raven.sender.ui;


import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import it.polito.mec.video.raven.R;
import it.polito.mec.video.raven.sender.encoding.*;
import it.polito.mec.video.raven.sender.net.WSClientImpl;
import it.polito.mec.video.raven.sender.record.*;

@SuppressWarnings("deprecation")
public class PreviewFragment extends Fragment {

    private static final String TAG = "PREVIEW";

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final String[] PERMISSIONS = new String[]{ Manifest.permission.CAMERA };
    //By default, camera permission is granted.
    //If current API is >= 23, it is denied until user explicitly grants it
    private boolean mCameraPermissionGranted = (Build.VERSION.SDK_INT < 23);
    private boolean mAutoDetectQuality;

    private SurfaceView preview;
    private Button rec, pause, stop, mConnect;
    private SeekBar mPresetsSeekBar;
    private TextView mPresetsLabel;
    private SharedPreferences mPreferences;

    private AbsCamcorder mRecorder;

    private WSClientImpl mClient;
    private WSClientImpl.Listener mWebSocketListener = new WSClientImpl.Listener() {
        @Override
        public void onConnectionEstablished(String uri) {
            mClient.setBandWidthMeasureEnabled(mAutoDetectQuality);
            mRecorder.registerEncodingListener(mClient, null);

            setupConnectionButton(false);
            rec.setEnabled(true);
            pause.setEnabled(false);
            stop.setEnabled(false);

            List<Params> params = mRecorder.getPresets();
            int curIdx = params.indexOf(mRecorder.getCurrentParams());
            mClient.sendHelloMessage2(params, curIdx);
            Toast.makeText(getContext(), "Connected to "+uri, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionFailed(Exception e) {
            Toast.makeText(getContext(), "Can't mConnect to server: "
                    + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onConnectionLost(boolean closedByServer) {
            mRecorder.unregisterEncodingListener(mClient);

            setupConnectionButton(true);
            rec.setEnabled(false);
            pause.setEnabled(false);
            stop.setEnabled(false);
        }

        @Override
        public void onBandwidthChange(int Kbps, double performancesPercentage) {
            Log.d(TAG, "BANDWIDTH UTILIZATION: "+performancesPercentage+" %");
            try{
                if (performancesPercentage == 100.0){
                    if (mRecorder.switchToHigherQuality()){
                        Log.d(TAG, "Switched to: "+mRecorder.getCurrentParams());
                    }
                }
                else {
                    if (mRecorder.switchToLowerQuality()){
                        Log.d(TAG, "Switched to: "+mRecorder.getCurrentParams());
                    }
                }
            }catch(Exception ex){
                Log.w(TAG, ex.getMessage());
            }
        }

        @Override
        public void onResetReceived(int w, int h, int kbps) {
            if (mAutoDetectQuality){
                Log.d(TAG, "Ignoring RESET from server: Auto detect quality enabled");
                return;
            }
            Params newParams = new Params.Builder().width(w).height(h).bitRate(kbps).build();
            if (mRecorder.getPresets().contains(newParams)){
                mRecorder.switchToVideoQuality(newParams);
                return;
            }
            List<Params> compatibleParams = Params.getNearestPresets(newParams.getSize());
            if (compatibleParams.isEmpty()){
                Log.w(TAG, "Cannot reset. "+newParams+" is not available for this device");
                return;
            }
            Params target = compatibleParams.get(compatibleParams.size() - 1);
            mRecorder.switchToVideoQuality(target);
        }

    };

    public PreviewFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mClient = new WSClientImpl(mWebSocketListener);
        mRecorder = new NativeRecorderImpl(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preview = (SurfaceView) view.findViewById(R.id.preview);
        rec = (Button) view.findViewById(R.id.button_rec);
        pause = (Button) view.findViewById(R.id.button_pause);
        stop = (Button) view.findViewById(R.id.button_stop);
        mConnect = (Button) view.findViewById(R.id.button_connect);
        mPresetsSeekBar = (SeekBar) view.findViewById(R.id.presets_seek_bar);
        mPresetsLabel = (TextView) view.findViewById(R.id.presets_text_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasPermissions(PERMISSIONS)){
            requestPermissions(PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        else{
            mCameraPermissionGranted = true;
            setupCameraRecorder();
        }
    }

    @Override
    public void onPause() {
        if (mCameraPermissionGranted) {
            mRecorder.stopRecording();
            mRecorder.clearEncodingListeners();
            mRecorder.closeCamera();
        }
        if (mClient.isOpen()){
            mClient.closeConnection();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE){
            for (int i=0; i<permissions.length; i++){
                if (permissions[i].equals(Manifest.permission.CAMERA)){
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                        mCameraPermissionGranted = true;
                    }
                    else {
                        mCameraPermissionGranted = false;
                        Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void setupCameraRecorder(){
        mAutoDetectQuality = mPreferences.getBoolean(getString(R.string.pref_key_adaptivity), false);
        mPresetsSeekBar.setEnabled(!mAutoDetectQuality);

        mRecorder.registerEncodingListener(new SimpleEncodingListener() {
            @Override
            public void onEncodingStarted(Params params) {
                rec.setEnabled(false);
                pause.setEnabled(true);
                stop.setEnabled(true);
            }
            @Override
            public void onParamsChanged(Params actualParams) {
                int presetIdx = mRecorder.getPresets().indexOf(actualParams);
                mPresetsSeekBar.setProgress(presetIdx);
            }
            @Override
            public void onEncodingPaused() {
                rec.setEnabled(true);
                pause.setEnabled(false);
                stop.setEnabled(false);
            }
            @Override
            public void onEncodingStopped() {
                rec.setEnabled(true);
                pause.setEnabled(false);
                stop.setEnabled(false);
            }
        }, new Handler());

        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.startRecording();
            }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.pauseRecording();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.stopRecording();
            }
        });
        setupConnectionButton(true);

        mRecorder.openCamera();
        mRecorder.setSurfaceView(preview);

        mPresetsSeekBar.setMax(mRecorder.getPresets().size() - 1);
        mPresetsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Params params = mRecorder.getPresets().get(progress);
                mPresetsLabel.setText(String.format("(%dx%d) %d Kbps",params.width(), params.height(), params.bitrate()));
                if (fromUser){
                    mRecorder.switchToVideoQuality(params);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mRecorder.switchToVideoQuality(mRecorder.getCurrentParams());
    }

    private boolean hasPermissions(String[] permissions){
        for (String perm : permissions){
            if (ContextCompat.checkSelfPermission(getActivity(), perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }


    private void setupConnectionButton(boolean connect){
        String text = connect ? "Connect" : "Disconnect";
        View.OnClickListener listener;
        if (connect){
            listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //mRecorder.pauseRecording();
                    String ip = mPreferences.getString(getString(R.string.pref_key_server_ip),
                            getString(R.string.pref_server_ip_default_value));
                    int port = Integer.parseInt(mPreferences.getString(getString(R.string.pref_key_server_port),
                            getString(R.string.pref_server_port_default_value)));
                    mClient.connect(ip, port, 2000);
                }
            };
        }
        else{
            listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mRecorder.pauseRecording();
                    mClient.closeConnection();
                }
            };
        }
        mConnect.setText(text);
        mConnect.setOnClickListener(listener);
    }

}
