package com.smartstrap;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.smartstrap.bluetooth.BluetoothInfo;
import com.smartstrap.bluetooth.BluetoothService;
import com.smartstrap.bluetooth.Constants;

import java.io.IOException;
import java.lang.reflect.Method;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class HomeActivity extends FragmentActivity {


    private static final String TAG = "HomeActivity";

    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_VIBRATE = 1;
    public static final int RINGER_MODE_NORMAL = 2;

    public static final String DBName =  "smartStrap.db";

    // 音量控制
    private AudioManager audioManager;


    TelephonyManager telephonyManager;
    // 儲存目前來電狀態
    static int phoneState = TelephonyManager.CALL_STATE_IDLE;

    public static BluetoothAdapter mBluetoothAdapter = null;
    public static BluetoothService mBlueToothService = null;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    public  MediaPlayer myMediaPlaye;
    public  final long[] pattern = {500, 1000, 500,1000};
    public  Vibrator myVibrator;

    public  SharedPreferences spref = null;
    public  SharedPreferences.Editor editor = null;
    public  final String SharePreSecure = "lastConnSecure";
    public  final String SharePreAddress = "lastConnAddress";

    // 給予fragment 使用 context
    public static Context contextOfApplication;
    public static Context getContextOfApplication()
    {
        return contextOfApplication;
    }
    public static MyDBHelper DB;

    private FragmentManager fragMgr;
    private FragmentTransaction transaction;
    private FragmentHome fragmentHome = new FragmentHome();
    private FragmentSetting fragmentSetting = new FragmentSetting();
    private FragmentPhone fragmentPhone = new FragmentPhone();
    private FragmentLocation fragmentLocation = new FragmentLocation();
    private Fragment_audio_recorder fragmentAudiorecorder = new Fragment_audio_recorder();

    private static MediaRecorder myAudioRecorder;
    private static String outputFile = null;
    private static String mFileName = null;

    class BuildDev {
        public static final int RECORD_AUDIO = 0;
    }


    private View.OnClickListener changeView = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            transaction = fragMgr.beginTransaction();
            switch (view.getId()) {
                case R.id.btn_phoneCall:
                    transaction.replace(R.id.frameLayout, fragmentPhone);
                    break;
                case R.id.btn_location:
                    transaction.replace(R.id.frameLayout, fragmentLocation);
                    break;
                case R.id.btnHome:
                    transaction.replace(R.id.frameLayout, fragmentHome);

                    break;
                case R.id.btnSetting:
                    transaction.replace(R.id.frameLayout, fragmentSetting);
                    break;
                case R.id.btn_audioRecord:
                    transaction.replace(R.id.frameLayout, fragmentAudiorecorder);
                    break;
            }
//呼叫commit讓變更生效。
            transaction.commit();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        HomeActivity.contextOfApplication = getApplicationContext();

        // 取得通話全縣
        if (  !(isGetPression(READ_PHONE_STATE)  && isGetPression(CALL_PHONE) && isGetPression(READ_CONTACTS)&& isGetPression(RECORD_AUDIO)&& isGetPression(WRITE_EXTERNAL_STORAGE)) ) {

            ActivityCompat.requestPermissions(HomeActivity.this, new String[] {READ_PHONE_STATE, CALL_PHONE,READ_CONTACTS,RECORD_AUDIO,WRITE_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);

            return;
        }


        outputFile = Environment.getExternalStorageDirectory().
                getAbsolutePath() + "/myrecording.3gp";
        ;

        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        myAudioRecorder.setOutputFile(outputFile);


        initView();
        initData();

        //bluetooth
        // 藍芽建立
        HomeActivity.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (HomeActivity.mBluetoothAdapter == null) {
            Activity activity = this;
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (HomeActivity.mBlueToothService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (HomeActivity.mBlueToothService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth services
                HomeActivity.mBlueToothService.start();
            }
        }
    }
    @Override
    public void onStart() {
        super.onStart();

        // 藍芽功能偵測  => 開啟
        if (!HomeActivity.mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (HomeActivity.mBlueToothService == null) {
            // setup mBlueToothService
            HomeActivity.mBlueToothService = new BluetoothService(this, mHandler);
        } else {
            // update mHandler
            HomeActivity.mBlueToothService.mHandler = mHandler;
        }
    }
    void initView(){
        ImageButton btnPhone = (ImageButton)findViewById(R.id.btn_phoneCall);
        ImageButton btnLocation = (ImageButton)findViewById(R.id.btn_location);
        ImageButton btnHome = (ImageButton)findViewById(R.id.btnHome);
        ImageButton btnSetting = (ImageButton)findViewById(R.id.btnSetting);
        ImageButton btnAutioRecorder = (ImageButton)findViewById(R.id.btn_audioRecord);
        btnPhone.setOnClickListener(changeView);
        btnLocation.setOnClickListener(changeView);
        btnHome.setOnClickListener(changeView);
        btnSetting.setOnClickListener(changeView);
        btnAutioRecorder.setOnClickListener(changeView);

    }

    void initData(){
        //取得音量控制器
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        // 使用來掛斷電話
        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        // 元件建立
        myVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        myMediaPlaye = MediaPlayer.create(this, R.raw.alert);
        fragMgr = getSupportFragmentManager();
        fragMgr.beginTransaction()
                .add(R.id.frameLayout, fragmentHome)
                .commit();


        DB = new MyDBHelper(this,DBName, null, 1);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:

                            fragmentSetting.updateStatus();
                            Toast.makeText(HomeActivity.this, "與" + BluetoothInfo.DEVICE_NAME + "裝置連線成功", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            fragmentSetting.updateStatus();
                        case BluetoothService.STATE_LISTEN:
                            break;
                        case BluetoothService.STATE_NONE:
                            fragmentSetting.updateStatus();
                            break;
                    }
                    break;

                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0 , msg.arg1);

                    // 更新設定畫面的資訊
                    fragmentSetting.updateReadStatus(readMessage);

                    if(readMessage.equals( getResources().getString(R.string.alertACondition) )){
                        alertStart();
                        new android.app.AlertDialog.Builder(HomeActivity.this)
                                .setTitle(R.string.alertATitle)
                                .setMessage(R.string.alertAContent)
                                .setPositiveButton(R.string.alertAPositiveBtn, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
//                                                                                  startActivity(new Intent(getApplicationContext(),CPRActivity.class));
                                        Toast.makeText(HomeActivity.this, "正向按鈕", Toast.LENGTH_SHORT).show();
                                        alertStop();
                                    }
                                })
                                .setNegativeButton(R.string.alertANegativeBtn, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertStop();
                                    }
                                })
                                .setOnCancelListener(new DialogInterface.OnCancelListener(){
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        alertStop();
                                    }
                                })
                                .show();
                    }else if(readMessage.equals( getResources().getString(R.string.alertBPhoneEndCondition))){
                        if(phoneState == TelephonyManager.CALL_STATE_RINGING){
                            endCall();
                            Toast.makeText(HomeActivity.this, "事件B：掛掉來電電話", Toast.LENGTH_SHORT).show();
                        }else{
                            Toast.makeText(HomeActivity.this, "事件B：目前來電可以掛斷", Toast.LENGTH_SHORT).show();
                        }
                    }else if(readMessage.equals( getResources().getString(R.string.alertRingerNormalCondition))){
                        Toast.makeText(HomeActivity.this, "事件C：聲音設定為聲音模式", Toast.LENGTH_SHORT).show();
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    }else if(readMessage.equals( getResources().getString(R.string.alertRingerVibrateCondition))){
                        Toast.makeText(HomeActivity.this, "事件D：聲音設定為震動模式", Toast.LENGTH_SHORT).show();
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    }else if(readMessage.equals( getResources().getString(R.string.alertRingerSilentCondition))){
                        Toast.makeText(HomeActivity.this, "事件E：聲音設定為靜音模式", Toast.LENGTH_SHORT).show();
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }else if(readMessage.equals( getResources().getString(R.string.recorderStart))){
                        Toast.makeText(HomeActivity.this, "事件F：開始錄音", Toast.LENGTH_SHORT).show();
                        start();
                        fragmentAudiorecorder.start();
                    }else if(readMessage.equals( getResources().getString(R.string.recorderStop))){
                        Toast.makeText(HomeActivity.this, "事件G：停止錄音", Toast.LENGTH_SHORT).show();
                        stop();
                        fragmentAudiorecorder.stop();
                    }else if(readMessage.equals( getResources().getString(R.string.recorderPlay))){

                        try{
                            play();
                        }catch(IOException e){
                            e.printStackTrace();
                        }

                        fragmentAudiorecorder.play();
                    }

                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    break;

                case Constants.MESSAGE_CONNLOST:
                    alertStart();
                    new AlertDialog.Builder(HomeActivity.this)
                            .setTitle(R.string.alertBTDisConnTitle)
                            .setMessage(R.string.alertBTDisConnContent)
                            .setPositiveButton(R.string.alertBTPositiveBtn, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    transaction = fragMgr.beginTransaction();
                                    transaction.replace(R.id.frameLayout, fragmentSetting);
                                    transaction.commit();

                                    alertStop();
                                }
                            })
                            .setNegativeButton(R.string.alertBTNegativeBtn, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    alertStop();
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener(){
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    alertStop();
                                }
                            })
                            .show();
                    break;
                case Constants.MESSAGE_CONNFAIL:
                    String deviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(HomeActivity.this,"與" + deviceName + "裝置連線失敗", Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    String strToast = msg.getData().getString(Constants.TOAST);
                    Toast.makeText(HomeActivity.this,strToast, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public  void alertStart(){
        myMediaPlaye.start();
        myMediaPlaye.setLooping(true);
        myVibrator.vibrate(pattern, 0);
    }

    public  void alertStop(){
        myMediaPlaye.pause();
        myMediaPlaye.setLooping(false);
        myVibrator.cancel();
    }

    boolean isGetPression(String pressionName){
        return ActivityCompat.checkSelfPermission(HomeActivity.this, pressionName) == PackageManager.PERMISSION_GRANTED;
    }

    private void endCall()
    {
        Class<TelephonyManager> c = TelephonyManager.class;
        try
        {
            Method getITelephonyMethod = c.getDeclaredMethod("getITelephony", (Class[]) null);
            getITelephonyMethod.setAccessible(true);
            ITelephony iTelephony = null;
            Log.e(TAG, "End call.");
            iTelephony = (ITelephony ) getITelephonyMethod.invoke(telephonyManager, (Object[]) null);
            iTelephony.endCall();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Fail to answer ring call.", e);
        }
    }

    // 錄音

    public void start() {
        try {
            myAudioRecorder.prepare();
            myAudioRecorder.start();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void stop() {
        myAudioRecorder.stop();
        myAudioRecorder.release();
        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        myAudioRecorder.setOutputFile(outputFile);
    }

    public void play() throws IllegalArgumentException,
            SecurityException, IllegalStateException, IOException {

        MediaPlayer m = new MediaPlayer();
        m.setDataSource(outputFile);
        m.prepare();
        m.start();
    }
}