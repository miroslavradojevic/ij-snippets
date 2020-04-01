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
import java.util.HashMap;
import java.util.Map;

import static com.braincadet.bacannot.Constants.COLOR_NEGATIVE;
import static com.braincadet.bacannot.Constants.COLOR_POSITIVE;

public class BacteriaRegions implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    float pickX, pickY, pickR; // 'picked' circle params
    boolean begunPicking;
    boolean drawMode = false;

    public enum BacteriaRegionType {POS, NEG}
    BacteriaRegionType currentBacteriaRegion;

    ImagePlus inImg;
    ImageWindow imWind;
    ImageCanvas imCanv;
    String imPath, imDir, imName;

    Overlay overlayMLRegions;

    Map<BacteriaRegionType, Integer> r2i;
    Map<Integer, BacteriaRegionType> i2r;
    Map<Integer, Color> i2c;

    @Override
    public void run(String s) {

        r2i = new HashMap<>();
        r2i.put(BacteriaRegionType.NEG, 0);
        r2i.put(BacteriaRegionType.POS, 1);

        i2r = new HashMap<>();
        i2r.put(0, BacteriaRegionType.NEG);
        i2r.put(1, BacteriaRegionType.POS);

        i2c = new HashMap<>();
        i2c.put(0, Constants.COLOR_NEGATIVE);
        i2c.put(1, Constants.COLOR_POSITIVE);

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
        currentBacteriaRegion = BacteriaRegionType.NEG; // initial ml region type

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        overlayMLRegions = new Overlay();

        for (BacteriaRegionType reg : BacteriaRegionType.values()) {

            IJ.log("" + reg + "   " + reg.toString().toLowerCase());

            File fAnnot = new File(imDir + File.separator + "annot_" + reg.toString().toLowerCase() + File.separator + imName + ".zip");

            if (fAnnot.exists()) {

                RoiManager rm = new RoiManager();

                rm.runCommand("Open", fAnnot.getAbsolutePath());

                for (int i = 0; i < rm.getCount(); i++) {

                    overlayMLRegions.add(rm.getRoi(i));

                }

                rm.close();

            }

        }

        begunPicking = false;

        currentBacteriaRegion = BacteriaRegionType.NEG;

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

    private void updateCircle() {

        // update pick circle
        OvalRoi circAdd = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);

        switch (currentBacteriaRegion) {
            case NEG:
                circAdd.setStrokeColor(COLOR_NEGATIVE);
                break;
            case POS:
                circAdd.setStrokeColor(COLOR_POSITIVE);
                break;
            default:
                break;
        }

        circAdd.setStrokeWidth(Constants.ANNOTATOR_OUTLINE_WIDTH);

        if (!begunPicking) {

            begunPicking = true;

            overlayMLRegions.add(circAdd);

        } else {

            int lastIndex = overlayMLRegions.size() - 1;

            overlayMLRegions.remove(lastIndex);

            overlayMLRegions.add(circAdd);

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

    @Override
    public void mouseClicked(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        imCanv.getImage().updateAndDraw();

        switch (currentBacteriaRegion) {
            case NEG:
                addCircle(COLOR_NEGATIVE);
                break;
            case POS:
                addCircle(COLOR_POSITIVE);
                break;
            default:
                break;
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

    @Override
    public void imageOpened(ImagePlus imagePlus) {

    }

    @Override
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

    @Override
    public void imageUpdated(ImagePlus imagePlus) {

    }

    @Override
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
//                selectMLRegionType();
                switchMLRegionType();
                break;
            default:
                break;
        }

        updateCircle();

    }

    private void selectMLRegionType() {

        String[] arrayMLRegions = new String[]{BacteriaRegionType.NEG.toString(), BacteriaRegionType.POS.toString()};

        GenericDialog gd = new GenericDialog("Choose ML region:");

        gd.addChoice("type", arrayMLRegions, arrayMLRegions[0]);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return;
        } else {
            String regionType = gd.getNextChoice();

            if (regionType.equalsIgnoreCase(BacteriaRegionType.NEG.toString())){
                currentBacteriaRegion = BacteriaRegionType.NEG;
            }
            else if (regionType.equalsIgnoreCase(BacteriaRegionType.POS.toString())) {
                currentBacteriaRegion = BacteriaRegionType.POS;
            }
        }
    }

    private void switchMLRegionType(){
        if (currentBacteriaRegion == BacteriaRegionType.NEG) {
            currentBacteriaRegion = BacteriaRegionType.POS;
        }
        else if (currentBacteriaRegion == BacteriaRegionType.POS) {
            currentBacteriaRegion = BacteriaRegionType.NEG;
        }
    }

    private void exportOverlayAnnot(String originInfo) {

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

                File directory = new File(imDir + File.separator + "annot_" + i2r.get(i).toString().toLowerCase() + File.separator);

                if (!directory.exists()) {

                    directory.mkdirs(); // mkdirs(); for subdirs

                    IJ.log("Created " + directory.getAbsolutePath());
                }

                // save the annotated image with the added property
                FileSaver fs = new FileSaver(imOut);
                File imOutFile = new File(imDir + File.separator + "annot_" + i2r.get(i).toString().toLowerCase() + File.separator + imName + ".tif");
                String imOutPath = imOutFile.getAbsolutePath();
                fs.saveAsTiff(imOutPath);
                IJ.log("export " + imOutPath);

                File regOutFile = new File(imDir + File.separator + "annot_" + i2r.get(i).toString().toLowerCase() + File.separator + imName + ".zip");
                roiOut.runCommand("Save", regOutFile.getAbsolutePath());

            }

            roiOut.close();
        }
    }



    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        updateCircle();

        if (drawMode) {

            mouseClicked(e);

        }
    }
}
