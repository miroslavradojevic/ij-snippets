package com.braincadet.annot;

import ij.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.Raw;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class AnnotatorBattery implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    float pickX, pickY, pickR; // 'picked' circle params
    boolean begunPicking; // denotes whether the task was started
    boolean drawMode = false; // chooses whether to start periodic clicking while moving mouse
    int countMoves = 0;
    int CLICK_FREQUENCY = 5;
    ImagePlus inImg;
    ImageWindow imWind;
    ImageCanvas imCanv;
    String imPath, imDir, imName;
    int imStackSize;
    Overlay overlayAnnot;

    @Override
    public void run(String s) {

        String inFolder = Prefs.get("com.braincadet.annot.inFolder", System.getProperty("user.home"));
        OpenDialog.setDefaultDirectory(inFolder);
        OpenDialog dc = new OpenDialog("Select image");
        inFolder = dc.getDirectory();
        imPath = dc.getPath();
        if (imPath == null) {
            IJ.log("Could not open selected image path");
            return;
        }

        Prefs.set("com.braincadet.annot.inFolder", inFolder);

        IJ.log("inFolder="+inFolder);
        IJ.log("imPath="+imPath);

        String imExt = Tools.getFileExtension(imPath);

        // read image depending on the extension
        if (imExt.equalsIgnoreCase("TIF") ||
                imExt.equalsIgnoreCase("TIFF") ||
                imExt.equalsIgnoreCase("JPG")
        ) {
            inImg = new ImagePlus(imPath);
        }
        else if (imExt.equalsIgnoreCase("RAW")) {
            FileInfo fi = new FileInfo();
            fi.width=1800; // TODO: parse from the image name
            fi.height=500; // TODO: parse from the image name
            fi.nImages=80; // TODO: parse from the image name
            fi.intelByteOrder = true;
            fi.fileType=FileInfo.GRAY32_FLOAT;
            fi.fileFormat=FileInfo.RAW;
            IJ.log("w=["+fi.width+"], h=["+fi.height+"], nImages=["+fi.nImages+"], intelByteOrder=["+fi.intelByteOrder+"], fileType=["+fi.fileType+"], fileFormat=["+fi.fileFormat+"]");
            inImg = Raw.open(imPath, fi);
        }
        else {
            IJ.log("Image extension ["+imExt+"] not supported");
            return;
        }

        IJ.log("title=["+inImg.getTitle()+"], w=["+inImg.getWidth()+"], h=["+inImg.getHeight()+"], l=["+inImg.getStackSize()+"]");

        imStackSize = inImg.getStackSize();

        pickR = 0.05f*Math.min(inImg.getWidth(), inImg.getHeight()); // initial circle size
        pickX = 0;
        pickY = 0;

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        IJ.log("imDir="+imDir);
        IJ.log("imName="+imName);

        // initialize Overlays with the annotations encountered in ./ANNOTATION_OUTPUT_DIR_NAME/
        overlayAnnot = new Overlay();
        // read existing annotation overlay if existent
        File ff = new File(imDir + File.separator + Constants.ANNOTATION_OUTPUT_DIR_NAME + File.separator + imName + ".zip");

        if (ff.exists()) {

            IJ.log("found annotation:\n" + ff.getPath());

            RoiManager rm = new RoiManager();

            rm.runCommand("Open", ff.getPath());

            if (rm.getCount() > 0) {

                rm.moveRoisToOverlay(inImg);

                overlayAnnot = inImg.getOverlay();

                rm.close();
            }
        }

        begunPicking = false;
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

        IJ.setTool("hand");
        IJ.log("Begin with anotations...");
    }

    /** Returns the current cursor layer index. */
    public int getCursorZ() {

        Calibration cal = imCanv.getImage().getCalibration();

        if (imCanv.getImage().getProperty("FHT")!=null)
            return 0;

        int z = 0;

        if (imCanv.getImage().getStackSize()>1) {
            Roi roi2 = imCanv.getImage().getRoi();
            if (roi2==null || roi2.getState()==Roi.NORMAL) {
                int z1 = imCanv.getImage().isDisplayedHyperStack()?imCanv.getImage().getSlice()-1:imCanv.getImage().getCurrentSlice()-1;
                z = (int)cal.getZ(z1);
            }
        }

        return z;
    }

    private void addCircle(Color col) {
        // use pickX, pickY and pickR to check if it does not overlap
        for (int i = 0; i < overlayAnnot.size() - 1; i++) { // check all the previous ones

            float x = (float) overlayAnnot.get(i).getFloatBounds().getX();
            float y = (float) overlayAnnot.get(i).getFloatBounds().getY();
            float w = (float) overlayAnnot.get(i).getFloatBounds().getWidth();
            float h = (float) overlayAnnot.get(i).getFloatBounds().getHeight();
            float r = (float) (Math.max(w, h) / 2);

            Color cl = overlayAnnot.get(i).getFillColor();

            x = x + r / 1 - .0f;
            y = y + r / 1 - .0f;

            boolean overlap = (x - pickX) * (x - pickX) + (y - pickY) * (y - pickY) <= (r + pickR) * (r + pickR);
            overlap = false; // don't check if it overlaps

            // allow only ignores on top of ignores
            if (col.equals(Constants.COLOR_IGNORE)) {
                if (overlap && !cl.equals(Constants.COLOR_IGNORE)) {
                    IJ.log("Ignore: cannot be added on top of existing non-ignore");
                    return;
                }
            } else {
                if (overlap) {
                    IJ.log("non-ignore cannot be added on top of anything");
                    return;
                }
            }
        }

        OvalRoi cc = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);
        OvalRoi ccc = cc;
        cc.setFillColor(col);
        cc.setStrokeColor(col);
