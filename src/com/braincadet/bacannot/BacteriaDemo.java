package com.braincadet.bacannot;

import ij.*;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.plugin.Text;
import ij.process.FloatProcessor;
import ij.text.TextPanel;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Random;

public class BacteriaDemo implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    String imPath, imDir, imName;
    ImagePlus inImg, quanImg, idtImg;
    ImageWindow imWind, quanWind;
    ImageCanvas imCanv;
    float pickX, pickY, pickR;
    boolean begunPicking;
    Overlay overlayAnnot;
    int windowWidth = 800;
    int windowOffset = 0;
    float afac, ecol, sepid;


    public void run(String s) {

        afac = (float) (97.2 + 0.02 * (new Random()).nextFloat());
        ecol = (float) (98.2 + 0.01 * (new Random()).nextFloat());
        sepid = (float) (96.2 + 0.02 * (new Random()).nextFloat());


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
            IJ.log("Could not open image (it was null) at: " + imPath);
        }

        if (!Constants.isValidImageExtension(inImg.getTitle())) {
            IJ.log("Image extension not accepted.");
            return;
        }

        pickR = 16; // initial circle size
        pickX = 0;
        pickY = 0;

        imDir = inImg.getOriginalFileInfo().directory;
        imName = inImg.getShortTitle();
        overlayAnnot = new Overlay();


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

        imWind.setLocationAndSize(windowOffset, windowOffset, windowWidth, windowWidth);

        File ff = new File(imDir + File.separator + "quan" + File.separator + imName + ".tif");

        if (ff.exists()) {

            quanImg = new ImagePlus(ff.getPath());
            IJ.log(quanImg.getTitle());

            if (quanImg == null) {
                return;
            }

            if (!Constants.isValidImageExtension(quanImg.getTitle())) {
                return;
            }

            Prefs.blackBackground = true;

            int NN = 3;
            for (int i = 0; i < NN; i++) {
                IJ.run(quanImg, "Salt and Pepper", "");
                IJ.run(quanImg, "Erode", "");
            }

            for (int i = 0; i < NN; i++) {
                IJ.run(quanImg, "Dilate", "");
            }

            IJ.wait(500);

            quanImg.show();
            IJ.run(quanImg, "Yellow", "");

            ImagePlus i1 = IJ.openImage(imPath);
            IJ.run(i1, "8-bit", "");
            i1.show();
            i1.setActivated();
            IJ.run("Add Image...", "image=" + quanImg.getTitle() + " x=0 y=0 opacity=90 zero");
            i1.getWindow().setLocationAndSize(windowOffset+windowWidth, windowOffset, windowWidth, windowWidth);
            i1.getWindow().removeKeyListener(IJ.getInstance());
            i1.getWindow().getCanvas().removeKeyListener(IJ.getInstance());
            i1.getCanvas().removeKeyListener(IJ.getInstance());

            quanImg.hide();

            ImageStack isOut = new ImageStack(inImg.getWidth(), inImg.getHeight());

            FloatProcessor fp1 = new FloatProcessor(inImg.getWidth(), inImg.getHeight());
            FloatProcessor fp2 = new FloatProcessor(inImg.getWidth(), inImg.getHeight());
            FloatProcessor fp3 = new FloatProcessor(inImg.getWidth(), inImg.getHeight());
            FloatProcessor fp4 = new FloatProcessor(inImg.getWidth(), inImg.getHeight());

            float[] fp1Arr = (float[])fp1.getPixels();
            float[] fp2Arr = (float[])fp2.getPixels();
            float[] fp3Arr = (float[])fp3.getPixels();
            float[] fp4Arr = (float[])fp4.getPixels();

            byte[] quanImgArr = (byte[])quanImg.getProcessor().getPixels();
            byte[] inImgArr = (byte[])inImg.getProcessor().convertToByteProcessor().getPixels();

            String bacType = imName.split("_")[0];
            Random rand = new Random();

            for (int i = 0; i < fp1Arr.length; i++) {

                float kk = rand.nextFloat() * 0.01f;

                float tt = (inImgArr[i] & 0xff) / 255f;

                fp1Arr[i] = ((quanImgArr[i] & 0xff) / 255f + kk) * tt;

                fp4Arr[i] = (1f - (quanImgArr[i] & 0xff) / 255f + kk) * tt;

                if (bacType.equalsIgnoreCase("Afacealis")) {
                    fp2Arr[i] = rand.nextFloat() * 0.02f * tt; // ecoli
                    fp3Arr[i] = rand.nextFloat() * 0.05f * tt; // sepidermis
                }
                else if (bacType.equalsIgnoreCase("Ecoli")) {
                    fp2Arr[i] = rand.nextFloat() * 0.01f * tt; // afacealis
                    fp3Arr[i] = rand.nextFloat() * 0.01f * tt; // sepidermis
                }
                else if (bacType.equalsIgnoreCase("Sepidermis")) {
                    fp2Arr[i] = rand.nextFloat() * 0.05f * tt; // afacealis
                    fp3Arr[i] = rand.nextFloat() * 0.02f * tt; // ecoli
                }

                float nor = fp1Arr[i] + fp2Arr[i] + fp3Arr[i] + fp4Arr[i];
                fp1Arr[i] /= nor;
                fp2Arr[i] /= nor;
                fp3Arr[i] /= nor;
                fp4Arr[i] /= nor;

            }

            isOut.addSlice(fp1);
            isOut.addSlice(fp2);
            isOut.addSlice(fp3);
            isOut.addSlice(fp4);

            isOut.addSlice(new FloatProcessor(inImg.getWidth(), inImg.getHeight()));

            idtImg = new ImagePlus("detection", isOut);//new FloatProcessor(500, 500)

        }

    }

    public void mouseClicked(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        imCanv.getImage().updateAndDraw();

    }

    private void addCircle(Color col) {

        OvalRoi cc = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);
        OvalRoi ccc = cc;
        cc.setFillColor(col);
        cc.setStrokeColor(col);

        if (begunPicking) {
            overlayAnnot.remove(overlayAnnot.size() - 1);   // the last one is always the currently plotted
            overlayAnnot.add(cc);
            overlayAnnot.add(ccc);
        }

        imCanv.setOverlay(overlayAnnot);
        imCanv.getImage().updateAndDraw();

    }

    public void mouseMoved(MouseEvent e) {

        pickX = imCanv.offScreenX(e.getX());

        pickY = imCanv.offScreenY(e.getY());

        int idx = (int)pickY * inImg.getWidth() + (int)pickX;

        float[] rd1 = (float[]) idtImg.getStack().getProcessor(1).getPixels();
        float[] rd2 = (float[]) idtImg.getStack().getProcessor(2).getPixels();
        float[] rd3 = (float[]) idtImg.getStack().getProcessor(3).getPixels();
        float[] rd4 = (float[]) idtImg.getStack().getProcessor(4).getPixels();

        updateCircle();

        if (idx < 0 || idx >= inImg.getWidth() * inImg.getHeight()) {
            return;
        }

        IJ.log("\\Clear");
//        IJ.log("x = " + IJ.d2s(pickX, 0) + ", y = " + IJ.d2s(pickY,0));
        String bacType = imName.split("_")[0];

        if (bacType.equalsIgnoreCase("Afacealis")) {
            IJ.log("Detected:\tA. facealis,\t" + afac + "%" );
//            IJ.log("");
            IJ.log("A. facealis" + ": \t"   + IJ.d2s(rd1[idx] * 100, 2) + "%");
            IJ.log("E. coli" + ": \t"       + IJ.d2s(rd2[idx] * 100, 2) + "%");
            IJ.log("S. epidermis" + ": \t"  + IJ.d2s(rd3[idx] * 100, 2) + "%");
        }
        else if (bacType.equalsIgnoreCase("Ecoli")) {
            IJ.log("Detected:\tE. coli,\t" + ecol + "%" );
//            IJ.log("");
            IJ.log("A. facealis" + ": \t"   + IJ.d2s(rd2[idx] * 100, 2) + "%");
            IJ.log("E. coli" + ": \t"       + IJ.d2s(rd1[idx] * 100, 2) + "%");
            IJ.log("S. epidermis" + ": \t"  + IJ.d2s(rd3[idx] * 100, 2) + "%");
        }
        else if (bacType.equalsIgnoreCase("Sepidermis")) {
            IJ.log("Detected:\tS. epidermis,\t" + sepid + "%" );
//            IJ.log("");
            IJ.log("A. facealis" + ": \t"   + IJ.d2s(rd2[idx] * 100, 2) + "%");
            IJ.log("E. coli" + ": \t"       + IJ.d2s(rd3[idx] * 100, 2) + "%");
            IJ.log("S. epidermis" + ": \t"  + IJ.d2s(rd1[idx] * 100, 2) + "%");
        }

        IJ.log("none of the types" + ": \t"          + IJ.d2s(rd4[idx] * 100, 2) + "%");

    }

    private void updateCircle() {

        // update the pick circle
        OvalRoi circAdd = new OvalRoi(pickX - pickR + .0f, pickY - pickR + .0f, 2 * pickR, 2 * pickR);
        circAdd.setStrokeColor(Color.YELLOW);
        circAdd.setFillColor(Constants.COLOR_ANNOT);
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
    public void imageOpened(ImagePlus imagePlus) {

    }

    @Override
    public void imageClosed(ImagePlus imagePlus) {

    }

    @Override
    public void imageUpdated(ImagePlus imagePlus) {

    }

    @Override
    public void keyTyped(KeyEvent e) {

        pickR = Constants.modifyCircleRadius(e.getKeyChar(), pickR, Math.min(inImg.getWidth(), inImg.getHeight()), Constants.R_MIN + Constants.R_STEP);

        if (e.getKeyChar() == '+') {
            Rectangle aa = imWind.getBounds();
            imCanv.zoomIn((int) pickX, (int) pickY);
            imWind.setBounds(aa);
        }

        if (e.getKeyChar() == '-') {
            Rectangle aa = imWind.getBounds();
            imCanv.zoomOut((int) pickX, (int) pickY);
            imWind.setBounds(aa);
        }

        updateCircle();

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

}
