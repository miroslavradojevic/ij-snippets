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

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by Miroslav on 23-Jul-17.
 */
public class SampleCirc implements PlugIn {

    int rMin, rMax;
//    int dMin = 2, dMax = 3;
    int d = 5;

    int margin = 1;

    float samplingStep = 2f;
    int ringValuesCountLimit = 500; // will affect the sampling density

    int numberOfRingSamples = 50;

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

        input_image = null; // GC cleans it

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

    private class Ring{

        public int x;
        public int y;
        public int r;
        public int d;
        public float prior;
        public float likelihood;
        public float posterior;

        Ring(int x, int y, int r, int d, float prior){
            this.x = x;
            this.y = y;
            this.r = (r<d)? d : r ; //  should not be less than d (half of the ring strip)
            this.d = d;
            this.prior = prior;
            this.likelihood = Float.NaN;
            this.posterior = Float.NaN;
        }

        public void print() {
            IJ.log("x="+this.x+" y="+y+" r="+r+" d="+d+" prior="+this.prior+" likelihood="+this.likelihood+" posterior="+posterior);
        }

    }

    public void run(String s) {

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

        IJ.log("-- generating circles...");

        // Random sample the circles (x,y,r,d)
        rMin = Math.round(0.1f * Math.min(inputImage.Width, inputImage.Height));
        rMax = Math.round(0.45f * Math.min(inputImage.Width, inputImage.Height));
        IJ.log(rMin + " -- " + rMax);

        Overlay circleSampleOverlay = new Overlay();
        ArrayList<Ring> rings = new ArrayList<Ring>(numberOfRingSamples);


        while (circleSampleOverlay.size() <= numberOfRingSamples) { // Also rings.size()

            int r = rMin + (int)(Math.random() * ((rMax - rMin) + 1)); // [rMin,rMax]

//            int d = ...randomize...; // [dMin, dMax]

            int xMin = r+d+margin;
            int xMax = inputImage.Width - xMin;
            int x = xMin + (int)(Math.random() * ((xMax - xMin) + 1));

            int yMin = r+d+margin;
            int yMax =  inputImage.Height - yMin;
            int y = yMin + (int)(Math.random() * ((yMax - yMin) + 1));

//            boolean isInsideImage = true; // x-r-d >=0f && x+r < inputImage.Width &&  y-r >=0 && y+r < inputImage.Width ;

            OvalRoi circleGen = new OvalRoi(x-r, y-r, 2*r, 2*r);
            circleGen.setStrokeWidth(2*d);
//            circleGen.setFillColor(new Color(1f, 1f, 1f, 0.2f));
            circleGen.setStrokeColor(new Color(1f, 1f, 0f, 0.75f));

            Ring ringToAdd = new Ring(x, y, r, d, 1f/numberOfRingSamples);
            ringToAdd.posterior = 1f/numberOfRingSamples;

            circleSampleOverlay.add(circleGen);
            rings.add(ringToAdd);

        }

        IJ.log("done.");
        for (int i = 0; i < rings.size(); i++) {
            rings.get(i).print();
        }

        // show image with overlays
        ImagePlus aa = getImagePlus(inputImage);
        aa.show();
        aa.setOverlay(circleSampleOverlay);

        // calculate likelihood for each circle (prior is uniform)

        float[] ringValues = new float[ringValuesCountLimit]; // Allocate storage for the ring samples
        float[] outVals = new float[4];
        for (int i = 0; i < rings.size(); i++) {

            int count = ringLikelihood(
                    rings.get(i),
                    inputImage.ImageData,
                    inputImage.Width,
                    inputImage.Height,
                    inputImage.Length,
                    ringValues,
                    samplingStep,
                    outVals
            );



//            IJ.log(i + " : " + Arrays.toString(outVals));
        }



//        float[] ringValuesInner = new float[ringValuesCountLimit];
//        int ringValuesNumber = Integer.MIN_VALUE;
//        IJ.log("Integer.MIN_VALUE = " + Integer.MIN_VALUE);

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

    private static int ringLikelihood(Ring inputRing,
                                      byte[] inputImage,
                                      int inputImageWidth,
                                      int inputImageHeight,
                                      int inputImageLength, // In case it is 3D, keep 0 if 2D image
                                      float[] ringValues,
                                      float samplingStep,
                                      float[] outVals) {    // outVals[0] = meanRing
                                                            // outVals[1] = ;
                                                            // outVals[2] = ;
                                                            // outVals[3] = ;
        float alphaStepMin = (2*3.14f) / ringValues.length;
//        IJ.log("alphaStepMin = " + alphaStepMin);
        float alphaStep = samplingStep / inputRing.r; // Sample preferably each samplingStep, unless there would be more values than ringValues array can accommodate
//        IJ.log("alphaStep = " + alphaStep);
        //
        alphaStep = (alphaStep<alphaStepMin)? alphaStepMin : alphaStep ;

//        IJ.log("use sampling = " + (alphaStep<alphaStepMin));

        float meanRing = 0, meanRingInner = 0, meanRingOuter = 0;
        float k0, k1;
        float ringValue, ringValueOuter, ringValueInner;

        float x,y,z;

        int count = 0;

        // sample ring values
        for (float alpha = 0; alpha < 2*3.14f; alpha+=alphaStep) {

            if (count>=ringValues.length) {break;}

            x = inputRing.x+.5f + inputRing.r * (float) Math.cos(alpha);
            y = inputRing.y+.5f + inputRing.r * (float) Math.sin(alpha);
            z = 0;

            ringValue = interpolate(x, y, z, inputImage, inputImageWidth, inputImageHeight, inputImageLength);

            if (Float.isNaN(ringValue)) {continue;}

            x = inputRing.x+.5f + (inputRing.r - inputRing.d) * (float) Math.cos(alpha);
            y = inputRing.y+.5f + (inputRing.r - inputRing.d) * (float) Math.sin(alpha);
            z = 0;

            ringValueInner = interpolate(x, y, z, inputImage, inputImageWidth, inputImageHeight, inputImageLength);

            if (Float.isNaN(ringValueInner)) {continue;}

            x = inputRing.x+.5f + (inputRing.r + inputRing.d) * (float) Math.cos(alpha);
            y = inputRing.y+.5f + (inputRing.r + inputRing.d) * (float) Math.sin(alpha);
            z = 0;

            ringValueOuter = interpolate(x, y, z, inputImage, inputImageWidth, inputImageHeight, inputImageLength);

            if (Float.isNaN(ringValueOuter)) {continue;}

            // all correctly read

            ringValues[count] = ringValue;

            count++;

            k0 = (count-1f) / count;
            k1 = 1f / count;

            meanRing = k0 * meanRing + k1 * ringValue; // Iterative mean for the ring

            meanRingInner = k0 * meanRingInner + k1 * (float)Math.pow(ringValue - ringValueInner,2); // Iterative mean for the inner ring

            meanRingOuter = k0 * meanRingOuter + k1 * (float)Math.pow(ringValue - ringValueOuter, 2); // Iterative mean for the outer ring

        }

        outVals[0] = meanRing;
        outVals[1] = meanRingInner;
        outVals[2] = meanRingOuter;

        // Go through the ringValues again to compute the variance of the ring values
        float varianceRing = 0;
        for (int i = 0; i < count; i++) {
            varianceRing += Math.pow(ringValues[i] - meanRing, 2);
        }
        if (count>=2) {
            varianceRing /= count-1; // count needs to be >=2
        }
        else {
            varianceRing = Float.NaN;
        }

        outVals[3] = varianceRing;

        return count;

    }

//    private static float interpolate(float atX, float atY, float atZ, byte[] inputImage, int inputImageWidth, int inputImageHeight, int inputImageLength) {
//        return 1f;
//    }

    private static float interpolate(float atX, float atY, float atZ, byte[] inputImage, int inputImageWidth, int inputImageHeight, int inputImageLength) {

        if (inputImageLength<=0) {
            return Float.NaN;
        }

        int x1 = (int) atX;
        int x2 = x1 + 1;
        float x_frac = atX - x1;

        int y1 = (int) atY;
        int y2 = y1 + 1;
        float y_frac = atY - y1;

        if (inputImageLength==1) { // atZ is not necessary

            boolean isIn2D = x1>=0 && x2<inputImageWidth && y1>=0 && y2<inputImageHeight;
            if(!isIn2D) return Float.NaN;

            // 2D neighbourhood
            float I11_1 = inputImage[y1*inputImageWidth+x1] & 0xff; // bit shifting will result in an int which is cast into float automatically
            float I12_1 = inputImage[y1*inputImageWidth+x2] & 0xff;
            float I21_1 = inputImage[y2*inputImageWidth+x1] & 0xff;
            float I22_1 = inputImage[y2*inputImageWidth+x2] & 0xff;

            return (1-y_frac) * ((1-x_frac)*I11_1 + x_frac*I12_1) + (y_frac) * ((1-x_frac)*I21_1 + x_frac*I22_1);

        }
        else {

            int z1 = (int) atZ;
            int z2 = z1 + 1;
            float z_frac = atZ - z1;

            boolean isIn3D = y1>=0 && y2<inputImageHeight && x1>=0 && x2<inputImageWidth && z1>=0 && z2<inputImageLength;
            if (!isIn3D) return Float.NaN;

            // 3D neighbourhood
            float I11_1 = inputImage[z1*inputImageWidth*inputImageHeight+y1*inputImageWidth+x1] & 0xff;
            float I12_1 = inputImage[z1*inputImageWidth*inputImageHeight+y1*inputImageWidth+x2] & 0xff;
            float I21_1 = inputImage[z1*inputImageWidth*inputImageHeight+y2*inputImageWidth+x1] & 0xff;
            float I22_1 = inputImage[z1*inputImageWidth*inputImageHeight+y2*inputImageWidth+x2] & 0xff;

            float I11_2 = inputImage[z2*inputImageWidth*inputImageHeight+y1*inputImageWidth+x1] & 0xff;
            float I12_2 = inputImage[z2*inputImageWidth*inputImageHeight+y1*inputImageWidth+x2] & 0xff;
            float I21_2 = inputImage[z2*inputImageWidth*inputImageHeight+y2*inputImageWidth+x1] & 0xff;
            float I22_2 = inputImage[z2*inputImageWidth*inputImageHeight+y2*inputImageWidth+x2] & 0xff;

            return (1-z_frac)  *
                    (  (1-y_frac) * ((1-x_frac)*I11_1 + x_frac*I12_1) + (y_frac) * ((1-x_frac)*I21_1 + x_frac*I22_1) )   +
                    z_frac      *
                            (  (1-y_frac) * ((1-x_frac)*I11_2 + x_frac*I12_2) + (y_frac) * ((1-x_frac)*I21_2 + x_frac*I22_2) );

        }


    }

}
