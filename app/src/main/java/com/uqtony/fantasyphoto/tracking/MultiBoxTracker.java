/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.uqtony.fantasyphoto.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.nfc.Tag;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import com.uqtony.fantasyphoto.Classifier.Recognition;
import com.uqtony.fantasyphoto.R;
import com.uqtony.fantasyphoto.env.BorderedText;
import com.uqtony.fantasyphoto.env.ImageUtils;
import com.uqtony.fantasyphoto.env.Logger;

/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {
  private final Logger logger = new Logger();

  private static final float TEXT_SIZE_DIP = 18;

  // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
  // the lower scored box (new or old) will be removed.
  private static final float MAX_OVERLAP = 0.2f;

  private static final float MIN_SIZE = 16.0f;

  // Allow replacement of the tracked box with new results if
  // correlation has dropped below this level.
  private static final float MARGINAL_CORRELATION = 0.75f;

  // Consider object to be lost if correlation falls below this threshold.
  private static final float MIN_CORRELATION = 0.3f;

  private static final int[] COLORS = {
    Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
    Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
  };

  private final Queue<Integer> availableColors = new LinkedList<Integer>();

  public ObjectTracker objectTracker;
  public HandTracker1 handTracker1 = new HandTracker1(3, 100);
  //public HandTracker2 handTracker2 = new HandTracker2(3, 10);
  private PointF dominantPt = new PointF();

  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

  public static class TrackedRecognition {
    ObjectTracker.TrackedObject trackedObject;
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }

  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();

  private final Paint boxPaint = new Paint();
  private final Paint circlePaint = new Paint();

  private final float textSizePx;
  private final BorderedText borderedText;

  private Matrix frameToCanvasMatrix;

  private int frameWidth;
  private int frameHeight;

  private int sensorOrientation;
  private Context context;

  public MultiBoxTracker(final Context context) {
    this.context = context;
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(12.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    circlePaint.setAntiAlias(true);
    circlePaint.setStyle(Style.FILL);
    circlePaint.setColor(Color.BLUE);
    circlePaint.setAlpha(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }

    if (objectTracker == null) {
      return;
    }

    // Draw correlations.
    for (final TrackedRecognition recognition : trackedObjects) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;

      final RectF trackedPos = trackedObject.getTrackedPositionInPreviewFrame();

      if (getFrameToCanvasMatrix().mapRect(trackedPos)) {
        final String labelString = String.format("%.2f", trackedObject.getCurrentCorrelation());
        borderedText.drawText(canvas, trackedPos.right, trackedPos.bottom, labelString);
      }
    }

    final Matrix matrix = getFrameToCanvasMatrix();
    objectTracker.drawDebug(canvas, matrix);
  }

  public synchronized void trackResults(
      final List<Recognition> results, final byte[] frame, final long timestamp) {
    //logger.i("[Tony]Processing %d results from %d", results.size(), timestamp);
    processResults(timestamp, results, frame);
  }

  private void sendDataToVirtualMouse(byte[] data) {
    try {
      InetAddress vmServerAddr = InetAddress.getByName("127.0.0.1");
      int vmServerPort = 9999;

      DatagramSocket socket = null;
      socket = new DatagramSocket();
      DatagramPacket dp;
      dp = new DatagramPacket(data, data.length, vmServerAddr, vmServerPort);
      //logger.i("[Tony] send msg = %s", data);
      socket.setBroadcast(true);
      socket.send(dp);
    }catch (UnknownHostException e){
      e.printStackTrace();
    }catch (SocketException e){
      e.printStackTrace();
    }catch (IOException e){
      e.printStackTrace();
    }
  }

  private void disableVirtualMouse() {
    logger.i("Disable mouse");
    int mouseEventId =  6;
    String data = Integer.toString(mouseEventId);
    sendDataToVirtualMouse(data.getBytes());
  }

  private  void sendToVirtualMouse(int frameWidth, int frameHeight, int x, int y) {
    int mouseEventId = 5;

    String data = String.format("%d, %d, %d, %d, %d", mouseEventId, frameWidth, frameHeight, x, y);
    logger.i("Send position to virtual mouse, %s", data);
    sendDataToVirtualMouse(data.getBytes());
  }

  public RectF lastDominantRect = new RectF(0, 0, 0, 0);

  public synchronized void update() {
      RectF dominantPosition = new RectF(dominantPt.x, dominantPt.y, dominantPt.x + 5, dominantPt.y + 5);
      String data = String.format("%d, %d, %d, %d", frameWidth, frameHeight, (int)dominantPt.x, (int)dominantPt.y);
      //logger.i("[Tony]Send position to virtual mouse, %s", data);
      int _frameWidth = (int)(frameWidth * 0.8);
      int _frameHeight = (int)(frameHeight * 0.8);
      int x = (int)(frameWidth * 0.9 - dominantPosition.centerX());
      int y = (int)(dominantPosition.centerY() - frameHeight* 0.1);

	  //MouseAccessibility.getInst(context).moveTo(frameWidth, frameHeight,  frameWidth - (int) dominantPosition.centerX(), (int) dominantPosition.centerY());
	  
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
        Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                 canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);

    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos =
          (objectTracker != null)
              ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
              : new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      //logger.i("[Tony]Hand position: frame width = %d, height=%d, x=%f, y=%f", frameWidth, frameHeight, recognition.location.centerX(), recognition.location.centerY());
      //sendToVirtualMouse(frameWidth, frameHeight, (int)recognition.location.centerX(), (int)recognition.location.centerY());
      final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      //canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
      float x = trackedPos.centerX();
      float y = trackedPos.centerY();
      float radius = (trackedPos.width() + trackedPos.height()) / 7.0f;
      //canvas.drawCircle(x, y, radius, circlePaint);

      final String labelString =
          !TextUtils.isEmpty(recognition.title)
              ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
              : String.format("%.2f", recognition.detectionConfidence);
      //borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
    }

  }

  private boolean initialized = false;
  private static final float HAND_SIZE_CHANGE_THRESHOLD = 0.7f;

  public synchronized void onFrame(
      final int w,
      final int h,
      final int rowStride,
      final int sensorOrientation,
      final byte[] frame,
      final long timestamp) {
    if (objectTracker == null && !initialized) {
      ObjectTracker.clearInstance();

      logger.i("Initializing ObjectTracker: %dx%d", w, h);
      // Tony
      //objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
      objectTracker = null;
      frameWidth = w;
      frameHeight = h;
      this.sensorOrientation = sensorOrientation;
      initialized = true;

      if (objectTracker == null) {
        String message =
            "Object tracking support not found. "
                + "See tensorflow/examples/android/README.md for details.";
        //Toast.makeText(context, message, Toast.LENGTH_LONG).show(); // Tony
        logger.e(message);
      }
    }

    if (objectTracker == null) {
      dominantPt = handTracker1.getDominantPoint(timestamp);
      RectF dominantRect = handTracker1.getDominantRect(timestamp);
      //logger.i("[Tony]DominatRect = (%f, %f, %f, %f)", dominantRect.centerY(), dominantRect.centerY(), dominantRect.width(), dominantRect.height());
      float widthRatio = dominantRect.width() / lastDominantRect.width();
      float heightRatio = dominantRect.height() / lastDominantRect.height();
      if ( widthRatio < 0.8f || heightRatio < 0.8f){
        //MouseAccessibility.getInst(context).updateHandSizeChange();
      }

      lastDominantRect = dominantRect;
      return;
    }

    objectTracker.nextFrame(frame, null, timestamp, null, true);

    // Clean up any objects not worth tracking any more.
    final LinkedList<TrackedRecognition> copyList =
        new LinkedList<TrackedRecognition>(trackedObjects);
    for (final TrackedRecognition recognition : copyList) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
      final float correlation = trackedObject.getCurrentCorrelation();
      if (correlation < MIN_CORRELATION) {
        logger.v("Removing tracked object %s because NCC is %.2f", trackedObject, correlation);
        trackedObject.stopTracking();
        trackedObjects.remove(recognition);

        availableColors.add(recognition.color);
      }
    }
  }

  private void processResults(
      final long timestamp, final List<Recognition> results, final byte[] originalFrame) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    // Tony
    if (objectTracker == null) {
      trackedObjects.clear();
      List<RectF> newRects = new LinkedList<RectF>();
          for (final Pair<Float, Recognition> potential : rectsToTrack) {
              newRects.add(new RectF(potential.second.getLocation()));
          }
          handTracker1.track(newRects, timestamp);
          int count = 0;
          for (HandTracker1.Tracklet tracklet: handTracker1.tracklets)
          {
              final TrackedRecognition trackedRecognition = new TrackedRecognition();
              trackedRecognition.detectionConfidence = (float)count;
              trackedRecognition.location = new RectF(tracklet.rect);
              trackedRecognition.trackedObject = null;
              trackedRecognition.title = "Hand";
              trackedRecognition.color = tracklet.color;
              trackedObjects.add(trackedRecognition);
              count++;

          }
          return;
      }

    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    logger.i("%d rects to track", rectsToTrack.size());
    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      handleDetection(originalFrame, timestamp, potential);
    }
  }

  private void handleDetection(
      final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
    final ObjectTracker.TrackedObject potentialObject =
        objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);

    final float potentialCorrelation = potentialObject.getCurrentCorrelation();
    logger.v(
        "Tracked object went from %s to %s with correlation %.2f",
        potential.second, potentialObject.getTrackedPositionInPreviewFrame(), potentialCorrelation);

    if (potentialCorrelation < MARGINAL_CORRELATION) {
      logger.v("Correlation too low to begin tracking %s.", potentialObject);
      potentialObject.stopTracking();
      return;
    }

    final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

    float maxIntersect = 0.0f;

    // This is the current tracked object whose color we will take. If left null we'll take the
    // first one from the color queue.
    TrackedRecognition recogToReplace = null;

    // Look for intersections that will be overridden by this object or an intersection that would
    // prevent this one from being placed.
    for (final TrackedRecognition trackedRecognition : trackedObjects) {
      final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
      final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
      final RectF intersection = new RectF();
      final boolean intersects = intersection.setIntersect(a, b);

      final float intersectArea = intersection.width() * intersection.height();
      final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
      final float intersectOverUnion = intersectArea / totalArea;

      // If there is an intersection with this currently tracked box above the maximum overlap
      // percentage allowed, either the new recognition needs to be dismissed or the old
      // recognition needs to be removed and possibly replaced with the new one.
      if (intersects && intersectOverUnion > MAX_OVERLAP) {
        if (potential.first < trackedRecognition.detectionConfidence
            && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
          // If track for the existing object is still going strong and the detection score was
          // good, reject this new object.
          potentialObject.stopTracking();
          return;
        } else {
          removeList.add(trackedRecognition);

          // Let the previously tracked object with max intersection amount donate its color to
          // the new object.
          if (intersectOverUnion > maxIntersect) {
            maxIntersect = intersectOverUnion;
            recogToReplace = trackedRecognition;
          }
        }
      }
    }

    // If we're already tracking the max object and no intersections were found to bump off,
    // pick the worst current tracked object to remove, if it's also worse than this candidate
    // object.
    if (availableColors.isEmpty() && removeList.isEmpty()) {
      for (final TrackedRecognition candidate : trackedObjects) {
        if (candidate.detectionConfidence < potential.first) {
          if (recogToReplace == null
              || candidate.detectionConfidence < recogToReplace.detectionConfidence) {
            // Save it so that we use this color for the new object.
            recogToReplace = candidate;
          }
        }
      }
      if (recogToReplace != null) {
        logger.v("Found non-intersecting object to remove.");
        removeList.add(recogToReplace);
      } else {
        logger.v("No non-intersecting object found to remove");
      }
    }

    // Remove everything that got intersected.
    for (final TrackedRecognition trackedRecognition : removeList) {
      logger.v(
          "Removing tracked object %s with detection confidence %.2f, correlation %.2f",
          trackedRecognition.trackedObject,
          trackedRecognition.detectionConfidence,
          trackedRecognition.trackedObject.getCurrentCorrelation());
      trackedRecognition.trackedObject.stopTracking();
      trackedObjects.remove(trackedRecognition);
      if (trackedRecognition != recogToReplace) {
        availableColors.add(trackedRecognition.color);
      }
    }

    if (recogToReplace == null && availableColors.isEmpty()) {
      logger.e("No room to track this object, aborting.");
      potentialObject.stopTracking();
      return;
    }

    // Finally safe to say we can track this object.
    logger.v(
        "Tracking object %s (%s) with detection confidence %.2f at position %s",
        potentialObject,
        potential.second.getTitle(),
        potential.first,
        potential.second.getLocation());
    final TrackedRecognition trackedRecognition = new TrackedRecognition();
    trackedRecognition.detectionConfidence = potential.first;
    trackedRecognition.trackedObject = potentialObject;
    trackedRecognition.title = potential.second.getTitle();

    // Use the color from a replaced object before taking one from the color queue.
    trackedRecognition.color =
        recogToReplace != null ? recogToReplace.color : availableColors.poll();
    trackedObjects.add(trackedRecognition);
  }
}