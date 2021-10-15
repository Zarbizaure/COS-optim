package utils;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;


public class TrajectoryView extends ApplicationFrame {
	
	XYDataset xySeriesCollection;
	ChartPanel chartPanel;
	List<Trajectory2d> trajectories;
	
	public TrajectoryView(String s, int xmin, int xmax, int ymin, int ymax, List<Trajectory2d> trajectories) {
		super(s);
		this.trajectories = trajectories;
		chartPanel = createDemoPanel(xmin,xmax,ymin,ymax);
		chartPanel.setDomainZoomable(false);
		chartPanel.setRangeZoomable(false);		
		chartPanel.setPreferredSize(new Dimension(640, 480));
		this.add(chartPanel);
		this.pack();
		this.setVisible(true);
	}

	public ChartPanel createDemoPanel(int xmin, int xmax, int ymin, int ymax) {
		XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
		
		final String[][] labels = new String[trajectories.size()][];
		int seriesId = 0;
		for(Trajectory2d traj : trajectories){
			XYSeries series = new XYSeries(traj.description,false);						
			int n = traj.size();
			labels[seriesId] = new String[n];
			for(int j=0;j<n;j++){
				Point2d p = traj.getPoint(j);
				series.add(p.x,p.y);
				labels[seriesId][j] = p.description;
			}
			xySeriesCollection.addSeries(series);
			seriesId++;
		}
		
		JFreeChart jfreechart = ChartFactory.createScatterPlot(
				"", "X", "Y", xySeriesCollection,
				PlotOrientation.VERTICAL, 
				false,// no legend 
				true, false);
		
		XYPlot xyPlot = (XYPlot) jfreechart.getPlot();
		xyPlot.setDomainCrosshairVisible(true);
		xyPlot.setRangeCrosshairVisible(true);
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		
		XYItemLabelGenerator xyItemLabelGenerator = new XYItemLabelGenerator() {
		    @Override
		    public String generateLabel(XYDataset dataset, int series, int item) {
		    	return labels[series][item];		    	
		    }
		};
		renderer.setBaseItemLabelGenerator(xyItemLabelGenerator);
		renderer.setBaseItemLabelsVisible(true);
		
		renderer.setSeriesLinesVisible(0, true);
		xyPlot.setRenderer(renderer);

		NumberAxis domain = (NumberAxis) xyPlot.getDomainAxis();
		domain.setRange(ymin,ymax);
		domain.setVerticalTickLabels(true);

		NumberAxis range = (NumberAxis) xyPlot.getRangeAxis();
		range.setRange(xmin,xmax);

		//	        XYSeriesLabelGenerator lblGen = new StandardXYSeriesLabelGenerator();
		//	        XYDataset dataset = xyPlot.getDataset();
		//	        renderer.setSeriesItemLabelGenerator(dataset.getSeriesCount()-1, lblGen);//.setBaseItemLabelGenerator(lblGen);//setSeriesLabelGenerator(dataset.getSeriesCount()-1,lblGen);
		//	        renderer.setSeriesItemLabelsVisible(dataset.getSeriesCount()-1,new Boolean(true));

		//renderer.setBaseItemLabelsVisible(Boolean.TRUE);

		//renderer.setBaseItemLabelGenerator((XYItemLabelGenerator) new StandardXYItemLabelGenerator());
		//renderer.setBaseItemLabelGenerator(new MyGenerator(deviceIdx));

		
		BufferedImage image = null;
        try {
            File url = new File(System.getProperty("user.dir").toString()+"/world.png");
            image = ImageIO.read(url);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        xyPlot.setBackgroundImage(image);
		
		return new ChartPanel(jfreechart);
	}

	
}
