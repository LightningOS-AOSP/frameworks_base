/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import java.util.List;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.MathUtils;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.media.MediaViewController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.ScrimState;
import com.android.systemui.tuner.TunerService;

public class WallpaperDepthUtils {

    private static final String WALLPAPER_DEPTH_KEY = "system:depth_wallpaper_subject_image_uri";
    private static final String WALLPAPER_DEPTH_ENABLED_KEY = "system:depth_wallpaper_enabled";
    private static final String WALLPAPER_DEPTH_OPACITY_KEY = "system:depth_wallpaper_opacity";
    private static final String WALLPAPER_DEPTH_OFFSET_X_KEY = "system:depth_wallpaper_offset_x";
    private static final String WALLPAPER_DEPTH_OFFSET_Y_KEY = "system:depth_wallpaper_offset_y";
    private static final String WALLPAPER_DEPTH_BOTTOM_FADE_KEY = "system:depth_wallpaper_bottom_fade";
    private static final String WALLPAPER_DEPTH_FADE_CURVE_KEY = "system:depth_wallpaper_fade_curve";
    private static final String WALLPAPER_DEPTH_BOTTOM_INSET_KEY = "system:depth_wallpaper_bottom_inset";

    private static final int DEFAULT_FADE_CURVE_PERCENT = 50;
    private static final int DEFAULT_BOTTOM_FADE_DP = 100;
    private static final int DEFAULT_BOTTOM_INSET_DP = 180;

    private static WallpaperDepthUtils instance;
    private FrameLayout mLockScreenSubject;

    private final Context mContext;
    private final ScrimController mScrimController;
    private final TunerService mTunerService;

    private boolean mDWallpaperEnabled;
    private int mDWallOpacity = 255;
    private String mWallpaperSubjectPath;
    private boolean mDozing;
    private boolean mBouncerShowing;
    private boolean mGlanceableHubShowing;
    private boolean mDynamicBarExpanded;
    private boolean mWallpaperLoaded = false;
    private String mPreviousWallpaperPath;
    private Bitmap mWallpaperBitmap;
    private int mOffsetX;
    private int mOffsetY;
    private boolean mUnlocking;

    private int mBottomFadeDp = DEFAULT_BOTTOM_FADE_DP;
    private int mFadeCurvePercent = DEFAULT_FADE_CURVE_PERCENT;
    private int mBottomInsetDp = DEFAULT_BOTTOM_INSET_DP;

