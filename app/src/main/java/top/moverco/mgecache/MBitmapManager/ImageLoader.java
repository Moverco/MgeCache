package top.moverco.mgecache.MBitmapManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import top.moverco.mgecache.R;


public class ImageLoader {
    private static final String TAG = "top.moverco.ImageLoader";
    public static final int MESSAGE_POST_RESULT = 1;
    /**
     * CPU_COUNT CORE_POOL_SIZE MAX_POOL_SIZE KEEP_ALIVE used to create a
     * thread pool
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;
    /**
     * Bind image id from layout resource
     */
    private static final int TAG_KEY_URI = R.id.image;
    /**
     * Define size of disk cache
     */
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    /**
     * Define size of IO buffer size
     */
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    private static final int DISK_CACHE_INDEX = 0;
    /**
     * A tag to tag if DiskLruCache has been created
     */
    private boolean mIsDiskLruCacheCreated = false;
    /**
     * Get applicationContext through {@Method Context.getApplicationContext}
     */
    private Context mContext;

    private ImageResizer mResizer = new ImageResizer();

    private LruCache<String, Bitmap> mLruCache;

    private DiskLruCache mDiskLruCache;

    private int reqWidth = 0;
    private int reqheight = 0;

    public ImageLoader setReqWidth(int reqWidth) {
        this.reqWidth = reqWidth;
        return this;
    }

    public ImageLoader setReqheight(int reqheight) {
        this.reqheight = reqheight;
        return this;
    }

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), sThreadFactory
    );

    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    /**
     * Get how many compacity can be used to restore cache
     *
     * @param dir
     * @return Usable space
     */
    private long getUsableSpace(File dir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return dir.getUsableSpace();
        }
        final StatFs statFs = new StatFs(dir.getPath());
        return statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong();
    }

    public File getDiskCacheDir(Context context, @Nullable String uniqueName) {
        boolean externalStorageAvailabe = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (externalStorageAvailabe) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * build a new ImageLoader instance with this method
     *
     * @param context
     * @return a new instance of ImageLoader
     */
    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    /**
     * Add bitmap to memory cache through LruCache
     *
     * @param key
     * @param bitmap
     */
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mLruCache.put(key, bitmap);
        }
    }

    /**
     * Bind ImageView and uri, you can use
     * {@link #bindBitmap(String, ImageView, int, int)}
     * to define require width and height of bitmap
     *
     * @param uri
     * @param imageView
     */
    public void bindBitmap(final String uri, final ImageView imageView) {
        bindBitmap(uri, imageView, this.reqWidth, this.reqheight);
    }

    public void bindBitmap(final String uri, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }


    public Bitmap loadBitamap(String uri) {
        return loadBitmap(uri, this.reqWidth, this.reqheight);
    }

    /**
     * The priority of load bitmap is MemoryCache > DiskCache > Http.
     *
     * @param uri
     * @param width
     * @param height
     * @return
     */
    public Bitmap loadBitmap(String uri, int width, int height) {
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            Log.d(TAG, "Bitmap has been loaded from memory cache");
            return bitmap;
        }

        bitmap = loadBitmapFromDiskCache(uri, width, height);
        if (bitmap != null) {
            Log.d(TAG, "Bitmap has been loaded from disk cache");
            return bitmap;
        }
        try {
            bitmap = loadBitmapFromHttp(uri, width, height);
        } catch (IOException e) {
            Log.d(TAG, "load bitmap from http failed");
        }
        if (bitmap == null && !mIsDiskLruCacheCreated) {
            Log.w(TAG, "DiskLruCache is not created");
            bitmap = downloadBitmapFromUrl(uri);
        }
        return bitmap;
    }

    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to download bitmap from http");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            MyUtil.close(in);
        }
        return bitmap;
    }

//    private Bitmap downloadBitamapWithOkhttp(String urlString) {
//        final Bitmap[] bitmap = {null};
//        final BufferedInputStream[] in = {null};
//
//        OkHttpUtils.get()
//                .build()
//                .connTimeOut(8000)
//                .readTimeOut(8000)
//                .execute(new BitmapCallback() {
//                    @Override
//                    public void onError(Call call, Exception e, int id) {
//                        Log.d(TAG, "Failed to download bitmap from http");
//                    }
//
//                    @Override
//                    public void onResponse(Bitmap response, int id) {
//                        in[0] = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
//                        bitmap[0] = BitmapFactory.decodeStream(in[0]);
//                    }
//                });
//    }


    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException {
        Bitmap bitmap = null;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("Can not visit network from UI Thread");
        }
        if (mDiskLruCache == null) {
            return bitmap;
        }
        String key = hashKeyFromUrl(url);

        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(url, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }

        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    private boolean downloadUrlToStream(String urlString, OutputStream stream) {
        HttpURLConnection connection = null;
        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(stream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "download failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            MyUtil.close(in);
            MyUtil.close(out);
        }
        return false;
    }

    private Bitmap loadBitmapFromDiskCache(String uri, int reqWidth, int reqheight) {
        Bitmap bitmap = null;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI thread!");
        }
        if (mDiskLruCache == null) {
            return bitmap;
        }
        String key = hashKeyFromUrl(uri);
        try {
            DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
            if (snapShot != null) {
                FileInputStream fileInputStream = (FileInputStream) snapShot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fileDescriptor = fileInputStream.getFD();
                bitmap = mResizer.decodeSampleFromFileDescriptor(fileDescriptor, reqheight, reqWidth);
                if (bitmap != null) {
                    addBitmapToMemoryCache(key, bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String uri) {
        final String key = hashKeyFromUrl(uri);
        Bitmap bitmap = getBitmapFromMemoryCache(key);
        return bitmap;
    }

    private String hashKeyFromUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    private Bitmap getBitmapFromMemoryCache(String key) {
        return mLruCache.get(key);
    }


    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.mImageView;
            imageView.setImageBitmap(result.mBitmap);
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            if (uri.equals(result.uri)) {
                imageView.setImageBitmap(result.mBitmap);
            } else {
                Log.w(TAG, "set image bitmap, but url has been changed");
            }

        }
    };


    private static class LoaderResult {
        public ImageView mImageView;
        public String uri;
        public Bitmap mBitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            mImageView = imageView;
            this.uri = uri;
            mBitmap = bitmap;
        }
    }


}
