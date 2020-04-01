package com.braincadet.bacannot;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.util.Arrays;
import java.util.Random;

public class StackImageModel implements PlugIn {

    int L = 10;
    int W = 40;
    int H = 40;

    public void run(String s) {

//        for (int D = 2; D <= 10; D++) {
//            int[] offsets = new int[D];
//            for (int i = -D/2; i <= ((D%2 == 0)? D/2-1 : D/2); i++) {
//                offsets[i+D/2] = i;
//            }
//            IJ.log("D = " + D);
//            IJ.log("D(offsets)" + Arrays.toString(offsets));
//        }
//        if (true)
//            return;

        Random rand = new Random();

        ImageStack isOut = new ImageStack(W, H);

        for (int z = 0; z < L; z++) {

            byte[] lay = new byte[W * H];

            for (int i = 0; i < W * H; i++) {
                lay[i] = (byte) (rand.nextInt(255));
            }

            isOut.addSlice(new ByteProcessor(W, H, lay));
        }

        ImagePlus ipOut = new ImagePlus("generated", isOut);

        ipOut.show();

    }
}
