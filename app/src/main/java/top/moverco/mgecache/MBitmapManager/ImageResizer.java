package top.moverco.mgecache.MBitmapManager;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

public class ImageResizer {
    private static final String TAG = "top.moverco.ImageResiser";
    private int reqWidth;
    private int reqHeight;

    public ImageResizer() {
    }

    public ImageResizer(int reqWidth, int reqHeight) {
        this.reqWidth = reqWidth;
        this.reqHeight = reqHeight;
    }

    public ImageResizer setReqWidth(int reqWidth) {
        this.reqWidth = reqWidth;
        return this;
    }

    public ImageResizer setReqHeight(int reqHeight) {
        this.reqHeight = reqHeight;
        return this;
    }

    public Bitmap decodeSampleBitmapFromResource(Resources resources, int resId) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, options);

        options.inSampleSize = calculateInsampleSize(options, this.reqWidth, this.reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(resources, resId, options);
    }

    public Bitmap decodeSampleFromFileDescriptor(FileDescriptor fd) {
        return decodeSampleFromFileDescriptor(fd,this.reqWidth,this.reqHeight);
    }

    public Bitmap decodeSampleFromFileDescriptor(FileDescriptor fd,int reqWidth,int reqHeight){
        final BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd,null,option);

        option.inSampleSize = calculateInsampleSize(option,reqWidth,reqHeight);

        option.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd,null,option);
    }


    public int calculateInsampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqHeight == 0 || reqWidth == 0) {
            return 1;
        }

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
