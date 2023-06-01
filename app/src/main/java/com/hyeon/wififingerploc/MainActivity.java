package com.hyeon.wififingerploc;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.wifi.ScanResult;

import java.util.List;

import android.util.Log;

import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;

import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.Manifest;



public class MainActivity extends AppCompatActivity {
    // 변수 선언
    ImageView background;
    Button mapButton;
    Button warButton;
    Button locButton;
    String TAG = "MainActivity";
    IntentFilter mIntentFilter;
    String mApStr;
    public static Context mContext;
    DotView dotView;

    public enum ApplicationState {Initial, Ready, Wardriving, Localizaion, Pause};
    ApplicationState applicationState;
    ApplicationState tempApplicationState;

    private Handler locHandler;
    private HandlerThread locHandlerThread;

    WifiManager mWifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 위치, wifi 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, 1);
            Log.e(TAG, "wifi 요청");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            Log.e(TAG, "fine 요청");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            Log.e(TAG, "coarse 요청");
        }

        // 변수 초기화
        mContext = this;
        background = findViewById(R.id.imageView);
        mapButton = findViewById(R.id.button);
        warButton = findViewById(R.id.button2);
        locButton = findViewById(R.id.button4);
        //mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        applicationState = ApplicationState.Initial;
        dotView = findViewById(R.id.dot_view);

        locHandlerThread = new HandlerThread("WifiScanThread");
        locHandlerThread.start();
        locHandler = new Handler(locHandlerThread.getLooper()){
            @Override
            public void handleMessage(@NonNull Message msg){
                switch (msg.what){
                    case 1: //start
                        if(applicationState == ApplicationState.Localizaion)
                            scanAp();
                        break;
                    case 2: //stop
                        removeMessages(1);
                        removeMessages(3);
                        break;
                    case 3:
                        dotView.setClosestdot();
                        sendEmptyMessageDelayed(1, 5000);
                        break;
                }
            }
        };

        // button setting
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(applicationState != ApplicationState.Pause){
                    if (applicationState == ApplicationState.Initial){
                        applicationState = ApplicationState.Ready;
                        mapButton.setText("Delete Map");
                        Log.d(TAG, "Ready state 됨");
                        takePicture();
                    }
                    else{
                        if(applicationState == ApplicationState.Localizaion){
                            locHandler.sendEmptyMessage(2);
                        }
                        applicationState = ApplicationState.Initial;
                        dotView.invalidate();
                        mapButton.setText("Upload Map");
                        Log.d(TAG, "Initial state 됨");
                        removePiture();
                    }
                }

            }
        });
        warButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if(applicationState != ApplicationState.Pause){
                    if(applicationState != ApplicationState.Initial){
                        if(applicationState == ApplicationState.Localizaion){
                            locHandler.sendEmptyMessage(2);
                        }
                        applicationState = ApplicationState.Wardriving;
                        dotView.invalidate();
                        Log.d(TAG, "wardriving state 됨");
                    }
                }
            }
        });
        locButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(applicationState != ApplicationState.Pause){
                    if(applicationState == ApplicationState.Wardriving){
                        applicationState = ApplicationState.Localizaion;
                        Log.d(TAG, "Localization state 됨");

                        dotView.positionUpdated = 0;
                        // handler 시작
                        locHandler.sendEmptyMessage(1);
                    }
                }
            }
        });

        // Set up IntentFilter for "WiFi scan results available" Intent.
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    //-----------이미지 처리------------

    // 갤러리 image 가져 오는 함수
    private void takePicture(){
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 1);
    }

    // background 이미지 제거 함수
    private void removePiture(){
        ImageView image;
        image = (ImageView) findViewById(R.id.imageView);
        image.setImageResource(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    background.setImageURI(uri);
                }
                break;
        }
    }
    //--------------------------------------

    //-----------wifi 정보 받아옴--------------

    // wifi scan 실행 하는 함수

    public void scanAp(){
        tempApplicationState = applicationState;
        applicationState = ApplicationState.Pause;
        // 권한 현황 체크
        reRequest(0);
        Log.e("ScanAp", "WiFi scan start");
        boolean scanStarted = mWifiManager.startScan();
        // If the scan failed, log it.
        if (!scanStarted) Log.e(TAG, "WiFi scan failed...");
        Log.e("ScanAp", "WiFi scan end");
    }

    // 최종적인 wifi 정보를 다루는 함수
    private void treatTextView(String str)
    {
        Log.e("TreatTextView", "wifi 결과 정리1");
        /*
        TextView tv = (TextView) findViewById(R.id.text_view);
        tv.setMovementMethod(new ScrollingMovementMethod());
        tv.setText(str);
         */

        //Log.d("TreatTextView", str);
        if (tempApplicationState == ApplicationState.Wardriving && applicationState == ApplicationState.Pause){
            Log.e("TreatTextView", "wardriving ap 데이터 저장");
            dotView.getApData(str,dotView.dots.get(dotView.savedDot));
            //dotView.dots.get(dotView.savedDot).getApData(str);
            displayPopupWindow(1);
        }
        if (tempApplicationState == ApplicationState.Localizaion && applicationState == ApplicationState.Pause) {
            Log.e("TreatTextView", "localization 현재 위치 저장");
            dotView.setCurrentAp(str);
        }

        applicationState = tempApplicationState;
        locHandler.sendEmptyMessage(3);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.e("OnReceive", "도착");
            String action = intent.getAction();
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action))
            {
                List<ScanResult> scanResults = mWifiManager.getScanResults();
                mApStr = "";
                for (ScanResult result : scanResults)
                {
                    mApStr = mApStr + result.SSID + "; ";
                    mApStr = mApStr + result.BSSID + "; ";
                    mApStr = mApStr + result.capabilities + "; ";
                    mApStr = mApStr + result.frequency + " MHz;";
                    mApStr = mApStr + result.level + " dBm\n\n";
                }
                // Update UI to show all this information.
                treatTextView(mApStr);
            }
        }
    };

    @Override
    protected void onResume()
    {
        Log.e("onResume", "도착");
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }
    @Override
    protected void onPause()
    {
        Log.e("onPause", "도착");
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    void reRequest(int j){
        int i = j;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "fine fail");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            i++;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "coarse fail");
            //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            i++;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "wifi fail");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, 1);
            i++;
        }
        Log.e(TAG, "권한 check 종료");
        Log.e(TAG, Integer.toString(i));
    }
    //----------------------------------------

    //----------------popup 창 다루기--------------
    // Method to display the pop-up window
    public void displayPopupWindow(int i) {
        View popupView;
        switch (i){
            case 1: //scan 저장 여부
                popupView = getLayoutInflater().inflate(R.layout.pop_window01, null);
                break;
            case 2: //scan 시도 여부
                popupView = getLayoutInflater().inflate(R.layout.pop_window02, null);
                break;
            case 3: //dots 선택시 팝업
                popupView = getLayoutInflater().inflate(R.layout.pop_window03, null);
                break;
            case 4: //save AP 확인
                popupView = getLayoutInflater().inflate(R.layout.pop_window04, null);
                break;
            default:
                popupView = getLayoutInflater().inflate(R.layout.popup_window, null);
                break;
        }

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(popupView)
                .create();

        Button button01;
        Button button02;
        Button button03;
        Button button04;
        TextView wifiText;

        switch (i){
            case 1:
                button01 = popupView.findViewById(R.id.yes_button);
                button01.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss(); // Close only the popup window
                    }
                });
                button02 = popupView.findViewById(R.id.no_button);
                button02.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dotView.removeDotFromList(dotView.savedDot);
                        dotView.invalidate();
                        alertDialog.dismiss(); // Close only the popup window
                    }
                });
                wifiText = popupView.findViewById(R.id.wifiText);
                wifiText.setMovementMethod(new ScrollingMovementMethod());
                wifiText.setText(dotView.dots.get(dotView.savedDot).retAp());
                break;
            case 2:
                button01 = popupView.findViewById(R.id.yes_button);
                button01.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scanAp();
                        alertDialog.dismiss();
                    }
                });
                button02 = popupView.findViewById(R.id.no_button);
                button02.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dotView.removeDotFromList(dotView.savedDot);
                        dotView.invalidate();
                        alertDialog.dismiss(); // Close only the popup window
                    }
                });
                break;
            case 3: //dots 선택시 팝업
                button01 = popupView.findViewById(R.id.wifi_button);
                button01.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scanAp();
                        alertDialog.dismiss();
                    }
                });
                button02 = popupView.findViewById(R.id.delete_button);
                button02.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dotView.removeDotFromList(dotView.savedDot);
                        dotView.invalidate();
                        alertDialog.dismiss();
                    }
                });
                button03 = popupView.findViewById(R.id.see_button);
                button03.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        displayPopupWindow(4);
                        alertDialog.dismiss();
                    }
                });
                button04 = popupView.findViewById(R.id.cancel_button);
                button04.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                break;
            case 4: //save AP 확인
                button01 = popupView.findViewById(R.id.yes_button);
                button01.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                wifiText = (TextView) popupView.findViewById(R.id.wifiText);
                wifiText.setMovementMethod(new ScrollingMovementMethod());
                wifiText.setText(dotView.dots.get(dotView.savedDot).retAp());
                break;
            default:
                button01 = popupView.findViewById(R.id.close_button);
                button01.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss(); // Close only the popup window
                    }
                });
                break;
        }

        alertDialog.show();
    }

    //---------------------------------------

    //----------localization 다루는 부분----
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        locHandler.removeCallbacksAndMessages(null);
        locHandlerThread.quitSafely();
    }

    //thread로 하려던 삽질의 흔적
    /*
    private class LocalizationThread extends Thread{
        private Message msg;
        private Bundle bundle;
        public LocalizationThread() {
            bundle = new Bundle();
        }

        @Override
        public void run(){
            Log.d(TAG, "Thread 시작");
            while(applicationState == ApplicationState.Localizaion){
                if(dotView.positionUpdated == 0){
                    //scanAp();
                    bundle.putInt("value", 0);
                    msg = handler.obtainMessage();
                    msg.setData(bundle);
                    handler.sendMessage(msg);

                    while(dotView.positionUpdated != 1 || applicationState == ApplicationState.Pause){ };

                    try{
                        Thread.sleep(1000);
                    } catch (InterruptedException e){
                        Log.d(TAG, "Thread 전복");
                    }

                    //dotView.setClosestdot();
                    //dotView.invalidate();
                    //dotView.positionUpdated = 0;
                    bundle.putInt("value", 1);
                    msg = handler.obtainMessage();
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                }
                try{
                    Thread.sleep(5000);
                } catch (InterruptedException e){
                    Log.d(TAG, "Thread 전복");
                }
            }
        }
    }

    private class FunctionPerformHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg){
            super.handleMessage(msg);

            int data = msg.getData().getInt("value");

            switch (data){
                case 0:
                    scanAp();
                    break;
                case 1:
                    dotView.setClosestdot();
                    dotView.invalidate();
                    dotView.positionUpdated = 0;
                    break;
                default:
                    break;
            }
        }
    }*/
    //-------------------------------------
}


