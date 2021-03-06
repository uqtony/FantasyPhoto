/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uqtony.fantasyphoto;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.beak.gifmakerlib.GifMaker;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.zxing.ResultPoint;
import com.uqtony.fantasyphoto.OverlayView.DrawCallback;
import com.uqtony.fantasyphoto.env.BorderedText;
import com.uqtony.fantasyphoto.env.ImageUtils;
import com.uqtony.fantasyphoto.env.Logger;
import com.uqtony.fantasyphoto.samsung.SamsungImageFilterManager;
import com.uqtony.fantasyphoto.tracking.MultiBoxTracker;
import com.uqtony.fantasyphoto.R; // Explicit import needed for internal Google builds.

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged multibox model.
  private static final int MB_INPUT_SIZE = 224;
  private static final int MB_IMAGE_MEAN = 128;
  private static final float MB_IMAGE_STD = 128;
  private static final String MB_INPUT_NAME = "ResizeBilinear";
  private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
  private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
  private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
  private static final String MB_LOCATION_FILE =
      "file:///android_asset/multibox_location_priors.txt";

  private static final int TF_OD_API_INPUT_SIZE = 300;
  //private static final String TF_OD_API_MODEL_FILE =
  //    "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  //private static final String TF_OD_API_MODEL_FILE = "file:///android_asset/frozen_inference_graph.pb";
  private static final String TF_OD_API_MODEL_FILE = "hand_detect_50000.tflite";
  //private static final String TF_OD_API_MODEL_FILE = "file:///android_asset/detect_hand.tflite";

  //private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labels.txt";

  // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
  // must be manually placed in the assets/ directory by the user.
  // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
  // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
  // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
  private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
  private static final int YOLO_INPUT_SIZE = 416;
  private static final String YOLO_INPUT_NAME = "input";
  private static final String YOLO_OUTPUT_NAMES = "output";
  private static final int YOLO_BLOCK_SIZE = 32;

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
  // or YOLO.
  private enum DetectorMode {
    TF_OD_API, MULTIBOX, YOLO;
  }
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
  private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
  private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

