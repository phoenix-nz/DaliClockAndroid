package org.jwz.daliclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;

import java.util.Calendar;

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
public class DaliClock {
    private SurfaceView surfaceView;
    private SurfaceHolder canvasHolder;
    private LinearLayout clockbg;
    private Font font;
    private Context context;
    private boolean shown_p = false;
    private float[] fg_hsv = {200, (float) 0.4, (float) 1.0};
    private int ctx_fillStyle;
    private int bg_fillStyle;
    private float[] bg_hsv = {128, (float) 1.0, (float) 0.4};
    private Runnable color_timer_fn;
    private Handler color_timer_handler;
    private Runnable clock_timer_fn;
    private Handler clock_timer_handler;
    private SharedPreferences newSettings;
    private int date_length;



    private int clock_freq = 10;
    private int color_freq = 12;
    private boolean color_cycle = false;
    private int width;
    private int height;
    private String time_mode = "";
    private int orientation;
    private boolean vp_scaling_p;
    private int debug_digit = -1;
    private String date_mode = "";
    private boolean twelve_hour_p;
    private boolean show_date_p;
    private int[][][][] orig_frames;
    private int[] orig_digits;
    private int[][][][] current_frames;
    private int[][][][] target_frames;
    private int[] target_digits;
    private int[] canvas_size = new int[2];
    private int displayed_digits;
    private int last_secs = -1;
    private int current_msecs;

    public DaliClock(Context context) {
        this.context = context;
    }

    public void setup(SurfaceView canvas_element, LinearLayout background_element, SharedPreferences settings) {
        this.surfaceView = canvas_element;
        this.clockbg = background_element;
//        this.fonts   = fonts;

        this.fg_hsv[0] += Math.floor(Math.random()*360);
        this.bg_hsv[0] += Math.floor(Math.random()*360);

        this.ctx_fillStyle = Color.HSVToColor(this.fg_hsv);

        this.bg_fillStyle = Color.HSVToColor(this.bg_hsv);
        this.clockbg.setBackgroundColor(this.bg_fillStyle);

        this.canvasHolder = this.surfaceView.getHolder();


        this.changeSettings(settings);
    }

