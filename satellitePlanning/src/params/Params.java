package params;

public class Params {

	/** Constellation considered */
	public final static String constellation = "02sat";
	//public final static String constellation = "08sat";
	//public final static String constellation = "18sat";
	/** Planning horizon considered */
	public final static String horizon = "04h"; 
	//public final static String horizon = "12h";
	//public final static String horizon = "24h";
	/** File containing a description of all static data (satellites, users, stations) */
	public final static String systemDataFile = "data/system_data_"+constellation+".xml";
	/** File containing a description of all dynamic data (candidate acquisitions, recorded acquisitions...) */
	public final static String planningDataFile = "data/planning_data_"+constellation+"_"+horizon+".xml";	
	/** Approximation of the rotation speed of the satellite (in radians per second) */
	public final static double meanRotationSpeed = (2*Math.PI)/180; // 2 degrees per second
	/** Rate associated with data downlink to ground stations (in bits per second) */
	public final static double downlinkRate = 1E6;
	
}
