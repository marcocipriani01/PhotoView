/*
 Copyright 2011, 2012 Chris Banes.
 <p>
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 <p>
 http://www.apache.org/licenses/LICENSE-2.0
 <p>
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package io.github.marcocipriani01.livephotoview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.OverScroller;

/**
 * The component of {@link PhotoView} which does the work allowing for zooming, scaling, panning, etc.
 * It is made in case you need to subclass something other than AppCompatImageView and still
 * gain the functionality that {@link PhotoView} offers
 */
class PhotoViewAttacher implements View.OnTouchListener, View.OnLayoutChangeListener {

    private static final int HORIZONTAL_EDGE_NONE = -1;
    private static final int HORIZONTAL_EDGE_LEFT = 0;
    private static final int HORIZONTAL_EDGE_RIGHT = 1;
    private static final int HORIZONTAL_EDGE_BOTH = 2;
    private static final int VERTICAL_EDGE_NONE = -1;
    private static final int VERTICAL_EDGE_TOP = 0;
    private static final int VERTICAL_EDGE_BOTTOM = 1;
    private static final int VERTICAL_EDGE_BOTH = 2;
    private static final float DEFAULT_MAX_SCALE = 3.0f;
    private static final float DEFAULT_MID_SCALE = 1.75f;
    private static final float DEFAULT_MIN_SCALE = 1.0f;
    private static final int DEFAULT_ZOOM_DURATION = 200;
    private static final int SINGLE_TOUCH = 1;
    // These are set so we don't keep allocating them on the heap
    private final Matrix baseMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final RectF displayRect = new RectF();
    private final float[] matrixValues = new float[9];
    private final ImageView imageView;
    private Interpolator interpolator = new AccelerateDecelerateInterpolator();
    private int zoomDuration = DEFAULT_ZOOM_DURATION;
    private float minScale = DEFAULT_MIN_SCALE;
    private float midScale = DEFAULT_MID_SCALE;
    private float maxScale = DEFAULT_MAX_SCALE;
    private boolean allowParentInterceptOnEdge = true;
    private boolean blockParentIntercept = false;
    // Gesture Detectors
    private GestureDetector gestureDetector;
    private CustomGestureDetector scaleDragDetector;
    // Listeners
    private OnMatrixChangedListener matrixChangeListener;
    private OnPhotoTapListener photoTapListener;
    private OnOutsidePhotoTapListener outsidePhotoTapListener;
    private OnViewTapListener viewTapListener;
    private View.OnClickListener onClickListener;
    private OnLongClickListener longClickListener;
    private OnScaleChangedListener scaleChangeListener;
    private OnSingleFlingListener singleFlingListener;
    private OnViewDragListener onViewDragListener;

    private FlingRunnable currentFlingRunnable;
    private int horizontalScrollEdge = HORIZONTAL_EDGE_BOTH;
    private int verticalScrollEdge = VERTICAL_EDGE_BOTH;
    private float baseRotation;

    private boolean zoomEnabled = true;
    private ScaleType scaleType = ScaleType.FIT_CENTER;

    private final OnGestureListener onGestureListener = new OnGestureListener() {
        @Override
        public void onDrag(float dx, float dy) {
            if (scaleDragDetector.isScaling()) {
                return; // Do not drag if we are already scaling
            }
            if (onViewDragListener != null) {
                onViewDragListener.onDrag(dx, dy);
            }
            suppMatrix.postTranslate(dx, dy);
            checkAndDisplayMatrix();

            /*
             * Here we decide whether to let the ImageView's parent to start taking
             * over the touch event.
             *
             * First we check whether this function is enabled. We never want the
             * parent to take over if we're scaling. We then check the edge we're
             * on, and the direction of the scroll (i.e. if we're pulling against
             * the edge, aka 'overscrolling', let the parent take over).
             */
            ViewParent parent = imageView.getParent();
            if (allowParentInterceptOnEdge && !scaleDragDetector.isScaling() && !blockParentIntercept) {
                if (horizontalScrollEdge == HORIZONTAL_EDGE_BOTH
                        || (horizontalScrollEdge == HORIZONTAL_EDGE_LEFT && dx >= 1f)
                        || (horizontalScrollEdge == HORIZONTAL_EDGE_RIGHT && dx <= -1f)
                        || (verticalScrollEdge == VERTICAL_EDGE_TOP && dy >= 1f)
                        || (verticalScrollEdge == VERTICAL_EDGE_BOTTOM && dy <= -1f)) {
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    }
                }
            } else {
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            }
        }

