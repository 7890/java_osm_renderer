package com.jetdrone.map.source;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.logging.Logger;

import com.jetdrone.map.BoundingBox;
import com.jetdrone.map.MapException;
import com.jetdrone.map.index.QTree;
import com.jetdrone.map.source.osm.OSMReader;

public class MapSource extends OSMReader {
	
	private static final Logger LOG = Logger.getLogger(MapSource.class.getName());

	private QTree<Way> wayIndex;

	@SuppressWarnings("unchecked")
	public MapSource(String filename) throws MapException, ClassNotFoundException, FileNotFoundException, IOException {
		long tRead = System.currentTimeMillis();
		File fIndex = new File(filename + ".idx");
		if(fIndex.exists()) {
			LOG.info("reading wayindex "+fIndex);

			ObjectInputStream in=null;

			//http://stackoverflow.com/questions/15863054/what-is-the-quickest-way-to-load-a-serialized-hashmap-in-java

			if (java.awt.GraphicsEnvironment.isHeadless()) {
				// non gui mode
				in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(fIndex)));
			} else {
				// gui mode
				in = new ObjectInputStream(
					new javax.swing.ProgressMonitorInputStream(
						null
						,"Loading wayindex..."
						,new BufferedInputStream(new FileInputStream(fIndex))
					)
				);
			}

			wayIndex = (QTree<Way>) in.readObject();
			in.close();
		} else {
			LOG.info("loading "+filename);
			FileInputStream in = new FileInputStream(filename);
			load(in);
			in.close();

			LOG.info("writing wayindex "+fIndex);
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fIndex));
			out.writeObject(wayIndex);
			out.close();
		}
		LOG.info("OSM loading done (" + ((System.currentTimeMillis() - tRead) / 1000f) + ") secs");
	}//end MapSource()

	@SuppressWarnings("unchecked")
	public MapSource(InputStream in) throws ClassNotFoundException, IOException {
		LOG.info("reading wayindex from input stream");
		long tRead = System.currentTimeMillis();
//		ObjectInputStream oin = new ObjectInputStream(in);
		ObjectInputStream oin = null;

		if (java.awt.GraphicsEnvironment.isHeadless()) {
			oin = new ObjectInputStream(in);
		} else {
			oin = new ObjectInputStream(
				new javax.swing.ProgressMonitorInputStream(
					null
					,"Loading wayindex"
					,in
				)
			);
		}

		wayIndex = (QTree<Way>) oin.readObject();
		oin.close();
		LOG.info("OSM loading done (" + ((System.currentTimeMillis() - tRead) / 1000f) + ") secs");
	}
	
	public BoundingBox getBoundingBox() {
		return wayIndex.getBoundingBox();
	}

	public List<Way> getWaysInBoundingBox(BoundingBox bbox) {
		return wayIndex.get(bbox);
	}

	public List<Node> getNodesInBoundingBox(BoundingBox bbox) {
		throw new RuntimeException("Not implemented!");
	}

	@Override
	public void initIndex(BoundingBox bbox) {
		wayIndex = new QTree<Way>(bbox);
	}
	
	@Override
	public void indexWay(Way w) {
		if(wayIndex==null)
		{
			System.err.println("wayIndex was null");
			wayIndex = new QTree<Way>(new BoundingBox(1,1,50,50));
		}
		wayIndex.add(w);
	}
	
	@Override
	public void indexNode(Node n) {
		
	}
	
	public static void main(String[] args) throws Exception {
		//new MapSource("WebContent/WEB-INF/classes/netherlands.osm");
		new MapSource(args[0]);
	}
}//end class MapSource
//EOF
