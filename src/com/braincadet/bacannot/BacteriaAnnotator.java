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
import ij.process.ImageProcessor;

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
    Overlay ovAnnot;

    private static final int  changeR = 2;
    private static final Color ignoreColor = Color.CYAN;
    private static final Color annotColor = new Color(255, 255, 0, 60);

    private static final char EXPORT_COMMAND = 'e';
    private static final char INCREASE_CIRCLE_RADIUS = 'u';
    private static final char DECREASE_CIRCLE_RADIUS = 'j';
    private static final String ANNNOT_DIR_NAME = "annot";

    @Override
    public void run(String s) {

        // Open the image for the annotation
        String inFolder = Prefs.get("com.braincadet.bacannot.inFolder", System.getProperty("user.home"));
        OpenDialog.setDefaultDirectory(inFolder);
        OpenDialog dc = new OpenDialog("Select file");
        inFolder = dc.getDirectory();
        imPath = dc.getPath();
        if (imPath == null) {
            IJ.log("Could not open path: " + imPath);
            return;
        }

        Prefs.set("com.braincadet.bacannot.inFolder", inFolder);

        inImg = new ImagePlus(imPath);
        if (inImg == null) {
            IJ.log("Could not open image (it was null) at: " + imPath);
        }

        boolean acceptedExtensions =
                Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("TIF") ||
                Tools.getFileExtension(inImg.getTitle()).equalsIgnoreCase("JPG");

        if(acceptedExtensions == false) {
            IJ.log("open image with .TIF or .JPG extension only");
            return;
        }

        pickR = Math.round(Math.min(inImg.getWidth(), inImg.getHeight()) / 30f); // initial size of the circle
        pickX = 0;
        pickY = 0;

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();

        // look for the annotations and initialize Overlays with the annotations encountered in ./ANNNOT_DIR_NAME/
        ovAnnot = new Overlay();

        // use RoiManager to read existing annotation overlay if existent
        File ff = new File(imDir + File.separator + ANNNOT_DIR_NAME + File.separator + imName + ".zip");

        System.out.println(ff.getPath());

        if (ff.exists()){
            RoiManager rm = new RoiManager();
            rm.runCommand("Open", ff.getPath());
            rm.moveRoisToOverlay(inImg);
            rm.close();
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
        circAdd.setStrokeWidth(8);

        if (!begunPicking) {
            begunPicking = true;
            ovAnnot.add(circAdd);
        }
        else {
            ovAnnot.remove(ovAnnot.size()-1);
            ovAnnot.add(circAdd);
        }

        imCanv.setOverlay(ovAnnot);
        imCanv.getImage().updateAndDraw();

    }

    private void removeCircle(){

        boolean found = false;

        // TODO...

    }

    private void addCircle(Color col){

        // use pickX, pickY and pickR to check if it does not overlap
        for (int i = 0; i < ovAnnot.size()-1; i++) { // check all the previous ones

            float x = (float) ovAnnot.get(i).getFloatBounds().getX();
            float y = (float) ovAnnot.get(i).getFloatBounds().getY();
            float w = (float) ovAnnot.get(i).getFloatBounds().getWidth();
            float h = (float) ovAnnot.get(i).getFloatBounds().getHeight();
            float r = (float) (Math.max(w, h)/2);

            Color cl = ovAnnot.get(i).getFillColor();

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
            ovAnnot.remove(ovAnnot.size()-1);   // the last one is always the currently plotted
            ovAnnot.add(cc);
            ovAnnot.add(ccc);
        }

        imCanv.setOverlay(ovAnnot);
        imCanv.getImage().updateAndDraw();

        IJ.showStatus("added, current number of annotations: " + (ovAnnot.size()-1));

    }

    @Override
    public void mouseClicked(MouseEvent e) {

        pickX = 	imCanv.offScreenX(e.getX());
        pickY = 	imCanv.offScreenY(e.getY());

        imCanv.getImage().updateAndDraw();

        System.out.println("click at x=" + pickX + " y=" + pickY);

        // process the clicked circle
        addCircle(annotColor);

        /*
        GenericDialog gd = new GenericDialog("CHOOSE...");
        gd.addChoice("choose ", new String[]{"BIF", "CRS", "END", "NON", "IGNORE"}, "NON");
        gd.showDialog();

        if (gd.wasCanceled()) return;

        String aa = gd.getNextChoice();

        if (aa.equals("BIF")) {
            addCircle(bif_color);
        }
        if (aa.equals("END")) {
            addCircle(end_color);
        }
        if (aa.equals("NON")) {
            addCircle(none_color);
        }
        if (aa.equals("CRS")) {
            addCircle(cross_color);
        }
        if (aa.equals("IGNORE")) {
            addCircle(ignoreColor);
        }
        */
    }

    @Override
    public void keyTyped(KeyEvent e) {

        if (e.getKeyChar() == INCREASE_CIRCLE_RADIUS) {
            pickR += (pickR < Math.min(inImg.getWidth(), inImg.getHeight()))? changeR : 0 ;
            IJ.log("R+=" + changeR + ", R = " + pickR);
        }

        if (e.getKeyChar() == DECREASE_CIRCLE_RADIUS) {
            pickR -= (pickR < Math.min(inImg.getWidth(), inImg.getHeight()))? changeR : 0 ;
            IJ.log("R-="+changeR+", R = " + pickR);
        }

        if (e.getKeyChar()=='+') {
            imCanv.zoomIn((int) pickX, (int) pickY);
        }

        if (e.getKeyChar()=='-') {
            imCanv.zoomOut((int) pickX, (int) pickY);
        }

        if (e.getKeyChar()==EXPORT_COMMAND) {

            File directory = new File(imDir + File.separator + ANNNOT_DIR_NAME + File.separator);

            if (! directory.exists()){
                directory.mkdir(); // use directory.mkdirs(); for subdirs
            }

//            ImageProcessor imOut = new ByteProcessor(inImg.getWidth(), inImg.getHeight());
            ImagePlus imOut = new ImagePlus(imName, new ByteProcessor(inImg.getWidth(), inImg.getHeight()));

            RoiManager rm = new RoiManager();

            for (int i = 0; i < ovAnnot.size() - 1; i++) { // exclude the last one because it is the pointer circle

                int xPatch = ovAnnot.get(i).getBounds().x;
                int yPatch = ovAnnot.get(i).getBounds().y;
                int wPatch = ovAnnot.get(i).getBounds().width;
//                int hPatch = ovAnnot.get(i).getBounds().height;

                byte[] ovAnnotArray = (byte[]) ovAnnot.get(i).getMask().convertToByteProcessor().getPixels(); //.getMaskArray();
                byte[] imOutArray = (byte[]) imOut.getProcessor().getPixels();

                for (int j = 0; j < ovAnnotArray.length; j++) {
                    if ( (ovAnnotArray[j] & 0xff) == 255 ) {
                        // get global image coords and overwrite I(xOut, yOut) to 255
                        int xOut = xPatch + (j%wPatch);
                        int yOut = yPatch + (j/wPatch);
                        imOutArray[yOut * inImg.getWidth() + xOut] = (byte) 255;
                    }
                }

                // go through 255 elements of tt and draw them to the offset location
                rm.addRoi(ovAnnot.get(i));
            }

            FileSaver fs = new FileSaver(imOut);
            fs.saveAsTiff(directory.getPath() + File.separator + imName + ".tif");

            rm.runCommand("Save", directory.getPath() + File.separator + imName + ".zip");
            rm.moveRoisToOverlay(imOut);

            imOut.show();
            rm.close();

            // save the annotated image

        } //addCircle(end_color); //  || e.getKddeyChar()=='1'
//        if (e.getKeyChar()=='n' || e.getKeyChar()=='0') addCircle(none_color);
//        if (e.getKeyChar()=='c' || e.getKeyChar()=='4') addCircle(cross_color);
//        if (e.getKeyChar()=='i' || e.getKeyChar()=='7') addCircle(ignoreColor);

        if (e.getKeyChar()=='d') {
            removeCircle();
        }

        if (e.getKeyChar()=='s') {
//            GenericDialog gd = new GenericDialog("Save?");
//            gd.addStringField("output path", gndtth_path, gndtth_path.length()+10);
//            gd.showDialog();
//            if (gd.wasCanceled()) return;
//            String gndtth_path_spec = gd.getNextString();
//            export(gndtth_path_spec);
        }
        updateCircle();
    }

    @Override
    public void mouseDragged(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {
        pickX = imCanv.offScreenX(e.getX());
        pickY = imCanv.offScreenY(e.getY());
        updateCircle();
    }

    @Override
    public void imageClosed(ImagePlus imagePlus) {

        if (imWind!=null)
            imWind.removeKeyListener(this);
        if (imCanv!=null)
            imCanv.removeKeyListener(this);
        ImagePlus.removeImageListener(this);

        GenericDialog gd = new GenericDialog("Save?");
//        gd.addStringField("output path", gndtth_path, gndtth_path.length()+10);
        gd.showDialog();
        if (gd.wasCanceled()) return;
//        String gndtth_path_spec = gd.getNextString();
//        export(gndtth_path_spec);


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
}
