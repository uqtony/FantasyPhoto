package com.uqtony.fantasyphoto;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

public class WatchdogService extends Service {
    private static final String TAG = WatchdogService.class.getSimpleName();
    Thread watchdogThread;
    String targetProcessName = "com.uqtony.fantasyphoto";
    boolean isStop = false;

    public WatchdogService() {
        super();

    }

    @Override
    public void onCreate() {
        super.onCreate();
        watchdogThread = new Thread(){
            @Override
            public void run(){
                Log.d(TAG, "Watchdog start");
                isStop = false;
                while(!isStop) {
                    synchronized (watchdogThread) {
                        if (isStop)
                            return;
                    }
                    boolean isTargetExist = false;
                    try {
                        sleep(5000);
                    }catch (InterruptedException e){
                        // DO NOTHING
                    }
                    List<ActivityManager.RunningAppProcessInfo> appList = getRunningTask();
                    for (ActivityManager.RunningAppProcessInfo running : appList) {
                        if (running.processName.equals(targetProcessName)){
                            Log.d(TAG, targetProcessName + " exist");
                            isTargetExist = true;
                            continue;
                        }
                    }
                    if (!isTargetExist){
                        Log.d(TAG, targetProcessName+" not exist");
                        try {
                            sleep(5000);
                            synchronized (watchdogThread) {
                                if (isStop)
                                    return;
                            }
                        }catch (InterruptedException e){
                            // DO NOTHING
                        }
                        startTarget();
                    }
                }
                Log.d(TAG, "Watchdog stop");
            }
        };
        watchdogThread.start();
    }

    private void startTarget() {
        Log.d(TAG, "Start "+targetProcessName+" from watchdog service");
        Intent dialogIntent = new Intent(this, DetectorActivity.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(dialogIntent);
    }

    private List<ActivityManager.RunningAppProcessInfo>  getRunningTask() {
        ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appList = mActivityManager.getRunningAppProcesses();
        return appList;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroy watchdog");
        synchronized (watchdogThread) {
            isStop = true;
        }
        watchdogThread.interrupt();
        watchdogThread = null;
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}