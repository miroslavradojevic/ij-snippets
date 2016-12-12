import ij.*;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

// compile in terminal
// $ javac -cp ij.jar Stack_Ovals.java

// copy the compiled class to imagej plugins directory
// $ cp Stack_Ovals.class $ImageJ_HOME/plugins/

// menu: ImageJ>Plugins>Stack Ovals

public class Stack_Ovals implements PlugIn {

	public void run(String s) {

		int N = 256, M = 256, P = 32;

        // list of (x,y,z) coordinates to add to stack visualization
        ArrayList<int[]> x = new ArrayList<>();
        Random r = new Random();
        Color c = new Color(1f, 1f, 1f, 0.5f);
        for (int i = 0; i < 200; i++) {
            x.add(new int[]{r.nextInt(N), r.nextInt(M), r.nextInt(P)});
        }

        Overlay ov = new Overlay();

        for (int i = 0; i < x.size(); i++) {
            OvalRoi oroi = new OvalRoi(x.get(i)[0], x.get(i)[1], N/20f, M/20f);
            oroi.setStrokeColor(c);
            oroi.setFillColor(c);
            oroi.setPosition(x.get(i)[2]+1);
            ov.add(oroi);
        }

        ImageStack template_is = new ImageStack(N, M);

        for (int i = 0; i < P; i++) {
            template_is.addSlice(new ByteProcessor(N, M));
        }

        ImagePlus template_ip = new ImagePlus();
        template_ip.setStack(template_is);
        template_ip.setOverlay(ov);
        template_ip.setTitle("ovals");
        template_ip.show();

        // IJ.run(template_ip, "8-bit", "");
        // template_ip.flattenStack();
        // IJ.saveAs(template_ip, "Zip", "im.zip");

	}
}
