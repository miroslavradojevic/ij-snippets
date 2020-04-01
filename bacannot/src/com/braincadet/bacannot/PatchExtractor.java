package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.*;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

public class PatchExtractor implements PlugIn {

    static String SAVE_EXT = "Tif";//"Png";

    String annotDir;
    int D = 20; // width/height of the patch (width=height)
    int Ntrain = 100; // number of samples
    int Nvalidate = 20;
    int Ntest = 100;

    float recCenterX, recCenterY;
    float[] recX = new float[4];
    float[] recY = new float[4];

    public void run(String s) {

        GenericDialog gd = new GenericDialog("Extract patches");
        gd.addStringField("annotdir", Prefs.get("com.braincadet.bacannot.annotdir", annotDir), 80);
        gd.addNumericField("R", Prefs.get("com.braincadet.bacannot.d", D), 0, 5, "");
        gd.addNumericField("Ntrain", Prefs.get("com.braincadet.bacannot.ntrain", Ntrain), 0, 5, "");
        gd.addNumericField("Nvalidate", Prefs.get("com.braincadet.bacannot.nvalidate", Nvalidate), 0, 5, "");
        gd.addNumericField("Ntest", Prefs.get("com.braincadet.bacannot.ntest", Ntest), 0, 5, "");
        gd.showDialog();

        if (gd.wasCanceled()) return;

        annotDir = gd.getNextString().replace("\"", "");
        Prefs.set("com.braincadet.bacannot.annotdir", annotDir);

        D = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.d", D);

        Ntrain = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.ntrain", Ntrain);

        Nvalidate = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.nvalidate", Nvalidate);

        Ntest = (int) gd.getNextNumber();
        Prefs.set("com.braincadet.bacannot.ntest", Ntest);

        IJ.log("annotdir=" + annotDir);

        File fAnnotDir = new File(annotDir);

        if (fAnnotDir.exists() == false) {
            IJ.log("Enetered annot directory does not exist.");
            return;
        }

        if (fAnnotDir.isDirectory() == false) {
            IJ.log("Enetered annot path is not a directory.");
            return;
        }

        // read all available annotation images "*.tif"
        File[] files = fAnnotDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".tif");
            }
        });

        if (files.length == 0) {
            IJ.log("No annotations were found.");
            return;
        }

        IJ.log("found " + files.length + " annotations");

        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateFormat = dateFormat.format(date);

        File outTrainDir = new File(fAnnotDir.getParent() + File.separator + currentDateFormat + File.separator + Constants.TRAIN_DIR_NAME + File.separator);
        File outValidateDir = new File(fAnnotDir.getParent() + File.separator + currentDateFormat + File.separator + Constants.VALIDATE_DIR_NAME + File.separator);
        File outTestDir = new File(fAnnotDir.getParent() + File.separator + currentDateFormat+ File.separator + Constants.TEST_DIR_NAME + File.separator);

        if (!outTrainDir.exists()) {
            outTrainDir.mkdirs(); // mkdirs() for subdirs
        } else {
            if (outTrainDir.listFiles().length > 0) {
                IJ.log("Train directory contains files.");
                return;
            }
        }

        if (!outTestDir.exists()) {
            outTestDir.mkdirs();
        } else {
            if (outTestDir.listFiles().length > 0) {
                IJ.log("Test directory contains files.");
                return;
            }
        }

        if (!outValidateDir.exists()) {
            outValidateDir.mkdirs();
        } else {
            if (outValidateDir.listFiles().length > 0) {
                IJ.log("Validate directory contains files.");
                return;
            }
        }

        int countSamples = 0; // count computed training samples over the whole annot directory

        for (File annotImgPath : files) {

            IJ.log(annotImgPath.getAbsolutePath()); // read annotation image

            // read image
            ImagePlus annotImg = new ImagePlus(annotImgPath.getAbsolutePath());

            if (annotImg == null) {
                IJ.log("Annotation file could not be read.");
                continue;
            }

            annotImg.show();

            int W = annotImg.getWidth();
            int H = annotImg.getHeight();

            if (annotImg.getProperty("Info") == null) {
                IJ.log("Could not find the origin, patch sampling cannot proceed.");
                continue;
            }

            String[] originInfo = ((String) annotImg.getProperty("Info")).split("\\|");
            String originPath = originInfo[0];
            String originTag = originInfo[1];

            IJ.log("Info = " + annotImg.getProperty("Info"));
            IJ.log("length = " + originInfo.length);
            IJ.log("originPath = " + originPath);
            IJ.log("originTag = " + originTag);

            // check if there are dedicated train/validation/test regions to combine with the read annotation
            File fOrigin = new File(originPath);

            if (fOrigin.exists() == false) {
                IJ.log("Origin file does not exist.");
                continue;
            }

            // read image being sampled read from the annotation metadata
            ImagePlus originStackImg = new ImagePlus(fOrigin.getAbsolutePath());

            if (originStackImg == null) {
                IJ.log("Origin file could not be read.");
                continue;
            }

            originStackImg.show();

            int Lorig = originStackImg.getStackSize();

            // process if read annot image is 8bit and .tif
            boolean imageIs8Bit = annotImg.getType() == ImagePlus.GRAY8;
            boolean imageFileIsTif = Tools.getFileExtension(annotImg.getTitle()).equalsIgnoreCase("TIF");
            boolean imageBandwidthExists = getMinMax((byte[]) annotImg.getProcessor().getPixels()).getInterval() > Float.MIN_VALUE;

            if (!imageIs8Bit || !imageFileIsTif || !imageBandwidthExists) {
                IJ.log("File could not be processed: image not byte8 or has no tif extension or no annots found.");
                continue;
            }

//            IJ.run(annotImg, "Options...", "iterations=1 count=1 black");
//            IJ.run(annotImg, "Skeletonize", ""); // "Distance Map"

            minMaxNormalizeByteImage(annotImg); // annotImg values wrap between 0 and 255
            byte[] annotImgPixels = (byte[]) annotImg.getProcessor().getPixels();

            if (getMinMax(annotImgPixels).getInterval() <= Float.MIN_VALUE) {
                IJ.log("distance map had zero range.");
                continue;
            }

//            File dParentOrigin = fOrigin.getParentFile();
//            if (dParentOrigin != null && dParentOrigin.isDirectory())

            //*************************
            // train mask
            byte[] mlRegionTrainMask = null;
            String mlRegionTrainPath = sanitizeDirectoryPathEnd(fOrigin.getParentFile().getAbsolutePath()) + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator +
                            Constants.TRAIN_DIR_NAME + File.separator + fOrigin.getName();

            ImagePlus mlRegionTrainImagePlus = new ImagePlus(mlRegionTrainPath);

            if (mlRegionTrainImagePlus != null && mlRegionTrainImagePlus.getType() == ImagePlus.GRAY8) {
                IJ.log("here!");
                minMaxNormalizeByteImage(mlRegionTrainImagePlus);
                mlRegionTrainMask = (byte[])mlRegionTrainImagePlus.getProcessor().getPixels();
                mlRegionTrainImagePlus.show();
            }

            //*************************
            // validation mask
            byte[] mlRegionValidateMask = null;

            ImagePlus mlRegionValidateImagePlus = new ImagePlus(
                    sanitizeDirectoryPathEnd(fOrigin.getParentFile().getAbsolutePath()) + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator +
                    Constants.VALIDATE_DIR_NAME + File.separator + fOrigin.getName());

            if (mlRegionValidateImagePlus != null && mlRegionValidateImagePlus.getType() == ImagePlus.GRAY8) {
                minMaxNormalizeByteImage(mlRegionValidateImagePlus);
                mlRegionValidateMask = (byte[])mlRegionValidateImagePlus.getProcessor().getPixels();
                mlRegionValidateImagePlus.show();
            }

            //*************************
            // test mask
            byte[] mlRegionTestMask = null;

            ImagePlus mlRegionTestImagePlus = new ImagePlus(sanitizeDirectoryPathEnd(fOrigin.getParentFile().getAbsolutePath()) + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator +
                    Constants.TEST_DIR_NAME + File.separator + fOrigin.getName());

            if (mlRegionTestImagePlus != null && mlRegionTestImagePlus.getType() == ImagePlus.GRAY8) {
                minMaxNormalizeByteImage(mlRegionTestImagePlus);
                mlRegionTestMask = (byte[])mlRegionTestImagePlus.getProcessor().getPixels();
                mlRegionTestImagePlus.show();
            }

            // compute summed area table (deprecated approach) computeIntegralImage(annotImgPixels, W, H); computeSumOverRect(annotIntegralImg, W, H, R);
            float[] cws = new float[annotImgPixels.length];
            float[] weight = new float[annotImgPixels.length];
            ArrayList<Overlay> samplingOverlay = new ArrayList<>();

            //************************************************************
            // train positive samples, annot and train ml region
            for (int i = 0; i < annotImgPixels.length; i++) {

                weight[i] = 0f;

                if (!isInsideMargin(i, W, H, D/2)) continue;

                if (!isInsideCircle(i, W, H)) continue;

                if ((annotImgPixels[i] & 0xff) == 0) continue;

                if (mlRegionTrainMask == null) continue;

                if ((mlRegionTrainMask[i] & 0xff) == 0) continue;

                weight[i] = 1f;

            }

            for (int i = 0; i < annotImgPixels.length; i++) cws[i] = weight[i] + ((i == 0) ? 0 : cws[i - 1]);

            int[] sampleTrainPos = sampleI(Ntrain/2, cws);

            countSamples = patchExtract(sampleTrainPos, countSamples, W, H, Lorig, D, originStackImg, originPath, originTag + "_" + String.format("%d", 1), outTrainDir);

            samplingOverlay.add(getOverlayFromIndexes(sampleTrainPos, W, new Color(1f, 0f, 0f, 0.4f), D));

            //************************************************************
            // train negative samples, !annot and train ml region
            for (int i = 0; i < annotImgPixels.length; i++) {

                weight[i] = 0f;

                if (!isInsideMargin(i, W, H, D/2)) continue;

                if (!isInsideCircle(i, W, H)) continue;

                if ((annotImgPixels[i] & 0xff) == 255) continue;

                if (mlRegionTrainMask == null) continue;

                if ((mlRegionTrainMask[i] & 0xff) == 0) continue;

                weight[i] = 1f;

            }

            for (int i = 0; i < annotImgPixels.length; i++) cws[i] = weight[i] + ((i == 0) ? 0 : cws[i - 1]);

            int[] sampleTrainNeg = sampleI(Ntrain/2, cws);

            countSamples = patchExtract(sampleTrainNeg, countSamples, W, H, Lorig, D, originStackImg, originPath, originTag + "_" + String.format("%d", 0), outTrainDir);

            samplingOverlay.add(getOverlayFromIndexes(sampleTrainNeg, W, new Color(0f, 0f, 1f, 0.4f), D));

            // save overlay with the train samples
            exportOverlay(asOverlay(samplingOverlay), outTrainDir.getPath() + File.separator + annotImg.getTitle() + ".zip");

            //************************************************************
            // validation positive samples
            for (int i = 0; i < annotImgPixels.length; i++) {

                weight[i] = 0f;

                if (!isInsideMargin(i, W, H, D/2)) continue;

                if (!isInsideCircle(i, W, H)) continue;

                if ((annotImgPixels[i] & 0xff) == 0) continue;

                if (mlRegionValidateMask == null) continue;

                if ((mlRegionValidateMask[i] & 0xff) == 0) continue;

                weight[i] = 1f;

            }

            for (int i = 0; i < annotImgPixels.length; i++) cws[i] = weight[i] + ((i == 0) ? 0 : cws[i - 1]); // todo computeCws(weight, cws)

            int[] sampleValidationPos = sampleI(Nvalidate/2, cws);

            countSamples = patchExtract(sampleValidationPos, countSamples, W, H, Lorig, D, originStackImg, originPath, originTag + "_" + String.format("%d", 1), outValidateDir);

            samplingOverlay.add(getOverlayFromIndexes(sampleValidationPos, W, new Color(1f, 0f, 0f, 0.1f), D));

            //************************************************************
            // validation negative samples
            for (int i = 0; i < annotImgPixels.length; i++) {

                weight[i] = 0f;

                if (!isInsideMargin(i, W, H, D/2)) continue;

                if (!isInsideCircle(i, W, H)) continue;

                if ((annotImgPixels[i] & 0xff) == 255) continue;

                if (mlRegionValidateMask == null) continue;

                if ((mlRegionValidateMask[i] & 0xff) == 0) continue;

                weight[i] = 1f;

            }

            for (int i = 0; i < annotImgPixels.length; i++) cws[i] = weight[i] + ((i == 0) ? 0 : cws[i - 1]);

            int[] sampleValidationNeg = sampleI(Nvalidate/2, cws);

            countSamples = patchExtract(sampleValidationNeg, countSamples, W, H, Lorig, D, originStackImg, originPath, originTag + "_" + String.format("%d", 0), outValidateDir);

            samplingOverlay.add(getOverlayFromIndexes(sampleValidationNeg, W, new Color(0f, 0f, 1f, 0.1f), D));

            // save overlay with the validation samples
            exportOverlay(asOverlay(samplingOverlay), outValidateDir.getPath() + File.separator + annotImg.getTitle() + ".zip");

            //************************************************************
            // test samples
            for (int i = 0; i < annotImgPixels.length; i++) {

                weight[i] = 0f;

                if (!isInsideMargin(i, W, H, D/2)) continue;

                if (!isInsideCircle(i, W, H)) continue;

//                if ((annotImgPixels[i] & 0xff) == 255) continue;

                if (mlRegionTestMask == null) continue;

                if ((mlRegionTestMask[i] & 0xff) == 0) continue;

                weight[i] = 1f;

            }

            for (int i = 0; i < annotImgPixels.length; i++) cws[i] = weight[i] + ((i == 0) ? 0 : cws[i - 1]);

            int[] sampleTest = sampleI(Ntest, cws); // here output the class too

            countSamples = patchExtract(sampleTest, countSamples, W, H, Lorig, D, originStackImg, originPath,originTag + "_" + String.format("%d", 0), outTestDir);

            samplingOverlay.add(getOverlayFromIndexes(sampleTest, W, new Color(0f, 1f, 0f, 0.2f), D));

            exportOverlay(asOverlay(samplingOverlay), outTestDir.getPath() + File.separator + annotImg.getTitle() + ".zip");

            //************************************************************
            ImagePlus trainSamplingViz = new ImagePlus("train", originStackImg.getStack().getProcessor(originStackImg.getStackSize()));
            trainSamplingViz.show();
            trainSamplingViz.setOverlay(asOverlay(samplingOverlay));

//            annotImg = new ImagePlus(annotImgPath.getAbsolutePath()); // reload it
//            IJ.run(annotImg, "Invert", "");
//            IJ.run(annotImg, "Options...", "iterations=1 count=1 black");
//            IJ.run(annotImg, "Skeletonize", "");
//            annotImgPixels = (byte[]) annotImg.getProcessor().getPixels();
//            for (int j = 0; j < cws.length; j++) {
//                float ww = (isInsideMargin(j, W, H, D/2) && isInsideCircle(j, W, H)) ? (float) Math.pow(annotImgPixels[j] & 0xff, 1) : 0f; // isInsideCircle(j, W, H)  && (annotImgPixels[j] & 0xff) > 0
//                cws[j] = ww + ((j == 0) ? 0 : cws[j - 1]);
//            }
//            Arrays.fill(cws, 1f / annotImgPixels.length);
//            for (int j = 0; j < cws.length; j++) {
//                float ww = (isInsideMargin(j, W, H, D/2) && isInsideCircle(j, W, H)) ? cws[j] : 0f; // isInsideCircle(j, W, H)  && (annotImgPixels[j] & 0xff) > 0
//                cws[j] = ww + ((j == 0) ? 0 : cws[j - 1]);
//            }
        }
    }

    private static String sanitizeDirectoryPathEnd(String pathIn) {
        String pathOut = pathIn;
        pathOut += (!pathOut.endsWith(File.separator))? File.separator : "";
        return pathOut;
    }

    private static int patchExtract(
            int[] idxList,
            int startCount,
            int W, int H, int L, int D,
            ImagePlus originImage,
            String originPath,
            String originTag,
            File outDir)
    {

        // create directory if it does not exist
        File outDir1 = new File(outDir.getAbsolutePath() + File.separator + originTag + File.separator);

        if (!outDir1.exists()) {
            outDir1.mkdir();
        }

        String outDirPath = outDir.getAbsolutePath() + File.separator + "patch.log";

        int currPatchIdx = startCount;

        try (FileWriter fw = new FileWriter(outDirPath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (currPatchIdx == 0) {
                out.println("#className,patchFileName,originPath");
            }

            ImageStack patchImageStack = new ImageStack(D, D);

            for (int z = 0; z < L; z++) {
                patchImageStack.addSlice(new ByteProcessor(D, D));
            }

            for (int i = 0; i < idxList.length; i++) {

                int x = idxList[i] % W;
                int y = idxList[i] / W;

                if (x - D/2 >= 0 && x + D/2 < W && y - D/2 >= 0 && y + D/2 < H) { // check if it is within the margins

                    currPatchIdx++;

                    // compute the patch from the annotated image stack
                    String patchName = String.format("%015d_%d_%d_%d", currPatchIdx, x, y, D);
                    String outPatchPath = outDir.getAbsolutePath() + File.separator + originTag + File.separator + patchName;

                    /*
                    int Wptch = (2 * R + 1) * L;

                    ImagePlus patchImage = new ImagePlus(patchName, new ByteProcessor((2 * R + 1) * L, 2 * R + 1));
                    byte[] patchImageArray = (byte[]) patchImage.getProcessor().getPixels();

                    for (int z = 0; z < L; z++) { // go throught the values in the origin (where the annots are from)
                        for (int x1 = x - R; x1 <= x + R; x1++) {
                            for (int j1 = y - R; j1 <= y + R; j1++) {
                                int xptch = z * (2 * R + 1);
                                byte[] lay = (byte[]) originImage.getStack().getProcessor((int) (z + 1)).getPixels();
                                if (z % 2 == 0)
                                    patchImageArray[(j1 - (y - R)) * Wptch + (x1 - (x - R) + xptch)] = lay[j1 * (W) + x1];
                                else
                                    patchImageArray[(j1 - (y - R)) * Wptch + (-(x1 - (x - R)) + (2 * R) + xptch)] = lay[j1 * (W) + x1];
                            }
                        }
                    }
                    */

//                    IJ.saveAs(patchImage, SAVE_EXT, outPatchPath);
//                    out.println(originTag + "," + patchName + '.' + SAVE_EXT.toLowerCase() + "," + originPath);
//                    ImageStack patchImageStack = new ImageStack((2 * R + 1), (2 * R + 1));

                    for (int z = 0; z < L; z++) {

                        byte[] originByteLay = (byte[]) originImage.getStack().getProcessor((int) (z + 1)).getPixels();

                        byte[] lay = new byte[D*D];

                        for (int x1 = x-D/2; x1 <= ((D%2==0)? x+D/2-1 : x+D/2); x1++) {
                            for (int y1 = y-D/2; y1 <= ((D%2==0)? y+D/2-1 : y+D/2); y1++) {
                                lay[(y1 - (y-D/2)) * D + (x1 - (x-D/2))] = originByteLay[y1 * W + x1];
                            }
                        }

                        patchImageStack.setPixels(lay, z+1);

                    }

                    IJ.log(outPatchPath);
                    IJ.saveAs(new ImagePlus("", patchImageStack), "Tiff", outPatchPath);
                    out.println(originTag + "," + patchName + '.' + SAVE_EXT.toLowerCase() + "," + originPath);

                }
            }

            IJ.log("done patchExtract() " + originTag + " --> " + outDirPath);

        } catch (IOException e) {
            IJ.log("File opening went wrong:");
            IJ.log(e.getMessage());
        }

        return currPatchIdx;
    }

    private static boolean isInsideMargin(int idx, int W, int H, int margin) {
        return (idx % W >= margin) && (idx % W < W - margin) && (idx / W >= margin) && (idx / W < H - margin);
    }

    private static boolean isInsideCircle(int idx, int W, int H) {
        return Math.pow(idx % W - (W / 2), 2) + Math.pow(idx / W - (H / 2), 2) <= Math.pow(0.85 * 0.5 * Math.min(W, H), 2);
    }

//    private static boolean isTrainMask(byte[] mask, int idx) {
//
//        if (mask != null) {
//
//            if (idx >= 0 && idx < mask.length) {
//
//                return (mask[idx] & 0xff) == 255;
//
//            }
//            else {
//                return false;
//            }
//
//        }
//        else {
//
//            return true;
//
//        }
//
//
//    }

    private static ImageStack getPatchArrays(ImagePlus inImg, int atX, int atY, int atR) {

        if (atX - atR >= 0 && atX + atR < inImg.getWidth() && atY - atR >= 0 && atY + atR < inImg.getHeight()) {

            ImageStack isOut = new ImageStack((2 * atR + 1), (2 * atR + 1));

            for (int i = 0; i < inImg.getStack().getSize(); i++) {
                byte[] layer = new byte[(2 * atR + 1) * (2 * atR + 1)];
                int j = 0;
                for (int dX = -atR; dX <= atR; dX++) {
                    for (int dY = -atR; dY <= atR; dY++) {
                        byte[] aa = (byte[]) inImg.getStack().getProcessor(i + 1).getPixels();
                        layer[j] = aa[(atY + dY) * inImg.getWidth() + (atX + dX)];
                        j++;
                    }
                }

                isOut.addSlice(inImg.getTitle() + ",x=" + IJ.d2s(atX, 0) + ",y=" + IJ.d2s(atY, 0) + ",R=" + IJ.d2s(atR, 0),
                        new ByteProcessor((2 * atR + 1), (2 * atR + 1), layer));

            }

            return isOut;
        }

        return null;

    }

    private static int wrap(int i, int iMin, int iMax) {
        return ((i < iMin) ? iMin : ((i > iMax) ? iMax : i));
    }


    private Overlay getOverlayFromIndexes(int[] indexes, int width, Color col, int D){

        Overlay ov = new Overlay();

        for (int i = 0; i < indexes.length; i++) {

            recX[0] = indexes[i] % width + .5f - D/2;
            recY[0] = indexes[i] / width + .5f - D/2;

            recX[1] = indexes[i] % width + .5f + D/2;
            recY[1] = indexes[i] / width + .5f - D/2;

            recX[2] = indexes[i] % width + .5f + D/2;
            recY[2] = indexes[i] / width + .5f + D/2;

            recX[3] = indexes[i] % width + .5f - D/2;
            recY[3] = indexes[i] / width + .5f + D/2;

            PolygonRoi rec = new PolygonRoi(recX, recY, PolygonRoi.POLYGON);

            rec.setFillColor(col);

            ov.add(rec);

        }

        return ov;
    }

    private Overlay asOverlay(ArrayList<Overlay> ovList) {

        Overlay ov = new Overlay();

        for (int i = 0; i < ovList.size(); i++) {

            for (int j = 0; j < ovList.get(i).size(); j++) {

                ov.add(ovList.get(i).get(j));

            }

        }

        return ov;

    }

    private Overlay concatenateOverlays(Overlay ov1, Overlay ov2) {

        Overlay ov = new Overlay();

        for (int i = 0; i < ov1.size(); i++) {

            ov.add(ov1.get(i));

        }

        for (int i = 0; i < ov2.size(); i++) {

            ov.add(ov2.get(i));

        }

        return  ov;

    }

    private void exportOverlay(Overlay ov, String overlayPath) {

        RoiManager rm = new RoiManager();

        for (int i = 0; i < ov.size(); i++) {

            rm.addRoi(ov.get(i));

        }

        rm.runCommand("Save", overlayPath);
        rm.close();

    }

    private void minMaxNormalizeByteImage(ImagePlus imp) {
        byte[] impArray = (byte[]) imp.getProcessor().getPixels();
        Range impRange = getMinMax(impArray);
        for (int i = 0; i < impArray.length; i++) {
            int scaledVal = (int) ((((impArray[i] & 0xff) - impRange.minValue) / impRange.maxValue) * 255f);
            scaledVal = (scaledVal > 255) ? 255 : (scaledVal < 0) ? 0 : scaledVal;
            impArray[i] = (byte) scaledVal;
        }
    }

    private void invertByteImage(ImagePlus imp) {
        invertByteArray((byte[]) imp.getProcessor().getPixels());
    }

    private void invertByteArray(byte[] impArray) {
        for (int i = 0; i < impArray.length; i++) {
            int scaledVal = 255 - (impArray[i] & 0xff);
            scaledVal = (scaledVal > 255) ? 255 : (scaledVal < 0) ? 0 : scaledVal;
            impArray[i] = (byte) scaledVal;
        }
    }

    private Range getMinMax(byte[] I) {

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
                i00 = wrap(y - d2, 0, H - 1) * W + wrap(x - d2, 0, W - 1);
                i10 = wrap(y - d2, 0, H - 1) * W + wrap(x + d2, 0, W - 1);
                i01 = wrap(y + d2, 0, H - 1) * W + wrap(x - d2, 0, W - 1);
                i11 = wrap(y + d2, 0, H - 1) * W + wrap(x + d2, 0, W - 1);
                Iout[iCurr] = I[i11] + I[i00] - I[i10] - I[i01];
                Iout[iCurr] /= Math.pow(2 * d2 + 1, 2);
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
                iUp = wrap(y - 1, 0, H - 1) * W + x; // I(x,y-1)
                iLeft = y * W + wrap(x - 1, 0, W - 1); // I(x-1,y)
                iUpLeft = wrap(y - 1, 0, H - 1) * W + wrap(x - 1, 0, W - 1); // I(x-1,y-1)
                Iout[iCurr] = ((I[iCurr] & 0xff) / 255f) + Iout[iUp] + Iout[iLeft] - Iout[iUpLeft];
            }
        }

        return Iout;
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

            out[j] = i; // tosample[i];//[k];[k]

        }

        return out;
    }

    class Range {

        public float minValue;
        public float maxValue;

        public Range() {
            this.minValue = Float.POSITIVE_INFINITY;
            this.maxValue = Float.NEGATIVE_INFINITY;
        }

        public float getInterval() {
            return this.maxValue - this.minValue;
        }


        public void print() {
            IJ.log("Range = [ " + this.minValue + "  - " + this.maxValue + " ]");
        }

    }
}