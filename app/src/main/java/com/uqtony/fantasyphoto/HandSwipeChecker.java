package com.uqtony.fantasyphoto;

import android.graphics.Point;
import android.util.Log;

public class HandSwipeChecker {

    final static private String TAG = "HandSwipeChecker";
    final static private long COOL_DOWN_INTERVAL = 1200;
    final static private int CENTER_AREA = 4;
    final static private int XMaxBlock = 3, YMaxBlock = 3;
    final static private long EXPIRED_TIME = 1000;
    private int previewWidth = -1,  previewHeight = -1;
    private int lastArea =1;
    private long lastUpdateTime = 0;
    private long coolDownTime = 0;

    public HandSwipeChecker(int previewWidth, int previewHeight)
    {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    private void reset() {
        lastArea = -1;
        lastUpdateTime = 0;
    }

    public synchronized int isSwipe(int x, int y) {
        if (previewHeight < 0 || previewWidth < 0 || x < 0 || y < 0 || x > previewWidth || y > previewHeight)
            return 0;
        // if time is expired than reset
        long currentTime = System.currentTimeMillis();
        int currentArea = getArea(x, y);
        if ((currentTime - lastUpdateTime) > EXPIRED_TIME) {
            Log.d(TAG, "Expired!!!");
            lastArea = currentArea;
            lastUpdateTime = currentTime;
            return 0;
        }
        // if is cool down than reset
        if (coolDownTime > 0) {
            //Log.d(TAG, "Cool down...");
            coolDownTime -= (currentTime - lastUpdateTime);
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            return 0;
        }
        // if last area == area or last area != 4 than reset
        if (lastArea == currentArea) {
            //Log.d(TAG, "same!!");
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            return 0;
        }
        if (Math.abs(lastArea - currentArea) == XMaxBlock)
        {
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            return 0;
        }
        Log.d(TAG, "area="+currentArea+", last area="+lastArea);
        // 1. set cool down time
        coolDownTime = COOL_DOWN_INTERVAL;
        // 2. reset
        lastUpdateTime = currentTime;
        lastArea = currentArea;
        // return 1;
        return 1;
    }

    Point lastPos = null;

    volatile boolean isPrepareDetection = false;
    final long PREPARE_DETECTION_TIME = 1000;

    public synchronized int isSwipeInDistance(int x, int y){

        long currentTime = System.currentTimeMillis();
        int heightTreshold = (int)(previewWidth*0.625);
        if (lastPos == null && (y > heightTreshold)) {
            //Log.d(TAG, "[isSwipeInDistance] y not pass threshold");
            return 0;
        }
        if (x == 701 && y <= -10) {
            lastPos = null;
            isPrepareDetection = true;
            lastUpdateTime = currentTime;
            return 0;
        }
        if (y <= 50) {
            Log.d(TAG, "[isSwipeInDistance] x="+x+", y="+y);
            lastPos = null;
            isPrepareDetection = true;
            lastUpdateTime = currentTime;
            return 0;
        }
        if (isPrepareDetection) {
            if ((currentTime - lastUpdateTime) > PREPARE_DETECTION_TIME) {
                isPrepareDetection = false;
            }
            return 0;
        }

        if ((currentTime - lastUpdateTime) > EXPIRED_TIME) {
            lastPos = null;
            if (y <= heightTreshold) {
                lastPos = new Point(x, y);
            }
            lastUpdateTime = currentTime;
            //Log.d(TAG, "[isSwipeInDistance] is expired!");
            return 0;
        }

        if (lastPos == null) {
            //Log.d(TAG, "[isSwipeInDistance] last pos is null!");
            lastPos = new Point(x, y);
            return 0;
        }

        double distance = Math.sqrt(Math.pow(x - lastPos.x, 2) + Math.pow(y - lastPos.y, 2));
        int offsetX = Math.abs(lastPos.x - x);
        if (distance > 150 && offsetX > 100){
            //Log.d(TAG, "[isSwipeInDistance]Yes!!!!! Is Swipe!!!!  lastPos=("+lastPos+"),  currentPos=("+x+", "+y+"), distance="+distance+", offsetX="+offsetX);
            lastPos = null;
            return 1;
        }
        //Log.d(TAG, "[isSwipeInDistance] No.......lastPos=("+lastPos+"),  currentPos=("+x+", "+y+"), distance="+distance+", offsetX="+offsetX);
        return 0;
    }

    public synchronized int isSwipeFromCenter(int x, int y) {
        if (previewHeight < 0 || previewWidth < 0 || x < 0 || y < 0 || x > previewWidth || y > previewHeight)
            return 0;
        // if time is expired than reset
        long currentTime = System.currentTimeMillis();
        int currentArea = getArea(x, y);
        if ((currentTime - lastUpdateTime) > EXPIRED_TIME) {
            Log.d(TAG, "Expired!!!");
            lastArea = currentArea;
            lastUpdateTime = currentTime;
            return 0;
        }
        // if is cool down than reset
        if (coolDownTime > 0) {
            Log.d(TAG, "Cool down...");
            coolDownTime -= (currentTime - lastUpdateTime);
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            return 0;
        }
        // if last area == area or last area != 4 than reset
        if (lastArea == currentArea || lastArea != CENTER_AREA) {
            if (lastArea == currentArea) {
                Log.d(TAG, "same!!");
            }
            if (lastArea != CENTER_AREA){
                Log.d(TAG, "Not center, last area ="+lastArea+", area="+currentArea);
            }
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            return 0;
        }
        //Log.d(TAG, "area="+currentArea+", last area="+lastArea);
        // if area == 3 or area == 5 or area == 6 or area == 8
        if (Math.abs(currentArea-lastArea) <= 1 || currentArea == 6 || currentArea == 8) {
            // than
            // 1. set cool down time
            coolDownTime = COOL_DOWN_INTERVAL;
            // 2. reset
            lastUpdateTime = currentTime;
            lastArea = currentArea;
            // return 1;
            return 1;
        }
        else
            Log.d(TAG, "area="+currentArea+", last area="+lastArea);
        return 0;
    }

    // return 1: swipe to right, -1: swipt to left, 0: no swipe
    public int _isSwipe(int x, int y) {
        if (previewHeight < 0 || previewWidth < 0 || x < 0 || y < 0 || x > previewWidth || y > previewHeight)
            return 0;
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastUpdateTime) > EXPIRED_TIME) {
            reset();
        }
        int area = getArea(x, y);
        if (lastArea < 0) {
            lastUpdateTime = currentTime;
            lastArea = area;
            return 0;
        }

        if (area != lastArea){
            Log.d("HandSwipeChecker","area="+area+", last area="+lastArea);
            if (lastArea == 4 && area == 5)
            {
                reset();
                return 1;
            }
            if (lastArea == 4 && area == 3)
            {
                reset();
                return -1;
            }
            reset();
            return 0;
        }
        return 0;
    }

    private int getArea(int x, int y){
        int blockWidth = previewWidth/XMaxBlock;
        int blockHeight = previewHeight/YMaxBlock;
        int _x = x/blockWidth;
        int _y = y/blockHeight;
        //Log.d("HandSwipeChecker", "x,y="+x+", "+y+"  width, height="+ previewWidth+", "+previewHeight+" area="+(_x + XMaxBlock * _y));
        return _x + XMaxBlock * _y;
    }
}
