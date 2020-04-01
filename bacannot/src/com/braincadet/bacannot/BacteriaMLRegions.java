package com.braincadet.bacannot;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.braincadet.bacannot.Constants.COLOR_ML_REGION_TRAIN;

public class BacteriaMLRegions implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    float pickX, pickY, pickR; // 'picked' circle params
    boolean begunPicking;
    boolean drawMode = false;

    public enum MLRegion {TRAIN, VALIDATE, TEST}

    MLRegion currentMLRegion;

    ImagePlus inImg;
    ImageWindow imWind;
    ImageCanvas imCanv;
    String imPath, imDir, imName;

    Overlay overlayMLRegions;

    Map<MLRegion, Integer> r2i;
    Map<Integer, MLRegion> i2r;
    Map<Integer, Color> i2c;

    public void run(String s) {

        r2i = new HashMap<>();
        r2i.put(MLRegion.TRAIN, 0);
        r2i.put(MLRegion.VALIDATE, 1);
        r2i.put(MLRegion.TEST, 2);

        i2r = new HashMap<>();
        i2r.put(0, MLRegion.TRAIN);
        i2r.put(1, MLRegion.VALIDATE);
        i2r.put(2, MLRegion.TEST);

        i2c = new HashMap<>();
        i2c.put(0, Constants.COLOR_ML_REGION_TRAIN);
        i2c.put(1, Constants.COLOR_ML_REGION_VALID);
        i2c.put(2, Constants.COLOR_ML_REGION_TEST);

        String inFolder = Prefs.get("com.braincadet.bacannot.inFolder", System.getProperty("user.home"));

        OpenDialog.setDefaultDirectory(inFolder);

        OpenDialog dc = new OpenDialog("Select file");

        inFolder = dc.getDirectory();

        imPath = dc.getPath();

        if (imPath == null) {
            IJ.showStatus("Could not open path: " + imPath);
            return;
        }

        Prefs.set("com.braincadet.bacannot.inFolder", inFolder);

        inImg = new ImagePlus(imPath);

        if (inImg == null) {
            IJ.showStatus("Could not open image (it was null) at: " + imPath);
        }

        if (!Constants.isValidImageExtension(inImg.getTitle())) {
            IJ.log("Image extension not accepted.");
            return;
        }

        pickR = 10; // initial circle size
        pickX = 0;
        pickY = 0;
        currentMLRegion = MLRegion.TRAIN; // initial ml region type

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        // initialize Overlays with the annotations encountered in ./ML_REGION_OUTPUT_DIR_NAME/
        overlayMLRegions = new Overlay();

        // read existing ml region overlay if existent
        for (MLRegion mlReg : MLRegion.values()) {

            File fMLRegion = getFileMLRegionRoi(imDir, imName, mlReg);

            if (fMLRegion.exists()) {

                IJ.log("found -- " + fMLRegion.getPath());

                RoiManager rm = new RoiManager();

                rm.runCommand("Open", fMLRegion.getAbsolutePath());

                for (int i = 0; i < rm.getCount(); i++) {

                    overlayMLRegions.add(rm.getRoi(i));

                }

                rm.close();

            }
            else {
                IJ.log("missing -- " + fMLRegion.getPath());
            }

        }

        begunPicking = false;
        currentMLRegion = MLRegion.TRAIN;
        inImg.show();
        imWind = inImg.getWindow();
        imCanv = inImg.getCanvas();

        imWind.removeKeyListener(IJ.getInstance());
        imWind.getCanvas().removeKeyListener(IJ.getInstance());
        imCanv.removeKeyListener(IJ.getInstance());

        imCanv.addMouseListener(this);
        imCanv.addMouseMotionListener(this);
        imCanv.addKeyListener(this);
        imWind.addKeyListener(this);
        ImagePlus.addImageListener(this);

        IJ.showStatus("loaded " + imName);
        IJ.setTool("hand");

    }

    private File getFileMLRegionRoi(String imageDir, String imageName, MLRegion regionType) {

        return new File(imageDir + File.separator + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator + regionType.toString().toLowerCase() + File.separator + imageName + ".zip");

    }

    private File getFileMLRegionImage(String imageDir, String imageName, MLRegion regionType) {

        return new File(imageDir + File.separator + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator + regionType.toString().toLowerCase() + File.separator + imageName + ".tif");

    }

    private File getFileMLRegionDirectory(String imageDir, MLRegion regionType) {

        return new File(imageDir + File.separator + Constants.ML_REGION_OUTPUT_DIR_NAME + File.separator + regionType.toString().toLowerCase() + File.separator);

    }

    private void updateCircle() {

        // update pick circle
        OvalRoi circAdd = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);

        switch (currentMLRegion) {
            case TRAIN:
                circAdd.setStrokeColor(COLOR_ML_REGION_TRAIN);
                break;
            case VALIDATE:
                circAdd.setStrokeColor(Constants.COLOR_ML_REGION_VALID);
                break;
            case TEST:
                circAdd.setStrokeColor(Constants.COLOR_ML_REGION_TEST);
                break;
            default:
                break;
        }

        circAdd.setStrokeWidth(Constants.ANNOTATOR_OUTLINE_WIDTH);

        if (!begunPicking) {

            begunPicking = true;

            overlayMLRegions.add(circAdd);

//            overlayMLRegionType.add(currentMLRegion);

        } else {

            int lastIndex = overlayMLRegions.size() - 1;

            overlayMLRegions.remove(lastIndex);

            overlayMLRegions.add(circAdd);

//            lastIndex = overlayMLRegionType.size() - 1;

//            overlayMLRegionType.remove(lastIndex);

//            overlayMLRegionType.add(currentMLRegion);
        }

        imCanv.setOverlay(overlayMLRegions);

        imCanv.getImage().updateAndDraw();

    }

    private void removeCircle() {

        boolean found = false;

        // loop current list of detections and delete if any falls over the current position
        for (int i = 0; i < overlayMLRegions.size() - 1; i++) { // the last one is always updated pick circle

            float xCurr = pickX;
            float yCurr = pickY;
            float rCurr = pickR;

            float xOvl = (float) (overlayMLRegions.get(i).getFloatBounds().getX() + overlayMLRegions.get(i).getFloatBounds().getWidth() / 2f - .0f);
            float yOvl = (float) (overlayMLRegions.get(i).getFloatBounds().getY() + overlayMLRegions.get(i).getFloatBounds().getHeight() / 2f - .0f);
            float rOvl = (float) (Math.max(overlayMLRegions.get(i).getFloatBounds().getWidth(), overlayMLRegions.get(i).getFloatBounds().getHeight()) / 2f);

            float d2 = (float) (Math.sqrt(Math.pow(xCurr - xOvl, 2) + Math.pow(yCurr - yOvl, 2)));
            float d2th = (float) Math.pow(rCurr + rOvl, 1);

            if (d2 < d2th) {
                found = true;
                overlayMLRegions.remove(i);
            }

        }

        if (!found) IJ.log("nothing to remove");
        else IJ.log("removed, current annot ovly size: " + (overlayMLRegions.size() - 1));

    }

    public void mouseClicked(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        imCanv.getImage().updateAndDraw();

        switch (currentMLRegion) {
            case TRAIN:
                addCircle(COLOR_ML_REGION_TRAIN);
                break;
            case VALIDATE:
                addCircle(Constants.COLOR_ML_REGION_VALID);
                break;
            case TEST:
                addCircle(Constants.COLOR_ML_REGION_TEST);
                break;
            default:
                break;
        }

    }

    public void keyTyped(KeyEvent e) {

        switch (e.getKeyChar()) {
            case Constants.INCREASE_CIRCLE_RADIUS:
                pickR += (pickR < Math.min(inImg.getWidth(), inImg.getHeight())) ? Constants.R_STEP : 0;
                break;
            case Constants.DECREASE_CIRCLE_RADIUS:
                pickR -= (pickR >= Constants.R_MIN + Constants.R_STEP) ? Constants.R_STEP : 0;
                break;
            case '+':
                imCanv.zoomIn((int) pickX, (int) pickY);
                break;
            case '-':
                imCanv.zoomOut((int) pickX, (int) pickY);
                break;
            case Constants.EXPORT_COMMAND:
                exportAnnotation(imPath, imName);
                break;
            case Constants.DELETE_COMMAND:
                removeCircle();
                break;
            case Constants.PERIODIC_CLICK:
                drawMode = !drawMode;
                break;
            case Constants.CHANGE_ML_REGION:
                switchMLRegionType();
                break;
            default:
                break;
        }

        updateCircle();

    }

    private void switchMLRegionType() {

        String[] arrayMLRegions = new String[]{MLRegion.TRAIN.toString(), MLRegion.VALIDATE.toString(), MLRegion.TEST.toString()};

        GenericDialog gd = new GenericDialog("Choose ML region:");

        gd.addChoice("type", arrayMLRegions, arrayMLRegions[0]);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return;
        } else {
            String regionType = gd.getNextChoice();
            System.out.println("regionType=" + regionType);

            if (regionType.equalsIgnoreCase(MLRegion.TRAIN.toString())){
                currentMLRegion = MLRegion.TRAIN;
            }
            else if (regionType.equalsIgnoreCase(MLRegion.VALIDATE.toString())) {
                currentMLRegion = MLRegion.VALIDATE;
            }
            else if (regionType.equalsIgnoreCase(MLRegion.TEST.toString())) {
                currentMLRegion = MLRegion.TEST;
            }
        }
    }

    private void exportAnnotation(String origin, String tag) {

        GenericDialog gd = new GenericDialog("Export regions?");

        gd.addStringField("origin", origin, 60);

        gd.addStringField("tag", tag, 40);

        gd.showDialog();

        if (gd.wasCanceled()) {

            return;

        } else {

            String originPath = gd.getNextString().replace("\"", "");

            String originTag = gd.getNextString();

            exportOverlayAnnot(originPath + "|" + originTag);

        }

    }

    private void addCircle(Color col) {

        // use pickX, pickY and pickR to check if it does not overlap
        OvalRoi circ1 = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);
        OvalRoi circ2 = circ1;
        circ1.setFillColor(col);
        circ1.setStrokeColor(col);

        //
        if (begunPicking) {

            // the last one is always the currently plotted
            overlayMLRegions.remove(overlayMLRegions.size() - 1);

            overlayMLRegions.add(circ1);

            overlayMLRegions.add(circ2);
        }

        imCanv.setOverlay(overlayMLRegions);

        imCanv.getImage().updateAndDraw();

        IJ.showStatus("added, current number of annotations: " + (overlayMLRegions.size() - 1));

    }

    private void exportOverlayAnnot(String originInfo) {
//        ArrayList<ImagePlus> imgsOut = new ArrayList<>();
//        imgsOut.add(new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()))); // 0 -> train
//        imgsOut.add(new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()))); // 1 -> validate
//        imgsOut.add(new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()))); // 2 -> test
//        imgsOut.get(0).setProperty("Info", originInfo); // set new info, metadata http://imagej.1557.x6.nabble.com/Push-Metadata-td4999917.html
//        imgsOut.get(1).setProperty("Info", originInfo);
//        imgsOut.get(2).setProperty("Info", originInfo);
//        ArrayList<RoiManager> roisOut = new ArrayList<>();
//        roisOut.add(new RoiManager()); // 0 -> train
//        roisOut.add(new RoiManager()); // 1 -> validate
//        roisOut.add(new RoiManager()); // 2 -> test
//        IJ.log("roisOut(0=TRAIN): " + roisOut.get(0).getCount());
//        IJ.log("roisOut(1=VALIDATE): " + roisOut.get(1).getCount());
//        IJ.log("roisOut(2=TEST): " + roisOut.get(2).getCount());

        for (int i = 0; i < 3; i++) {

            ImagePlus imOut = new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()));
            imOut.setProperty("Info", originInfo); // set new info, metadata http://imagej.1557.x6.nabble.com/Push-Metadata-td4999917.html
            byte[] imOutArray = (byte[]) imOut.getProcessor().getPixels();

            RoiManager roiOut = new RoiManager();

            // go through the overlay and separate three kinds of regions based on the color
            for (int j = 0; j < overlayMLRegions.size() - 1; j++) {

                Color currentOverlayElementColor = overlayMLRegions.get(j).getFillColor();

                if (currentOverlayElementColor.equals(i2c.get(i))) {

                    roiOut.addRoi(overlayMLRegions.get(j));

                    int xPatch = overlayMLRegions.get(j).getBounds().x;
                    int yPatch = overlayMLRegions.get(j).getBounds().y;
                    int wPatch = overlayMLRegions.get(j).getBounds().width;

                    byte[] ovAnnotArray = (byte[]) overlayMLRegions.get(j).getMask().convertToByteProcessor().getPixels();

                    for (int k = 0; k < ovAnnotArray.length; k++) {
                        if ((ovAnnotArray[k] & 0xff) == 255) {
                            // get global image coords and overwrite I(xOut, yOut) to 255
                            int xOut = xPatch + (k % wPatch);
                            int yOut = yPatch + (k / wPatch);

                            if (xOut >= 0 && xOut < inImg.getWidth() && yOut >= 0 && yOut < inImg.getHeight()) {
                                imOutArray[yOut * inImg.getWidth() + xOut] = (byte) 255;
                            }
                        }
                    }
                }
            }

            if (roiOut != null && roiOut.getCount() > 0) {

                IJ.log(i2r.get(i) + " ---> " + roiOut.getCount() + " roi elements");

                File directory = getFileMLRegionDirectory(imDir, i2r.get(i));

                if (!directory.exists()) {

                    directory.mkdirs(); // mkdirs(); for subdirs

                    IJ.log("Created " + directory.getAbsolutePath());
                }

                // save the annotated image with the added property
                FileSaver fs = new FileSaver(imOut);
                String imOutPath = getFileMLRegionImage(imDir, imName, i2r.get(i)).getAbsolutePath();
                fs.saveAsTiff(imOutPath);
                IJ.log("export " + imOutPath);

                roiOut.runCommand("Save", getFileMLRegionRoi(imDir, imName, i2r.get(i)).getAbsolutePath());

                IJ.log("export " + getFileMLRegionRoi(imDir, imName, i2r.get(i)).getAbsolutePath());

            }

            roiOut.close();
        }
    }

    public void mouseMoved(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        updateCircle();

        if (drawMode) {

            mouseClicked(e);

        }

    }

    public void imageClosed(ImagePlus imagePlus) {

        String closedImageName = imagePlus.getTitle();

        String imputImageName = inImg.getTitle();

        if (closedImageName.equals((imputImageName))) {

            exportAnnotation(imPath, imName);

            if (imWind != null)
                imWind.removeKeyListener(this);

            if (imCanv != null)
                imCanv.removeKeyListener(this);

            ImagePlus.removeImageListener(this);

        } else {
            System.out.println("Closed image was not input image.");
        }

    }

    public void keyPressed(KeyEvent e) {
    }

    public void imageOpened(ImagePlus imagePlus) {
    }

    public void imageUpdated(ImagePlus imagePlus) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }
}
