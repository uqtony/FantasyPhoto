package com.uqtony.fantasyphoto;

import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.AsyncTask;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.lang.ref.WeakReference;
import java.util.Map;

public class QRDecodeFrameTask extends AsyncTask<byte[], Void, Result> {
    public interface OnQRCodeReadListener {
        void onQRCodeRead(String text, ResultPoint[] points);
    }
    static final String TAG = "QRDecodeFrameTask";
    static private QRCodeReader mQRCodeReader = new QRCodeReader();

    private Map<DecodeHintType, Object> decodeHints;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private OnQRCodeReadListener mOnQRCodeReadListener = null;

    private final WeakReference<Map<DecodeHintType, Object>> hintsRef;

    QRDecodeFrameTask(Map<DecodeHintType, Object> hints, int previewWidth, int previewHeight) {
        hintsRef = new WeakReference<>(hints);
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
    }

    public void setOnQRCodeReadListener(OnQRCodeReadListener listener){
        mOnQRCodeReadListener = listener;
    }

    @Override
    protected Result doInBackground(byte[]... params) {
        final PlanarYUVLuminanceSource source =
                //buildLuminanceSource(params[0], mPreviewWidth, mPreviewHeight);
                new PlanarYUVLuminanceSource(params[0], mPreviewWidth, mPreviewHeight, 0, 0,
                        mPreviewWidth, mPreviewHeight, false);

        final HybridBinarizer hybBin = new HybridBinarizer(source);
        final BinaryBitmap bitmap = new BinaryBitmap(hybBin);

        try {
            return mQRCodeReader.decode(bitmap, hintsRef.get());
        } catch (ChecksumException e) {
            //Log.d(TAG, "ChecksumException", e);
        } catch (NotFoundException e) {
            //Log.d(TAG, "No QR Code found");
        } catch (FormatException e) {
            //Log.d(TAG, "FormatException", e);
        }catch (Exception e){
            Log.d(TAG, "Exception", e);
        }
        finally {
            mQRCodeReader.reset();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);

        // Notify we found a QRCode
        if (result != null && mOnQRCodeReadListener != null) {
            ResultPoint[] points = result.getResultPoints();
            mOnQRCodeReadListener.onQRCodeRead(result.getText(), points);
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data A preview frame.
     * @param width The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    static public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = new Rect(0, 0, width, height);

        byte[] rotatedData = new byte[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++)
                rotatedData[x * height + height - y - 1] = data[x + y * width];
        }
        int tmp = width;
        width = height;
        height = tmp;

        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(rotatedData, width, height, rect.left, rect.top,
                rect.width(), rect.height(), false);
    }

    public static byte[] yuvImageToByteArray(Image image, Image.Plane[] planes) {

        assert(image.getFormat() == ImageFormat.YUV_420_888);

        int width = image.getWidth();
        int height = image.getHeight();

        byte[] result = new byte[width * height * 3 / 2];

        int stride = planes[0].getRowStride();
        if (stride == width) {
            planes[0].getBuffer().position(0);
            planes[0].getBuffer().get(result, 0, width);
        }
        else {
            for (int row = 0; row < height; row++) {
                planes[0].getBuffer().position(row*stride);
                planes[0].getBuffer().get(result, row*width, width);
            }
        }

        stride = planes[1].getRowStride();
        assert (stride == planes[2].getRowStride());
        byte[] rowBytesCb = new byte[stride];
        byte[] rowBytesCr = new byte[stride];

        for (int row = 0; row < height/2; row++) {
            int rowOffset = width*height + width/2 * row;
            planes[1].getBuffer().position(row*stride);
            planes[1].getBuffer().get(rowBytesCb, 0, width/2);
            planes[2].getBuffer().position(row*stride);
            planes[2].getBuffer().get(rowBytesCr, 0, width/2);

            for (int col = 0; col < width/2; col++) {
                result[rowOffset + col*2] = rowBytesCr[col];
                result[rowOffset + col*2 + 1] = rowBytesCb[col];
            }
        }
        return result;
    }
}