//  private static final Size DESIRED_PREVIEW_SIZE = new Size(1440, 1080);
  private static final Size DESIRED_PREVIEW_SIZE = new Size(800, 600);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;
    /*if (MODE == DetectorMode.YOLO) {
      detector =
          TensorFlowYoloDetector.create(
              getAssets(),
              YOLO_MODEL_FILE,
              YOLO_INPUT_SIZE,
              YOLO_INPUT_NAME,
              YOLO_OUTPUT_NAMES,
              YOLO_BLOCK_SIZE);
      cropSize = YOLO_INPUT_SIZE;
    } else if (MODE == DetectorMode.MULTIBOX) {
      detector =
          TensorFlowMultiBoxDetector.create(
              getAssets(),
              MB_MODEL_FILE,
              MB_LOCATION_FILE,
              MB_IMAGE_MEAN,
              MB_IMAGE_STD,
              MB_INPUT_NAME,
              MB_OUTPUT_LOCATIONS_NAME,
              MB_OUTPUT_SCORES_NAME);
      cropSize = MB_INPUT_SIZE;
    } else*/ {
      try {
        //detector = TensorFlowObjectDetectionAPIModel.create(getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        detector =
                TFLiteObjectDetectionAPIModel.create(
                        getAssets(),
                        TF_OD_API_MODEL_FILE,
                        TF_OD_API_LABELS_FILE,
                        TF_OD_API_INPUT_SIZE,
                        TF_OD_API_IS_QUANTIZED);
        cropSize = TF_OD_API_INPUT_SIZE;
      } catch (final IOException e) {
        LOGGER.e("Exception initializing classifier!", e);
        Toast toast =
            Toast.makeText(
                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
        toast.show();
        finish();
      }
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();
    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            if (isWelcomPage) {
              showWelcomePage();
            }
            tracker.draw(canvas);
            drawCountDown(canvas);
            //drawPhotoFrame(canvas);
            showAnimFrame(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            if (!isDebug()) {
              return;
            }
            final Bitmap copy = cropCopyBitmap;
            if (copy == null) {
              return;
            }

            final int backgroundColor = Color.argb(100, 0, 0, 0);
            canvas.drawColor(backgroundColor);

            final Matrix matrix = new Matrix();
            final float scaleFactor = 2;
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
            canvas.drawBitmap(copy, matrix, new Paint());

            final Vector<String> lines = new Vector<String>();
            if (detector != null) {
              final String statString = detector.getStatString();
              final String[] statLines = statString.split("\n");
              for (final String line : statLines) {
                lines.add(line);
              }
            }
            lines.add("");

            lines.add("Frame: " + previewWidth + "x" + previewHeight);
            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
            lines.add("Rotation: " + sensorOrientation);
            lines.add("Inference time: " + lastProcessingTimeMs + "ms");

            borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
          }
        });
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    startWatchdogService();
  }

  @Override
  public void onResume() {
      super.onResume();
      isWelcomPage = true;
  }

  @Override
  public void onDestroy(){
    super.onDestroy();
    stopWatchdogService();
  }

  OverlayView trackingOverlay;
  HandSwipeChecker handSwipeChecker;
  Bitmap resultBitmap;

  Bitmap combinePhotoWithFrame(Bitmap photo, Bitmap frame)
  {
    Bitmap res = frame.copy(Config.ARGB_8888,true);
    Canvas canvas = new Canvas(res);
    int offsetX = 258, offsetY = 151;
    int scale = 100;
    int scaleX = photo.getWidth() > photo.getHeight()?scale:0;
    int scaleY = scaleX == 0? scale: 0;
    canvas.drawBitmap(photo, new Rect(0,0,photo.getWidth(), photo.getHeight()),
            new Rect(offsetX - scaleX,offsetY - scaleY, offsetX+815 + scaleX, offsetY+1085+ scaleY)
    , new Paint());
    canvas.drawBitmap(frame, 0, 0, new Paint());
    return res;
  }

  @Override
  protected void processImage() {

    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
        previewWidth,
        previewHeight,
        getLuminanceStride(),
        sensorOrientation,
        originalLuminance,
        timestamp);
    trackingOverlay.postInvalidate();
    if (handSwipeChecker == null) {
      handSwipeChecker = new HandSwipeChecker(previewWidth, previewHeight);
    }
    Matrix _matrix = new Matrix();
    _matrix.postRotate(sensorOrientation, previewWidth/2.0f, previewHeight/2.0f);
    _matrix.mapRect(tracker.lastDominantRect);
    int isHandSwipe = 0;
    if (countDown < 0) {
      //if (isWelcomPage || !isRetakePage)
        //isHandSwipe = handSwipeChecker.isSwipe((int) tracker.lastDominantRect.centerX(), (int) tracker.lastDominantRect.centerY());
        isHandSwipe = handSwipeChecker.isSwipeInDistance((int) tracker.lastDominantRect.centerX(), (int) tracker.lastDominantRect.centerY());
      //else
      //  isHandSwipe = handSwipeChecker.isSwipeFromCenter((int) tracker.lastDominantRect.centerX(), (int) tracker.lastDominantRect.centerY());
      if (isWelcomPage) {
        isHandSwipe = isQRCodeSmileDetected?1:0;
        isQRCodeSmileDetected = false;
      }
      if (isRetakePage) {
        isHandSwipe = isQRCodeRetryDetected?1:0;
        isQRCodeRetryDetected = false;
      }
    }
    if (isHandSwipe != 0) {
      if (isWelcomPage) {
        hideWelcomePage();
      }
      else if (isRetakePage) {
        stopRetakeCountDown();
      }
      else {
        if (isHandSwipe == 1) {
          PhotoFrameManager.getInst(this).next();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateAnimFrame();
            }
          });

        } else if (isHandSwipe == -1) {
          PhotoFrameManager.getInst(this).previous();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateAnimFrame();
            }
          });
        }
      }
    }

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }
    if (shouldTakeShot){
      shouldTakeShot = false;

      Matrix matrix = new Matrix();
      matrix.postRotate(sensorOrientation);// TODO by Tony
      matrix.postScale(-1f, 1f);
      Bitmap bitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0, rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight(), matrix, true);
      Canvas _canvas = new Canvas(bitmap);
      Bitmap photoFrame = PhotoFrameManager.getInst(this).getCurrentPhotoFrame();
      //Bitmap _photoFrame = Bitmap.createBitmap(photoFrame, 0, 0, photoFrame.getWidth(), photoFrame.getHeight(), matrix, true);
      //bitmap = SamsungImageFilterManager.getInst(this).processBeautyFilter(bitmap);
      String origin = ImageUtils.saveBitmap(bitmap, "origin.jpg");
      resultBitmap = bitmap.copy(Config.ARGB_8888, true);
