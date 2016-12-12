import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

// compile in terminal
// $ javac -cp ~/ij.jar Mouse_Listener.java

// copy the compiled class to imagej plugins directory and
// $ cp Mouse_Listener.class /ImageJ/plugins/

// menu: ImageJ>Plugins>Mouse Pinpointer

public class Mouse_Pinpointer implements PlugIn, MouseListener {

	ImageCanvas can;

	public void mouseClicked(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		int x_can = can.offScreenX(x);
		int y_can = can.offScreenY(y);
		int z_can = can.getSliceNumber("");
		z_can = can.getImage().getCurrentSlice();
		IJ.log("Mouse clicked: x=" + x_can + ", y=" + y_can + ", z=" + z_can);
	}

	public void mousePressed(MouseEvent e) {}

	public void mouseReleased(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}

	public void run(String s) {

		ImageStack is_gen = new ImageStack(512, 512);
		for (int i = 0; i < 120; i++) {
			is_gen.addSlice(new ByteProcessor(512, 512));
		}
		ImagePlus ip_gen = new ImagePlus("click over canvas", is_gen);
		ip_gen.show();

		can = ip_gen.getCanvas();
		if(can==null) return;
		can.addMouseListener(this);

		IJ.log("click over image canvas...");

	}
}
