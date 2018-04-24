package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Random;

public class PatchExtractor implements PlugIn {

    String annotDir;
    int R = 8;
    int N = 500;
    int gaussBlur = 12;

    private  static final String PATCH_DIR_NAME = "patch";

    @Override
    public void run(String s) {

        GenericDialog gd = new GenericDialog("Extract patches");
        gd.addStringField("annotdir",     Prefs.get("com.braincadet.bacannot.annotdir", annotDir), 50);
        gd.addNumericField("R",   Prefs.get("com.braincadet.bacannot.r", R), 0, 5, "");
        gd.addNumericField("N",   Prefs.get("com.braincadet.bacannot.n", N), 0, 5, "");
        gd.showDialog();
        if (gd.wasCanceled()) return;
        annotDir = gd.getNextString().replace("\"", "");
        Prefs.set("com.braincadet.bacannot.annotdir", annotDir);

        R = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.r", R);

        N = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.n", N);

        System.out.println("annotdir=" + annotDir);
        System.out.println("R=" + R);
        System.out.println("N=" + N);

        File fAnnotDir = new File(annotDir);

        if (fAnnotDir.exists() == false) {
            System.out.println("Enetered annot directory does not exist.");
            return;
        }

        if (fAnnotDir.isDirectory() == false) {
            System.out.println("Enetered annot path is not a directory.");
            return;
        }

        System.out.println("parent = " + fAnnotDir.getParent());

        // read all available annotation images "*.tif"
        File [] files = fAnnotDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".tif");
            }
        });

        if (files.length == 0) {
            System.out.println("No annotations were found.");
            return;
        }

        System.out.println("found " + files.length + " annotations");


        File directory = new File(fAnnotDir.getParent() + File.separator + PATCH_DIR_NAME + File.separator);

        if (! directory.exists()){
            System.out.println("Created patch directory.");
            directory.mkdir(); // use directory.mkdirs(); for subdirs
        }
        else {
            if (directory.listFiles().length > 0) {
                System.out.println("Patch directory contains files.");
                return;
            }
        }

        int countSamples = 1;

        for (File annotImgPath : files) {

            System.out.println("\n" + annotImgPath.getAbsolutePath());

            // read image
            ImagePlus annotImg = new ImagePlus(annotImgPath.getAbsolutePath());
            if (annotImg.getType() == ImagePlus.GRAY8 && Tools.getFileExtension(annotImg.getTitle()).equalsIgnoreCase("TIF")) {
                // if read annot image is 8bit and .tif

                System.out.println(annotImgPath.getAbsolutePath());

                System.out.println("Compute distance map...");
                IJ.run(annotImg, "Distance Map", "");

                System.out.println("");

                annotImg.show();

                byte[] annotImgPixels = (byte[]) annotImg.getProcessor().getPixels();
                float[] annotImgRange = getMinMax(annotImgPixels);
                System.out.println("Range = [ " + annotImgRange[0] + " / " + annotImgRange[1] + " ]");

                if (annotImgRange[1] - annotImgRange[0] > Float.MIN_VALUE) {

                    // compute summed area table
//                    float[] annotIntegralImg = computeIntegralImage(annotImgPixels, annotImg.getWidth(), annotImg.getHeight());
//                    float[] overlapImg = computeSumOverRect(annotIntegralImg, annotImg.getWidth(), annotImg.getHeight(), R);

                    // min-max for sumOverRec
//                    float[] overlapImgRange = getMinMax(overlapImg);
                    //************************************************************
                    // sample positives

                    //cws compute
                    float[] cws = new float[annotImgPixels.length];
                    float weight;
                    int x, y;

                    for (int j = 0; j < cws.length; j++) {
                        x = j % annotImg.getWidth();
                        y = j / annotImg.getWidth();
                        weight = (margin(x, annotImg.getWidth(), R) && margin(y, annotImg.getHeight(), R))? (float) Math.pow(annotImgPixels[j] & 0xff, 2) : 0f;
                        cws[j] = weight + ((j==0)? 0 : cws[j-1]);
                    }

                    int[] smpPos = sampleI(N, cws); // sample positives

                    //************************************************************
                    // sample negatives

                    // invert annotImg


                    //************************************************************
                    Overlay ov = new Overlay();

                    for (int i = 0; i < smpPos.length; i++) {

                        OvalRoi p = new OvalRoi(smpPos[i]%annotImg.getWidth()+.5f-(R/2f), smpPos[i]/annotImg.getWidth()+.5f-(R/2f), R, R);
                        p.setFillColor(new Color(1f,0f,0f,0.1f));
                        ov.add(p);

//                        PointRoi pp = new PointRoi(smpPos[i]%annotImg.getWidth()+.5f, smpPos[i]/annotImg.getWidth()+.5f);
//                        ov.add(pp);
                    }

                    annotImg.setOverlay(ov);
                    annotImg.show();

                    ImagePlus overlapImgPlus = new ImagePlus(annotImgPath.getAbsolutePath()); //new ImagePlus("sumOver d2=" +IJ.d2s(R,0), new ByteProcessor(annotImg.getWidth(), annotImg.getHeight(), annotImgPixels));
                    overlapImgPlus.setOverlay(ov);
                    overlapImgPlus.show();
                    //************************************************************

                    //

                }

//                float annotFuzzyMin = Float.POSITIVE_INFINITY, annotFuzzyMax = Float.NEGATIVE_INFINITY;
//                for (int i = 0; i < annotImgPixels.length; i++) {
//                    if ((annotImgPixels[i] & 0xff) < annotFuzzyMin) {
//                        annotFuzzyMin = annotImgPixels[i] & 0xff;
//                    }
//                    if ((annotImgPixels[i] & 0xff) > annotFuzzyMax) {
//                        annotFuzzyMax = annotImgPixels[i] & 0xff;
//                    }
//                }

            }

            // sample negatives with the inverted image

        }
    }

    private static boolean margin(int x, int W, int marginVal) {
        return (x>=marginVal && x<W-marginVal)? true : false ;
    }

    private static ImageStack getPatchArrays(ImagePlus inImg, int atX, int atY, int atR) {

        if (atX-atR >= 0 && atX+atR < inImg.getWidth() && atY-atR >= 0 && atY+atR < inImg.getHeight()) {

            ImageStack isOut = new ImageStack((2*atR+1), (2*atR+1));

            for (int i=0; i < inImg.getStack().getSize(); i++) {
                byte[] layer = new byte[(2*atR+1)*(2*atR+1)];
                int j = 0;
                for (int dX = -atR; dX <= atR; dX++) {
                    for (int dY = -atR; dY <= atR; dY++) {
                        byte[] aa = (byte[])inImg.getStack().getProcessor(i+1).getPixels();
                        layer[j] = aa[(atY+dY) * inImg.getWidth() + (atX+dX)];
                        j++;
                    }
                }

                isOut.addSlice(inImg.getTitle() + ",x=" + IJ.d2s(atX, 0) + ",y=" + IJ.d2s(atY, 0) + ",R=" + IJ.d2s(atR,0),
                        new ByteProcessor((2*atR+1), (2*atR+1), layer));

            }

            return isOut;
        }

        return null;

    }

    private float[] getMinMax(byte[] I){

        float[] range = new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};

        for (int i = 0; i < I.length; i++) {
            if ((I[i] & 0xff) < range[0]) {
                range[0] = I[i] & 0xff;
            }
            if ((I[i] & 0xff) > range[1]) {
                range[1] = I[i] & 0xff;
            }
        }

        return range;

    }

    private float[] getMinMax(float[] I) {

        float[] range = new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};

        for (int i = 0; i < I.length; i++) {
            if (I[i] < range[0]) {
                range[0] = I[i];
            }
            if (I[i] > range[1]) {
                range[1] = I[i];
            }
        }

        return range;
    }

    private float[] computeSumOverRect(float[] I, int W, int H, int d2) {

        float[] Iout = new float[I.length];

        int iCurr, i00, i10, i01, i11;

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                iCurr = y * W + x; // i(x,y)
                i00 = wrap(y-d2, 0, H-1) * W + wrap(x-d2, 0, W-1);
                i10 = wrap(y-d2, 0, H-1) * W + wrap(x+d2, 0, W-1);
                i01 = wrap(y+d2, 0, H-1) * W + wrap(x-d2, 0, W-1);
                i11 = wrap(y+d2, 0, H-1) * W + wrap(x+d2, 0, W-1);
                Iout[iCurr] = I[i11] + I[i00] - I[i10] - I[i01];
                Iout[iCurr] /= Math.pow(2*d2+1, 2);
            }
        }

        return Iout;

    }

    private float[] computeIntegralImage(byte[] I, int W, int H) {

        float[] Iout = new float[I.length];

        int iCurr, iUp, iLeft, iUpLeft;

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                iCurr = y * W + x; // i(x,y)
                iUp = wrap(y-1, 0, H-1) * W + x; // I(x,y-1)
                iLeft = y * W + wrap(x-1, 0, W-1); // I(x-1,y)
                iUpLeft = wrap(y-1, 0, H-1) * W + wrap(x-1, 0, W-1); // I(x-1,y-1)
                Iout[iCurr] = ( (I[iCurr] & 0xff)/255f ) + Iout[iUp] + Iout[iLeft] - Iout[iUpLeft];
            }
        }

        return Iout;
    }

    private static int wrap(int i, int iMin, int iMax){
        return ((i<iMin)?iMin:( (i>iMax)?iMax:i ));
    }

    private int[] sampleI(int nsamples, float[] csw) { // , int[][] tosample

        int[] out = new int[nsamples];//[tosample[0].length];

        float totalmass = csw[csw.length - 1];

        // use systematic resampling, Beyond Kalman Filtering, Ristic et al.
        int i = 0;

        float u1 = (totalmass / (float) nsamples) * new Random().nextFloat();

        for (int j = 0; j < nsamples; j++) {

            float uj = u1 + j * (totalmass / (float) nsamples);

            while (uj > csw[i]) {
                i++;
            }

//            System.out.println("out["+j+"]="+out[j]);

            out[j] = i;//tosample[i];//[k];[k]

//            for (int k = 0; k < tosample[i].length; k++) {
//                out[j][k] = tosample[i][k];
//            }

        }


        return out;
    }

}