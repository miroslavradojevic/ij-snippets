package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import javafx.fxml.FXMLLoader;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.io.*;
import java.util.Random;

public class PatchExtractor implements PlugIn {

    String annotDir;
//    String srcStack;
    int R = 10; // size of the patch
    int N = 100; // number of samples

    private  static final String PATCH_DIR_NAME = "patch";

    @Override
    public void run(String s) {

        GenericDialog gd = new GenericDialog("Extract patches");
        gd.addStringField("annotdir", Prefs.get("com.braincadet.bacannot.annotdir", annotDir), 80);
//        gd.addStringField("srcstack", Prefs.get("com.braincadet.bacannot.srcstack", srcStack), 80);
        gd.addNumericField("R", Prefs.get("com.braincadet.bacannot.r", R), 0, 5, "");
        gd.addNumericField("N", Prefs.get("com.braincadet.bacannot.n", N), 0, 5, "");
        gd.showDialog();

        if (gd.wasCanceled()) return;

        annotDir = gd.getNextString().replace("\"", "");
        Prefs.set("com.braincadet.bacannot.annotdir", annotDir);

//        srcStack = gd.getNextString().replace("\"", "");
//        Prefs.set("com.braincadet.bacannot.srcstack", srcStack);

        R = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.r", R);

        N = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.n", N);

        System.out.println("annotdir=" + annotDir);

        File fAnnotDir = new File(annotDir);

        if (fAnnotDir.exists() == false) {
            System.out.println("Enetered annot directory does not exist.");
            return;
        }

        if (fAnnotDir.isDirectory() == false) {
            System.out.println("Enetered annot path is not a directory.");
            return;
        }

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


        File outDir = new File(fAnnotDir.getParent() + File.separator + PATCH_DIR_NAME + File.separator);

        if (! outDir.exists()){
            outDir.mkdir(); // use directory.mkdirs(); for subdirs
        }
        else {
            if (outDir.listFiles().length > 0) {
                System.out.println("Abort:\tpatch directory contains files.");
                return;
            }
        }

        int countSamples = 0; // count computed training samples over the whole annot directory

        for (File annotImgPath : files) {

            System.out.println("\n" + annotImgPath.getAbsolutePath()); // read annotation image

            // read image
            ImagePlus annotImg = new ImagePlus(annotImgPath.getAbsolutePath());
            int W = annotImg.getWidth();
            int H = annotImg.getHeight();

            if (annotImg.getProperty("Info") == null) {
                System.out.println("Could not find the origin, patch sampling cannot proceed.");
                continue;
            }

            File fOrigin = new File((String)annotImg.getProperty("Info"));
            if (fOrigin.exists() == false) {
                System.out.println("Origin file does not exist."); // maybe check extension etc
                continue;
            }

            // read image being sampled read from the annotation metadata
            ImagePlus originStackImg = new ImagePlus(fOrigin.getAbsolutePath());
            if (originStackImg == null) {
                System.out.println("Origin file could not be read."); // maybe check extension etc
                continue;
            }
            int L = originStackImg.getStackSize();
            System.out.println("L = " + L);

            // process if read annot image is 8bit and .tif
            if (annotImg.getType() == ImagePlus.GRAY8 &&
                    Tools.getFileExtension(annotImg.getTitle()).equalsIgnoreCase("TIF") &&
                            getMinMax((byte[]) annotImg.getProcessor().getPixels()).getInterval() > Float.MIN_VALUE) {

                System.out.println("Computing distance map...");

                IJ.run(annotImg, "Options...", "iterations=1 count=1 black");
                IJ.run(annotImg, "Distance Map", "");

//                System.out.println("BEFORE:");
//                getMinMax((byte[]) annotImg.getProcessor().getPixels()).print();

                minMaxNormalizeByteImage(annotImg);

//                System.out.println("AFTER:");
//                getMinMax((byte[]) annotImg.getProcessor().getPixels()).print();

                byte[] annotImgPixels = (byte[]) annotImg.getProcessor().getPixels(); // get array after distance map transform

                if (getMinMax(annotImgPixels).getInterval() > Float.MIN_VALUE) {

                    // compute summed area table (deprecated approach)
//                    float[] annotIntegralImg = computeIntegralImage(annotImgPixels, W, H);
//                    float[] overlapImg = computeSumOverRect(annotIntegralImg, W, H, R);

                    //************************************************************
                    // sample positives
                    float[] cws = new float[annotImgPixels.length];

                    for (int j = 0; j < cws.length; j++) {
                        float weight = (isInsideMargin(j, W, H, R))? (float) Math.pow(annotImgPixels[j] & 0xff, 2) : 0f;
                        cws[j] = weight + ((j==0)? 0 : cws[j-1]);
                    }

                    // sample locations based on the weights
                    int[] smpPos = sampleI(N, cws);
                    // sampled location class indexes
                    int[] smpTag = new int[smpPos.length];
                    for (int i = 0; i < smpPos.length; i++) smpTag[i] = 1;

                    //************************************************************
                    // sample negatives, invert weight image used to compute cws (min/max invert)
//                    invertByteArray(annotImgPixels);


                    //************************************************************
                    // show overlay
                    Overlay ov = new Overlay();

                    for (int i = 0; i < smpPos.length; i++) {

                        OvalRoi p = new OvalRoi(smpPos[i]%W+.5f-R, smpPos[i]/W+.5f-R, 2*R, 2*R);
                        p.setFillColor(new Color(1f,0f,0f,0.1f));
                        ov.add(p);

                        PointRoi pp = new PointRoi(smpPos[i]%W+.5f, smpPos[i]/W+.5f);
                        ov.add(pp);
                    }

//                    annotImg.setOverlay(ov);
//                    annotImg.show();

//                    ImagePlus overlapImgPlus = new ImagePlus(fOrigin.getAbsolutePath());
//                    overlapImgPlus.show();
                    originStackImg.show();
                    originStackImg.setOverlay(ov);


                    //************************************************************
                    //
                    //

//                    String dd = outDir.getAbsolutePath();
//                    System.out.println(dd);

                    if (true) { // move it before the overlay shown
                        // extract the patches at sampled locations
                        countSamples = patchExtract(
                                smpPos, smpTag, countSamples,
                                W, H, L, R,
//                                annotImgPixels,
                                originStackImg,
                                annotImg.getShortTitle(),
                                outDir
                        );

                    }


                }
                else {
                    System.out.println("distance map had zero range.");
                }
            }
            else {
                System.out.println("image not byte8 or has no tif extension or no annots found.");
            }
        }
    }

