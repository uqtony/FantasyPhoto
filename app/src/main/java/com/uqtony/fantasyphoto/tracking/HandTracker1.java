package com.uqtony.fantasyphoto.tracking;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class HandTracker1 {

    private static final String TAG = "HandTracker1";

    public HandTracker1(int lostEndurance, int staticLimit) {
        this.lostEndurance = lostEndurance;
        this.staticLimit = staticLimit;
    }
    public List<Tracklet> tracklets = new LinkedList<Tracklet>();

    private int lostEndurance;
    private int staticLimit;
    private double distanceThrld = 2000;
    private double staticThrld = 0.02;
    private int globalTrackletCount = 0;

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };

    public class Tracklet
    {
        public int color;
        public RectF rect; // current position
        public int gonetime; // count how long the tracklet has been lost (frame)
        public int staticTime; // count how long the tracklet rect hasn't move

        //private CurveTracker curveTracker = new CurveTracker(1);
        //private CurveTracker1 curveTracker1 = new CurveTracker1(10);
        private CurveTracker2 curveTracker2 = new CurveTracker2(1);


        public Tracklet(RectF newRect, Long timestamp)
        {
            this.color = COLORS[globalTrackletCount%COLORS.length];
            this.rect = new RectF(newRect);
            this.gonetime = 0;
            this.staticTime = 0;
            globalTrackletCount++;

            //curveTracker.updateCurve(newRect, timestamp);
            //curveTracker1.updateCurve(newRect, timestamp);
            curveTracker2.updateCurve(newRect, timestamp);
        }

        public void updateTracklet(RectF newRect, Long timestamp)
        {
            if(Distance2(this.rect, newRect) < staticThrld) this.staticTime++;
            else this.staticTime = 0;
            this.gonetime = 0;
            this.rect = new RectF(newRect);

            //curveTracker.updateCurve(newRect, timestamp);
            //curveTracker1.updateCurve(newRect, timestamp);
            curveTracker2.updateCurve(newRect, timestamp);
        }
    }

    // distance table element
    private class TableElement
    {
        public int newRectID; // NO. of new rect
        public int trackletID; // NO. of tracklet
        public double dist;
        public TableElement(Pair<RectF, Integer> currentRects, Pair<Tracklet, Integer> currentTracklets)
        {
            this.newRectID = currentRects.second;
            this.trackletID = currentTracklets.second;
            this.dist = Distance2(currentRects.first, currentTracklets.first.rect);
        }
    }

    public void track(final List<RectF> inputRects, Long timestamp)
    {
        //Log.d(TAG, "strart track. input " + Integer.toString(inputRects.size()) + " rects.");

        // set flags to record if the input rects and old tracklets are available or occupied (matched with someone)
        // (flag < 0) means the new rect or tracklet is available
        // (flag >= 0) the value of flag stands for the matching tracklet or new rect
        int[] newRectFlags = new int[inputRects.size()];
        int[] trackletFlags = new int[tracklets.size()];
        for (int i = 0; i < newRectFlags.length; i++) { newRectFlags[i] = -1;}
        for (int i = 0; i < trackletFlags.length; i++) { trackletFlags[i] = -1;}

        // pair one input to its label number (NO.X inputRect), which is correspondent to array element NO. in newRectFlags
        List<Pair<RectF, Integer>> currentRects = new LinkedList<Pair<RectF, Integer>>();
        int newRectID = 0;
        for (final RectF inputRect : inputRects)
        {
            Pair<RectF, Integer> currentRect = new Pair<>(inputRect, newRectID);
            currentRects.add(currentRect);
            newRectID++;
        }

        // pair one current tracklet to its label number (NO.X tracklet), which is correspondent to array element NO. in trackletFlags
        List<Pair<Tracklet, Integer>> currentTracklets = new LinkedList<Pair<Tracklet, Integer>>();
        int trackletID = 0;
        for (final Tracklet tracklet : tracklets)
        {
            Pair<Tracklet, Integer> currentTracklet = new Pair<>(tracklet, trackletID);
            currentTracklets.add(currentTracklet);
            trackletID++;
        }

        // create distance table: every tracklet to every new rect.
        List<TableElement> distTable = new LinkedList<TableElement>();
        for (final Pair<Tracklet, Integer> currentTracklet : currentTracklets)
        {
            for (final Pair<RectF, Integer> currentRect : currentRects)
            {
                TableElement element = new TableElement(currentRect, currentTracklet);
                distTable.add(element);
            }
        }
        // sort elements in distance table by dist (from small to large)
        Collections.sort(distTable, new Comparator<TableElement>() {
            public int compare(TableElement e1, TableElement e2) {
                if (e1.dist < e2.dist) return -1;
                else return 1;
            }});

        // start matching one new rect to one tracklet (smaller distance has higher priority)
        this.match(distTable, newRectFlags, trackletFlags);

        // tracklets info should be updated no matter it's successfully tracked or not
        int trackletCount = 0;
        for (final Tracklet tracklet : tracklets)
        {
            if (trackletFlags[trackletCount] >= 0)
            {
                tracklet.updateTracklet(inputRects.get(trackletFlags[trackletCount]), timestamp);
            }
            else tracklet.gonetime++;
            trackletCount++;
        }

        // create a new tracklet to every new rect which is left unmatched to any current tracklet
        int inputCount = 0;
        for (final RectF inputRect: inputRects)
        {
            if (newRectFlags[inputCount] < 0)
            {
                Tracklet tracklet = new Tracklet(inputRect, timestamp);
                this.tracklets.add(tracklet);
            }
            inputCount++;
        }

        // check all tracklets' status and remove gone tracklets
        clearTracklets();
    }

    public PointF getDominantPoint(Long time)
    {
        for (final Tracklet tracklet: tracklets)
        {
            if(tracklet.staticTime < staticLimit) {
                //return tracklet.curveTracker.getPrediction(time);
                //return tracklet.curveTracker1.getPrediction(time);
                return tracklet.curveTracker2.getPrediction(time);
            }
        }
        return new PointF(-1, -1);
    }

    public RectF getDominantRect(Long time)
    {
        for (final Tracklet tracklet: tracklets)
        {
            if(tracklet.staticTime < staticLimit) {
                //return tracklet.curveTracker.getPrediction(time);
                //return tracklet.curveTracker1.getPrediction(time);
                RectF rectF = tracklet.rect;
                PointF newPointF = tracklet.curveTracker2.getPrediction(time);
                rectF.offset(newPointF.x-rectF.centerX(), newPointF.y - rectF.centerY());
                return rectF;
            }
        }
        return new RectF(-1, -1, -1, -1);
    }

    private double Distance2(RectF a, RectF b)
    {
        PointF p1 = new PointF(a.left - b.left, a.top - b.top);
        PointF p2 = new PointF(a.left - b.left, a.bottom - b.bottom);
        PointF p3 = new PointF(a.right - b.right, a.top - b.top);
        PointF p4 = new PointF(a.right - b.right, a.bottom - b.bottom);
        float area = Math.min(a.width() * a.height(), b.width() * b.height());
        return (p1.length() * p1.length() + p2.length() * p2.length() + p3.length() * p3.length() + p4.length() * p4.length()) / area;
    }

    private void match(List<TableElement> distTable, int[] newRectFlags, int[] trackletFlags)
    {
        for (final TableElement element: distTable)
        {
            if (newRectFlags[element.newRectID] < 0 && trackletFlags[element.trackletID] < 0 && element.dist < distanceThrld)
            {
                newRectFlags[element.newRectID] = element.trackletID;
                trackletFlags[element.trackletID] = element.newRectID;
            }
        }
    }

    private void clearTracklets()
    {
        for (int i = this.tracklets.size() - 1; i >= 0; i--)
        {
            Tracklet item = this.tracklets.get(i);
            if (item.gonetime >= lostEndurance) tracklets.remove(item);
        }
    }

}
