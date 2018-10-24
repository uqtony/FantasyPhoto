package com.uqtony.fantasyphoto.tracking;

import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import java.util.LinkedList;
import java.util.List;

import Jama.Matrix;


public class CurveTracker2 {

    private static final String TAG = "CurveTracker2";

    final private int mode = 2;
    final private int order = 3;
    private int savePastPtNum;

    private boolean curveAvailable;
    private long lastAccessTime;
    private List<Pair<Long, Float>> xCurvePoints = new LinkedList<Pair<Long, Float>>();
    private List<Pair<Long, Float>> yCurvePoints = new LinkedList<Pair<Long, Float>>();

    private TimeCounter timecounter = new TimeCounter();
    private CurveInfo xCurve;
    private CurveInfo yCurve;

    public CurveTracker2(int groupNum) {
        this.lastAccessTime = 0;
        this.curveAvailable = false;
        //this.savePastPtNum = order + 1;
        this.xCurve = new CurveInfo(this.order);
        this.yCurve = new CurveInfo(this.order);
    }

    public PointF getPrediction(Long time) {
        this.lastAccessTime = time;
        float x = -1;
        float y = -1;
        if (this.curveAvailable) {
            x = (float)xCurve.getCurveValue(time);
            y = (float)yCurve.getCurveValue(time);
        }

        //Log.d(TAG, "[Tony]current time" + time + ", result: " + x + "," + y);
        return new PointF(x, y);
    }