//        IJ.log("current stack layer="+imCanv.getImage().getLocationAsString((int)pickX, (int)pickY));
//        IJ.log("current stack layer="+getCursorZ()+1);
        cc.setPosition(getCursorZ()+1); // Overlay indexing: setPosition(n) 1<=n<=nslices

        if (begunPicking) {
            overlayAnnot.remove(overlayAnnot.size() - 1);   // the last one is always the currently plotted
            overlayAnnot.add(cc); // cc.getPosition()
            overlayAnnot.add(ccc); // ccc.getPosition()
        }

        imCanv.setOverlay(overlayAnnot);
        imCanv.getImage().updateAndDraw();

//        IJ.log("added, annotation has " + (overlayAnnot.size() - 1) + " elements");
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        pickX = imCanv.offScreenX(e.getX());
        pickY = imCanv.offScreenY(e.getY());
        imCanv.getImage().updateAndDraw();
        addCircle(Constants.COLOR_ANNOT);
    }

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {}

    private void updateCircle() {

        // update the pick circle
        OvalRoi circAdd = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);
        circAdd.setStrokeColor(Color.YELLOW);
        circAdd.setStrokeWidth(Constants.ANNOTATOR_OUTLINE_WIDTH);

        if (!begunPicking) {
            begunPicking = true;
            overlayAnnot.add(circAdd);
        } else {
            overlayAnnot.remove(overlayAnnot.size() - 1);
            overlayAnnot.add(circAdd);
        }

        imCanv.setOverlay(overlayAnnot);
        imCanv.getImage().updateAndDraw();

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        pickX = imCanv.offScreenX(e.getX());
        pickY = imCanv.offScreenY(e.getY());

        updateCircle();

        countMoves++;
        countMoves = countMoves % CLICK_FREQUENCY;

        if (drawMode) {

            if (countMoves == 0) {
                mouseClicked(e);
            }

        }
    }

    private void exportOverlayAnnot(boolean showResult, String originInfo) {

        File directory = new File(imDir + File.separator + Constants.ANNOTATION_OUTPUT_DIR_NAME + File.separator);

        if (!directory.exists()) {

            directory.mkdir(); // mkdirs() for subdirs

            IJ.log("Created " + directory.getAbsolutePath());
        }

//        IJ.log("output " + inImg.getWidth() +", "+ inImg.getHeight()+", "+imStackSize);

        ImageStack isOut = new ImageStack(inImg.getWidth(), inImg.getHeight());
        for (int i = 0; i < imStackSize; i++) {
            isOut.addSlice(new ByteProcessor(inImg.getWidth(), inImg.getHeight()));
        }
        ImagePlus imOut = new ImagePlus(imName, isOut);

        RoiManager rm = new RoiManager();

        IJ.log((overlayAnnot.size()-1)+ " overlay elements");

        for (int i = 0; i < overlayAnnot.size() - 1; i++) { // exclude the last one because it is the pointer circle

            int xPatch = overlayAnnot.get(i).getBounds().x;
            int yPatch = overlayAnnot.get(i).getBounds().y;
            int wPatch = overlayAnnot.get(i).getBounds().width;

//            IJ.log("x="+xPatch + ", y=" + yPatch + ", w=" + wPatch + ", z=" + overlayAnnot.get(i).getPosition());

            byte[] ovAnnotArray = (byte[]) overlayAnnot.get(i).getMask().convertToByteProcessor().getPixels(); //.getMaskArray();
            byte[] imOutArray = (byte[]) imOut.getStack().getProcessor(overlayAnnot.get(i).getPosition()).getPixels(); // 1<=n<=nslices

            int countNew = 0;

            for (int j = 0; j < ovAnnotArray.length; j++) {
                if ((ovAnnotArray[j] & 0xff) == 255) {
                    // get global image coords and overwrite I(xOut, yOut) to 255
                    int xOut = xPatch + (j % wPatch);
                    int yOut = yPatch + (j / wPatch);

                    if (xOut >= 0 && xOut < inImg.getWidth() && yOut >= 0 && yOut < inImg.getHeight()) {
                        if (imOutArray[yOut * inImg.getWidth() + xOut] != (byte) 255) {
                            countNew++;
                        }
                        imOutArray[yOut * inImg.getWidth() + xOut] = (byte) 255;
                    }
                }
            }

            if (countNew > 0) {
                rm.addRoi(overlayAnnot.get(i));
            }

        }

        IJ.log((overlayAnnot.size() - 1) + " >> " + rm.getCount());

//      save metadata: http://imagej.1557.x6.nabble.com/Push-Metadata-td4999917.html
        imOut.setProperty("Info", originInfo); // set new info

        // save the annotated image with the added property
        FileSaver fs = new FileSaver(imOut);
        String imOutPath = directory.getPath() + File.separator + imName + ".tif";
        fs.saveAsTiff(imOutPath);
        IJ.log("Exported:\n" + imOutPath);

        rm.runCommand("Save", directory.getPath() + File.separator + imName + ".zip");
        rm.moveRoisToOverlay(imOut);
        rm.close();

        if (showResult) {
            imOut.setTitle(inImg.getTitle() + "annot");
            imOut.show();
        }

    }

    private void exportAnnotation(String origin, String tag) {

        GenericDialog gd = new GenericDialog("Export annotations?");
        gd.addStringField("origin", origin, 60);
        gd.addStringField("tag", tag, 40);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        } else {
            String originPath = gd.getNextString().replace("\"", "");
            String originTag = gd.getNextString();
            exportOverlayAnnot(false, originPath + "|" + originTag);
        }
    }

    private void removeCircle() {

        boolean found = false;

        // loop current list of detections and delete if any falls over the current position
        for (int i = 0; i < overlayAnnot.size() - 1; i++) { // the last one is always updated pick circle

            float xCurr = pickX;
            float yCurr = pickY;
            float rCurr = pickR;

            float xOvl = (float) (overlayAnnot.get(i).getFloatBounds().getX() + overlayAnnot.get(i).getFloatBounds().getWidth() / 2f - .0f);
            float yOvl = (float) (overlayAnnot.get(i).getFloatBounds().getY() + overlayAnnot.get(i).getFloatBounds().getHeight() / 2f - .0f);
            float rOvl = (float) (Math.max(overlayAnnot.get(i).getFloatBounds().getWidth(), overlayAnnot.get(i).getFloatBounds().getHeight()) / 2f);

            float d2 = (float) (Math.sqrt(Math.pow(xCurr - xOvl, 2) + Math.pow(yCurr - yOvl, 2)));
            float d2th = (float) Math.pow(rCurr + rOvl, 1);

            if (d2 < d2th) {
                found = true;
                overlayAnnot.remove(i);
            }

        }

        if (!found) IJ.log("nothing to remove");
        else IJ.log("removed, current annotation overlay size: " + (overlayAnnot.size() - 1));

    }

    @Override
    public void keyTyped(KeyEvent e) {
        pickR = Constants.modifyCircleRadius(e.getKeyChar(), pickR, Math.min(inImg.getWidth(), inImg.getHeight()), Constants.R_MIN + Constants.R_STEP);
        if (e.getKeyChar() == '+') {
            imCanv.zoomIn((int) pickX, (int) pickY);
        } else if (e.getKeyChar() == '-') {
            imCanv.zoomOut((int) pickX, (int) pickY);
        } else if (e.getKeyChar() == Constants.EXPORT_COMMAND) {
            exportAnnotation(imPath, imName);
        } else if (e.getKeyChar() == Constants.DELETE_COMMAND) {
            removeCircle();
        } else if (e.getKeyChar() == Constants.PERIODIC_CLICK) {
            drawMode = !drawMode;
        } else if (e.getKeyChar() == Constants.HELP) {
            IJ.showMessage("Keyboard commands",
                    "h:\thelp\n"+
                            "p:\tactivate/deactivate draw mode (periodic click)\n"+
                            "d:\tdelete underlying element (corrections)\n"+
                            "+:\tzoom canvas in\n"+
                            "-:\tzoom canvas out\n"+
                            "u:\tincrease circle radius\n"+
                            "j:\tdecrease circle radius\n"+
                            "e:\texport current annotation");
        }
        updateCircle();
    }

    @Override
    public void keyPressed(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void imageOpened(ImagePlus ip) {}

    @Override
    public void imageClosed(ImagePlus ip) {
        String closedImageName = ip.getTitle();
        String imputImageName = inImg.getTitle();
        IJ.log(imputImageName);
        IJ.log("closed: " + inImg.getStack().getWidth() + ", " + inImg.getStack().getHeight() + ", " + imStackSize);

        if (closedImageName.equals((imputImageName))) {

            IJ.log(overlayAnnot.size()+" items");

            exportAnnotation(imPath, imName);

            if (imWind != null)
                imWind.removeKeyListener(this);

            if (imCanv != null)
                imCanv.removeKeyListener(this);

            ImagePlus.removeImageListener(this);
        } else {
            IJ.log("Closed image was not the input image.");
        }
    }

    @Override
    public void imageUpdated(ImagePlus ip) {}

}
