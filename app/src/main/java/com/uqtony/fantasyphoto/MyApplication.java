package com.uqtony.fantasyphoto;

import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e("MyApplication", ex.getMessage());
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }
}
