package com.uqtony.fantasyphoto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Log;

import com.beak.gifmakerlib.GifMaker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final public class PhotoFrameManager {
    private static final String TAG = "PhotoFrameManager";
    private static final Object lock = new Object();
    private static  PhotoFrameManager _inst;
    private ArrayList<Bitmap> photoFrames = new ArrayList<Bitmap>();
    private ArrayList<Integer> photoAnimFrames = new ArrayList<Integer>();
    private int currentIdx = -1;
    private Context context;
    private Thread makeGifThread = null;
    private ArrayList<Integer> photoAnimFrame1Images = new ArrayList<Integer>();
    private ArrayList<Integer> photoAnimFrame2Images = new ArrayList<Integer>();

    public static PhotoFrameManager getInst(Context context) {
        synchronized (lock){
            if (_inst == null)
                _inst = new PhotoFrameManager(context);
        }
        return _inst;
    }

    private PhotoFrameManager(){

    }

    private PhotoFrameManager(Context context) {
        this.context = context;
        init();
    }

    public synchronized Bitmap getCurrentPhotoFrame() {
        if (currentIdx < 0 || currentIdx > photoFrames.size())
            return null;
        return photoFrames.get(currentIdx);
    }

    public synchronized Integer getCurrentPhotoAnimFrameId() {
        if (currentIdx < 0 || currentIdx > photoAnimFrames.size())
            return -1;
        return photoAnimFrames.get(currentIdx);
    }

    public synchronized Bitmap next() {
        if (photoFrames.size() <= 0)
            return null;
        currentIdx = (currentIdx+1) % photoFrames.size();
        Log.d(TAG, "Next image, current Index = "+currentIdx);
        return getCurrentPhotoFrame();
    }

    public synchronized Bitmap previous() {
        if (photoFrames.size() <= 0)
            return null;
        currentIdx = (currentIdx - 1 + photoFrames.size()) % photoFrames.size();
        Log.d(TAG, "Previous image, current Index = "+currentIdx);
        return getCurrentPhotoFrame();
    }

    volatile boolean makingGif = false;

    public synchronized boolean isMakingGif() {
        return makingGif;
    }

    public synchronized void startMakeGif(Bitmap source, final GifMaker.OnGifListener listener) {
        if (makeGifThread !=  null){
            stopMakeGif();
        }
        Log.i(TAG, "Start making Gif");

        //final Bitmap _source = source.copy(Bitmap.Config.ARGB_8888, true);
        final Bitmap _source = Bitmap.createScaledBitmap(source, (int)(source.getWidth()* 0.8f), (int)(source.getHeight()*0.8f), false);
        makeGifThread = new Thread(){
            @Override
            public void run() {
                makingGif = true;
                final String root =
                        Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
                Log.i(TAG, "Make gif at "+root);
                final File myDir = new File(root);

                if (!myDir.mkdirs()) {
                    Log.w(TAG, "Make dir failed and stop making Gif");
                }
                final long startTime = System.currentTimeMillis();
                final String absolutePath = new File(myDir, "preview.gif").getAbsolutePath();
                GifMaker maker = new GifMaker(1);
                maker.setOnGifListener(listener);
                try {
                    maker.makeGif(combinePhotoAndFrame(_source), absolutePath);
                }catch (IOException e){
                    e.printStackTrace();
                }
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "Make Gif @ "+absolutePath);
                Log.d(TAG, "Completed in "+duration/1000+" sec");
                listener.onCompleted(absolutePath);
                makingGif = false;
            }
        };
        makeGifThread.start();
    }// end of startMakeGif

    public synchronized void stopMakeGif() {
        Log.i(TAG, "Stop making Gif");
        if (makeGifThread != null && makeGifThread.isAlive()) {
            makeGifThread.interrupt();
        }
        makingGif = false;
        makeGifThread = null;
    }// end of stopMakeGif

    private List<Bitmap> combinePhotoAndFrame(Bitmap source) {
        List<Integer> frames = (currentIdx == 0)?photoAnimFrame1Images:photoAnimFrame2Images;
        List<Bitmap> result = new ArrayList<Bitmap>();
        for (Integer id: frames){
            Bitmap _source = source.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(_source);
            Bitmap frame = BitmapFactory.decodeResource(context.getResources(), id);
            canvas.drawBitmap(frame, new Rect(0, 0, frame.getWidth(), frame.getHeight()),
                    new Rect(0, 0, _source.getWidth(), _source.getHeight()), new Paint());
            result.add(_source);
        }
        return result;
    }

    static public Bitmap drawColorFrame(Bitmap photo, int color){
        Bitmap _photo = photo.copy(Bitmap.Config.ARGB_8888, true);
        Canvas _canvas = new Canvas(_photo);
        Paint photoPaint = new Paint();
        photoPaint.setColor(color);
        photoPaint.setStyle(Paint.Style.STROKE);
        photoPaint.setStrokeWidth(100);
        _canvas.drawRect(new Rect(0, 0, photo.getWidth(), photo.getHeight()), photoPaint);
        return _photo;
    }

    static public Bitmap drawArtResult(Bitmap photo, Bitmap background){
        Bitmap result = background.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap _photo = photo.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        Canvas _canvas = new Canvas(_photo);
        Paint photoPaint = new Paint();
        photoPaint.setColor(Color.WHITE);
        photoPaint.setStyle(Paint.Style.STROKE);
        photoPaint.setStrokeWidth(100);
        _canvas.drawRect(new Rect(0, 0, photo.getWidth(), photo.getHeight()), photoPaint);
        //
        Matrix matrix = new Matrix();
        matrix.setRotate(
                15, // degrees
                photo.getWidth() / 2, // px
                photo.getHeight() / 2 // py
        );
        matrix.postScale(1.4f, 1.4f);
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        matrix.postTranslate(
                canvas.getWidth() * 0.2f,
                canvas.getHeight() *0.14f
        );
        // Initialize a new Paint instance to draw on canvas
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);

        canvas.drawBitmap(
                _photo, // Bitmap
                matrix, // Matrix
                paint // Paint
        );
        return result;
    }

    private void init() {
        //photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.style1));
        //photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.style2));
        //photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.style3));
//        photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.frame1));
//        photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.frame2));
//        photoAnimFrames.add(R.drawable.anim_frame1);
//        photoAnimFrames.add(R.drawable.anim_frame2);

        photoFrames.add(BitmapFactory.decodeResource(context.getResources(), R.drawable.new_photo_frame));
        photoAnimFrames.add(R.drawable.new_photo_frame);

        currentIdx = 0;
        initAnimFrameImages();
    }

    private void initAnimFrameImages() {
        photoAnimFrame1Images.add(R.drawable.anime_frame1_00);
        photoAnimFrame1Images.add(R.drawable.anime_frame1_02);
        photoAnimFrame1Images.add(R.drawable.anime_frame1_04);
        photoAnimFrame1Images.add(R.drawable.anime_frame1_06);
        photoAnimFrame1Images.add(R.drawable.anime_frame1_08);
        photoAnimFrame1Images.add(R.drawable.anime_frame1_10);

        photoAnimFrame2Images.add(R.drawable.anime_frame2_00);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_04);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_08);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_12);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_16);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_20);
        photoAnimFrame2Images.add(R.drawable.anime_frame2_24);
    }
}
