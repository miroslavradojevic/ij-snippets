package com.braincadet.bacannot;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;

import java.awt.event.*;
import java.awt.Color;
import java.io.File;

public class BacteriaAnnotator implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    float pickX, pickY, pickR; // 'picked' circle params
    boolean begunPicking;

    ImagePlus inImg;
    ImageWindow imWind;
    ImageCanvas imCanv;
    String imPath, imDir, imName;
    Overlay overlayAnnot;

    private static final int  changeR = 2;
    private static final Color ignoreColor = Color.CYAN;
    private static final Color annotColor = new Color(1.0f, 1.0f, 0.0f, 0.6f);

    private static final char EXPORT_COMMAND = 'e';
    private static final char DELETE_COMMAND = 'd';
    private static final char INCREASE_CIRCLE_RADIUS = 'u';
    private static final char DECREASE_CIRCLE_RADIUS = 'j';
    private static final String ANNNOT_DIR_NAME = "annot";

    private static final float ANNOTATOR_OUTLINE_WIDTH = 1f;

    @Override
    public void run(String s) {

        // Open the image for the annotation
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

        boolean acceptedExtensions =
                Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("TIF") ||
                Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("JPG");

        if(acceptedExtensions == false) {
            IJ.showStatus("open image with .TIF or .JPG extension only");
            return;
        }

        pickR = 10;//Math.round(Math.min(inImg.getWidth(), inImg.getHeight()) / 30f); // initial size of the circle
        pickX = 0;
        pickY = 0;

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        // look for the annotations and initialize Overlays with the annotations encountered in ./ANNNOT_DIR_NAME/
        overlayAnnot = new Overlay();

        // use RoiManager to read existing annotation overlay if existent
        File ff = new File(imDir + File.separator + ANNNOT_DIR_NAME + File.separator + imName + ".zip");

        System.out.println("trye to read existing annotation at:");
        System.out.println(ff.getPath());

        if (ff.exists()){
            RoiManager rm = new RoiManager();
            rm.runCommand("Open", ff.getPath());
            if (rm.getCount()>0) {

//                System.out.println("overlay before = " + ((inImg.getOverlay() != null)? inImg.getOverlay().size() : "none"));

                rm.moveRoisToOverlay(inImg);

//                System.out.println("overlay after = " + inImg.getOverlay().size());

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

        IJ.showStatus("loaded " + imName);
        IJ.setTool("hand");

    }

    private void updateCircle() {

        // update the pick circle
        OvalRoi circAdd = new OvalRoi(pickX-pickR+.0f, pickY-pickR+.0f, 2*pickR, 2*pickR);
        circAdd.setStrokeColor(Color.RED);
        circAdd.setStrokeWidth(ANNOTATOR_OUTLINE_WIDTH);

        if (!begunPicking) {
            begunPicking = true;
            overlayAnnot.add(circAdd);
        }
        else {
            overlayAnnot.remove(overlayAnnot.size()-1);
            overlayAnnot.add(circAdd);
        }

        imCanv.setOverlay(overlayAnnot);
        imCanv.getImage().updateAndDraw();

    }

    private void exportOverlayAnnot(boolean showResult, String originInfo) {

        File directory = new File(imDir + File.separator + ANNNOT_DIR_NAME + File.separator);

        if (! directory.exists()){

            directory.mkdir(); // use directory.mkdirs(); for subdirs
            System.out.println("Created " + directory.getAbsolutePath());
        }

        ImagePlus imOut = new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()));

        RoiManager rm = new RoiManager();

        for (int i = 0; i < overlayAnnot.size() - 1; i++) { // exclude the last one because it is the pointer circle

            int xPatch = overlayAnnot.get(i).getBounds().x;
            int yPatch = overlayAnnot.get(i).getBounds().y;
            int wPatch = overlayAnnot.get(i).getBounds().width;
//                int hPatch = overlayAnnot.get(i).getBounds().height;

            byte[] ovAnnotArray = (byte[]) overlayAnnot.get(i).getMask().convertToByteProcessor().getPixels(); //.getMaskArray();
            byte[] imOutArray = (byte[]) imOut.getProcessor().getPixels();

            for (int j = 0; j < ovAnnotArray.length; j++) {
                if ( (ovAnnotArray[j] & 0xff) == 255 ) {
                    // get global image coords and overwrite I(xOut, yOut) to 255
                    int xOut = xPatch + (j%wPatch);
                    int yOut = yPatch + (j/wPatch);

                    if (xOut>=0 && xOut < inImg.getWidth() && yOut>=0 && yOut < inImg.getHeight()) {
                        imOutArray[yOut * inImg.getWidth() + xOut] = (byte) 255;
                    }
                }
            }

            // go through 255 elements of tt and draw them to the offset location
            rm.addRoi(overlayAnnot.get(i));
        }

//      save metadata: http://imagej.1557.x6.nabble.com/Push-Metadata-td4999917.html
        imOut.setProperty("Info", originInfo); // set new info

        // save the annotated image with the added property
        FileSaver fs = new FileSaver(imOut);
        String imOutPath = directory.getPath() + File.separator + imName + ".tif";
        fs.saveAsTiff(imOutPath);
        System.out.println("Exported:\n" + imOutPath);

        rm.runCommand("Save", directory.getPath() + File.separator + imName + ".zip");
        rm.moveRoisToOverlay(imOut);
        rm.close();

        if (showResult) {
            imOut.setTitle(inImg.getTitle()+"annot");
            imOut.show();
        }

    }

    private void removeCircle(){

        boolean found = false;

        // loop current list of detections and delete if any falls over the current position
        for (int i = 0; i< overlayAnnot.size()-1; i++) { // the last one is always updated pick circle

            float xCurr = pickX;
            float yCurr = pickY;
            float rCurr = pickR;

            float xOvl = (float) (overlayAnnot.get(i).getFloatBounds().getX() +  overlayAnnot.get(i).getFloatBounds().getWidth()/2f - .0f);
            float yOvl = (float) (overlayAnnot.get(i).getFloatBounds().getY() + overlayAnnot.get(i).getFloatBounds().getHeight()/2f - .0f);
            float rOvl = (float) (Math.max(overlayAnnot.get(i).getFloatBounds().getWidth(), overlayAnnot.get(i).getFloatBounds().getHeight()) / 2f);

            float d2 = (float) (Math.sqrt(Math.pow(xCurr-xOvl, 2) + Math.pow(yCurr-yOvl, 2)));
            float d2th = (float) Math.pow(rCurr+rOvl, 1);

            if (d2<d2th) {
                found = true;
                overlayAnnot.remove(i);
            }

        }

        if (!found) IJ.showStatus("nothing to remove");
        else 		IJ.showStatus("removed, current annot ovly size: " + (overlayAnnot.size()-1));

    }

    private void addCircle(Color col){

        // use pickX, pickY and pickR to check if it does not overlap
        for (int i = 0; i < overlayAnnot.size()-1; i++) { // check all the previous ones

            float x = (float) overlayAnnot.get(i).getFloatBounds().getX();
            float y = (float) overlayAnnot.get(i).getFloatBounds().getY();
            float w = (float) overlayAnnot.get(i).getFloatBounds().getWidth();
            float h = (float) overlayAnnot.get(i).getFloatBounds().getHeight();
            float r = (float) (Math.max(w, h)/2);

            Color cl = overlayAnnot.get(i).getFillColor();

            x = x+r/1-.0f;
            y = y+r/1-.0f;

            boolean overlap = (x-pickX)*(x-pickX)+(y-pickY)*(y-pickY)<=(r+pickR)*(r+pickR);
            overlap = false; // don't check if it overlaps

            // allow only ignores on top of ignores
            if (col.equals(ignoreColor)) {
                if (overlap && !cl.equals(ignoreColor)) {
                    IJ.showStatus("ignore cannot be added on top of existing non-ignore");
                    return;
                }
            }
            else {
                if (overlap) {
                    IJ.showStatus("non-ignore cannot be added on top of anything");
                    return;
                }
            }
        }

        OvalRoi cc = new OvalRoi(pickX-pickR+.0f, pickY-pickR+.0f, 2*pickR, 2*pickR);
        OvalRoi ccc = cc;
        cc.setFillColor(col);
        cc.setStrokeColor(col);

        if (begunPicking) {
            overlayAnnot.remove(overlayAnnot.size()-1);   // the last one is always the currently plotted
            overlayAnnot.add(cc);
            overlayAnnot.add(ccc);
        }

        imCanv.setOverlay(overlayAnnot);
        imCanv.getImage().updateAndDraw();

        IJ.showStatus("added, current number of annotations: " + (overlayAnnot.size()-1));

    }

    @Override
    public void mouseClicked(MouseEvent e) {

        pickX = 	imCanv.offScreenX(e.getX());
        pickY = 	imCanv.offScreenY(e.getY());

        imCanv.getImage().updateAndDraw();

        IJ.showStatus("mouseClicked at x=" + pickX + " y=" + pickY);

        // process the clicked circle
        addCircle(annotColor);

    }

    @Override
    public void keyTyped(KeyEvent e) {

        if (e.getKeyChar() == INCREASE_CIRCLE_RADIUS) {
            pickR += (pickR < Math.min(inImg.getWidth(), inImg.getHeight()))? changeR : 0 ;
            IJ.showStatus("R = " + pickR + " R-=" + changeR);
        }

        if (e.getKeyChar() == DECREASE_CIRCLE_RADIUS) {
            pickR -= (pickR < Math.min(inImg.getWidth(), inImg.getHeight()))? changeR : 0 ;
            IJ.showStatus("R = " + pickR + " R+=" + changeR);
        }

        if (e.getKeyChar()=='+') {
            imCanv.zoomIn((int) pickX, (int) pickY);
        }

        if (e.getKeyChar()=='-') {
            imCanv.zoomOut((int) pickX, (int) pickY);
        }

        if (e.getKeyChar() == EXPORT_COMMAND) {

            //
            exportAnnotation(imPath, imName);







        }

        if (e.getKeyChar() == DELETE_COMMAND) {
            removeCircle();
        }

        updateCircle();
    }

    private void exportAnnotation(String origin, String tag){
        GenericDialog gd = new GenericDialog("Export annotations?");
        gd.addStringField("origin", origin, 60);
        gd.addStringField("tag", tag, 40);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        }
        else {
            String originPath = gd.getNextString().replace("\"", "");
            String originTag = gd.getNextString();
            exportOverlayAnnot(false, originPath + "|" + originTag);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        pickX = imCanv.offScreenX(e.getX());
        pickY = imCanv.offScreenY(e.getY());
        updateCircle();
    }

    @Override
    public void imageClosed(ImagePlus imagePlus) {

        String closedImageName = imagePlus.getTitle();
        String imputImageName = inImg.getTitle();

        if (closedImageName.equals((imputImageName))) {



            exportAnnotation(imPath, imName);

//            GenericDialog gd = new GenericDialog("Export annotations?");
//            gd.addStringField("origin", imPath, 60);
//            gd.addStringField("tag", imName, 40);
//            if (gd.wasCanceled()) {
//                return;
//            }
//            else {
//                String originPath = gd.getNextString().replace("\"", "");
//                String originTag = gd.getNextString();
//                exportOverlayAnnot(false, originPath + "|" + originTag);
//            }





            if (imWind!=null)
                imWind.removeKeyListener(this);

            if (imCanv!=null)
                imCanv.removeKeyListener(this);

            ImagePlus.removeImageListener(this);

        }
        else {
            System.out.println("closed image was not the input image.");
        }


    }

    @Override
    public void keyPressed(KeyEvent e) {}
    @Override
    public void imageOpened(ImagePlus imagePlus) {}
    @Override
    public void imageUpdated(ImagePlus imagePlus) {}
    @Override
    public void keyReleased(KeyEvent e) {}
    @Override
    public void mouseReleased(MouseEvent e) {}
    @Override
    public void mousePressed(MouseEvent e) {}
    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}
    @Override
    public void mouseDragged(MouseEvent e) {}
}
