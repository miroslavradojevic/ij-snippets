package com.braincadet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.util.Random;

/**
 * Created by Miroslav on 23-Jul-17.
 */
public class SampleCirc implements PlugIn {

    int rMin, rMax;
    int dMin = 2, dMax = 3;
    float samplingStep = 2f;
    int numberOfSamples = 100;

    // Struct containing image information
    private class Image8 {
        public int Width;
        public int Height;
        public int Length;
        String ShortTitle;
        String ImageDirectory;
        byte[] ImageData; // byte8 data array
    }

    private Image8 loadImage(String ImagePath){

        if (!(getFileExtension(ImagePath).equalsIgnoreCase("tif") ||
              getFileExtension(ImagePath).equalsIgnoreCase("zip") ||
              getFileExtension(ImagePath).equalsIgnoreCase("jpg")))
        {
            IJ.log("Input image needs to be .tif, .zip of .jpg. Exiting...");
            return null;
        }

        ImagePlus input_image = new ImagePlus(ImagePath);

        if (input_image == null) {
            IJ.log("Input image is null.");
            return null;
        }

        if (input_image.getType() != ImagePlus.GRAY8) {
            IJ.log("Input image is not 8bit.");
            return null;
        }

        Image8 img          = new Image8();
        img.Width           = input_image.getWidth();
        img.Height          = input_image.getHeight();
        img.Length          = input_image.getStack().getSize();
        img.ShortTitle      = input_image.getShortTitle();
        img.ImageDirectory  = input_image.getOriginalFileInfo().directory;

        img.ImageData = new byte[img.Width*img.Height*img.Length]; // read image into byte[]
        for (int z = 1; z <= img.Length; z++) { // layer count, zcoord is layer-1
            byte[] slc = (byte[]) input_image.getStack().getPixels(z);
            for (int x = 0; x < img.Width; x++) {
                for (int y = 0; y < img.Height; y++) {
                    img.ImageData[(z - 1) * (img.Width * img.Height) + y * img.Width + x] = slc[y * img.Width + x];// & 0xff;
                }
            }
        }

        input_image = null; // Ensure to clean it

        return img;

    }

    ImagePlus getImagePlus(Image8 image8) {

        ImageStack imageStackOut = new ImageStack(image8.Width, image8.Height);

        for (int z = 1; z <= image8.Length; z++) {

            ByteProcessor imageLayer = new ByteProcessor(image8.Width, image8.Height);
            byte[] imageLayerArray = (byte[])imageLayer.getPixels();

            for (int x = 0; x < image8.Width; x++) {
                for (int y = 0; y < image8.Height; y++) {
                    imageLayerArray[y * image8.Width + x] = image8.ImageData[(z - 1) * (image8.Width * image8.Height) + y * image8.Width + x];
                }
            }

            imageStackOut.addSlice(imageLayer);

        }

        return new ImagePlus(image8.ShortTitle, imageStackOut);
    }

    public void run(String s) {

//        IJ.log("read image...");

        String image_path; // read input image, store the most recent path in Prefs
        String in_folder = Prefs.get("com.braincadet.platedetection.dir", System.getProperty("user.home"));
        OpenDialog.setDefaultDirectory(in_folder);
        OpenDialog dc = new OpenDialog("Select image");
        in_folder = dc.getDirectory();
        Prefs.set("com.braincadet.platedetection.dir", in_folder);
        image_path = dc.getPath();
        if (image_path == null) return;

        Image8 inputImage = loadImage(image_path);

        if (inputImage == null) {
            IJ.log("could not load " + image_path);
            return;
        }

        IJ.log("generating circles...");

        // Random sample the circles (x,y,r,d)
        rMin = Math.round(0.1f * Math.min(inputImage.Width, inputImage.Height));
        rMax = Math.round(0.45f * Math.min(inputImage.Width, inputImage.Height));

        Overlay circleSampleOverlay = new Overlay();
        Random rand = new Random();

        while (circleSampleOverlay.size() <= numberOfSamples) {

            int r = (rand.nextInt() % (rMax - rMin)) + rMin; // [rMin,rMax]
            int d = (rand.nextInt() % (dMax - dMin)) + dMin; // [dMin, dMax]

            int xMin = r+d;
            int xMax = inputImage.Width - (r+d);
            int x = xMin + (int)(Math.random() * ((xMax - xMin) + 1));

            int yMin = r+d;
            int yMax =  inputImage.Height - (r+d);
            int y = yMin + (int)(Math.random() * ((yMax - yMin) + 1));;

            boolean isInsideImage = true; // x-r-d >=0f && x+r < inputImage.Width &&  y-r >=0 && y+r < inputImage.Width ;

            if (isInsideImage) {

//                IJ.log("x=" + x + " [" +xMin + "," + xMax + "], y=" + y + " [" +yMin + "," + yMax+"], r=" + r + ", d=" + d + " | r=[" + rMin + "," + rMax + "] | d=[" + dMin + "," + dMax + "]");

                OvalRoi circleGen = new OvalRoi(x-r, y-r, 2*r, 2*r);
//                OvalRoi circleGen = new OvalRoi(0, 0, 100, 100);

                circleSampleOverlay.add(circleGen);

            }


        }

        // show image with overlays
        ImagePlus aa = getImagePlus(inputImage);
        aa.show();
        aa.setOverlay(circleSampleOverlay);


    }

    private class cRoi {
        public float x;
        public float y;
        public float r;
        public float d;
    }

    private static String getFileExtension(String FilePath)
    {
        String extension = "";

        int i = FilePath.lastIndexOf('.');
        if (i >= 0) {
            extension = FilePath.substring(i+1);
        }

        return extension;
    }

}
