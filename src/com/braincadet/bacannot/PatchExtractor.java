package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

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

            System.out.println(annotImgPath.getAbsolutePath());



            // read image
            ImagePlus annotImg = new ImagePlus(annotImgPath.getAbsolutePath());
            if (annotImg.getType() == ImagePlus.GRAY8) {

                // if 8bit and .tif consider it
                // check extension too
                // smooth
                IJ.run(annotImg, "Gaussian Blur...", "sigma="+gaussBlur);

                byte[] annotImgPixels = (byte[]) annotImg.getProcessor().getPixels();

                System.out.println("annotImgPixels.length="+annotImgPixels.length);

                float annotFuzzyMin = Float.POSITIVE_INFINITY, annotFuzzyMax = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < annotImgPixels.length; i++) {
                    if ((annotImgPixels[i] & 0xff) < annotFuzzyMin) {
                        annotFuzzyMin = annotImgPixels[i] & 0xff;
                    }
                    if ((annotImgPixels[i] & 0xff) > annotFuzzyMax) {
                        annotFuzzyMax = annotImgPixels[i] & 0xff;
                    }
                }

                System.out.println("annotFuzzyMin="+annotFuzzyMin);
                System.out.println("annotFuzzyMax="+annotFuzzyMax);

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