        @Override
        public void onFling(float startX, float startY, float velocityX, float velocityY) {
            currentFlingRunnable = new FlingRunnable(imageView.getContext());
            currentFlingRunnable.fling(getImageViewWidth(imageView),
                    getImageViewHeight(imageView), (int) velocityX, (int) velocityY);
            imageView.post(currentFlingRunnable);
        }

        @Override
        public void onScale(float scaleFactor, float focusX, float focusY) {
            onScale(scaleFactor, focusX, focusY, 0, 0);
        }

        @Override
        public void onScale(float scaleFactor, float focusX, float focusY, float dx, float dy) {
            if (getScale() < maxScale || scaleFactor < 1f) {
                if (scaleChangeListener != null) {
                    scaleChangeListener.onScaleChange(scaleFactor, focusX, focusY);
                }
                suppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
                suppMatrix.postTranslate(dx, dy);
                checkAndDisplayMatrix();
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    PhotoViewAttacher(ImageView imageView) {
        this.imageView = imageView;
        imageView.setOnTouchListener(this);
        imageView.addOnLayoutChangeListener(this);
        if (imageView.isInEditMode()) {
            return;
        }
        baseRotation = 0.0f;
        // Create Gesture Detectors...
        scaleDragDetector = new CustomGestureDetector(imageView.getContext(), onGestureListener);
        gestureDetector = new GestureDetector(imageView.getContext(), new GestureDetector.SimpleOnGestureListener() {

            // forward long click listener
            @Override
            public void onLongPress(MotionEvent e) {
                if (longClickListener != null) {
                    longClickListener.onLongClick(PhotoViewAttacher.this.imageView);
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                if (singleFlingListener != null) {
                    if (getScale() > DEFAULT_MIN_SCALE) {
                        return false;
                    }
                    if (e1.getPointerCount() > SINGLE_TOUCH
                            || e2.getPointerCount() > SINGLE_TOUCH) {
                        return false;
                    }
                    return singleFlingListener.onFling(e1, e2, velocityX, velocityY);
                }
                return false;
            }
        });
        gestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (onClickListener != null) {
                    onClickListener.onClick(PhotoViewAttacher.this.imageView);
                }
                final RectF displayRect = getDisplayRect();
                final float x = e.getX(), y = e.getY();
                if (viewTapListener != null) {
                    viewTapListener.onViewTap(PhotoViewAttacher.this.imageView, x, y);
                }
                if (displayRect != null) {
                    // Check to see if the user tapped on the photo
                    if (displayRect.contains(x, y)) {
                        float xResult = (x - displayRect.left)
                                / displayRect.width();
                        float yResult = (y - displayRect.top)
                                / displayRect.height();
                        if (photoTapListener != null) {
                            photoTapListener.onPhotoTap(PhotoViewAttacher.this.imageView, xResult, yResult);
                        }
                        return true;
                    } else {
                        if (outsidePhotoTapListener != null) {
                            outsidePhotoTapListener.onOutsidePhotoTap(PhotoViewAttacher.this.imageView);
                        }
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent ev) {
                try {
                    float scale = getScale();
                    float x = ev.getX();
                    float y = ev.getY();
                    if (scale < getMediumScale()) {
                        setScale(getMediumScale(), x, y, true);
                    } else if (scale >= getMediumScale() && scale < getMaximumScale()) {
                        setScale(getMaximumScale(), x, y, true);
                    } else {
                        setScale(getMinimumScale(), x, y, true);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Can sometimes happen when getX() and getY() is called
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                // Wait for the confirmed onDoubleTap() instead
                return false;
            }
        });
    }

    private static void checkZoomLevels(float minZoom, float midZoom, float maxZoom) {
        if (minZoom >= midZoom) {
            throw new IllegalArgumentException(
                    "Minimum zoom has to be less than Medium zoom. Call setMinimumZoom() with a more appropriate value");
        } else if (midZoom >= maxZoom) {
            throw new IllegalArgumentException(
                    "Medium zoom has to be less than Maximum zoom. Call setMaximumZoom() with a more appropriate value");
        }
    }

    void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener newOnDoubleTapListener) {
        this.gestureDetector.setOnDoubleTapListener(newOnDoubleTapListener);
    }

    void setOnScaleChangeListener(OnScaleChangedListener onScaleChangeListener) {
        this.scaleChangeListener = onScaleChangeListener;
    }

    void setOnSingleFlingListener(OnSingleFlingListener onSingleFlingListener) {
        this.singleFlingListener = onSingleFlingListener;
    }

    RectF getDisplayRect() {
        checkMatrixBounds();
        return getDisplayRect(getDrawMatrix());
    }

    boolean setDisplayMatrix(Matrix finalMatrix) {
        if (finalMatrix == null) {
            throw new IllegalArgumentException("Matrix cannot be null");
        }
        if (imageView.getDrawable() == null) {
            return false;
        }
        suppMatrix.set(finalMatrix);
        checkAndDisplayMatrix();
        return true;
    }

    void setBaseRotation(final float degrees) {
        baseRotation = degrees % 360;
        update();
        setRotationBy(baseRotation);
        checkAndDisplayMatrix();
    }

    void setRotationTo(float degrees) {
        suppMatrix.setRotate(degrees % 360);
        checkAndDisplayMatrix();
    }

    void setRotationBy(float degrees) {
        suppMatrix.postRotate(degrees % 360);
        checkAndDisplayMatrix();
    }

    float getMinimumScale() {
        return minScale;
    }

    void setMinimumScale(float minimumScale) {
        checkZoomLevels(minimumScale, midScale, maxScale);
        minScale = minimumScale;
    }

    float getMediumScale() {
        return midScale;
    }

    void setMediumScale(float mediumScale) {
        checkZoomLevels(minScale, mediumScale, maxScale);
        midScale = mediumScale;
    }

    float getMaximumScale() {
        return maxScale;
    }

    void setMaximumScale(float maximumScale) {
        checkZoomLevels(minScale, midScale, maximumScale);
        maxScale = maximumScale;
    }

    float getScale() {
        return (float) Math.sqrt((float) Math.pow(getValue(suppMatrix, Matrix.MSCALE_X), 2) + (float) Math.pow
                (getValue(suppMatrix, Matrix.MSKEW_Y), 2));
    }

    void setScale(float scale) {
        setScale(scale, false);
    }

    ScaleType getScaleType() {
        return scaleType;
    }

    void setScaleType(ScaleType scaleType) {
        if (scaleType == null) return;
        if (scaleType == ScaleType.MATRIX)
            throw new IllegalStateException("Matrix scale type is not supported");
        if (scaleType != this.scaleType) {
            this.scaleType = scaleType;
            update();
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int
            oldRight, int oldBottom) {
        // Update our base matrix, as the bounds have changed
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix(imageView.getDrawable());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        boolean handled = false;
        if (zoomEnabled && ((ImageView) v).getDrawable() != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    ViewParent parent = v.getParent();
                    // First, disable the Parent from intercepting the touch
                    // event
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    // If we're flinging, and the user presses down, cancel
                    // fling
                    cancelFling();
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    // If the user has zoomed less than min scale, zoom back
                    // to min scale
                    if (getScale() < minScale) {
                        RectF rect = getDisplayRect();
                        if (rect != null) {
                            v.post(new AnimatedZoomRunnable(getScale(), minScale,
                                    rect.centerX(), rect.centerY()));
                            handled = true;
                        }
                    } else if (getScale() > maxScale) {
                        RectF rect = getDisplayRect();
                        if (rect != null) {
                            v.post(new AnimatedZoomRunnable(getScale(), maxScale,
                                    rect.centerX(), rect.centerY()));
                            handled = true;
                        }
                    }
                    break;
            }
            // Try the Scale/Drag detector
            if (scaleDragDetector != null) {
                boolean wasScaling = scaleDragDetector.isScaling();
                boolean wasDragging = scaleDragDetector.isDragging();
                handled = scaleDragDetector.onTouchEvent(ev);
                boolean didntScale = !wasScaling && !scaleDragDetector.isScaling();
                boolean didntDrag = !wasDragging && !scaleDragDetector.isDragging();
                blockParentIntercept = didntScale && didntDrag;
            }
            // Check to see if the user double tapped
            if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
                handled = true;
            }

        }
        return handled;
    }

    void setAllowParentInterceptOnEdge(boolean allow) {
        allowParentInterceptOnEdge = allow;
    }

    void setScaleLevels(float minimumScale, float mediumScale, float maximumScale) {
        checkZoomLevels(minimumScale, mediumScale, maximumScale);
        minScale = minimumScale;
        midScale = mediumScale;
        maxScale = maximumScale;
    }

    void setOnLongClickListener(OnLongClickListener listener) {
        longClickListener = listener;
    }

    void setOnClickListener(View.OnClickListener listener) {
        onClickListener = listener;
    }

    void setOnMatrixChangeListener(OnMatrixChangedListener listener) {
        matrixChangeListener = listener;
    }

    void setOnPhotoTapListener(OnPhotoTapListener listener) {
        photoTapListener = listener;
    }

    void setOnOutsidePhotoTapListener(OnOutsidePhotoTapListener mOutsidePhotoTapListener) {
        this.outsidePhotoTapListener = mOutsidePhotoTapListener;
    }

    void setOnViewTapListener(OnViewTapListener listener) {
        viewTapListener = listener;
    }

    void setOnViewDragListener(OnViewDragListener listener) {
        onViewDragListener = listener;
    }

    void setScale(float scale, boolean animate) {
        setScale(scale, (imageView.getRight()) / 2f,
                (imageView.getBottom()) / 2f, animate);
    }

    void setScale(float scale, float focalX, float focalY, boolean animate) {
        // Check to see if the scale is within bounds
        if (scale < minScale || scale > maxScale) {
            throw new IllegalArgumentException("Scale must be within the range of minScale and maxScale");
        }
        if (animate) {
            imageView.post(new AnimatedZoomRunnable(getScale(), scale,
                    focalX, focalY));
        } else {
            suppMatrix.setScale(scale, scale, focalX, focalY);
            checkAndDisplayMatrix();
        }
    }

    /**
     * Set the zoom interpolator
     *
     * @param interpolator the zoom interpolator
     */
    void setZoomInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    boolean isZoomable() {
        return zoomEnabled;
    }

    void setZoomable(boolean zoomable) {
        zoomEnabled = zoomable;
        update();
    }

    void update() {
        if (zoomEnabled) {
            // Update the base matrix using the current drawable
            updateBaseMatrix(imageView.getDrawable());
        } else {
            // Reset the Matrix...
            resetMatrix();
        }
    }

    /**
     * Get the display matrix
     *
     * @param matrix target matrix to copy to
     */
    void getDisplayMatrix(Matrix matrix) {
        matrix.set(getDrawMatrix());
    }

    /**
     * Get the current support matrix
     */
    void getSuppMatrix(Matrix matrix) {
        matrix.set(suppMatrix);
    }

    private Matrix getDrawMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);
        return drawMatrix;
    }

