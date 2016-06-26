package com.jetdrone.map.render;

import java.io.IOException;
import java.io.File;

import com.jetdrone.map.render.backend.Renderer;

public class RenderTask implements Runnable {

	private final Renderer renderer;
	private final String filename;
	private final int x, y, zoom_level;

	@SuppressWarnings("boxing")
	public RenderTask(Renderer renderer, int x, int y, int zoom_level) {
		this.renderer = renderer;

		File outdir = new File(""+String.format(Options.outdir+"/%d/%d/",zoom_level,x));
		if (!outdir.exists())
		{
			if(outdir.mkdirs())
			{
				System.err.println("Created missing directories: " + outdir.getAbsolutePath());
			}
			else
			{
				System.err.println("/!\\ could not create missing directories: " + outdir.getAbsolutePath());
				System.err.println("check permissions, free inodes on disk (df -i).");
			}
		}

		//this.filename = String.format("tiles/%d_%d.png", x, y);

		//   /tiles/z/x/x_y.png
		this.filename = String.format(Options.outdir+"/%d/%d/%d_%d.png", zoom_level, x, x, y);
		this.x = x;
		this.y = y;
		this.zoom_level = zoom_level;
	}

	@Override
	public void run() {
		try {
			System.err.println("Drawing tile "+filename+" zoom "+zoom_level);
			renderer.drawTile(filename, x, y, zoom_level);
		} catch (IOException e) {
			System.err.println("Error: RenderTask[" + this + "] caused by [" + e + "]");
			e.printStackTrace();
		}
	}
}
