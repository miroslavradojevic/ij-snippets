package com.braincadet.bacannot;

import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.FilenameFilter;

public class PatchExtractor implements PlugIn {

    String annotDir;

    @Override
    public void run(String s) {

        GenericDialog gd = new GenericDialog("Extract patches");
        gd.addStringField("annotdir",     Prefs.get("com.braincadet.bacannot.annotdir", annotDir), 30);

//        gd.addStringField("th",         Prefs.get("com.braincadet.phd.th", th_csv), 10);
//        gd.addStringField("no",         Prefs.get("com.braincadet.phd.no", no_csv), 20);
//        gd.addStringField("ro",         Prefs.get("com.braincadet.phd.ro", ro_csv), 10);
//        gd.addStringField("ni",         Prefs.get("com.braincadet.phd.ni", ni_csv), 10);
//        gd.addStringField("step",       Prefs.get("com.braincadet.phd.step", step_csv), 10);
//        gd.addStringField("kappa",      Prefs.get("com.braincadet.phd.kappa", kappa_csv), 10);
//        gd.addStringField("ps",         Prefs.get("com.braincadet.phd.ps", pS_csv), 10);
//        gd.addStringField("pd",         Prefs.get("com.braincadet.phd.pd", pD_csv), 10);
//        gd.addStringField("krad",       Prefs.get("com.braincadet.phd.krad", krad_csv), 10);
//        gd.addStringField("kc",         Prefs.get("com.braincadet.phd.kc", kc_csv), 10);
//        gd.addNumericField("maxiter",   Prefs.get("com.braincadet.phd.maxiter", maxiter), 0, 5, "");
//        gd.addStringField("maxepoch",   Prefs.get("com.braincadet.phd.maxepoch", Integer.toString(maxepoch)), 10);
//        gd.addCheckbox("savemidres",    Prefs.get("com.braincadet.phd.savemidres", savemidres));

        gd.showDialog();
        if (gd.wasCanceled()) return;

        annotDir = gd.getNextString();
        Prefs.set("com.braincadet.bacannot.annotdir", annotDir);

//        th_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.th", th_csv);
//        no_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.no", no_csv);
//        ro_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.ro", ro_csv);
//        ni_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.ni", ni_csv);
//        step_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.step", step_csv);
//        kappa_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.kappa", kappa_csv);
//        pS_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.ps", pS_csv);
//        pD_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.pd", pD_csv);
//        krad_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.krad", krad_csv);
//        kc_csv = gd.getNextString();
//        Prefs.set("com.braincadet.phd.kc", kc_csv);
//        maxiter = (int) gd.getNextNumber();
//        Prefs.set("com.braincadet.phd.maxiter", maxiter);
//        String maxepoch_str = gd.getNextString();
//        maxepoch = (maxepoch_str.equals("inf")) ? Integer.MAX_VALUE : Integer.valueOf(maxepoch_str);
//        Prefs.set("com.braincadet.phd.maxepoch", maxepoch);
//        savemidres = gd.getNextBoolean();
//        Prefs.set("com.braincadet.phd.savemidres", savemidres);

        System.out.println("annotdir=" + annotDir);
        File fAnnotDir = new File(annotDir);
        if (fAnnotDir.exists() == false) {
            System.out.println("Enetered annot directory does not exist!");
            return;
        }

        // read all available annotation images "**.tif"
        File [] files = fAnnotDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".tif");
            }
        });

        System.out.println("dound " + files.length + "annotations");

        for (File annotImgPath : files) {
            System.out.println(annotImgPath.getAbsolutePath());

            // sample positives

            // sample negatives with the inverted image



        }



    }
}



//    private int[][] sample(int nsamples, float[] csw, int[][] tosample) {
//
//        int[][] out = new int[nsamples][tosample[0].length];
//
//        float totalmass = csw[csw.length - 1];
//
//        // use systematic resampling
//        int i = 0;
//
//        float u1 = (totalmass / (float) nsamples) * new Random().nextFloat();
//
//        for (int j = 0; j < nsamples; j++) {
//
//            float uj = u1 + j * (totalmass / (float) nsamples);
//
//            while (uj > csw[i]) {
//                i++;
//            }
//
//            for (int k = 0; k < tosample[i].length; k++) {
//                out[j][k] = tosample[i][k];
//            }
//
//        }
//
//
//        return out;
//    }


//            for (int j = 0; j < stpr._pcws0.length; j++) {
//        stpr._pcws0[j] = (tmax-tmin>Float.MIN_VALUE)?((stpr._pcws0[j]-tmin)/(tmax-tmin)):0;
//        stpr._pcws0[j] = (float) (Math.pow(stpr._pcws0[j],weight_deg) * 1f);
//        stpr._pcws0[j] = stpr._pcws0[j] + ((j==0)? 0 : stpr._pcws0[j-1]);
//        }