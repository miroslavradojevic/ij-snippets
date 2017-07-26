package com.braincadet;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Created by Miroslav on 23-Jul-17.
 */
public class SampleCirc implements PlugIn {

    float r;
    float rMin, rMax;
    float d = 3f;
    float samplingStep = 2f;

    int imageWidth;
    int imageHeight;

    public void run(String s) {

        IJ.log("read image...");

        // Random sample the circles (x,y,r,d)
        rMin = 0.1f * imageWidth;
        rMax = ;

        // Generate random ovals



    }

    private class cRoi {
        public float x;
        public float y;
        public float r;
        public float d;
    }

}
