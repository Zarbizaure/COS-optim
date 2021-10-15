package problem;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used for representing an acquisition which is candidate for being realized over the planning horizon
 * @author cpralet
 *
 */
public class CandidateAcquisition extends Acquisition {

	/** Index of the candidate acquisition in the list of candidate acquisitions of the problem */
	public final int idx;
	/** Longitude of the acquisition (in radians) */
	public final double longitude;
	/** Latitude of the acquisition (in radians) */
	public final double latitude;
	/** All acquisition windows associated with this candidate acquisition */
	public List<AcquisitionWindow> acquisitionWindows;
	
	/** Acquisition window selected for realizing the candidate acquisition (value null if no window is selected, that is if the acquisition is not planned) */
	public AcquisitionWindow selectedAcquisitionWindow;
	/** Start time of the acquisition if the acquisition is selected for being realized */
	public double selectedAcquisitionStartTime;
	/** End time of the acquisition */
	public double selectedAcquisitionEndTime;
	
	
	/**
	 * Create a candidate acquisition
	 * @param name
	 * @param user
	 * @param priority
	 * @param longitude
	 * @param latitude
	 * @param idx
	 * 
	 */
	public CandidateAcquisition(String name, User user, int priority, double longitude, double latitude, int idx){
		super(name,user,priority);
		this.longitude = longitude;
		this.latitude = latitude;
		this.idx = idx; 
		acquisitionWindows = new ArrayList<AcquisitionWindow>();
	}

	/**
	 * Add an acquisition window for this acquisition
	 * @param id
	 * @param satellite
	 * @param earliestStart
	 * @param latestStart
	 * @param duration
	 * @param zenithAngle
	 * @param rollAngle
	 * @param cloudProba
	 * @param volume
	 * @return the acquisition window created
	 */
	public AcquisitionWindow addAcqOpportunity(int id, Satellite satellite, double earliestStart, double latestStart, double duration, double zenithAngle, double rollAngle, double cloudProba, long volume){
		AcquisitionWindow w = new AcquisitionWindow(id, this, satellite, earliestStart, latestStart, duration, zenithAngle, rollAngle, cloudProba, volume);
		acquisitionWindows.add(w);
		return w;
	}

	/**
	 * 
	 * @param idx
	 * @return the acquisition window whose identifier equals "idx"
	 */
	public AcquisitionWindow getAcquisitionWindow(int idx) {
		return acquisitionWindows.get(idx);
	}
	
	@Override
	public Satellite getSatellite() {
		return selectedAcquisitionWindow.satellite;
	}

	@Override
	public double getAcquisitionTime() {
		return selectedAcquisitionEndTime;
	}

	@Override
	public long getVolume() {
		return selectedAcquisitionWindow.volume;
	}
	
	@Override
	public String toString(){
		return name + ": " + acquisitionWindows;
	}

}