//      PhotoFrameManager.getInst(this).startMakeGif(resultBitmap, new GifMaker.OnGifListener() {
//        @Override
//        public void onMake(int current, int total) {
//
//        }
//
//        @Override
//        public void onCompleted(String path) {
//
//        }
//      });
      //_canvas.drawBitmap(photoFrame, new Rect(0, 0, photoFrame.getWidth(), photoFrame.getHeight()), new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), new Paint());
      resultBitmap = combinePhotoWithFrame(resultBitmap, photoFrame);
      String path = ImageUtils.saveBitmap(resultBitmap);

      LOGGER.i("Save photo to "+path);
      showRetakePage();
      //startPhotoViewer();
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
              case MULTIBOX:
                minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                break;
              case YOLO:
                minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                break;
            }

            final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

            for (final Classifier.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
              }
            }

            tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
            trackingOverlay.postInvalidate();

            requestRender();
            computingDetection = false;
          }
        });
  }

  private void startWatchdogService() {
    Intent intent = new Intent(DetectorActivity.this, WatchdogService.class);
    startService(intent);
  }

  private void stopWatchdogService() {
    Intent intent = new Intent(DetectorActivity.this, WatchdogService.class);
    stopService(intent);
  }

  volatile int countDown = -1;

  Typeface countDownTextFont = null;

  protected void drawCountDown(Canvas canvas){
    if (countDown <= 0)
      return;
    if (countDownTextFont == null)
      //countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/tt0140m.ttf");
      countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/gillsans.ttf");
    Paint paint;

    String countText = Integer.toString(countDown);
    paint = new Paint();
    paint.setTypeface(countDownTextFont);
    int textSize = 640;
    //paint.setTextSize(1000);
    paint.setTextSize(textSize);
    paint.setColor(Color.WHITE);
    paint.setStyle(Style.FILL_AND_STROKE);
    paint.setStrokeWidth(textSize / 8);
    paint.setAntiAlias(false);
    paint.setAlpha(255);
    Rect rect = new Rect();
    paint.getTextBounds(countText, 0, countText.length(), rect);

    canvas.drawText(countText, (canvas.getWidth()/2)-(rect.width()/2), (canvas.getHeight()/2)+(rect.height()/2), paint);
    //canvas.drawBitmap(bg, new Rect(0, 0, bg.getWidth(), bg.getHeight()), new Rect(0, 0, bg.getWidth(), bg.getHeight()), paint);
  }

  private void drawPhotoFrame(Canvas canvas) {
    Paint paint = new Paint();
    Bitmap photoFrame = PhotoFrameManager.getInst(this).getCurrentPhotoFrame();
    int width = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getHeight(): rgbFrameBitmap.getWidth();
    int height = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getWidth(): rgbFrameBitmap.getHeight();
    int canvasWidth = canvas.getWidth();
    int canvasHeight = canvas.getHeight();
    float widthRatio = canvasWidth*1.0f / width;
    float heightRatio = canvasHeight*1.0f /height;
    float ratio = (widthRatio < heightRatio)? widthRatio: heightRatio;
    width = (int)(width * ratio);
    height = (int)(height*ratio);
    canvas.drawBitmap(photoFrame, new Rect(0, 0, photoFrame.getWidth(), photoFrame.getHeight()),
            new Rect(0, 0, width, height), paint);
  }

  ImageView animFrameImageView = null;

  private void showAnimFrame(Canvas canvas) {
      if (animFrameImageView == null) {
        int width = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getHeight(): rgbFrameBitmap.getWidth();
        int height = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getWidth(): rgbFrameBitmap.getHeight();
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        float widthRatio = canvasWidth*1.0f / width;
        float heightRatio = canvasHeight*1.0f /height;
        float ratio = (widthRatio < heightRatio)? widthRatio: heightRatio;
        width = (int)(width * ratio);
        height = (int)(height*ratio);
        animFrameImageView = findViewById(R.id.animFrameImageView);
        Glide.with(this).load(PhotoFrameManager.getInst(this).getCurrentPhotoAnimFrameId()).into(animFrameImageView);
//        Glide.with(this).load(PhotoFrameManager.getInst(this).getCurrentPhotoAnimFrameId()).apply(
//                new RequestOptions().diskCacheStrategy(DiskCacheStrategy.DATA)).into(animFrameImageView);

      }
    animFrameImageView.setVisibility(View.VISIBLE);
  }

  private void updateAnimFrame() {
    if (animFrameImageView == null) {
        animFrameImageView = findViewById(R.id.animFrameImageView);
        animFrameImageView.setVisibility(View.VISIBLE);
    }
    //Glide.with(this).load(PhotoFrameManager.getInst(this).getCurrentPhotoAnimFrameId()).into(animFrameImageView);
    Glide.with(this).load(PhotoFrameManager.getInst(this).getCurrentPhotoAnimFrameId()).apply(
            new RequestOptions().diskCacheStrategy(DiskCacheStrategy.DATA)).into(animFrameImageView);

    Display display = getWindowManager().getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    int width = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getHeight(): rgbFrameBitmap.getWidth();
    int height = (sensorOrientation % 180 !=  0)?  rgbFrameBitmap.getWidth(): rgbFrameBitmap.getHeight();
    int canvasWidth = size.x;
    int canvasHeight = size.y;
    float widthRatio = canvasWidth*1.0f / width;
    float heightRatio = canvasHeight*1.0f /height;
    float ratio = (widthRatio < heightRatio)? widthRatio: heightRatio;
    width = (int)(width * ratio);
    height = (int)(height*ratio);
    animFrameImageView.invalidate();
  }

  protected void startCountDown() {
    if (countDown >= 0){
      return;
    }
    countDown = 3;
    Thread t = new Thread(){
      @Override
      public void run(){
        try {
          while (countDown >= 0) {
            if (countDown == 1) {
              // Take shot earlier
              sleep(800);
              takeShot();
              sleep(200);
            }
            else
              sleep(1000);
            countDown--;
          }
          //takeShot();
        }catch (InterruptedException e){
          e.printStackTrace();
        }

      }// end of run
    };
    t.start();
  }

  volatile boolean shouldTakeShot = false;

  protected void takeShot() {
    shouldTakeShot = true;
    stopBackToWelcomePageTimer();
  }

  protected void startPhotoViewer() {
    Intent intent = new Intent(DetectorActivity.this, PhotoViewerActivity.class);
    startActivity(intent);
    finish();
  }

  volatile boolean isWelcomPage = true;
  private ImageView welcomeImageView = null;

  protected void showWelcomePage(){
    if (welcomeImageView == null) {
      welcomeImageView = findViewById(R.id.welcomeImageView);
      //Glide.with(this).load(R.drawable.welcome).into(welcomeImageView);
      Glide.with(this).load(R.drawable.welcome_qrcode).into(welcomeImageView);
    }
    if (welcomeImageView != null) {
      welcomeImageView.setVisibility(View.VISIBLE);
    }
  }

  protected void hideWelcomePage() {
    isWelcomPage = false;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (welcomeImageView != null) {
          welcomeImageView.setVisibility(View.INVISIBLE);
        }
      }
    });

    startBackToWelcomePageTimer();
  }

  Timer backToWelcomePageTimer = null;

  protected void startBackToWelcomePageTimer() {
    if (backToWelcomePageTimer == null) {
      backToWelcomePageTimer = new Timer();
    }
    backToWelcomePageTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        isWelcomPage = true;
      }
    },
    600000);
  }
  protected void stopBackToWelcomePageTimer() {
    if (backToWelcomePageTimer == null)
      return;
    backToWelcomePageTimer.cancel();
  }

  final static int RETAKE_COUNT_DOWN_INTERVAL = 10;// 10sec
  volatile boolean isRetakePage = false;
  Thread retakeCountDownThread = null;
  Bitmap retakeBackgroundBitmap = null;
  ImageView retakeImageView = null;
  ImageView retakePhotoFrameImageView = null;
  ImageView retakeBGImageView = null;
  TextView retakeCountDownTextView = null;

  void prepareRetakeUI() {
    if (retakeImageView == null){
      retakeImageView = findViewById(R.id.retakeImageView);
    }
    if (retakePhotoFrameImageView == null)
    {
      retakePhotoFrameImageView = findViewById(R.id.retakePhotoFrameImageView);
    }
    if (retakeBGImageView == null) {
      retakeBGImageView = findViewById(R.id.retakeBGImageView);
    }
    if (retakeBackgroundBitmap == null) {
      retakeBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.retake_background);
    }

    if (retakeCountDownTextView == null){
      retakeCountDownTextView = findViewById(R.id.retakeCountDownTextView);
      if (countDownTextFont == null){
        //countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/tt0140m.ttf");
        countDownTextFont = Typeface.createFromAsset(getAssets(),"fonts/gillsans.ttf");
      }
    }
  }

  protected void showRetakePage() {
    isRetakePage = true;
    prepareRetakeUI();
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        retakeCountDownTextView.setTypeface(countDownTextFont);
        //Bitmap result = PhotoFrameManager.drawArtResult(resultBitmap, retakeBackgroundBitmap);
        //Bitmap result = PhotoFrameManager.drawColorFrame(resultBitmap, Color.WHITE);
        Bitmap result =  resultBitmap;
        retakeImageView.setVisibility(View.VISIBLE);
        //retakePhotoFrameImageView.setVisibility(View.VISIBLE);
        retakeBGImageView.setVisibility(View.VISIBLE);
        retakeImageView.setImageBitmap(result);
        //Glide.with(DetectorActivity.this).load(PhotoFrameManager.getInst(DetectorActivity.this).getCurrentPhotoAnimFrameId()).into(retakePhotoFrameImageView);
        //Glide.with(DetectorActivity.this).asGif().load(R.drawable.retake_background_anim).into(retakeBGImageView);
        Glide.with(DetectorActivity.this).load(R.drawable.retake_background_qrcode).into(retakeBGImageView);
      }
    });
    startRetakeCountDown();
  }

  protected void updateRetakeCountDownUI(final int count) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (retakeCountDownTextView.getVisibility() == View.INVISIBLE)
          retakeCountDownTextView.setVisibility(View.VISIBLE);
        retakeCountDownTextView.setText(Integer.toString(count));
      }
    });
  }

  volatile boolean isDelayRetakPage = false;

  protected void startRetakeCountDown() {
    if (retakeCountDownThread == null || !retakeCountDownThread.isAlive()){
      retakeCountDownThread = new Thread(){
        @Override
        public void run() {
          int count = 0;
          try {
            isDelayRetakPage = true;
            Thread.sleep(200);
            isDelayRetakPage = false;
            while(count++ < RETAKE_COUNT_DOWN_INTERVAL || PhotoFrameManager.getInst(DetectorActivity.this).isMakingGif()) {
              Thread.sleep(1000);
              int res = RETAKE_COUNT_DOWN_INTERVAL - count;
              res = res < 0? 0 : res;
              updateRetakeCountDownUI(res);
            }// end of while
            startPhotoViewer();
          }catch (InterruptedException e){
            e.printStackTrace();
          }finally {
            //PhotoFrameManager.getInst(DetectorActivity.this).stopMakeGif();
          }
        }
      };
      retakeCountDownThread.start();
    }
  }

  protected void stopRetakeCountDown() {
    if (isDelayRetakPage)
      return;
    isRetakePage = false;
    if (retakeCountDownThread != null && retakeCountDownThread.isAlive()){
      retakeCountDownThread.interrupt();
    }
    retakeCountDownThread = null;
    prepareRetakeUI();
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        retakeImageView.setVisibility(View.INVISIBLE);
        retakePhotoFrameImageView.setVisibility(View.INVISIBLE);
        retakeBGImageView.setVisibility(View.INVISIBLE);
        retakeCountDownTextView.setVisibility(View.INVISIBLE);
      }
    });
  }

  volatile boolean isQRCodeSmileDetected = false, isQRCodeRetryDetected = false;
  volatile boolean isWelcomePageCoolDown = false;
  long enterWelcomePageTime = 0;
  static final long WELCOMEPAGE_COOL_DOWN_TIME = 3000;

  @Override
  public void onQRCodeRead(String text, ResultPoint[] points)
  {
      LOGGER.i("onQRCodeRead, result=%s", text);
      if (text.equals("smile")) {
        if(!isWelcomPage && !isRetakePage){
          long currentTime = System.currentTimeMillis();
          if ((currentTime - enterWelcomePageTime) > WELCOMEPAGE_COOL_DOWN_TIME) {
            isWelcomePageCoolDown = false;
            enterWelcomePageTime = 0;
          }
          if (!isWelcomePageCoolDown) {
            startCountDown();
          }
        }// end of if !isWelcomPage && !isRetakePage
        else if (isWelcomPage) {
          isQRCodeSmileDetected = true;
          isWelcomePageCoolDown = true;
          enterWelcomePageTime = System.currentTimeMillis();
        }
      }// end of equals smile
    if (text.equals("retry")) {
        if (isRetakePage) {
          isQRCodeRetryDetected = true;
        }
    }
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
}
