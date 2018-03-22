package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;

import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Random;

public class PatchExtractor implements PlugIn {

    String annotDir;
    int R = 8;
    int N = 10;
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

        System.out.println("found " + files.length + "annotations");


        File directory = new File(fAnnotDir.getParent() + File.separator + PATCH_DIR_NAME + File.separator);

        if (! directory.exists()){
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


            // read image
            ImagePlus annotImg = new ImagePlus(annotImgPath.getAbsolutePath());
            if (annotImg.getType() == ImagePlus.GRAY8 && Tools.getFileExtension(annotImg.getTitle()).equalsIgnoreCase("TIF")) {
                // if read annot image is 8bit and .tif

                System.out.println(annotImgPath.getAbsolutePath());

                // compute summed area table
                byte[] annotImgPixels = (byte[]) annotImg.getProcessor().getPixels();
                int[] annotIntegralImg = computeIntegralImage(annotImgPixels, annotImg.getWidth(), annotImg.getHeight());
                int[] sumOverRec = computeSumOverRect(annotIntegralImg);

                new ImagePlus("sumOverRec", new FloatProcessor(annotImg.getWidth(), annotImg.getWidth(), sumOverRec)).show();

//                IJ.run(annotImg, "Gaussian Blur...", "sigma="+gaussBlur);

                System.out.println("computed integral image");

                float annotFuzzyMin = Float.POSITIVE_INFINITY, annotFuzzyMax = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < annotImgPixels.length; i++) {
                    if ((annotImgPixels[i] & 0xff) < annotFuzzyMin) {
                        annotFuzzyMin = annotImgPixels[i] & 0xff;
                    }
                    if ((annotImgPixels[i] & 0xff) > annotFuzzyMax) {
                        annotFuzzyMax = annotImgPixels[i] & 0xff;
                    }
                }

//                System.out.println("annotFuzzyMin="+annotFuzzyMin);
//                System.out.println("annotFuzzyMax="+annotFuzzyMax);

                if (annotFuzzyMax - annotFuzzyMin > Float.MIN_VALUE) {

                    //cws compute
                    float[] annotFuzzyCws = new float[annotImgPixels.length];

                    for (int j = 0; j < annotFuzzyCws.length; j++) {
                        annotFuzzyCws[j] = ((annotImgPixels[j]&0xff)-annotFuzzyMin)/(annotFuzzyMax-annotFuzzyMin); // min-max normalize
                        annotFuzzyCws[j] = annotFuzzyCws[j] + ((j==0)? 0 : annotFuzzyCws[j-1]);
                    }

                    System.out.println("annotFuzzyCws.top()="+annotFuzzyCws[annotFuzzyCws.length-1]);

                    int[] smp = sampleI(N, annotFuzzyCws);

                    Overlay ov = new Overlay();
                    for (int i = 0; i < smp.length; i++) {
//                        System.out.println(smp[i] + " -- " + smp[i]%annotImg.getWidth() + " | " + smp[i]/annotImg.getWidth() );
                        PointRoi p = new PointRoi(smp[i]%annotImg.getWidth(), smp[i]/annotImg.getWidth());
                        p.setFillColor(Color.RED);
                        p.setPointType(Roi.OVAL);
//                        System.out.println(p.getXBase() + " " + p.getYBase());
                        ov.add(p);
                    }

                    annotImg.setOverlay(ov);

                    annotImg.show();

                    System.out.println("OK");

                }
            }
            // sample positives

            // sample negatives with the inverted image

        }
    }

    private int[] computeIntegralImage(byte[] inputImageArray, int inputImageWidth, int inputImageHeight) {

        int[] integralImg = new int[inputImageArray.length];

        int iCurr, iUp, iLeft, iUpLeft;

        for (int x = 0; x < inputImageWidth; x++) {
            for (int y = 0; y < inputImageHeight; y++) {
                iCurr = y * inputImageWidth + x; // i(x,y)
                iUp = wrap(y-1, 0, inputImageHeight-1) * inputImageWidth + x; // I(x,y-1)
                iLeft = y * inputImageWidth + wrap(x-1, 0, inputImageWidth-1); // I(x-1,y)
                iUpLeft = wrap(y-1, 0, inputImageHeight-1) * inputImageWidth + wrap(x-1, 0, inputImageWidth-1); // I(x-1,y-1)
                integralImg[iCurr] = (inputImageArray[iCurr] & 0xff) + integralImg[iUp] + integralImg[iLeft] - integralImg[iUpLeft];
            }
        }

        return integralImg;
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

    // sampleXY

}








//    private ArrayList<Integer> importsamp(ArrayList<Double> lcws, int n) {
//        // systematic resampling, Beyond Kalman Filtering, Ristic et al.
//        double totalmass = lcws.get(lcws.size() - 1);
//        double u1 = (totalmass / (float) n) * new Random().nextDouble();
//
//        ArrayList<Integer> out = new ArrayList<Integer>(n);
//        out.clear();
//        int i = 0;
//        for (int j = 0; j < n; j++) {
//            double uj = u1 + j * (totalmass / (float) n);
//            while (uj > lcws.get(i)) i++;
//            out.add(i);
//        }
//
//        return out;
//
//    }