    private WallpaperDepthUtils(Context context, ScrimController scrimController) {
        mContext = context.getApplicationContext();
        mScrimController = scrimController;
        mTunerService = Dependency.get(TunerService.class);
        mSpatialEffectController = new SpatialEffectController(mContext);
        mTunerService.addTunable(mTunable, WALLPAPER_DEPTH_KEY,
                WALLPAPER_DEPTH_ENABLED_KEY, WALLPAPER_DEPTH_OPACITY_KEY,
                WALLPAPER_DEPTH_OFFSET_X_KEY, WALLPAPER_DEPTH_OFFSET_Y_KEY,
                WALLPAPER_DEPTH_BOTTOM_FADE_KEY, WALLPAPER_DEPTH_FADE_CURVE_KEY,
                WALLPAPER_DEPTH_BOTTOM_INSET_KEY, WALLPAPER_DEPTH_SPATIAL_KEY);

        mLockScreenBackground = new FrameLayout(mContext) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(-1, -1);
        mLockScreenBackground.setLayoutParams(bgLp);
        mLockScreenBackground.setClickable(false);
        mLockScreenBackground.setLongClickable(false);
        mLockScreenBackground.setFocusable(false);
        mLockScreenBackground.setFocusableInTouchMode(false);
        mLockScreenBackground.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        mLockScreenSubject = new FrameLayout(mContext) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null && mLockScreenBackground.getParent() == null) {
                    View keyguardRootView = parent.findViewById(
                            com.android.systemui.res.R.id.keyguard_root_view);
                    if (keyguardRootView != null) {
                        int keyguardIndex = parent.indexOfChild(keyguardRootView);
                        if (keyguardIndex >= 0) {
                            parent.addView(mLockScreenBackground, keyguardIndex);
                        } else {
                            parent.addView(mLockScreenBackground, 0);
                        }
                    } else {
                        int myIndex = parent.indexOfChild(this);
                        parent.addView(mLockScreenBackground, Math.max(0, myIndex));
                    }
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        mLockScreenSubject.setLayoutParams(lp);
        // Make view non-interactive so touches pass through to notifications
        mLockScreenSubject.setClickable(false);
        mLockScreenSubject.setLongClickable(false);
        mLockScreenSubject.setFocusable(false);
        mLockScreenSubject.setFocusableInTouchMode(false);
        mLockScreenSubject.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        mSpatialEffectController.attachBackgroundView(mLockScreenBackground);
        mSpatialEffectController.attachSubjectView(mLockScreenSubject);

        mLockScreenSubject.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            int oldWidth = oldRight - oldLeft;
            int oldHeight = oldBottom - oldTop;
            if (width != oldWidth || height != oldHeight) {
                updateDepthWallpaperVisibility();
            }
        });

        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        if (wm != null) {
            try {
                wm.addOnColorsChangedListener(mWallpaperColorsListener,
                        new Handler(Looper.getMainLooper()));
                mWallpaperColorsListenerRegistered = true;
            } catch (Exception ignored) {
            }
        }
    }

    public static WallpaperDepthUtils getInstance(Context context, ScrimController scrimController) {
        if (instance == null) {
            instance = new WallpaperDepthUtils(context, scrimController);
        }
        return instance;
    }

    public static WallpaperDepthUtils get() {
        return instance;
    }

    public void onUnlockStarted() {
        mUnlocking = true;
        hideDepthWallpaperImmediate();
    }

    public void onUnlockCancelled() {
        mUnlocking = false;
        updateDepthWallpaperVisibility();
    }

    public void onUnlockCompleted() {
        mUnlocking = false;
    }
    
    public void onDozingChanged(boolean dozing) {
        if (mDozing == dozing) {
            return;
        }
        mDozing = dozing;
        if (mDozing) {
            hideDepthWallpaper();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    public void onBouncerShowingChanged(boolean showing) {
        if (mBouncerShowing == showing) {
            return;
        }
        mBouncerShowing = showing;
        if (mBouncerShowing) {
            hideDepthWallpaper();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    public void setDynamicBarExpanded(boolean expanded) {
        if (mDynamicBarExpanded == expanded) return;
        mDynamicBarExpanded = expanded;
        if (expanded) {
            hideDepthWallpaper();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    public void onGlanceableHubShowingChanged(boolean showing) {
        if (mGlanceableHubShowing == showing) {
            return;
        }
        mGlanceableHubShowing = showing;
        if (mGlanceableHubShowing) {
            hideDepthWallpaper();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case WALLPAPER_DEPTH_ENABLED_KEY:
                    mDWallpaperEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_KEY:
                    mPreviousWallpaperPath = mWallpaperSubjectPath;
                    mWallpaperSubjectPath = newValue;
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OPACITY_KEY:
                    int opacity = TunerService.parseInteger(newValue, 100);
                    mDWallOpacity = Math.round(opacity * 2.55f);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_X_KEY:
                    mOffsetX = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_Y_KEY:
                    mOffsetY = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_BOTTOM_FADE_KEY:
                    mBottomFadeDp = TunerService.parseInteger(newValue, DEFAULT_BOTTOM_FADE_DP);
                    applyBottomFade();
                    break;
                case WALLPAPER_DEPTH_FADE_CURVE_KEY:
                    mFadeCurvePercent = TunerService.parseInteger(newValue, DEFAULT_FADE_CURVE_PERCENT);
                    applyFadeCurve();
                    break;
                case WALLPAPER_DEPTH_BOTTOM_INSET_KEY:
                    mBottomInsetDp = TunerService.parseInteger(newValue, DEFAULT_BOTTOM_INSET_DP);
                    applyBottomInset();
                    break;
                default:
                    break;
            }
        }
    };
    
    public void setSubjectAlpha(float subjectAlpha) {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setAlpha(subjectAlpha));
    }

    private void applyBottomFade() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setFadeHeightPx(dpToPx(mBottomFadeDp));
        }
    }

    private void applyBottomInset() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setBottomInsetPx(dpToPx(mBottomInsetDp));
        }
    }

    private void applyFadeCurve() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setFadeCurveExponent(computeFadeCurveExponent());
        }
    }

    private float computeFadeCurveExponent() {
        float percent = MathUtils.constrain(mFadeCurvePercent, 0, 100) / 100f;
        return 0.2f + percent * 0.8f;
    }

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        return Math.round(dp * displayMetrics.density);
    }
    
    public void updateDepthWallpaper() {
        updateDepthWallpaper(false);
    }

    public FrameLayout getDepthWallpaperView() {
        return mLockScreenSubject;
    }

    private boolean isDWallpaperEnabled() {
        return mDWallpaperEnabled && mWallpaperSubjectPath != null
                && !mWallpaperSubjectPath.isEmpty();
    }

    private boolean isLandscape() {
        try {
            WindowManager wm = mContext.getSystemService(WindowManager.class);
            if (wm != null) {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                if (bounds != null && bounds.width() > bounds.height()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Display display = mContext.getDisplay();
            if (display != null) {
                int rotation = display.getRotation();
                if (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        if (mLockScreenSubject != null && mLockScreenSubject.isAttachedToWindow()) {
            int w = mLockScreenSubject.getWidth();
            int h = mLockScreenSubject.getHeight();
            if (w > 0 && h > 0 && w > h) {
                return true;
            }
        }

        return mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean canShowDepthWallpaper() {
        ScrimState currentState = mScrimController.getState();
        MediaViewController mediaViewController = MediaViewController.getOrNull();
        boolean albumArtVisible = mediaViewController != null && mediaViewController.albumArtVisible();
        // Only show on KEYGUARD state when bouncer is NOT showing
        return mLockScreenSubject != null
                && isDWallpaperEnabled()
                && !mDozing
                && !mBouncerShowing
                && !mGlanceableHubShowing
                && !mDynamicBarExpanded
                && !mUnlocking
                && currentState == ScrimState.KEYGUARD
                && !isLandscape()
                && !albumArtVisible;
    }

    public void updateDepthWallpaperVisibility() {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        int subjectVisibility = canShowDepthWallpaper() ? View.VISIBLE : View.GONE;
        if (mLockScreenSubject.getVisibility() == subjectVisibility) return;
        mLockScreenSubject.post(() -> {
            mLockScreenSubject.setVisibility(subjectVisibility);
            if (subjectVisibility == View.VISIBLE) {
                mLockScreenSubject.invalidate();
            }
        });
    }
    
    public void hideDepthWallpaper() {
        if (mLockScreenSubject == null || mLockScreenSubject.getVisibility() == View.GONE) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setVisibility(View.GONE));
    }

    public void hideDepthWallpaperImmediate() {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> {
            mLockScreenSubject.animate().cancel();
            mLockScreenSubject.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        mLockScreenSubject.setVisibility(View.GONE);
                        mLockScreenSubject.setAlpha(1f);
                    })
                    .start();
        });
    }

    public Bitmap getResizedBitmap(Bitmap wallpaperBitmap, float xOffsetDp, float yOffsetDp) {
        Rect displayBounds = mContext.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();

        DisplayMetrics displayMetrics =
                mContext.getResources().getDisplayMetrics();

        int targetWidth = Math.min(displayBounds.width(), displayBounds.height());
        int targetHeight = Math.max(displayBounds.width(), displayBounds.height());

        float xOffsetPx = xOffsetDp * displayMetrics.density;
        float yOffsetPx = yOffsetDp * displayMetrics.density;

        float ratioW = targetWidth
                / (float) wallpaperBitmap.getWidth();
        float ratioH = targetHeight
                / (float) wallpaperBitmap.getHeight();

        int desiredHeight = Math.round(
                Math.max(ratioH, ratioW) * wallpaperBitmap.getHeight());
        int desiredWidth = Math.round(
                Math.max(ratioH, ratioW) * wallpaperBitmap.getWidth());

        desiredHeight = Math.max(desiredHeight, 0);
        desiredWidth = Math.max(desiredWidth, 0);

        Bitmap scaledWallpaperBitmap = Bitmap.createScaledBitmap(
                wallpaperBitmap, desiredWidth, desiredHeight, true);

        int xPixelShift = Math.max(
                (desiredWidth - targetWidth) / 2, 0)
                - Math.round(xOffsetPx);

        int yPixelShift = Math.max(
                (desiredHeight - targetHeight) / 2, 0)
                - Math.round(yOffsetPx);

        int cropWidth = Math.min(
                targetWidth,
                scaledWallpaperBitmap.getWidth() - xPixelShift);

        int cropHeight = Math.min(
                targetHeight,
                scaledWallpaperBitmap.getHeight() - yPixelShift);

        scaledWallpaperBitmap = Bitmap.createBitmap(
                scaledWallpaperBitmap,
                Math.max(xPixelShift, 0),
                Math.max(yPixelShift, 0),
                cropWidth,
                cropHeight);

        return scaledWallpaperBitmap;
    }

    private Bitmap cropToDisplay(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return bitmap;
        }
        Rect displayBounds = mContext
                .getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();
        int targetWidth = Math.min(displayBounds.width(), displayBounds.height());
        int targetHeight = Math.max(displayBounds.width(), displayBounds.height());
        if (targetWidth <= 0 || targetHeight <= 0) {
            return bitmap;
        }

        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        if (wm == null) {
            return bitmap;
        }

        int which = (wm.getWallpaperFile(WallpaperManager.FLAG_LOCK) != null)
                ? WallpaperManager.FLAG_LOCK
                : WallpaperManager.FLAG_SYSTEM;

        try {
            List<Rect> crops = wm.getBitmapCrops(
                    List.of(new Point(targetWidth, targetHeight)), which, false);
            if (crops != null && !crops.isEmpty() && crops.get(0) != null) {
                Rect wallpaperFrame = crops.get(0);
                if (wallpaperFrame.width() > 0 && wallpaperFrame.height() > 0) {
                    float screenRatio = targetWidth / (float) targetHeight;
                    float frameRatio = wallpaperFrame.width() / (float) wallpaperFrame.height();
                    Rect visibleCrop;
                    if (frameRatio > screenRatio) {
                        int visibleW = Math.round(wallpaperFrame.height() * screenRatio);
                        int leftOffset = (wallpaperFrame.width() - visibleW) / 2;
                        visibleCrop = new Rect(
                                wallpaperFrame.left + leftOffset,
                                wallpaperFrame.top,
                                wallpaperFrame.left + leftOffset + visibleW,
                                wallpaperFrame.bottom);
                    } else if (frameRatio < screenRatio) {
                        int visibleH = Math.round(wallpaperFrame.width() / screenRatio);
                        int topOffset = (wallpaperFrame.height() - visibleH) / 2;
                        visibleCrop = new Rect(
                                wallpaperFrame.left,
                                wallpaperFrame.top + topOffset,
                                wallpaperFrame.right,
                                wallpaperFrame.top + topOffset + visibleH);
                    } else {
                        visibleCrop = wallpaperFrame;
                    }

                    int cl = Math.max(0, Math.min(visibleCrop.left, bitmap.getWidth() - 1));
                    int ct = Math.max(0, Math.min(visibleCrop.top, bitmap.getHeight() - 1));
                    int cw = Math.min(visibleCrop.width(), bitmap.getWidth() - cl);
                    int ch = Math.min(visibleCrop.height(), bitmap.getHeight() - ct);
                    if (cw > 0 && ch > 0 && (cl > 0 || ct > 0
                            || cw < bitmap.getWidth() || ch < bitmap.getHeight())) {
                        Bitmap cropped = Bitmap.createBitmap(bitmap, cl, ct, cw, ch);
                        if (cropped != bitmap) {
                            bitmap.recycle();
                            return cropped;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w("WallpaperDepthUtils", "Failed to crop background bitmap to display", e);
        }
        return bitmap;
    }

    public void updateDepthWallpaper(boolean forced) {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        boolean pathChanged = (mPreviousWallpaperPath != null && !mPreviousWallpaperPath.equals(mWallpaperSubjectPath));
        if (!mWallpaperLoaded || pathChanged || forced) {
            Log.d("WallpaperDepthUtils", "updateDepthWallpaper: " + (mWallpaperLoaded || forced ? "update required" : "first load"));
            new LoadWallpaperTask().execute();
            mWallpaperLoaded = true;
            mPreviousWallpaperPath = mWallpaperSubjectPath;
        }
        updateDepthWallpaperVisibility();
    }

    private class LoadWallpaperTask extends AsyncTask<Void, Void, Drawable> {
        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                Log.d("LoadWallpaperTask", "Wallpaper path: " + mWallpaperSubjectPath);
                Bitmap bitmap = BitmapFactory.decodeFile(mWallpaperSubjectPath);
                if (bitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode bitmap from file");
                    return null;
                }
                Bitmap resizedBitmap = getResizedBitmap(bitmap, mOffsetX, mOffsetY);
                if (resizedBitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode resized bitmap from file");
                    return null;
                }
                if (mWallpaperBitmap != null) {
                    mWallpaperBitmap = null;
                }
                mWallpaperBitmap = resizedBitmap;
                Drawable bitmapDrawable = new FadeBottomDrawable(mWallpaperBitmap, dpToPx(mBottomFadeDp));
                ((FadeBottomDrawable) bitmapDrawable).setFadeCurveExponent(computeFadeCurveExponent());
                ((FadeBottomDrawable) bitmapDrawable).setBottomInsetPx(dpToPx(mBottomInsetDp));
                bitmapDrawable.setAlpha(255);

                Drawable backgroundDrawable = null;
                Bitmap bgRaw = loadSystemWallpaperBitmap();

                if (bgRaw != null) {
                    bgRaw = cropToDisplay(bgRaw);
                    Bitmap resizedBg =
                            getResizedBitmap(
                                    bgRaw,
                                    mOffsetX,
                                    mOffsetY);

                    if (resizedBg != null) {
                        if (mBackgroundBitmap != null) {
                            mBackgroundBitmap = null;
                        }

                        mBackgroundBitmap = resizedBg;

                        backgroundDrawable =
                                new BitmapDrawable(
                                        mContext.getResources(),
                                        mBackgroundBitmap);

                        backgroundDrawable.setAlpha(255);
                    }
                }

                return new WallpaperLayers(
                        backgroundDrawable,
                        bitmapDrawable);

            } catch (OutOfMemoryError e) {
                Log.e("LoadWallpaperTask", "Out of memory error", e);
                return null;
            } catch (Exception e) {
                Log.e("LoadWallpaperTask", "Error loading wallpaper", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (drawable == null || mWallpaperBitmap == null) {
                Log.d("LoadWallpaperTask", "decodeFile returned nothing, skipping application of subject as background");
                mWallpaperLoaded = false;
                return;
            }
            if (drawable != null) {
                mLockScreenSubject.setBackground(drawable);
                mLockScreenSubject.getBackground().setAlpha(mDWallOpacity);
                Log.d("LoadWallpaperTask", "Subject Loaded!");
            } else {
                updateDepthWallpaperVisibility();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            mWallpaperBitmap = null;
        }
    }
    
    public void onDetachedFromWindow() {
        mTunerService.removeTunable(mTunable);
        mWallpaperBitmap = null;
    }
}
