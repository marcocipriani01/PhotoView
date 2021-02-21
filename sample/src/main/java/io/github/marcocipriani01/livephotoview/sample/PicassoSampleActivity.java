package io.github.marcocipriani01.livephotoview.sample;

import android.os.Bundle;

import io.github.marcocipriani01.livephotoview.PhotoView;

import com.github.marcocipriani01.livephotoview.sample.R;

import androidx.appcompat.app.AppCompatActivity;

public class PicassoSampleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple);

        final PhotoView photoView = findViewById(R.id.iv_photo);
    }
}