    private PointF getPointFromRect(RectF rect) {
        PointF point = new PointF((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2);
        return point;
    }

    public void updateCurve(RectF input, Long time) {

        PointF point2D = getPointFromRect(input);

        //Log.i(TAG, "xy " + point2D);


        Pair<Long, Float> xNewPoint = new Pair<Long, Float>(time, point2D.x);
        Pair<Long, Float> yNewPoint = new Pair<Long, Float>(time, point2D.y);
        xCurvePoints.add(xNewPoint);
        yCurvePoints.add(yNewPoint);
        while (xCurvePoints.size() > order + 1 || yCurvePoints.size() > order + 1) {
            xCurvePoints.remove(xCurvePoints.get(0));
            yCurvePoints.remove(yCurvePoints.get(0));
        }
        if (xCurvePoints.size() != order + 1 || yCurvePoints.size() != order + 1) {
            this.curveAvailable = false;
            return;
        }
        //Log.d(TAG, "x calculation:");
        xCurve.updateCurve(this.xCurvePoints);
        //Log.d(TAG, "y calculation:");
        yCurve.updateCurve(this.yCurvePoints);

        timecounter.count(time);
        this.curveAvailable = true;
    }

    private class TimeCounter {

        public double avgTimeInterval;

        public long lastTimeStamp;
        private List<Double> timeIntervals = new LinkedList<Double>();

        public TimeCounter() {
            lastTimeStamp = 0;
            avgTimeInterval = 1;
        }

        public void count(Long time) {


            double tempInterval = time - lastTimeStamp;
            timeIntervals.add(tempInterval);
            if (timeIntervals.size() > order + 1) timeIntervals.remove(0);
            double total = 0;
            for (int i = 0; i < timeIntervals.size(); i++) {
                total += timeIntervals.get(i);
            }
            avgTimeInterval = total / (double)timeIntervals.size();

            this.lastTimeStamp = time;
            //Log.d(TAG, "lastTimeStamp=" + lastTimeStamp);
        }
    }

    public class CurveInfo {

        final private int order;

        private Long timeBias;
        private double position0;
        private double position1;
        private double lastAccessedPosition;
        private double lastMovingAvg;
        private List<Double> accessPosList = new LinkedList<Double>();

        // new input value
        private Matrix Y;
        private Matrix theta;
        // recrusive update
        private Matrix theta_0; // initial
        private Matrix P_0; // initial
        // calculated dependent varables
        private Matrix model;
        private Matrix P;
        private Matrix K;
        // constant
        private Matrix I_forP;
        private double lambda = 0.9;

        public CurveInfo(int order) {
            this.order = order;
            this.position0 = 0;
            this.position1 = 0;
            this.lastMovingAvg = 0;

            Y = new Matrix(1, 1);
            theta = new Matrix(order, 1);
            theta_0 = new Matrix(order, 1);
            P_0 = new Matrix(order, order);
            model = new Matrix(order, 1);
            P = new Matrix(order, order);
            K = new Matrix(order, 1);

            this.I_forP = Matrix.identity(order, order);
            for (int rows = 0; rows < order; rows++) {
                this.theta_0.set(rows, 0, 0.3);
            }
            this.P_0 = Matrix.identity(order, order);
            P_0 = P_0.times(10000.0d);
            //Log.d(TAG, "initial P_0:" + matrixToString(P_0));
        }

        public void updateCurve(List<Pair<Long, Float>> points) {

            int t = points.size() - 1;
            this.timeBias = points.get(t).first;

            //Log.d(TAG, "timeBias:" + timeBias);

            // new inputs (Y, model)
            Y.set(0,0, points.get(t).second);
            for (int rows = 0; rows < order; rows++) {
                model.set(rows, 0, points.get(t - rows - 1).second);
            }
            //Log.d(TAG, "model:" + matrixToString(model));

            // calculate variables
            Matrix tempMatrix;
            double tempValue;
            // K
            Matrix modelT = model.transpose();
            tempMatrix = modelT.times(P_0);
            tempMatrix = tempMatrix.times(model);
            tempValue = lambda + tempMatrix.get(0, 0);
            tempValue = 1.0d / tempValue;
            tempMatrix = P_0.times(model);
            K = tempMatrix.times(tempValue);
            //Log.d(TAG, "K:" + matrixToString(K));

            // P
            tempMatrix = K.times(modelT);
            tempMatrix = I_forP.minus(tempMatrix);
            tempMatrix = tempMatrix.times(P_0);
            tempValue = 1.0d / lambda;
            P = tempMatrix.times(tempValue);
            //Log.d(TAG, "P matrix:" + matrixToString(P) + ", eig = " + matrixToString(P.eig().getD()));
            // theta
            tempMatrix = modelT.times(theta_0);
            tempMatrix = Y.minus(tempMatrix);
            tempMatrix = K.times(tempMatrix);
            theta = theta_0.plus(tempMatrix);
            //Log.d(TAG, "theta:" + matrixToString(theta));
            // theta_0
            theta_0 = theta.copy();
            // P_0
            P_0 = P.copy();

            // prediction
            Matrix newModel = new Matrix(order, 1);
            for (int rows = 0; rows < order; rows++) {
                newModel.set(rows, 0, points.get(t - rows).second);
            }
            Matrix nextY = theta.transpose().times(newModel);
            if (nextY.getColumnDimension() != 1 || nextY.getRowDimension() != 1)
                Log.e(TAG, "error: resultMatrix size != 1x1");

            this.position0 = this.lastAccessedPosition;
            double xxx = lastAccessedPosition;
            this.position0 = xxx;
            this.position1 = nextY.get(0,0);

        }

        public double getCurveValue(Long time) {

            /*double result = position0 + (position1 - position0) * (double)(time - timecounter.lastTimeStamp) / timecounter.avgTimeInterval;
            this.lastAccessedPosition = result;
            Log.d(TAG, " avgTimeInterval=" + timecounter.avgTimeInterval + " currentTime=" + time + " timeDiff=" + (double)(time - timecounter.lastTimeStamp) + " position0=" + position0 + " position1=" + position1 + " result=" + result);*/

            double alpha = 0.9; // forgetting factor
            lastMovingAvg = alpha * lastMovingAvg + (1 - alpha) * position1;

            return lastMovingAvg;
        }
    }

    private String matrixToString(Matrix mat) {
        String output = "";
        for (int cols = 0; cols < mat.getColumnDimension(); cols++) {
            output += " [";
            for (int rows = 0; rows < mat.getRowDimension(); rows++) {
                output += Double.toString(mat.get(rows,cols));
                output += ",";
            }
            output += "] ";
        }
        return output;
    }

}