    /**
     * For setup tasks that have to happen each time the window becomes visible.
     */
    public void show() {
        if (this.shown_p) return;
            this.shown_p = true;

        // Create the color timer.
        color_timer_fn = new Runnable() {
            @Override
            public void run() {
                if(shown_p) color_timer();
            }
        };
        color_timer_handler = new Handler();


        // Create the clock timer.
        clock_timer_fn = new Runnable() {
            @Override
            public void run() {
                if(shown_p) clock_timer();
            }
        };
        clock_timer_handler = new Handler();

        this.canvasHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                color_timer_handler.post(color_timer_fn);
                clock_timer_handler.post(clock_timer_fn);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor settingsEditor = settings.edit();


                Rect rect = holder.getSurfaceFrame();
                settingsEditor.putInt("width", (int) (0.95 * rect.width()));
                settingsEditor.putInt("height", (int) (0.95 * rect.height()));

                settingsEditor.apply();

                changeSettings(settings);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                hide();
            }
        });

    }

    /**
     * Tasks that have to happen each time the window is hidden.
     */
    public void hide() {
        if (!this.shown_p) return;
        this.shown_p = false;

        if (this.clock_timer_fn != null) {
            this.clock_timer_handler = null;
            this.clock_timer_fn = null;
        }
        if (this.color_timer_fn != null) {
            this.color_timer_handler = null;
            this.color_timer_fn = null;
        }
    }

    /**
     * About to exit.
     */
    public void cleanup() {
        this.hide();
    }

    private void clock_timer() {

        Canvas canvas = canvasHolder.lockCanvas();
        if(canvas != null) {
            if(this.vp_scaling_p) {
                canvas.scale((float)0.5, (float)0.5);
            }
            this.tick_sequence(canvas);
            this.draw_clock(canvas);
            canvasHolder.unlockCanvasAndPost(canvas);
        }

        if(this.show_date_p) this.date_length -= this.clock_freq;
        if(this.date_length < 0) this.show_date_p = false;

        // Re-trigger our timer.
        this.clock_timer_handler.postDelayed(this.clock_timer_fn, this.clock_freq);
    }

    private void color_timer() {
        // cps == 0 means don't cycle colors. but the timer still goes off
        // at least once a second in case cps has changed.
        int when = this.color_freq;
        if (when > 0)
            this.tick_colors();
        else
            when = 2000;

        this.color_timer_handler.postDelayed(this.color_timer_fn, when);
    }

    private void tick_colors() {
        this.ctx_fillStyle = Color.HSVToColor(this.fg_hsv);

        this.bg_fillStyle = Color.HSVToColor(this.bg_hsv);

        this.clockbg.setBackgroundColor(this.bg_fillStyle);

        this.fg_hsv[0]++;
        if (this.fg_hsv[0] >= 360) { this.fg_hsv[0] -= 360; }

        this.bg_hsv[0] += 0.91;
        if (this.bg_hsv[0] >= 360) { this.bg_hsv[0] -= 360; }
    }

    /**
     *Change display settings at next second-tick.
     */
    public void changeSettings(SharedPreferences settings) {
        // We can process these immediately
        if (settings != null) {
            int fps = Integer.parseInt(settings.getString("target_frame_rate","10"));
            this.clock_freq = (int) Math.round (1000.0 / fps);

            this.color_cycle = settings.getBoolean("color_cycle", false);
            if(this.color_cycle) {
                int cps = Integer.parseInt(settings.getString("color_cycle_speed", "12"));
                this.color_freq = (int) Math.round(1000.0 / cps);
            } else {
                this.color_freq = 0;

                this.bg_fillStyle = Color.argb(255,0,0,0);
                this.ctx_fillStyle = Color.argb(255,255,255,255);
            }

        }

        if (this.clock_freq <= 0) this.clock_freq = 1;
        if (this.color_freq < 0) this.color_freq = 1;

        // If the clock is hidden, we can process everything immediately.
        if (!this.shown_p) this.settings_changed(settings);
        else {
            this.newSettings = settings;
        }
    }

    /**
     * Called at the start of each sequence if the swChangeSettings == true.
     * All settings changes are delayed until the second-tick.
     * The settings object contains:
     *
     *    width		size of clock display area
     *    height		size of clock display area
     *    orientation	'up' | 'left' | 'right' | 'down'
     *    time_mode		'HHMMSS' | 'HHMM' | 'SS'
     *    date_mode		'MMDDYY' | 'DDMMYY' | 'YYMMDD'
     *    twelve_hour_p	boolean, whether to display 12 or 24-hour time
     *    show_date_p	boolean, whether to display date instead of time
     *    fps		integer (frames per second)
     *    cps		integer (color changes per second)
     *    vp_scaling_p	whether surfaceView scaling works for antialiasing
     */
    private void settings_changed(SharedPreferences settings) {

        // Changes to some settings require tearing down and rebuilding
        // the clock.  Changes to others can be animated normally.
        boolean reset_p = ( settings      == null ||
                        this.width        != settings.getInt("width", 100)  ||
                        this.height       != settings.getInt("height",100)  ||
                        !this.time_mode.equals(settings.getString("time_display", "HHMMSS")) ||
                        this.twelve_hour_p != settings.getBoolean("time_12h", false) ||
                        this.vp_scaling_p != settings.getBoolean("aaliasing",false));// ||
                        //this.show_date_p  != settings.getBoolean("show_date", false);

        if(settings != null) {
            this.date_mode = settings.getString("date_display", "DDMMYY");
            this.width = settings.getInt("width", 100);
            this.height = settings.getInt("height", 100);
            this.time_mode = settings.getString("time_display", "HHMMSS");
            this.vp_scaling_p = settings.getBoolean("aaliasing", false);
            this.show_date_p = settings.getBoolean("show_date", false);
            this.debug_digit = settings.getInt("debug_digit", -1);
            this.twelve_hour_p = settings.getBoolean("time_12h", false);
        }
        // store for how long we will show the date
        if(this.show_date_p) this.date_length = 2000;

        if (reset_p) this.clock_reset();
    }

    /** Reset the animation when the settings (number of digits, orientation)
     *  has changed.  We have to start over since the resolution is different.
     */
    private void clock_reset() {

        this.pick_font_size();


        this.orig_frames = new int[8][][][];  // what was there
        this.orig_digits = new int[8];  // what was there
        this.current_frames = new int[8][][][];  // current intermediate animation
        this.target_frames = new int[8][][][];  // where we are going
        this.target_digits = new int[8];  // where we are going

        for (int i = 0; i < this.current_frames.length; i++) {
            boolean colonic_p = (i == 2 || i == 5);
            int[][][] empty = (colonic_p ? this.font.getEmpty_colon() : this.font.getEmpty_frame());
            this.orig_frames[i] = empty;
            this.orig_digits[i] = -1;
            this.target_frames[i] = empty;
            this.current_frames[i] = font.copy_frame(empty);
        }

        // Rotation is done by the framework

        // And now set the CSS position and size of the surfaceView
        // (not the same thing as size of the surfaceView's frame buffer).
        //
        //int width  = ( this.width);   // size of the framebuffer
        //int height = ( this.height);

        int nn, cc;

        switch (this.time_mode) {
            case "SS":   nn = 2; cc = 0; break;
            case "HHMM": nn = 4; cc = 1; break;
            default:     nn = 6; cc = 2; break;
        }

        this.displayed_digits = nn + cc;

        //if (this.vp_scaling_p) {   // was doubled, for anti-aliasing
            // width  /= 2;
            // height /= 2;
        //    float r  = (float) height / (float) width;
        //    width  = (int) Math.floor(this.width);
        //    height = (int) Math.floor(width * r);
        //}

        //int x = (this.width  - width)  / 2;
        //int y = (this.height - height) / 2;

        //this.surfaceView.offsetLeftAndRight(x);
        //this.surfaceView.offsetTopAndBottom(y);
        //this.surfaceView.setZOrderOnTop(true);
        //this.canvasHolder.setFixedSize(width,height);
    }

    /** Find the largest font that fits in the surfaceView given the current settings
     * (number of digits and orientation).
     */
    private void pick_font_size() {

        int nn, cc;

        switch (this.time_mode) {
            case "SS":   nn = 2; cc = 0; break;
            case "HHMM": nn = 4; cc = 1; break;
            default:     nn = 6; cc = 2; break;
        }

        int width  = this.width;
        int height = this.height;

        if (this.vp_scaling_p) {   // double it, for anti-aliasing
            width <<= 1;
            height <<= 1;
        }

        if (this.orientation == LinearLayout.VERTICAL) {
            int swap = width; width = height; height = swap;
        }

        for (int i = Font.numFonts-1; i >= 0; i--) {
            Font font = new Font(i, this.context);
            int w = (font.getChar_width() * nn) + (font.getColon_width() * cc);
            int h = font.getChar_height();

            if ((w <= width && h <= height) || i == 0) {
                this.font          = font;
                this.canvas_size[0] = w;
                this.canvas_size[1] = h;
                return;
            }
        }
    }

