package org.jwz.daliclock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;


/* Dali Clock - a melting digital clock for Palm WebOS.
 * Copyright (c) 1991-2010 Jamie Zawinski <jwz@jwz.org>
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation.  No representations are made about the suitability of this
 * software for any purpose.  It is provided "as is" without express or
 * implied warranty.
 *
 * Ported to Android 2015 by Robin Müller-Cajar <robinmc@mailbox.org>
 */
public class DaliClockActivity extends Activity {

    private DaliClock clock;
    private Display display;

    // detect zoom in/out
    private ScaleGestureDetector scaleGestureDetector;
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dream_layout);

        display = getWindowManager().getDefaultDisplay();
        LinearLayout bdiv = (LinearLayout) findViewById(R.id.clockbg);
        SurfaceView canvas = (SurfaceView) findViewById(R.id.canvas);
        this.settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor settingsEditor = settings.edit();

        settingsEditor.putBoolean("show_date", false);

//        android.os.Debug.waitForDebugger();

        Point size = new Point();
        display.getRealSize(size); //using realSize as we are in fullscreen

        settingsEditor.putInt("width", (int) (0.95 * size.x));
        settingsEditor.putInt("height", (int) (0.95 * size.y));

        settingsEditor.apply();


        bdiv.setOnTouchListener( this.mTouchListener );
        bdiv.setOnClickListener( this.mClickListener );
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        if (clock != null) {
            clock.hide();
            clock.changeSettings(settings);
        } else {
            clock = new DaliClock(this, canvas, bdiv, settings);
        }
        clock.show();

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }


    View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {

            // the user seems to want to zoom, pass the event on to our scale detector
            if( event.getPointerCount() == 2 ) {
                scaleGestureDetector.onTouchEvent(event);
                view.invalidate();
                return true;
            }

            return false;

        }
    };

    View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            SharedPreferences.Editor settingsEditor = settings.edit();

            // The clock will reset show_date itself after 2 seconds.
            // thus we need to check if the clock is still showing the date from last time.
            if(clock.showingDate()) {
                settingsEditor.putBoolean("show_date", false);
            } else {
                settingsEditor.putBoolean("show_date", true);
            }

            settingsEditor.apply();
            clock.changeSettings(settings);
        }
    };

    private class ScaleListener extends
            ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            String currTimeDisplay = clock.getTimeDisplay();

            // always just move one step.
            switch(currTimeDisplay) {
                case "SS": {
                    if(scaleFactor > 1) currTimeDisplay = "HHMM";
                    break;
                }
                case "HHMM": {
                    if (scaleFactor > 1) currTimeDisplay = "HHMMSS";
                    else                 currTimeDisplay = "SS";
                    break;
                }
                case "HHMMSS":
                default: {
                    if(scaleFactor < 1) currTimeDisplay = "HHMM";
                    break;
                }
            }


            // check if the settings changed
            String oldTimeDisplay = settings.getString("time_display", currTimeDisplay);
            if(!settings.contains("time_display") || !oldTimeDisplay.equals(currTimeDisplay)){
                SharedPreferences.Editor settingsEditor = settings.edit();
                settingsEditor.putString("time_display", currTimeDisplay);
                settingsEditor.apply();

                clock.changeSettings(settings);
            }

            return true;
        }
    }
}
