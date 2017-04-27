package top.moverco.mgecache;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import top.moverco.mgecache.MBitmapManager.ImageLoader;

public class MainActivity extends AppCompatActivity {
    public static final String POSTER_ROOT = "https://image.tmdb.org/t/p/w1280/2DQrGFQ6P4hhgkY2hcAhUTdsJ97.jpg";
    public static final String poster = "https://image.tmdb.org/t/p/original/2DQrGFQ6P4hhgkY2hcAhUTdsJ97.jpg";
    public static final String SRC = "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1493288160793&di=fb8624e54f7cdc62f79abec38ea311de&imgtype=0&src=http%3A%2F%2Fmy.csdn.net%2Fuploads%2F201207%2F31%2F1343741905_7828.jpg";
    private int reqWidth;
    private int reqHeight;
    private ImageView mImageView,mImageView2;
    private Button mButton;
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.image);
        mImageView2 = (ImageView) findViewById(R.id.image2);
        mTextView = (TextView) findViewById(R.id.textView);
        loadImage();
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadImage();

            }
        });


    }

    private void loadImage() {
        ImageLoader imageLoader = ImageLoader.build(MainActivity.this);
        imageLoader.bindBitmap(POSTER_ROOT, mImageView2, 60, 60);
        Bitmap bitmap = null;
        BitmapAsyncTask task = new BitmapAsyncTask();
        task.execute(POSTER_ROOT);
    }

    class BitmapAsyncTask extends AsyncTask<String, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... params) {
            Bitmap bitmap = null;
            ImageLoader imageLoader = ImageLoader.build(MainActivity.this)
                    .setReqWidth(60)
                    .setReqheight(60);
            bitmap = imageLoader.loadBitamap(params[0]);
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
//            mImageView.setImageBitmap(bitmap);
//            mImageView2.setImageBitmap(bitmap);
//                mTextView.setText("Image has been changed");
        }


    }
}
