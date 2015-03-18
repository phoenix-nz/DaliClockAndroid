package org.jwz.daliclock;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

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
public class Font {
    private int char_height;
    private int char_width;
    private int colon_width;
    private int font_number;
    private int[][][][] segments;
    private int[][][] empty_frame;
    private int[][][] empty_colon;
    public static final int numFonts = 8;

    /**
     * Create the static font map depending on font number
     * @param fontNumber, larger == bigger font
     */
    public Font(int fontNumber, Context context){
        InputStream is;
        switch (fontNumber) {
            case 0: is = context.getResources().openRawResource(R.raw.font0); break;
            case 1: is = context.getResources().openRawResource(R.raw.font1); break;
            case 2: is = context.getResources().openRawResource(R.raw.font2); break;
            case 3: is = context.getResources().openRawResource(R.raw.font3); break;
            case 4: is = context.getResources().openRawResource(R.raw.font4); break;
            case 5: is = context.getResources().openRawResource(R.raw.font5); break;
            case 6: is = context.getResources().openRawResource(R.raw.font6); break;
            case 7: is = context.getResources().openRawResource(R.raw.font7); break;
            default: return; //TODO -error handling?
        }
        try {
            Writer writer = new StringWriter();
            int length = is.available();
            char[] buffer = new char[length];
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            reader.read(buffer);
            writer.write(buffer, 0, length);

            // Parse the data into jsonobject to get original data in form of json.
            JSONObject jObject = new JSONObject( writer.toString());
            this.char_height = jObject.getInt("char_height");
            this.char_width = jObject.getInt("char_width");
            this.colon_width = jObject.getInt("colon_width");
            this.font_number = jObject.getInt("font_number");
            //not really necessary, but may avoid screw ups later
            // (when changing fonts)
            assert(this.font_number == fontNumber);


            JSONArray jSegments = jObject.getJSONArray("segments");
            this.segments = new int[12][this.char_height][][];
            for (int i = 0; i < 12; i++) {
                JSONArray jSegment = jSegments.getJSONArray(i);
                //not really necessary, but may avoid screw ups later
                assert(this.char_height == jSegment.length());
                for(int j = 0; j < this.char_height; j++) {
                    JSONArray jBlocks = jSegment.optJSONArray(j);
                    int numBlocks = 0;
                    // NOTE: for some reason jBlocks.length can be MORE than the real number of
                    // arrays inside of it. Thus we double check.
                    for (int k = 0; k < jBlocks.length(); k++) {
                        JSONArray jValues = jBlocks.optJSONArray(k);
                        if (jValues == null) {
                            break;
                        }
                        numBlocks = k + 1;
                    }
                    this.segments[i][j] = new int[numBlocks][2];
                    for(int k = 0; k < jBlocks.length(); k++) {
                        JSONArray jValues = jBlocks.getJSONArray(k);
                        this.segments[i][j][k][0] = jValues.getInt(0);
                        this.segments[i][j][k][1] = jValues.getInt(1);
                    }
                }
            }
        } catch (Exception e) {
            //TODO Error handling?
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.empty_colon = make_empty_frame(true);
        this.empty_frame = make_empty_frame(false);
    }

    public int getFont_number() {
        return font_number;
    }


    public int getChar_height() {
        return char_height;
    }

    public int getChar_width() {
        return char_width;
    }

    public int getColon_width() {
        return colon_width;
    }

    public int[][][] getSegment(int segNo) {
        return segments[segNo];
    }

    public int[][][] getEmpty_frame() {
        return empty_frame;
    }

    public int[][][] getEmpty_colon() {
        return empty_colon;
    }

    private int[][][] make_empty_frame(boolean colonic_p) {
        int cw = (colonic_p ? this.colon_width : this.char_width);
        int ch = this.char_height;
        int mid = Math.round(cw / 2);
        int[][][] frame = new int[ch][1][2];
        for (int y = 0; y < ch; y++) {
            frame[y][0][0] = frame[y][0][1] = mid;
//            frame[y][1][0] = frame[y][1][1] = -1;
        }
        return frame;
    }

    public int[][][] copy_frame( int[][][] oframe) {

        if (oframe == null || oframe.length == 0) { return oframe; }
        int length = oframe.length;
        int[][][] nframe = new int[length][][];   // copy array of lines
        for (int y = 0; y < length; y++) {
            nframe[y] = new int[oframe[y].length][2];
            for(int z = 0; z < oframe[y].length; z++) {
                nframe[y][z][0] = oframe[y][z][0];
                nframe[y][z][0] = oframe[y][z][0];
            }
        }
        return nframe;
    }
}
