package com.jetdrone.map.render.backend;

import static com.jetdrone.map.mercator.Mercator.coord2xy;
import static com.jetdrone.map.mercator.Mercator.lat2tiley;
import static com.jetdrone.map.mercator.Mercator.lon2tilex;
import static com.jetdrone.map.mercator.Mercator.tile2latlon;
import static java.lang.StrictMath.ceil;
import static java.lang.StrictMath.pow;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.jetdrone.map.BoundingBox;
import com.jetdrone.map.Coordinate;
import com.jetdrone.map.MapException;
import com.jetdrone.map.mercator.Mercator;
import com.jetdrone.map.rules.Draw;
import com.jetdrone.map.rules.Rule;
import com.jetdrone.map.rules.RuleSet;
import com.jetdrone.map.source.MapSource;
import com.jetdrone.map.source.Node;
import com.jetdrone.map.source.Way;

import java.io.File;

public class Renderer {

	private static final int MIN_ZOOM_LEVEL = 2;
	private static final int MAX_ZOOM_LEVEL = 18;

	private static final int DEFAULT_RESOLUTION = 256;

	// icon/pattern management
	private static final Map<String, Paint> TEX_CACHE = new HashMap<String, Paint>();
	private static final Map<Integer, Font> FNT_CACHE = new HashMap<Integer, Font>();

	private static Paint getPaint(String pattern, Paint color) {
		if (null != pattern) {
			Paint paint = TEX_CACHE.get(pattern);
			if (null == paint) {
				try {
					//BufferedImage texture = ImageIO.read(Renderer.class.getResourceAsStream("pattern/" + pattern + ".png"));
					BufferedImage texture = ImageIO.read(new File("resources/pattern/" + pattern + ".png"));
					paint = new TexturePaint(texture, new Rectangle2D.Float(0, 0, texture.getWidth(), texture.getHeight()));
				} catch (IOException e) {
					System.err.println("pattern not found: "+pattern);
					paint = color;
				}
				TEX_CACHE.put(pattern, paint);
			}
			return paint;
		}
		return color;
	}

	@SuppressWarnings("boxing")
	private static Font getFont(int size) {
		if (size > 5) {
			Font font = FNT_CACHE.get(size);
			if (null == font) {
				font = new Font("SansSerif", Font.PLAIN, size);
				FNT_CACHE.put(size, font);
			}
			return font;
		}
		return null;
	}

	// render source data (final can be used by several threads)
	private final MapSource map;
	private final BoundingBox bb;
	private final RuleSet rules;
	private final int resolution;

	// Utility function
	private static int clamp(int val, int min, int max) {
		if (val < min) {
			return min;
		} else if (val > max) {
			return max;
		}
		return val;
	}

	// utility function
	private static int linesize(int z) {
		return z < 12 ? 1 : z == 18 ? 6 : (int) (pow(2, z - 12) / (z - 12 + 1));
	}
	
	/**
	 * Creates a thread safe renderer
	 * 
	 * @param rules RuleSet that describe how to render
	 * @param map Parsed Map data
	 * @throws com.jetdrone.map.MapException Bad parameters
	 */
	public Renderer(RuleSet rules, MapSource map) throws MapException {
		this(rules, map, DEFAULT_RESOLUTION);
	}

	/**
	 * Creates a thread safe renderer
	 * 
	 * @param rules RuleSet that describe how to render
	 * @param map Parsed Map data
	 * @param resolution size of the tile
	 * @throws com.jetdrone.map.MapException Bad parameters
	 */
	public Renderer(RuleSet rules, MapSource map, int resolution) throws MapException {
		if (rules == null || map == null) {
			throw new MapException("No map and/or rules data");
		}
		
		this.map = map;
		this.bb = map.getBoundingBox();
		this.rules = rules;
		this.resolution = resolution;
	}

	public boolean tileHasData(int x, int y, int zoom_level) {

		int minx, miny, maxx, maxy;

		int c_zoom_level = clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);

		minx = lon2tilex(bb.getMinLon(), c_zoom_level);
		miny = lat2tiley(bb.getMaxLat(), c_zoom_level);
		maxx = lon2tilex(bb.getMaxLon(), c_zoom_level);
		maxy = lat2tiley(bb.getMinLat(), c_zoom_level);

