package com.braincadet.bacannot;

import ij.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.awt.*;
import java.awt.event.*;

public class ColonyInspector implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    float pickX, pickY;
    int pickR; //
    private static int pickRMax = 16; // picked radius should not go more than this value
    private static int pickRMin = 2; // picked radius should not go more than this value

    boolean begunPicking;
    ImagePlus inImg;
    ImageWindow imWind;
    ImageCanvas imCanv;

    String imPath, imDir, imName;
    Overlay overlayAnnot;

    ImagePlus inspectProfileImage = new ImagePlus();
    ImageStack inspectProfileStack = null;
    boolean inspectProfileIsFirst = true;

    ImagePlus inspectPatchImage = new ImagePlus();
    ImageStack inspectPatchStack = null;
    boolean inspectPatchIsFirst = true;

    int upperLeftX = 20;
    int upperLeftY = 20;

    private static final int  changeR = 2;
    private static final Color annotColor = new Color(255, 255, 0, 60);

    float[][] profiles; // getProfilePlots()
    float[] profilesZ;
    byte[][] patches; // getPatches()


    private static final char EXPORT_COMMAND = 'e';
    private static final char DELETE_COMMAND = 'd';
    private static final char INCREASE_CIRCLE_RADIUS = 'u';
    private static final char DECREASE_CIRCLE_RADIUS = 'j';

    @Override
    public void run(String s) {

        // Open the image for the inspection
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
            IJ.showStatus("null image at: " + imPath);
            return;
        }

        if (inImg.getType() != ImagePlus.GRAY8) {
            IJ.showStatus("Image needs to be 8-bit");
            return;
        }

        boolean acceptedExtensions =
                Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("TIF") ||
                        Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("JPG");

        if(acceptedExtensions == false) {
            IJ.showStatus("open image with .TIF or .JPG extension only");
            return;
        }

        pickR = pickRMax;//Math.round(Math.min(inImg.getWidth(), inImg.getHeight()) / 35f); // initial size of the circle
        pickX = 0;
        pickY = 0;

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        //
        profiles = new float[(int)Math.pow(2*pickRMax+1, 2)][inImg.getStack().getSize()];
        profilesZ = new float[inImg.getStack().getSize()];
        patches = new byte[inImg.getStack().getSize()][];

        for (int i = 0; i < inImg.getStack().getSize(); i++) {
            profilesZ[i] = i;
        }
        
        // look for the annotations and initialize Overlays with the annotations encountered in ./ANNNOT_DIR_NAME/
        overlayAnnot = new Overlay();

        // use RoiManager to read existing annotation overlay if existent
        /*
        File ff = new File(imDir + File.separator + ANNNOT_DIR_NAME + File.separator + imName + ".zip");

        System.out.println("trye to read existing annotation at:");
        System.out.println(ff.getPath());

        if (ff.exists()){
            RoiManager rm = new RoiManager();
            rm.runCommand("Open", ff.getPath());
            if (rm.getCount()>0) {

                System.out.println("overlay before = " + ((inImg.getOverlay() != null)? inImg.getOverlay().size() : "none"));

                rm.moveRoisToOverlay(inImg);

                System.out.println("overlay after = " + inImg.getOverlay().size());

                overlayAnnot = inImg.getOverlay();
                rm.close();
            }
        }
        */

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


    }

    private void updateCircle() {

        OvalRoi circAdd = new OvalRoi(pickX-pickR+.0f, pickY-pickR+.0f, 2*pickR, 2*pickR);
        circAdd.setStrokeColor(Color.RED);
        circAdd.setStrokeWidth(1);

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
            /*
            if (col.equals(ignoreColor)) {
                if (overlap && !cl.equals(ignoreColor)) {
                    IJ.showStatus("ignore cannot be added on top of existing non-ignore");
                    return;
                }
            }
            else {
            */
                if (overlap) {
                    IJ.showStatus("non-ignore cannot be added on top of anything");
                    return;
                }
            /*
            }
            */
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

    @Override
    public void imageOpened(ImagePlus imagePlus) {}

    @Override
    public void imageUpdated(ImagePlus imagePlus) {}

    @Override
    public void keyTyped(KeyEvent e) {

        if (e.getKeyChar() == INCREASE_CIRCLE_RADIUS) {
            pickR += changeR;
            pickR = (pickR>pickRMax)? pickRMax : pickR;
        }

        if (e.getKeyChar() == DECREASE_CIRCLE_RADIUS) {
            pickR -= changeR;
            pickR = (pickR>0)? pickR : pickRMin;
        }

        if (e.getKeyChar()=='+') {
            imCanv.zoomIn((int) pickX, (int) pickY);
        }

        if (e.getKeyChar()=='-') {
            imCanv.zoomOut((int) pickX, (int) pickY);
        }

        if (e.getKeyChar() == EXPORT_COMMAND) {
//            exportOverlayAnnot(true);
        }

        if (e.getKeyChar() == DELETE_COMMAND) {
            removeCircle();
        }

//        if (e.getKeyChar()=='s') {
//            GenericDialog gd = new GenericDialog("Save?");
//            gd.addStringField("output path", gndtth_path, gndtth_path.length()+10);
//            gd.showDialog();
//            if (gd.wasCanceled()) return;
//            String gndtth_path_spec = gd.getNextString();
//            export(gndtth_path_spec);
//        }

        updateCircle();

    }

    @Override
    public void keyPressed(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {

//        pickX = 	imCanv.offScreenX(e.getX());
//        pickY = 	imCanv.offScreenY(e.getY());
//        imCanv.getImage().updateAndDraw();

        inspectProfileStack = getProfilePlots(Math.round(pickX), Math.round(pickY), Math.round(pickR));

        if (inspectProfileStack != null) {

            inspectProfileImage.setStack("Colony Intensity Profile", inspectProfileStack);

            if (inspectProfileIsFirst) {
                inspectProfileImage.show();
                inspectProfileImage.getWindow().setLocation(upperLeftX, upperLeftY);
                inspectProfileIsFirst = false;
            }

            inspectProfileImage.updateAndDraw();

        }

        inspectPatchStack = getPatchArrays(Math.round(pickX), Math.round(pickY), Math.round(pickR));

        if (inspectPatchStack != null) {

            inspectPatchImage.setStack("Colony Patch", inspectPatchStack);

            if (inspectPatchIsFirst) {
                inspectPatchImage.show();
                inspectPatchImage.getWindow().setLocation(upperLeftX, upperLeftY+200);
                inspectPatchIsFirst = false;
            }

            inspectPatchImage.updateAndDraw();
            inspectPatchImage.getCanvas().zoomIn(0, 0);

        }

        IJ.setTool("hand");

    }

    public ImageStack getPatchArrays(int atX, int atY, int atR) {
        if (atX-atR >= 0 && atX+atR < inImg.getWidth() && atY-atR >= 0 && atY+atR < inImg.getHeight()) {

            ImageStack isOut = new ImageStack((2*atR+1), (2*atR+1));

            for (int i=0; i < inImg.getStack().getSize(); i++) {
                byte[] layer = new byte[(2*atR+1)*(2*atR+1)];
                int layIdx = 0;
                for (int dX = -atR; dX <= atR; dX++) {
                    for (int dY = -atR; dY <= atR; dY++) {
                        byte[] aa = (byte[])inImg.getStack().getProcessor(i+1).getPixels();
                        int idx = (atY+dY) * inImg.getWidth() + (atX+dX);
                        layer[layIdx] = aa[idx];// & 0xff;
                        layIdx++;
                    }
                }

                isOut.addSlice("t="+IJ.d2s(i,0), new ByteProcessor((2*atR+1), (2*atR+1), layer)); // p.getProcessor()

            }

            return isOut;
        }

        return null;

    }

    public ImageStack getProfilePlots(int atX, int atY, int atR){

        if (atX-atR >= 0 && atX+atR < inImg.getWidth() && atY-atR >= 0 && atY+atR < inImg.getHeight()) {

            Plot p = null;

            int profileIdx = 0;

            for (int dX = -atR; dX <= atR; dX++) {
                for (int dY = -atR; dY <= atR; dY++) {

                    for (int i=0; i < inImg.getStack().getSize(); i++) {
                        byte[] aa = (byte[])inImg.getStack().getProcessor(i+1).getPixels();
                        int idx = (atY+dY) * inImg.getWidth() + (atX+dX);
                        profiles[profileIdx][i] = aa[idx] & 0xff;
                    }


                    if (profileIdx==0) {
                        p = new Plot("", "h", "I", profilesZ, profiles[profileIdx]);
                    }
                            else {
                                p.addPoints(profilesZ, profiles[profileIdx], Plot.LINE);
                            }

                            profileIdx++;
                }
            }

            ImageStack isOut = new ImageStack(p.getProcessor().getWidth(), p.getProcessor().getHeight());
            isOut.addSlice(p.getProcessor());
            return isOut;

        }

        return null;

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

    @Override
    public void mouseMoved(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());
        pickY = imCanv.offScreenY(e.getY());
//        pickR =

        updateCircle();
//        mouseClicked(e);

    }

    public void imageClosed(ImagePlus imagePlus) {

        String closedImageName = imagePlus.getTitle();
        String imputImageName = inImg.getTitle();

        if (closedImageName.equals((imputImageName))) {



            GenericDialog gd = new GenericDialog("Save");
            gd.addMessage("Export annotations before closing?");
            gd.showDialog();
            if (gd.wasCanceled()) {
                return;
            }
            else {
//                exportOverlayAnnot(true);
            }

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


}
