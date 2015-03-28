
package org.jwz.daliclock;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
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
public class DaliDreamService extends DreamService {

    DaliClock clock;
    Display display;


    public DaliDreamService() {
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.dream_layout);

        display = getWindowManager().getDefaultDisplay();
        LinearLayout bdiv = (LinearLayout) findViewById(R.id.clockbg);
        SurfaceView canvas = (SurfaceView) findViewById(R.id.canvas);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor settingsEditor = settings.edit();

        settingsEditor.putBoolean("show_date", false);

//        android.os.Debug.waitForDebugger();

        Point size = new Point();
        display.getRealSize(size); //using realSize as we are in fullscreen

        settingsEditor.putInt("width", (int) (0.95 * size.x));
        settingsEditor.putInt("height", (int) (0.95 * size.y));

        settingsEditor.apply();

        if (clock != null) {
            clock.hide();
            clock.changeSettings(settings);
        } else {
            clock = new DaliClock(this, canvas, bdiv, settings);
        }
        clock.show();
    }

//    public void onDreamingStarted() {
//        super.onDreamingStarted();
//
//        clock.show();
//    }

    public void onDreamingStopped() {
        super.onDreamingStopped();

        clock.hide();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        clock.cleanup();
    }

//    public boolean dispatchTouchEvent(MotionEvent event) {
//        super.dispatchTouchEvent(event);
//
//        if(event.getAction() != MotionEvent.ACTION_DOWN) return true;
//
//        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
//        SharedPreferences.Editor settingsEditor = settings.edit();
//
//        // The clock will reset show_date itself after 2 seconds.
//        // thus we need to check if the clock is still showing the date from last time.
//        if(clock.showingDate()) {
//            settingsEditor.putBoolean("show_date", false);
//        } else {
//            settingsEditor.putBoolean("show_date", true);
//        }
//
//        Point size = new Point();
//        display.getRealSize(size); //using RealSize as we are in fullscreen
//
//        settingsEditor.putInt("width", (int) (0.95 * size.x));
//        settingsEditor.putInt("height", (int) (0.95 * size.y));
//
//        settingsEditor.apply();
//        clock.changeSettings(settings);
//        return true;
//    }
}