		return !(x < minx || x > maxx || y < miny || y > maxy);
	}

	public int getMinXTile(int zoom_level) {
		return lon2tilex(bb.getMinLon(), clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL));
	}

	public int getMaxXTile(int zoom_level) {
		return lon2tilex(bb.getMaxLon(), clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL));
	}

	public int getMinYTile(int zoom_level) {
		return lat2tiley(bb.getMaxLat(), clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL));
	}

	public int getMaxYTile(int zoom_level) {
		return lat2tiley(bb.getMinLat(), clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL));
	}

	public void drawMap(final String filename, final int zoom_level) throws IOException {

		int c_zoom_level = clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);

		Coordinate min = coord2xy(bb.getMinLat(), bb.getMinLon(), c_zoom_level, resolution);
		Coordinate max = coord2xy(bb.getMaxLat(), bb.getMaxLon(), c_zoom_level, resolution);

		int w = (int) ceil(max.x - min.x);
		int h = (int) ceil(min.y - max.y);

		Coordinate offset = coord2xy(bb.getMaxLat(), bb.getMinLon(), c_zoom_level, resolution);
		MapSurface surface = new MapSurface(w, h, rules.getBgColor(), c_zoom_level, offset, bb);
		List<Way> mapData = map.getWaysInBoundingBox(bb);
		render(surface, mapData);

		surface.write(filename);
		surface.flush();
	}
	
	public void drawTile(final String filename, final int x, final int y, final int zoom_level) throws IOException {

		int c_zoom_level = clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);
		Coordinate crd = tile2latlon(x, y, c_zoom_level);
		
		Coordinate offset = coord2xy(crd.x, crd.y, c_zoom_level, resolution);
		BoundingBox bbox = Mercator.tileEdges(x, y, c_zoom_level);
		
		MapSurface surface = new MapSurface(resolution, resolution, rules.getBgColor(), c_zoom_level, offset, bbox);
		List<Way> mapData = map.getWaysInBoundingBox(bbox);
		render(surface, mapData);

		surface.write(filename);
		surface.flush();
	}

	public void drawTile(final OutputStream out, final int x, final int y, final int zoom_level) throws IOException {

		int c_zoom_level = clamp(zoom_level, MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);
		Coordinate crd = tile2latlon(x, y, c_zoom_level);
		
		Coordinate offset = coord2xy(crd.x, crd.y, c_zoom_level, resolution);
		BoundingBox bbox = Mercator.tileEdges(x, y, c_zoom_level);
		
		MapSurface surface = new MapSurface(resolution, resolution, rules.getBgColor(), c_zoom_level, offset, bbox);
		List<Way> mapData = map.getWaysInBoundingBox(bbox);
		render(surface, mapData);

		surface.write(out);
		surface.flush();
	}
	
	private void render(final MapSurface surface, final List<Way> mapData) {
		// Start checking osm from bottom layer.
		for (int layer = -5; layer <= 5; layer++) {
			// Process Rule by Rule
			for (Rule rule = rules.root(); rule != null; rule = rule.next()) {
				if (rule.getDraw() != null) {
					renderPaths(surface, layer, rule, rule.getDraw(), mapData);
					// Text Rendering
					renderText(surface, layer, rule, rule.getDraw(), mapData);
				}
			}
		}
	}

	private void renderPaths(MapSurface surface, int layer, Rule rule, Draw draw, final List<Way> mapData) {
		int paths = 0;
		Draw d = draw;
		surface.path.reset();
		
		// Loop through ways for
		for (Way way : mapData) {
			// perform geometry culling. If an object is not on the current layer or
			// inside the tile bounding box, it can be skipped
			if (way.getLayer() != layer) continue;
			Map<String, String> tags = way.getTags();
			if(tags != null) {
				if (RuleSet.checkRule(rule, tags)) {
					paths += buildPath(surface, way.getWayNode());
				}
			}
		}
		if (paths != 0) {
			
			Graphics2D graphics = surface.getGraphics();
			
			while (d != null) {
				if (d.getMinZoom() > surface.zoomLevel || d.getMaxZoom() < surface.zoomLevel) {
					d = d.next();
					continue;
				}
				switch (d.type) {
				case Draw.POLYGONE:
					graphics.setPaint(getPaint(d.getPattern(), d.getColor()));
					graphics.fill(surface.path);
					break;
				case Draw.LINE:
					/*
					CAP_BUTT
					CAP_ROUND
					CAP_SQUARE
					JOIN_BEVEL
					JOIN_MITER
					JOIN_ROUND
					*/
					float strokeWidth = d.getWidth() * linesize(surface.zoomLevel);
					if(strokeWidth > 0.5f) {
						Stroke stroke = new BasicStroke(
							strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
							///strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
						graphics.setPaint(d.getColor());
						graphics.setStroke(stroke);
						graphics.draw(surface.path);
					}
					break;
//				case Draw.TEXT:
//					break; /* ignore */
				}
				d = d.next();
			}
		}
	}//end renderPaths()

	private int buildPath(MapSurface surface, List<Node> nodes) {
		Node nd = nodes.get(0);
		Coordinate xy0 = coord2xy(nd.getLat(), nd.getLon(), surface.zoomLevel, resolution);
		surface.path.moveTo(xy0.x - surface.offset.x, xy0.y - surface.offset.y);

		Coordinate xy;
		int paths = 0;
		for (int i = 1; i < nodes.size(); i++) {
			nd = nodes.get(i);
			xy = coord2xy(nd.getLat(), nd.getLon(), surface.zoomLevel, resolution);
			if(xy0.x != xy.x && xy0.y != xy.y) {
				surface.path.lineTo(xy.x - surface.offset.x, xy.y - surface.offset.y);
				paths++;
			}
			xy0 = xy;
		}
		return paths;
	}

	private void renderText(MapSurface surface, int layer, Rule rule, Draw draw, final List<Way> mapData) {
		Graphics2D graphics = surface.getGraphics();
		Draw d = draw;

		while (d != null) {
			Font font = getFont((int) (d.getWidth() * linesize(surface.zoomLevel)));

			if (d.type == Draw.TEXT && font != null) {
				if (draw.getMinZoom() <= surface.zoomLevel && surface.zoomLevel <= draw.getMaxZoom()) {

					for (Way way : mapData) {
						// Only objects on current layer
						if (way.getLayer() != layer || way.getName() == null)
							continue;

						Map<String, String> tags = way.getTags();
						if(tags != null) {
							if (RuleSet.checkRule(rule, tags)) {
								// TODO: verify if there is a path and render text
								// along the path? or see how osmarender does it?
								graphics.setFont(font);
								graphics.setPaint(d.getColor());
								Node nd = way.getWayNode().get(0);
								Coordinate xy = coord2xy(
										nd.getLat(), nd.getLon(), surface.zoomLevel, resolution);
	
								graphics.drawString(
										way.getName(),
										(int) (xy.x - surface.offset.x),
										(int) (xy.y - surface.offset.y));
							}
						}
					}
				}
				break;
			}
			d = d.next();
		}
	}//end renderText()
}//end class Renderer
//EOF