    Matrix getImageMatrix() {
        return drawMatrix;
    }

    void setZoomTransitionDuration(int milliseconds) {
        this.zoomDuration = milliseconds;
    }

    /**
     * Helper method that 'unpacks' a Matrix and returns the required value
     *
     * @param matrix     Matrix to unpack
     * @param whichValue Which value from Matrix.M* to return
     * @return returned value
     */
    private float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(matrixValues);
        return matrixValues[whichValue];
    }

    /**
     * Resets the Matrix back to FIT_CENTER, and then displays its contents
     */
    private void resetMatrix() {
        suppMatrix.reset();
        setRotationBy(baseRotation);
        setImageViewMatrix(getDrawMatrix());
        checkMatrixBounds();
    }

    private void setImageViewMatrix(Matrix matrix) {
        imageView.setImageMatrix(matrix);
        // Call MatrixChangedListener if needed
        if (matrixChangeListener != null) {
            RectF displayRect = getDisplayRect(matrix);
            if (displayRect != null) {
                matrixChangeListener.onMatrixChanged(displayRect);
            }
        }
    }

    /**
     * Helper method that simply checks the Matrix, and then displays the result
     */
    private void checkAndDisplayMatrix() {
        if (checkMatrixBounds()) {
            setImageViewMatrix(getDrawMatrix());
        }
    }

    /**
     * Helper method that maps the supplied Matrix to the current Drawable
     *
     * @param matrix - Matrix to map Drawable against
     * @return RectF - Displayed Rectangle
     */
    private RectF getDisplayRect(Matrix matrix) {
        Drawable d = imageView.getDrawable();
        if (d != null) {
            displayRect.set(0, 0, d.getIntrinsicWidth(),
                    d.getIntrinsicHeight());
            matrix.mapRect(displayRect);
            return displayRect;
        }
        return null;
    }

    /**
     * Calculate Matrix for FIT_CENTER
     *
     * @param drawable - Drawable being displayed
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void updateBaseMatrix(Drawable drawable) {
        if (drawable == null) return;
        final float viewWidth = getImageViewWidth(imageView);
        final float viewHeight = getImageViewHeight(imageView);
        final int drawableWidth = drawable.getIntrinsicWidth();
        final int drawableHeight = drawable.getIntrinsicHeight();
        baseMatrix.reset();
        final float widthScale = viewWidth / drawableWidth;
        final float heightScale = viewHeight / drawableHeight;
        if (scaleType == ScaleType.CENTER) {
            baseMatrix.postTranslate((viewWidth - drawableWidth) / 2F,
                    (viewHeight - drawableHeight) / 2F);

        } else if (scaleType == ScaleType.CENTER_CROP) {
            float scale = Math.max(widthScale, heightScale);
            baseMatrix.postScale(scale, scale);
            baseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);

        } else if (scaleType == ScaleType.CENTER_INSIDE) {
            float scale = Math.min(1.0f, Math.min(widthScale, heightScale));
            baseMatrix.postScale(scale, scale);
            baseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);

        } else {
            RectF mTempSrc = new RectF(0, 0, drawableWidth, drawableHeight);
            RectF mTempDst = new RectF(0, 0, viewWidth, viewHeight);
            if ((int) baseRotation % 180 != 0) {
                mTempSrc = new RectF(0, 0, drawableHeight, drawableWidth);
            }
            switch (scaleType) {
                case FIT_CENTER:
                    baseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.CENTER);
                    break;
                case FIT_START:
                    baseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.START);
                    break;
                case FIT_END:
                    baseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.END);
                    break;
                case FIT_XY:
                    baseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.FILL);
                    break;
                default:
                    break;
            }
        }
        resetMatrix();
    }

    private boolean checkMatrixBounds() {
        final RectF rect = getDisplayRect(getDrawMatrix());
        if (rect == null) {
            return false;
        }
        final float height = rect.height(), width = rect.width();
        float deltaX = 0, deltaY = 0;
        final int viewHeight = getImageViewHeight(imageView);
        if (height <= viewHeight) {
            switch (scaleType) {
                case FIT_START:
                    deltaY = -rect.top;
                    break;
                case FIT_END:
                    deltaY = viewHeight - height - rect.top;
                    break;
                default:
                    deltaY = (viewHeight - height) / 2 - rect.top;
                    break;
            }
            verticalScrollEdge = VERTICAL_EDGE_BOTH;
        } else if (rect.top > 0) {
            verticalScrollEdge = VERTICAL_EDGE_TOP;
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            verticalScrollEdge = VERTICAL_EDGE_BOTTOM;
            deltaY = viewHeight - rect.bottom;
        } else {
            verticalScrollEdge = VERTICAL_EDGE_NONE;
        }
        final int viewWidth = getImageViewWidth(imageView);
        if (width <= viewWidth) {
            switch (scaleType) {
                case FIT_START:
                    deltaX = -rect.left;
                    break;
                case FIT_END:
                    deltaX = viewWidth - width - rect.left;
                    break;
                default:
                    deltaX = (viewWidth - width) / 2 - rect.left;
                    break;
            }
            horizontalScrollEdge = HORIZONTAL_EDGE_BOTH;
        } else if (rect.left > 0) {
            horizontalScrollEdge = HORIZONTAL_EDGE_LEFT;
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
            horizontalScrollEdge = HORIZONTAL_EDGE_RIGHT;
        } else {
            horizontalScrollEdge = HORIZONTAL_EDGE_NONE;
        }
        // Finally actually translate the matrix
        suppMatrix.postTranslate(deltaX, deltaY);
        return true;
    }

    private int getImageViewWidth(ImageView imageView) {
        return imageView.getWidth() - imageView.getPaddingLeft() - imageView.getPaddingRight();
    }

    private int getImageViewHeight(ImageView imageView) {
        return imageView.getHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom();
    }

    private void cancelFling() {
        if (currentFlingRunnable != null) {
            currentFlingRunnable.cancelFling();
            currentFlingRunnable = null;
        }
    }

    private class AnimatedZoomRunnable implements Runnable {

        private final float mFocalX, mFocalY;
        private final long mStartTime;
        private final float mZoomStart, mZoomEnd;

        AnimatedZoomRunnable(final float currentZoom, final float targetZoom,
                             final float focalX, final float focalY) {
            mFocalX = focalX;
            mFocalY = focalY;
            mStartTime = System.currentTimeMillis();
            mZoomStart = currentZoom;
            mZoomEnd = targetZoom;
        }

        @Override
        public void run() {
            float t = interpolate();
            float scale = mZoomStart + t * (mZoomEnd - mZoomStart);
            float deltaScale = scale / getScale();
            onGestureListener.onScale(deltaScale, mFocalX, mFocalY);
            // We haven't hit our target scale yet, so post ourselves again
            if (t < 1f) {
                imageView.postOnAnimation(this);
            }
        }

        private float interpolate() {
            float t = 1f * (System.currentTimeMillis() - mStartTime) / zoomDuration;
            t = Math.min(1f, t);
            t = interpolator.getInterpolation(t);
            return t;
        }
    }

    private class FlingRunnable implements Runnable {

        private final OverScroller mScroller;
        private int mCurrentX, mCurrentY;

        FlingRunnable(Context context) {
            mScroller = new OverScroller(context);
        }

        void cancelFling() {
            mScroller.forceFinished(true);
        }

        void fling(int viewWidth, int viewHeight, int velocityX,
                   int velocityY) {
            final RectF rect = getDisplayRect();
            if (rect == null) {
                return;
            }
            final int startX = Math.round(-rect.left);
            final int minX, maxX, minY, maxY;
            if (viewWidth < rect.width()) {
                minX = 0;
                maxX = Math.round(rect.width() - viewWidth);
            } else {
                minX = maxX = startX;
            }
            final int startY = Math.round(-rect.top);
            if (viewHeight < rect.height()) {
                minY = 0;
                maxY = Math.round(rect.height() - viewHeight);
            } else {
                minY = maxY = startY;
            }
            mCurrentX = startX;
            mCurrentY = startY;
            // If we actually can move, fling the scroller
            if (startX != maxX || startY != maxY) {
                mScroller.fling(startX, startY, velocityX, velocityY, minX,
                        maxX, minY, maxY, 0, 0);
            }
        }

        @Override
        public void run() {
            if (mScroller.isFinished()) {
                return; // remaining post that should not be handled
            }
            if (mScroller.computeScrollOffset()) {
                final int newX = mScroller.getCurrX();
                final int newY = mScroller.getCurrY();
                suppMatrix.postTranslate(mCurrentX - newX, mCurrentY - newY);
                checkAndDisplayMatrix();
                mCurrentX = newX;
                mCurrentY = newY;
                // Post On animation
                imageView.postOnAnimation(this);
            }
        }
    }
}