    private void minMaxNormalizeByteImage(ImagePlus imp){
        byte[] impArray = (byte[])imp.getProcessor().getPixels();
        Range impRange = getMinMax(impArray);
        for (int i = 0; i < impArray.length; i++) {
             int scaledVal =  (int)((((impArray[i] & 0xff) - impRange.minValue) / impRange.maxValue) * 255f);
             scaledVal = (scaledVal>255)?255:(scaledVal<0)?0:scaledVal;
             impArray[i] = (byte)scaledVal;
        }
    }

    private void invertByteImage(ImagePlus imp){
        invertByteArray((byte[])imp.getProcessor().getPixels());
    }

    private void invertByteArray(byte[] impArray) {
        for (int i = 0; i < impArray.length; i++) {
            int scaledVal = 255 - (impArray[i] & 0xff);
            scaledVal = (scaledVal>255)?255:(scaledVal<0)?0:scaledVal;
            impArray[i] = (byte)scaledVal;
        }
    }

    private static int patchExtract(int[] idxList, int[] classIdx, int startCount,
                                    int W, int H, int L, int R,
//                                    byte[] imgSampled,
                                    ImagePlus originImage,
                                    String annotImgName,
                                    File outDir) {

        String outDirPath = outDir.getAbsolutePath() + File.separator + "patch.log";

        int currPatchIdx = startCount;

//        System.out.println(outDirPath);

        try (FileWriter fw = new FileWriter(outDirPath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (currPatchIdx == 0) {
                out.println("#name,x,y,R,annot,tag");
            }

            for (int i = 0; i < idxList.length; i++) {

                int x = idxList[i] % W;
                int y = idxList[i] / W;

                if (x-R>=0 && x+R<W && y-R>=0 && y+R<H) {



                currPatchIdx++;

                // compute the patch from the annotated image stack
                String patchName = String.format("%015d.tif", currPatchIdx);
                String outPatchPath = outDir.getAbsolutePath() + File.separator + patchName;

                int Wptch = (2 * R + 1) * L;
//                int Hptch = 2 * R + 1;

                ImagePlus patchImage = new ImagePlus(patchName, new ByteProcessor((2 * R + 1) * L, 2 * R + 1));
                byte[] patchImageArray = (byte[])patchImage.getProcessor().getPixels();



                for (int z = 0; z < L; z++) { // go throught the values in the origin (where the annots are from)
                    for (int x1 = x-R; x1 <= x+R; x1++) {
                        for (int j1 = y-R; j1 <= y+R; j1++) {
                            int xptch = z*(2*R+1);
                            byte[] lay = (byte[])originImage.getStack().getProcessor((int)(z+1)).getPixels();
//                            System.out.println("val =" + (j1+R) * Wptch + (x1+R + xptch));
//                            System.out.println("(j1+R) = " + (j1+R));
//                            System.out.println("(x1+R) = " + (x1+R));
                            patchImageArray[(j1-(y-R)) * Wptch + (x1-(x-R) + xptch)] = lay[j1*(W)+x1];
                        }
                    }
                }

                IJ.saveAs(patchImage, "Tiff", outPatchPath); // alternative Jpg format to add

                out.println(patchName + "," + x + "," + y + "," + R + "," + annotImgName + "," + classIdx[i]);
                }
            }

        } catch (IOException e) {
            System.out.println("File opening went wrong:");
            System.out.println(e.getMessage());
        }


        return currPatchIdx;
    }

    private static boolean isInsideMargin(int idx, int W, int H, int margin) {
        return  (idx % W >= margin) && (idx % W < W-margin) && (idx / W >= margin) && (idx / W < H-margin);
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

    private Range getMinMax(byte[] I){

        Range r = new Range();

        for (int i = 0; i < I.length; i++) {
            if ((I[i] & 0xff) < r.minValue) {
                r.minValue = I[i] & 0xff;
            }
            if ((I[i] & 0xff) > r.maxValue) {
                r.maxValue = I[i] & 0xff;
            }
        }

        return r;

    }

    private Range getMinMax(float[] I) {

        Range r = new Range();

        for (int i = 0; i < I.length; i++) {
            if (I[i] < r.minValue) {
                r.minValue = I[i];
            }
            if (I[i] > r.maxValue) {
                r.maxValue = I[i];
            }
        }

        return r;

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

        int[] out = new int[nsamples]; // [tosample[0].length];

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

            out[j] = i; // tosample[i];//[k];[k]

//            for (int k = 0; k < tosample[i].length; k++) {
//                out[j][k] = tosample[i][k];
//            }

        }


        return out;
    }

    class Range {

        public float minValue;
        public float maxValue;

        public Range(){
            this.minValue = Float.POSITIVE_INFINITY;
            this.maxValue = Float.NEGATIVE_INFINITY;
        }

        public float getInterval() {
            return this.maxValue - this.minValue;
        }


        public void print() {
             System.out.println("Range = [ " + this.minValue + "  - " + this.maxValue + " ]");
        }

    }
}