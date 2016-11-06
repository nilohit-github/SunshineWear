/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class MyWatchFace extends CanvasWatchFaceService  {
    private static final String TAG = "MyWatchFace";

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String WEARABLE_DATA_PATH = "/wearable_data";
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }



    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }


    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {
        private Typeface WATCH_TEXT_TYPEFACE = Typeface.create( Typeface.SERIF, Typeface.NORMAL );
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */

        private Paint mHourPaint;
        private Paint mHighTemperaturePaint;
        private Paint mLowTemperaturePaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
       // private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;

        GoogleApiClient mGoogleApiClient ;
       // private int mBackgroundColor = ResourcesCompat.getColor(getResources(), R.color.lightBlue, null);

        private int mTextColor = Color.parseColor( "white" );

        Paint mBackgroundPaint;
        Bitmap mWeatherIconBitmap;
        String mHighTemp;
        String mLowTemp;

        ///
        private long mUpdateRateMs = 1000;

        private long DEFAULT_UPDATE_RATE_MS=500;

        private Time mDisplayTime;
        private static final String DATE_FORMAT = "%02d.%02d.%d";

        private Paint mBackgroundColorPaint;
        private Paint mTextColorPaint;
        private Rect bounds;
        Paint mWeatherIconPaint;
        Paint mDatePaint;

        private Calendar mCalendar;
        private SimpleDateFormat mDayOfWeekFormat;
        private Date mDate;


        private boolean mLowBitAmbient;
        private boolean mAmbient;
        private boolean mBurnInProtection;


        private boolean mHasTimeZoneReceiverBeenRegistered = false;
        private boolean mIsInMuteMode;
        private boolean mIsLowBitAmbient;
        private boolean mRound;

        //Watch face drawn without cuts at the corners
        private float mXOffset;
        private float mYOffset;
        private float mCenterX;
        private float mCenterY;
        //Resources resources = MyWatchFace.this.getResources();





        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            initWeatherDetails(0,0,R.drawable.ic_clear);

            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
            initBackground();
            initDisplayText();
            mDisplayTime = new Time();
            mCalendar = Calendar.getInstance();
            mDate = new Date();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

                       /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

       

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change.
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);*/

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.

            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }*/
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds)
        {
            Log.d("Ondraw::", "inside on draw");
            super.onDraw(canvas, bounds);
           // mDisplayTime.clear( TimeZone.getDefault().getID() );
            mDisplayTime.setToNow();


            //background color

            canvas.drawRect( 0, 0, bounds.width(), bounds.height(), mBackgroundPaint );


            //time

            String timeText = getHourString() + ":" + String.format( "%02d", mDisplayTime.minute);

            if( isInAmbientMode() || mIsInMuteMode )
            {

                timeText += ( mDisplayTime.hour < 12 ) ? "AM" : "PM";

            }
            else
            {

                timeText += String.format( ":%02d", mDisplayTime.second);

            }
            mXOffset = getResources().getDimension( R.dimen.x_offset );
            mYOffset = getResources().getDimension( R.dimen.y_offset );
            canvas.drawText( timeText, mXOffset, mYOffset+20, mTextColorPaint );


            //date

            String dateText = String.format(DATE_FORMAT, mDisplayTime.monthDay,(mDisplayTime.month + 1), mDisplayTime.year);

            long now = System.currentTimeMillis();

            mCalendar.setTimeInMillis(now);

            mDate.setTime(now);

            float y = getTextHeight(dateText, mTextColorPaint) + mYOffset +10;

            y += getTextHeight(dateText, mTextColorPaint);
            mXOffset = getResources().getDimension( R.dimen.x_offset );
            float x =mXOffset;

            mDayOfWeekFormat = new SimpleDateFormat("EEE, dd MMM", Locale.getDefault());

            mDayOfWeekFormat.setCalendar(mCalendar);
            int thisYear = mCalendar.get(Calendar.YEAR);
            String dayString = mDayOfWeekFormat.format(mDate)+" "+thisYear ;

            x = (bounds.width() - mDatePaint.measureText(dayString)) / 2;

            canvas.drawText(dayString.toUpperCase(), x, y, mDatePaint);


            //temperature

            int dummy = 0;
            if (mAmbient) {
                if (!mLowBitAmbient && !mBurnInProtection) {
                    dummy = 2;
                }
            } else {
                dummy = 1;
            }

            if (dummy > 0) {

                y = getTextHeight(dayString, mDatePaint) + mYOffset +50;

                y += getTextHeight(timeText, mDatePaint);

                x = mXOffset;

                x = (bounds.width() - (mWeatherIconBitmap.getWidth() + 20 + mTextColorPaint.measureText(mHighTemp))) / 2;

                if (dummy == 1)
                {
                    if(!isInAmbientMode())
                        canvas.drawBitmap(mWeatherIconBitmap, x, y, mWeatherIconPaint);

                }
                //   else
                //   {

                //       canvas.drawBitmap(mGrayWeatherIconBitmap, x, y, mWeatherIconPaint);

                //   }

                x += mWeatherIconBitmap.getWidth() + 5;

                y = y + mWeatherIconBitmap.getHeight() / 2;

                canvas.drawText(mHighTemp, x, y - 5, mHighTemperaturePaint);

                y += getTextHeight(mHighTemp, mTextColorPaint);

                canvas.drawText(mLowTemp, x, y + 5, mLowTemperaturePaint);

            }

        }

        private float getTextHeight(String text, Paint paint) {

            Rect rect = new Rect();
            paint.getTextBounds(text, 0, text.length(), rect);
            return rect.height();
        }

        private String getHourString()
        {
            if( mDisplayTime.hour % 12 == 0 )
                return "12";
            else if( mDisplayTime.hour <= 12 )
                return String.valueOf( mDisplayTime.hour );
            else
                return String.valueOf( mDisplayTime.hour - 12 );
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
               // mGoogleApiClient.connect();
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            Log.d("DataChange in::","222");
            DataMap dataMap;
            for (DataEvent event : dataEventBuffer) {

                // Check the data type
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    Log.d("DataChange in::","333");
                    // Check the data path
                    String path = event.getDataItem().getUri().getPath();
                    if (path.equals(WEARABLE_DATA_PATH)) {
                        Log.d("DataChange in::", "444");


                        // dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                        dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                        Log.d("DataChange in::", "555");
                        int high = (int) Math.round(dataMap.getDouble("highTemp"));
                        int low = (int) Math.round(dataMap.getDouble("lowTemp"));

                        int id = (int) dataMap.getLong("weatherId");
                        int icon = utility.getWeatherIcon(id);
                        initWeatherDetails(high, low, icon);
                        invalidate();
                        continue;
                    }

                }
            }


        }




        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.i(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i(TAG, "onConnectionSuspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.i(TAG, "onConnectionFailed");
        }

        private void initBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getResources().getColor(R.color.lightBlue));
           // mBackgroundPaint.setColor( mBackgroundColor );
           //mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
        }

        private void initDisplayText() {
            mTextColorPaint = new Paint();
            mTextColorPaint.setColor( mTextColor );
            mTextColorPaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mTextColorPaint.setAntiAlias( true );
            mTextColorPaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

            mDatePaint = new Paint();
            mDatePaint.setColor(getResources().getColor(R.color.dullWhite));
            mDatePaint.setAntiAlias( true );
            mDatePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mDatePaint.setTextSize( getResources().getDimension( R.dimen.date_text_size ) );

            mLowTemperaturePaint = new Paint();
            mLowTemperaturePaint.setColor(getResources().getColor(R.color.dullWhite));
            mLowTemperaturePaint.setAntiAlias( true );
            mLowTemperaturePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mLowTemperaturePaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

            mHighTemperaturePaint = new Paint();
            mHighTemperaturePaint.setColor( mTextColor );
            mHighTemperaturePaint.setAntiAlias( true );
            mHighTemperaturePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mHighTemperaturePaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

            mWeatherIconPaint = new Paint();

        }

        private void initWeatherDetails(int high, int low, int icon  ){
            Log.d("initWeatherDetails::", String.valueOf(high));
            Log.d("initWeatherDetails::", String.valueOf(low));
            int resID = getResources().getIdentifier("ic_" + icon , "drawable", getPackageName());

            mWeatherIconBitmap = BitmapFactory.decodeResource(getResources(), icon);
           // mWeatherIconBitmap = BitmapFactory.decodeResource(getResources(), resID);

           // mHighTemp = String.format("%3s",String.valueOf(high)) + "° C";
           // mLowTemp = String.format("%3s",String.valueOf(low)) + "° C";
             mHighTemp = String.valueOf(high) + "° F";
             mLowTemp =  String.valueOf(low) + "° F";

        }
    }
}
