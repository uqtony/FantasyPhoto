package com.uqtony.fantasyphoto;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.uqtony.fantasyphoto.azure.ImageManager;
import com.uqtony.fantasyphoto.dropbox.DropboxManager;
import com.uqtony.fantasyphoto.samsung.SamsungImageFilterManager;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PhotoViewerActivity extends Activity {
    private static final String TAG = "PhotoViewerActivity";
    private ImageView imageView, qrImageView, photoImageView;
    private static final int COUNTDOWN_TO_FINISH_INTERVAL = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_photo_viewer);
        imageView = findViewById(R.id.imageView);
        qrImageView = (ImageView)findViewById(R.id.qrImageView);
        photoImageView = findViewById(R.id.photoImageView);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (countDownToFinishThread != null && countDownToFinishThread.isAlive()){
                    countDownToFinishThread.interrupt();
                    countDownToFinishThread = null;
                }
                startActivity(new Intent(PhotoViewerActivity.this, DetectorActivity.class));
                PhotoViewerActivity.this.finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Picasso.get().load("/storage/emulated/0/tensorflow/preview.png").into(imageView);
        File imgFile = new  File("/storage/emulated/0/tensorflow/preview.jpg");
        qrImageView.setVisibility(View.INVISIBLE);
        if(imgFile.exists()){
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.retake_background);
            //Bitmap result = PhotoFrameManager.drawColorFrame(myBitmap, Color.WHITE);
            Bitmap result = myBitmap.copy(Bitmap.Config.ARGB_8888, true);
            photoImageView.setImageBitmap(result);
            //Glide.with(this).load(imgFile).into(photoImageView);
            //Glide.with(this).asGif().load(R.drawable.retake_background_anim).into(imageView);
            Glide.with(this).load(R.drawable.retake_background_qrcode).into(imageView);
            //imageView.setImageBitmap(background);
            if (imgFile.exists())
                uploadFile(Uri.fromFile(imgFile).toString());
        }
        startFinishThread();
    }

    public Bitmap generateQRCode(String text){
        Bitmap qrImage = null;
        int size = 200;

        Map<EncodeHintType, Object> hintMap = new EnumMap<>(EncodeHintType.class);
        hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hintMap.put(EncodeHintType.MARGIN, 1);
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        try {
            BitMatrix byteMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size,
                    size, hintMap);
            qrImage = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++){
                for (int y = 0; y < size; y++){
                    qrImage.setPixel(x, y, byteMatrix.get(x,y) ? Color.BLACK : Color.WHITE);
                }
            }
        }catch (WriterException e){
            e.printStackTrace();
        }

        return qrImage;
    }

    Thread finishThread = null;

    void startFinishThread() {
        finishThread = new Thread(){
            @Override
            public void run(){
                try{
                    Thread.sleep(10*60*1000); // sleep 10minutes
                    if (countDownToFinishThread != null && countDownToFinishThread.isAlive())
                    {
                        countDownToFinishThread.interrupt();
                        countDownToFinishThread = null;
                    }
                    if (uploadThread != null && uploadThread.isAlive()){
                        uploadThread.interrupt();
                        uploadThread = null;
                    }
                    startActivity(new Intent(PhotoViewerActivity.this, DetectorActivity.class));
                    PhotoViewerActivity.this.finish();
                }catch (InterruptedException e){

                }
            }
        };
        finishThread.start();
    }

    public void uploadFile(String fileUri) {
        //uploadToDropbox(fileUri);
        uploadToAzureBlob(fileUri);
    }

    Thread countDownToFinishThread = null;
    TextView countDownTextView = null;

    private void countDownToFinish() {
        if (countDownToFinishThread != null)
            return;
        countDownToFinishThread = new Thread(){
            @Override
            public void run() {
                try {
                    int count = 0;
                    while(count++ < COUNTDOWN_TO_FINISH_INTERVAL) {
                        final int countProgress = COUNTDOWN_TO_FINISH_INTERVAL - count;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(countDownTextView == null) {
                                    countDownTextView = findViewById(R.id.countDownToFinishTextView);
                                    //Typeface countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/tt0140m.ttf");
                                    Typeface countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/gillsans.ttf");
                                    countDownTextView.setTypeface(countDownTextFont);
                                    countDownTextView.setVisibility(View.VISIBLE);
                                }
                                countDownTextView.setText(Integer.toString(countProgress));
                            }
                        });
                        Thread.sleep(1000);
                    }// end of while
                    if(finishThread != null && finishThread.isAlive()){
                        finishThread.interrupt();
                        finishThread = null;
                    }
                    startActivity(new Intent(PhotoViewerActivity.this, DetectorActivity.class));
                    PhotoViewerActivity.this.finish();
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        };
        countDownToFinishThread.start();
    }

    Thread uploadThread = null;

    private void uploadToAzureBlob(String fileUri){
        final String _fileUri = fileUri;
        uploadThread = new Thread() {
            @Override
            public void run() {
                try {
                    String uriPath = ImageManager.UploadImage(_fileUri);
                    final Bitmap qrImage = generateQRCode(uriPath);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            qrImageView.setVisibility(View.VISIBLE);
                            qrImageView.setImageBitmap(qrImage);
                            Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.photo_viewer_qrcode_background_qrcode);
                            imageView.setImageBitmap(background);
                            imageView.setImageAlpha(125);
                            photoImageView.setImageAlpha(125);
                            countDownToFinish();
                        }
                    });
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        };
        uploadThread.start();
    }

    private void uploadToDropbox(String fileUri) {
        DropboxManager.UploadListener listener = new DropboxManager.UploadListener() {
            @Override
            public void onUploadComplete(final String dropboxFilePath) {
                new Thread(){
                    @Override
                    public void run(){
                        String link = DropboxManager.getInst(PhotoViewerActivity.this).getDropboxSharedLink(dropboxFilePath);
                        final Bitmap qrImage = generateQRCode(link);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                qrImageView.setVisibility(View.VISIBLE);
                                qrImageView.setImageBitmap(qrImage);
                            }
                        });
                    }
                }.start();
            }

            @Override
            public void onError(Exception e) {

            }
        };
        DropboxManager.getInst(this).uploadFile(fileUri, listener);
    }
}
