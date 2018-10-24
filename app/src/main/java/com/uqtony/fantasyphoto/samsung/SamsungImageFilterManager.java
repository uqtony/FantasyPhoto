package com.uqtony.fantasyphoto.samsung;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.camera.SCamera;
import com.samsung.android.sdk.camera.filter.SCameraFilter;
import com.samsung.android.sdk.camera.filter.SCameraFilterInfo;
import com.samsung.android.sdk.camera.filter.SCameraFilterManager;

import java.util.ArrayList;
import java.util.List;

public class SamsungImageFilterManager {
    private static final String TAG = "SImageFilterManager";
    private static final Object lock = new Object();
    private static SamsungImageFilterManager _inst = null;

    private Context context;
    private SCamera mSCamera = null;
    private SCameraFilterManager mSCameraFilterManager;
    private List<SCameraFilterInfo> mFilterInfoList;

    public static SamsungImageFilterManager getInst(Context context) {
        synchronized (lock) {
            if (_inst == null){
                _inst = new SamsungImageFilterManager(context);
            }
        }
        return _inst;
    }

    private SamsungImageFilterManager(Context context) {
        this.context = context;
        init();
    }

    SCameraFilter beautyFilter;

    private void init() {
        mSCamera = new SCamera();
        try {
            mSCamera.initialize(context);
        } catch (SsdkUnsupportedException e) {
            Log.e(TAG, "Fail to initialize SCamera.");
            return;
        }
        if (!mSCamera.isFeatureEnabled(SCamera.SCAMERA_FILTER)) {
            Log.e(TAG, "This device does not support SCamera Filter feature.");
            return;
        }

        // retrieving an {@link com.samsung.android.sdk.camera.filter.SCameraFilterManager}
        mSCameraFilterManager = mSCamera.getSCameraFilterManager();
        mFilterInfoList = new ArrayList<>();
        for(SCameraFilterInfo info : mSCameraFilterManager.getAvailableFilters()) {
            if(info.getType() != SCameraFilterInfo.FILTER_TYPE_FACE_AR || info.getName().equals("No Effect")) {
                Log.d(TAG, "SCameraFilterInfo: "+info.getName());
                mFilterInfoList.add(info);
                if (info.getName().equals("Beauty")) {
                    //if (info.getName().equals("Negative"))
                    beautyFilter = mSCameraFilterManager.createFilter(info);
                    beautyFilter.setParameter("intensity", 4);
                    Number number = beautyFilter.getParameter("intensity");
                    Log.d(TAG, "Beauty intensity= "+number);
                }
            }
        }
    }// end of init

    public Bitmap processBeautyFilter(Bitmap bitmap) {
        return beautyFilter.processImage(bitmap);
    }

}// end of class SamsungImageFilterManager
