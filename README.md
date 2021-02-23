# PhotoView

PhotoView aims to help produce an easily usable implementation of a zooming Android ImageView.

This is a modified version of the [PhotoView library](https://github.com/Baseflow/PhotoView) by Baseflow.
It doesn't reset the zoom level when an image is changed.
This library is used by [Telescope.Touch](https://github.com/marcocipriani01/Telescope.Touch) as a live, zoomable image viewer for INDI cameras.

[![](https://jitpack.io/v/marcocipriani01/PhotoView.svg)](https://jitpack.io/#marcocipriani01/PhotoView)

## Dependency

Add this in your root `build.gradle` file (**not** your module `build.gradle` file):

```gradle
allprojects {
    repositories {
        maven { url "https://www.jitpack.io" }
    }
}

buildscript {
    repositories {
        maven { url "https://www.jitpack.io" }
    }	
}
```

Then, add the library to your module `build.gradle`

```gradle
dependencies {
    implementation 'com.github.marcocipriani01:PhotoView:master-SNAPSHOT'
}
```

## Features
- Out of the box zooming, using multi-touch and double-tap.
- Scrolling, with smooth scrolling fling.
- Works perfectly when used in a scrolling parent (such as ViewPager).
- Allows the application to be notified when the displayed Matrix has changed. Useful for when you need to update your UI based on the current zoom/scroll position.
- Allows the application to be notified when the user taps on the Photo.

## Usage
There is a [sample](https://github.com/chrisbanes/PhotoView/tree/master/sample) provided which shows how to use the library in a more advanced way, but for completeness, here is all that is required to get PhotoView working:
```xml
<com.github.chrisbanes.photoview.PhotoView
    android:id="@+id/photo_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```
```java
PhotoView photoView = (PhotoView) findViewById(R.id.photo_view);
photoView.setImageResource(R.drawable.image);
```
That's it!
