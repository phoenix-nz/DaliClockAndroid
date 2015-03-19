package org.jwz.daliclock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.MotionEvent;
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

    DaliClock clock;
    Display display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        bdiv.setOnTouchListener( this.mTouchListener );
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }


    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return false;
        }
    };
}