// Gets the current wall clock and formats the display accordingly.
//
    private void fill_target_digits(Calendar date) {

        int h = date.get(Calendar.HOUR_OF_DAY);
        int m = date.get(Calendar.MINUTE);
        int s = date.get(Calendar.SECOND);
        int D = date.get(Calendar.DAY_OF_MONTH);
        int M = date.get(Calendar.MONTH);
        int Y = date.get(Calendar.YEAR) % 100;

        if (this.twelve_hour_p) {
            if (h > 12) { h -= 12; }
            else if (h == 0) { h = 12; }
        }

        for (int i = 0; i < this.target_digits.length; i++) {
            this.target_digits[i] = -1;
        }

        if (this.debug_digit != -1) {
            if (this.debug_digit < 0 || this.debug_digit > 11)
                this.debug_digit = -1;
            this.target_digits[0] = this.target_digits[1] = this.target_digits[3] = this.target_digits[4] =
                            this.target_digits[6] = this.target_digits[7] = this.debug_digit;
            this.debug_digit = -1;

        } else if (!this.show_date_p) {

            switch (this.time_mode) {
                case "SS":
                    this.target_digits[0] = (s / 10);
                    this.target_digits[1] = (s % 10);
                    break;
                case "HHMM":
                    this.target_digits[0] = (h / 10);
                    this.target_digits[1] = (h % 10);
                    this.target_digits[2] =	10;  // colon
                    this.target_digits[3] = (m / 10);
                    this.target_digits[4] =  (m % 10);
                    if (this.twelve_hour_p && this.target_digits[0] == 0) {
                        this.target_digits[0] = -1;
                    }
                    break;
                default:
                    this.target_digits[0] = (h / 10);
                    this.target_digits[1] = (h % 10);
                    this.target_digits[2] =	10;  // colon
                    this.target_digits[3] = (m / 10);
                    this.target_digits[4] = (m % 10);
                    this.target_digits[5] =	10;  // colon
                    this.target_digits[6] = (s / 10);
                    this.target_digits[7] = (s % 10);
                    if (this.twelve_hour_p && this.target_digits[0] == 0) {
                        this.target_digits[0] = -1;
                    }
                    break;
            }
        } else {  // date mode

            switch (this.date_mode) {
                case "MMDDYY":
                    switch (this.time_mode) {
                        case "SS":
                            this.target_digits[0] = (D / 10);
                            this.target_digits[1] = (D % 10);
                            break;
                        case "HHMM":
                            this.target_digits[0] = (M / 10);
                            this.target_digits[1] = (M % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (D / 10);
                            this.target_digits[4] = (D % 10);
                            break;
                        default:  // HHMMSS
                            this.target_digits[0] = (M / 10);
                            this.target_digits[1] = (M % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (D / 10);
                            this.target_digits[4] = (D % 10);
                            this.target_digits[5] =	11;  // dash
                            this.target_digits[6] = (Y / 10);
                            this.target_digits[7] = (Y % 10);
                            break;
                    }
                    break;
                case "DDMMYY":
                    switch (this.time_mode) {
                        case "SS":
                            this.target_digits[0] = (D / 10);
                            this.target_digits[1] = (D % 10);
                            break;
                        case "HHMM":
                            this.target_digits[0] = (D / 10);
                            this.target_digits[1] = (D % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (M / 10);
                            this.target_digits[4] = (M % 10);
                            break;
                        default:  // HHMMSS
                            this.target_digits[0] = (D / 10);
                            this.target_digits[1] = (D % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (M / 10);
                            this.target_digits[4] = (M % 10);
                            this.target_digits[5] =	11;  // dash
                            this.target_digits[6] = (Y / 10);
                            this.target_digits[7] = (Y % 10);
                            break;
                    }
                    break;
                default:
                    switch (this.time_mode) {
                        case "SS":
                            this.target_digits[0] = (D / 10);
                            this.target_digits[1] = (D % 10);
                            break;
                        case "HHMM":
                            this.target_digits[0] = (M / 10);
                            this.target_digits[1] = (M % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (D / 10);
                            this.target_digits[4] = (D % 10);
                            break;
                        default:  // HHMMSS
                            this.target_digits[0] = (Y / 10);
                            this.target_digits[1] = (Y % 10);
                            this.target_digits[2] =	11;  // dash
                            this.target_digits[3] = (M / 10);
                            this.target_digits[4] = (M % 10);
                            this.target_digits[5] =	11;  // dash
                            this.target_digits[6] = (D / 10);
                            this.target_digits[7] = (D % 10);
                            break;
                    }
                    break;
            }
        }
    }

    private void draw_frame( Canvas canvas, int[][][] frame, int x, int y, Paint paint) {
        int ch = this.font.getChar_height();
        for (int py = 0; py < ch; py++)
        {
            int[][] line  = frame[py];

            for(int px = 0; px < line.length; px++) {
                canvas.drawRect(x + line[px][0], y + py, x + line[px][1], y + py + 1, paint);
            }
        }
    }

    /** The second has ticked: we need a new set of digits to march toward.
     */
    public void start_sequence(Canvas canvas, Calendar date) {

       if (this.newSettings != null) {
           this.settings_changed(this.newSettings);
           this.newSettings = null;
       }

        // Move the (old) current_frames into the (new) orig_frames,
        // since that's what's on the screen now.
        //
        for (int i = 0; i < this.current_frames.length; i++) {
            this.orig_frames[i] = this.current_frames[i];
            this.orig_digits[i] = this.target_digits[i];
        }

        // generate new target_digits
        this.fill_target_digits (date);

        // Fill the (new) target_frames from the (new) target_digits.
        for (int i = 0; i < this.target_frames.length; i++) {
            boolean colonic_p = (i == 2 || i == 5);
            int[][][] empty = (colonic_p ? this.font.getEmpty_colon() : this.font.getEmpty_frame());
            int[][][] frame = (this.target_digits[i] == -1
                    ? empty
                    : this.font.getSegment(this.target_digits[i]));
            this.target_frames[i] = frame;
        }

        this.draw_clock(canvas);
    }


    private void one_step(int[][][] orig, int[][][] curr, int[][][] target, int msecs) {

        int ch = this.font.getChar_height();
        double frac = msecs / 1000.0;

        for (int i = 0; i < ch; i++) {
            int[][] oline = orig[i];
            int[][] tline = target[i];
            int osegs = oline.length;
            int tsegs = tline.length;

            int segs = (osegs > tsegs ? osegs : tsegs);

            // orig and target might have different numbers of segments.
            // current ends up with the maximal number.
            curr[i] = new int[segs][2];
            int[][] cline = curr[i];

            for (int j = 0; j < segs; j++) {
                int[] oseg = oline[0];
                if(j>0 && osegs>1)
                    oseg = oline[j];

                int[] cseg = cline[j];

                int[] tseg = tline[0];
                if(j>0 && tsegs>1)
                    tseg = tline[j];

                cseg[0] = (int) (oseg[0] + Math.round (frac * (tseg[0] - oseg[0])));
                cseg[1] = (int) (oseg[1] + Math.round (frac * (tseg[1] - oseg[1])));
            }
        }
    }

    /** Compute the current animation state of each digit into target_frames
     *  according to our current position within the current wall-clock second.
     */
    private void tick_sequence(Canvas canvas) {

        Calendar now   = Calendar.getInstance();
        int secs  = now.get(Calendar.SECOND);
        int msecs = now.get(Calendar.MILLISECOND);    // msec position within this second

        if (this.last_secs == -1) {
            this.last_secs = secs;   // fading in!
        } else if (secs != this.last_secs) {
            // End of the animation sequence; fill target_frames with the
            // digits of the current time.
            this.start_sequence(canvas, now);
            this.last_secs = secs;
        }

        // Linger for about 1/10th second at the end of each cycle.
        msecs *= 1.2;
        if (msecs > 1000) msecs = 1000;

        // Construct current_frames by interpolating between
        // orig_frames and target_frames.
        //
        for (int i = 0; i < this.orig_frames.length; i++) {
            this.one_step (this.orig_frames[i],
                    this.current_frames[i],
                    this.target_frames[i],
                    msecs);
        }

        this.current_msecs = msecs;
    }

    /** left_offset is so that the clock can be centered in the window
     *  when the leftmost digit is hidden (in 12-hour mode when the hour
     *  is 1-9).  When the hour rolls over from 9 to 10, or from 12 to 1,
     * we animate the transition to keep the digits centered.
     */
    private int compute_left_offset() {
        int left_offset;
        if (this.target_digits[0] == -1 &&	 // Fading in to no digit
                this.orig_digits[1] == -1)
            left_offset = this.font.getChar_width() / 2;
        else if (this.target_digits[0] != -1 && // Fading in to a digit
                this.orig_digits[1] == -1)
            left_offset = 0;
        else if (this.orig_digits[0] != -1 &&	 // Fading out from digit
                this.target_digits[1] == -1)
            left_offset = 0;
        else if (this.orig_digits[0] == -1 &&	 // Fading out from no digit
                this.target_digits[1] == -1)
            left_offset = this.font.getChar_width() / 2;
        else if (this.orig_digits[0] == -1 &&	 // Anim no digit to digit.
                this.target_digits[0] != -1)
            left_offset = (this.font.getChar_width() * (1000 - this.current_msecs) / 2000);
        else if (this.orig_digits[0] != -1 &&	 // Anim digit to no digit.
                this.target_digits[0] == -1)
            left_offset = this.font.getChar_width() * this.current_msecs / 2000;
        else if (this.target_digits[0] == -1)	 // No anim, no digit.
            left_offset = this.font.getChar_width() / 2;
        else						 // No anim, digit.
            left_offset = 0;

        if(this.vp_scaling_p)
            left_offset += ((this.width<<1) - this.canvas_size[0])>>1;
        else
            left_offset += (this.width - this.canvas_size[0])>>1;
        return left_offset;
    }

    /** Render the current animation state of each digit.
     */
    private void draw_clock(Canvas canvas) {

        int x = this.compute_left_offset();
        int y;
        if(this.vp_scaling_p) {
            y = ((this.height << 1) - this.canvas_size[1])>>1;
        }
        else
            y = (this.height - this.canvas_size[1])>>1;

//        canvas.drawARGB(255,0,0,0);
        canvas.drawColor(this.bg_fillStyle);
        Paint paint = new Paint();
        paint.setColor(this.ctx_fillStyle);
        for (int i = 0; i < this.displayed_digits; i++) {
            this.draw_frame (canvas, this.current_frames[i], x, y, paint);
            boolean colonic_p = (i == 2 || i == 5);
            x += (colonic_p ? this.font.getColon_width() : this.font.getChar_width());
        }
    }

}