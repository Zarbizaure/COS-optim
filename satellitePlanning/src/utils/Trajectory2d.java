package utils;

import java.util.ArrayList;
import java.util.List;

public class Trajectory2d {
	
	/** Description of the trajectory */
	public final String description;
	/** All points of the trajectory */
	private final List<Point2d> points;
	
	public Trajectory2d(String description){
		this.description = description;
		points = new ArrayList<Point2d>();
	}
	/**
	 * Add a point at the end of the trajectory
	 * @param description
	 * @param x
	 * @param y
	 */
	public void addPoint(String description, double x, double y){
		points.add(new Point2d(description,x,y));
	}
	/**
	 * Add a point at the end of the trajectory
	 * @param x
	 * @param y
	 */
	public void addPoint(double x, double y){
		points.add(new Point2d("",x,y));
	}
	/**
	 * 
	 * @return the number of points in this trajectory
	 */
	public int size(){
		return points.size();
	}
	/**
	 * 
	 * @param i
	 * @return the ith point in this trajectory
	 */
	public Point2d getPoint(int i){
		return points.get(i);
	}
	
}
