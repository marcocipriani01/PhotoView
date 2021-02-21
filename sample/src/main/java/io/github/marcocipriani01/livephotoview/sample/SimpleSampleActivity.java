/*
 Copyright 2011, 2012 Chris Banes.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package io.github.marcocipriani01.livephotoview.sample;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.github.marcocipriani01.livephotoview.sample.R;

import java.util.Random;

import io.github.marcocipriani01.livephotoview.OnMatrixChangedListener;
import io.github.marcocipriani01.livephotoview.OnPhotoTapListener;
import io.github.marcocipriani01.livephotoview.OnSingleFlingListener;
import io.github.marcocipriani01.livephotoview.PhotoView;

public class SimpleSampleActivity extends AppCompatActivity {

    static final String PHOTO_TAP_TOAST_STRING = "Photo Tap! X: %.2f %% Y:%.2f %% ID: %d";
    static final String SCALE_TOAST_STRING = "Scaled to: %.2ff";
    static final String FLING_LOG_STRING = "Fling velocityX: %.2f, velocityY: %.2f";

    private PhotoView mPhotoView;
    private TextView mCurrMatrixTv;

    private Toast mCurrentToast;

    private Matrix mCurrentDisplayMatrix = null;

    @SuppressLint("DefaultLocale")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_sample);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Simple Sample");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_zoom_toggle) {
                mPhotoView.setZoomable(!mPhotoView.isZoomable());
                item.setTitle(mPhotoView.isZoomable() ? R.string.menu_zoom_disable : R.string.menu_zoom_enable);
                return true;
            } else if (itemId == R.id.menu_scale_fit_center) {
                mPhotoView.setScaleType(ImageView.ScaleType.CENTER);
                return true;
            } else if (itemId == R.id.menu_scale_fit_start) {
                mPhotoView.setScaleType(ImageView.ScaleType.FIT_START);
                return true;
            } else if (itemId == R.id.menu_scale_fit_end) {
                mPhotoView.setScaleType(ImageView.ScaleType.FIT_END);
                return true;
            } else if (itemId == R.id.menu_scale_fit_xy) {
                mPhotoView.setScaleType(ImageView.ScaleType.FIT_XY);
                return true;
            } else if (itemId == R.id.menu_scale_scale_center) {
                mPhotoView.setScaleType(ImageView.ScaleType.CENTER);
                return true;
            } else if (itemId == R.id.menu_scale_scale_center_crop) {
                mPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return true;
            } else if (itemId == R.id.menu_scale_scale_center_inside) {
                mPhotoView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                return true;
            } else if (itemId == R.id.menu_scale_random_animate || itemId == R.id.menu_scale_random) {
                Random r = new Random();

                float minScale = mPhotoView.getMinimumScale();
                float maxScale = mPhotoView.getMaximumScale();
                float randomScale = minScale + (r.nextFloat() * (maxScale - minScale));
                mPhotoView.setScale(randomScale, item.getItemId() == R.id.menu_scale_random_animate);

                showToast(String.format(SCALE_TOAST_STRING, randomScale));

                return true;
            } else if (itemId == R.id.menu_matrix_restore) {
                if (mCurrentDisplayMatrix == null)
                    showToast("You need to capture display matrix first");
                else
                    mPhotoView.setDisplayMatrix(mCurrentDisplayMatrix);
                return true;
            } else if (itemId == R.id.menu_matrix_capture) {
                mCurrentDisplayMatrix = new Matrix();
                mPhotoView.getDisplayMatrix(mCurrentDisplayMatrix);
                return true;
            }
            return false;
        });
        mPhotoView = findViewById(R.id.iv_photo);
        mCurrMatrixTv = findViewById(R.id.tv_current_matrix);

        mPhotoView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.wallpaper));

        // Lets attach some listeners, not required though!
        mPhotoView.setOnMatrixChangeListener(new MatrixChangeListener());
        mPhotoView.setOnPhotoTapListener(new PhotoTapListener());
        mPhotoView.setOnSingleFlingListener(new SingleFlingListener());
    }

    private void showToast(CharSequence text) {
        if (mCurrentToast != null) {
            mCurrentToast.cancel();
        }
        mCurrentToast = Toast.makeText(SimpleSampleActivity.this, text, Toast.LENGTH_SHORT);
        mCurrentToast.show();
    }

    private static class SingleFlingListener implements OnSingleFlingListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            Log.d("PhotoView", String.format(FLING_LOG_STRING, velocityX, velocityY));
            return true;
        }
    }

    private class PhotoTapListener implements OnPhotoTapListener {

        @SuppressLint("DefaultLocale")
        @Override
        public void onPhotoTap(ImageView view, float x, float y) {
            float xPercentage = x * 100f;
            float yPercentage = y * 100f;
            showToast(String.format(PHOTO_TAP_TOAST_STRING, xPercentage, yPercentage, view == null ? 0 : view.getId()));
        }
    }

    private class MatrixChangeListener implements OnMatrixChangedListener {

        @Override
        public void onMatrixChanged(RectF rect) {
            mCurrMatrixTv.setText(rect.toString());
        }
    }
}