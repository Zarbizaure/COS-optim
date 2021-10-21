package solver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.LinkedList;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.Acquisition;
import problem.AcquisitionWindow;
import problem.CandidateAcquisition;
import problem.PlanningProblem;
import problem.ProblemParserXML;
import problem.Satellite;


/**
 * Acquisition planner which solves the acquisition problem based on a greedy algorithm
 * which tries to plan at each step one additional acquisition, while there are candidate acquisitions left.
 * @author cpralet
 *
 */
public class AcquisitionPlannerACO {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private final Map<Satellite,SatellitePlan> satellitePlans;
	/**Data for the weights */
	private Map<AcquisitionWindow,Double> weightsMap;


	private int searchDepth = 10;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerACO(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
		// Initialize weights
		initializeWeightMap(planningProblem.acquisitionWindows);
	}

	public void initializeWeightMap(List<AcquisitionWindow> acquWindowList){
		weightsMap = new HashMap<AcquisitionWindow,Double>();
		for (AcquisitionWindow acquWindow : acquWindowList){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			Double weight = 1 - acquWindow.cloudProba;
			weightsMap.put(acquWindow, weight);
		}
	}

	/**
	 * Planning function which uses a greedy algorithm. The latter tries to plan at each step 
	 * one additional acquisition (randomly chosen), while there are candidate acquisitions left.
	 */
	public void planAcquisitions(){

		List<CandidateAcquisition> candidateAcquisitions = new ArrayList<CandidateAcquisition>(planningProblem.candidateAcquisitions);
		int nCandidates = candidateAcquisitions.size();
		int nPlanned = 0;
		List<CandidateAcquisition> candidateAcquisitionsP0 = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> candidateAcquisitionsP1 = new ArrayList<CandidateAcquisition>();
		List<AcquisitionWindow> acqWindowP0Sorted = new ArrayList<AcquisitionWindow>();
		List<AcquisitionWindow> acqWindowP1Sorted = new ArrayList<AcquisitionWindow>();

		List<Acquisition> acqP0Selected = new ArrayList<Acquisition>();
		List<Acquisition> acqP1Selected = new ArrayList<Acquisition>();



		for (CandidateAcquisition Acq:candidateAcquisitions) {
			if (Acq.priority == 0) {
				candidateAcquisitionsP0.add(Acq);
				acqWindowP0Sorted.addAll(Acq.acquisitionWindows) ;
			}
			else {
				candidateAcquisitionsP1.add(Acq);
				acqWindowP1Sorted.addAll(Acq.acquisitionWindows) ;
			}

		}

		// Collections.sort(acqWindowP0Sorted,cloudComparator);
		// Collections.sort(acqWindowP1Sorted,cloudComparator);
		
		while(!acqWindowP0Sorted.isEmpty()){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			AcquisitionSelectorContainer selectorContainer = selectRandom(acqP0Selected,acqWindowP0Sorted, searchDepth);
			acqWindowP0Sorted = selectorContainer.acquisitionList;
			AcquisitionWindow acqWindow = selectorContainer.acquisition;
			//System.out.println("nRemainingCandidatesP0: " + acqWindowP0Sorted.size());
			if (Objects.nonNull(acqWindow)){
				Acquisition acq = acqWindow.candidateAcquisition;
				acqP0Selected.add(acq);
				nPlanned++;
				// System.out.println("selected acqWindow tstart=" + acqWindow.earliestStart);
			}
		}
		while(!acqWindowP1Sorted.isEmpty()){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			AcquisitionSelectorContainer selectorContainer = selectRandom(acqP1Selected,acqWindowP1Sorted, searchDepth);
			acqWindowP1Sorted = selectorContainer.acquisitionList;
			AcquisitionWindow acqWindow = selectorContainer.acquisition;
			// System.out.println("nRemainingCandidatesP1: " + acqWindowP1Sorted.size());
			if (Objects.nonNull(acqWindow)){
				Acquisition acq = acqWindow.candidateAcquisition;
				acqP1Selected.add(acq);
				nPlanned++;
				// System.out.println("selected acqWindow tstart=" + acqWindow.earliestStart);
			}
		}
		System.out.println("nPlanned: " + nPlanned + "/" + nCandidates);
	}

	private AcquisitionSelectorContainer selectRandom(List<Acquisition> acqSelected, List<AcquisitionWindow> acqWindowSorted, int searchDepth) {
		int maxLength = Math.min(searchDepth, acqWindowSorted.size());
		List<AcquisitionWindow> acqWindowFirsts = new ArrayList<>(acqWindowSorted.subList(0, maxLength));
		List<AcquisitionWindow> acqWindowFirstsValid = new ArrayList<AcquisitionWindow>();


		// Compute the weights
		double weightsSum = 0.0;
		for (AcquisitionWindow acqWindow : acqWindowFirsts) {
			CandidateAcquisition acq = acqWindow.candidateAcquisition;
			Satellite satellite = acqWindow.satellite;
			SatellitePlan satellitePlan = satellitePlans.get(satellite);
	
			// Check that the acquisition window is not already realized and feasible
			
			if (!acqSelected.contains(acq) && satellitePlan.isFeasible(acqWindow)){
				acqWindowFirstsValid.add(acqWindow);
				Double weight = weightsMap.get(acqWindow);
				weightsSum += weight;
				// System.out.println("Potential acq weigtht " + weight + " ts=" + acqWindow.earliestStart);
			}
			else{
				acqWindowSorted.remove(acqWindow);
			}
		}
		
		// no valid acquisition window
		if (acqWindowFirstsValid.isEmpty()) { 
			return new AcquisitionSelectorContainer(acqWindowSorted, null);
		}
		
		// Select a random acquisition based on the computed weigths and add it to the corresponding satellitePlan
		Random rand = new Random(System.currentTimeMillis());
		AcquisitionWindow acqWindow = selectWeightedAcquisitionWindow(rand, acqWindowFirstsValid, weightsMap, weightsSum);
		Satellite satellite = acqWindow.satellite;
		SatellitePlan satellitePlan = satellitePlans.get(satellite);
		// CandidateAcquisition acq = acqWindow.candidateAcquisition;
		// acq.selectedAcquisitionWindow = acqWindow;
		// acqSelected.add(acq);
		satellitePlan.add(acqWindow);
		acqWindowSorted.remove(acqWindow);

		return new AcquisitionSelectorContainer(acqWindowSorted, acqWindow);
	}

	public AcquisitionWindow selectWeightedAcquisitionWindow(Random rand, List<AcquisitionWindow> acqWindows, Map<AcquisitionWindow,Double> weightsMap, double weightsSum) {
		double randomValue = rand.nextDouble()*weightsSum;
        double currentSum = 0.0;
		AcquisitionWindow selectedAcqWindow = null;
		
        for (AcquisitionWindow acqWindow : acqWindows){
            if (randomValue < currentSum + weightsMap.get(acqWindow)){
                return acqWindow;
            }
            currentSum+= weightsMap.get(acqWindow);
            selectedAcqWindow = acqWindow;
        }
        return selectedAcqWindow;
	}

	public class AcquisitionSelectorContainer{

		private List<AcquisitionWindow> acquisitionList;
		private AcquisitionWindow acquisition;
	  
		public AcquisitionSelectorContainer(List<AcquisitionWindow> acquisitionList, AcquisitionWindow acquisition){
			this.acquisitionList = acquisitionList;
			this.acquisition = acquisition;
		}
	
		// getters and setters
	}

	

	private class SatellitePlan {

		/** Acquisitions to be realized by the satellite */
		private List<AcquisitionWindow> acqWindows;
		/** Map defining the start time of each acquisition in the solution schedule */
		private Map<AcquisitionWindow,Double> startTimes;
		/** Map defining the end time of each acquisition in the solution schedule */
		private Map<AcquisitionWindow, Double> endTimes;


		public SatellitePlan(){
			acqWindows = new ArrayList<AcquisitionWindow>();
			startTimes = new HashMap<AcquisitionWindow,Double>();
			endTimes = new HashMap<AcquisitionWindow,Double>();
		}

		public double getStart(AcquisitionWindow aw){
			return startTimes.get(aw);
		}

		public double getEnd(AcquisitionWindow aw){
			return endTimes.get(aw);

		}

		public List<AcquisitionWindow> getAcqWindows(){
			return acqWindows;
		}

		public void add(AcquisitionWindow aw){
			acqWindows.add(aw);
		}

		public void remove(AcquisitionWindow aw){
			acqWindows.remove(aw);
			startTimes.remove(aw);
		}

		public void addTimes(AcquisitionWindow aw, double startTime){
			double endTime = startTime + aw.duration;
			startTimes.put(aw, startTime);
			endTimes.put(aw, endTime);
		}

		public Comparator<AcquisitionWindow> chosenStartTimeComparator = new Comparator<AcquisitionWindow>(){
			@Override
			public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
				return Double.compare(startTimes.get(w0), startTimes.get(w1));
			}		
		};


		/**
		 * 
		 * @return true if the list of acquisition windows is evaluated as being feasible from a temporal point of view
		 */
		public boolean isFeasible(AcquisitionWindow acqWindow){

			// sort acquisition windows by increasing start times
			Collections.sort(acqWindows,chosenStartTimeComparator);
			// sortByValues();

			// First acquisition to be added
			if (acqWindows.isEmpty()){
				double startTime = Math.max(planningProblem.horizonStart,acqWindow.earliestStart);
				double endTime = startTime + acqWindow.duration;
				startTimes.put(acqWindow, startTime);
				endTimes.put(acqWindow, endTime);
				return true;
			}else{
				// Else try to insert it between existing acqWindows

				for (int i=0;i<acqWindows.size();i++){
					System.out.println(acqWindows.size());

					AcquisitionWindow acqWindowPrev = acqWindows.get(i);
					double rollAngleTransitionTimePrev = planningProblem.getTransitionTime(acqWindowPrev, acqWindow);
					double startCandidate = endTimes.get(acqWindowPrev) + rollAngleTransitionTimePrev;
					if (i < acqWindows.size()-1){

						AcquisitionWindow acqWindowNext = acqWindows.get(i+1);
						double rollAngleTransitionTimeNext = planningProblem.getTransitionTime(acqWindowNext, acqWindow);
						double nextWindowStart = startTimes.get(acqWindowNext);
						double startTime = Math.max(startCandidate, acqWindow.earliestStart);
						double endCandidate = startTime + acqWindow.duration + rollAngleTransitionTimeNext;
						if ((acqWindow.latestStart > startCandidate) && (endCandidate < nextWindowStart)) {
							// Feasible
							addTimes(acqWindow, startTime);
							return true;
						}
					}
					else{
						if (acqWindow.latestStart > startCandidate) {
							// Add to the end
							double startTime = Math.max(startCandidate, acqWindow.earliestStart);
							addTimes(acqWindow, startTime);
							return true;
						}
					}

				}
				return false;
			}
		}

	}

	/** Comparator used for sorting acquisition windows by increasing earliest start time */
	private final Comparator<AcquisitionWindow> startTimeComparator = new Comparator<AcquisitionWindow>(){
		@Override
		public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
			return Double.compare(w0.earliestStart, w1.earliestStart);
		}		
	};

	private final Comparator<AcquisitionWindow> cloudComparator = new Comparator<AcquisitionWindow>(){
		@Override
		public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
			return Double.compare(w0.cloudProba, w1.cloudProba);
		}		
	};

	/**
	 * Write the acquisition plan of a given satellite in a file
	 * @param satellite
	 * @param solutionFilename
	 * @throws IOException
	 */
	public void writePlan(Satellite satellite, String solutionFilename) throws IOException{
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(solutionFilename, false)));
		SatellitePlan plan = satellitePlans.get(satellite);
		for(AcquisitionWindow aw : plan.getAcqWindows()){
			double start = plan.getStart(aw);
			writer.write(aw.candidateAcquisition.idx + " " + aw.idx + " " + start + " " + (start+aw.duration) + 
					 " " + aw.candidateAcquisition.name + "\n");
		}
		writer.flush();
		writer.close();
	}

	
	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerACO planner = new AcquisitionPlannerACO(pb);
		planner.planAcquisitions();	
		for(Satellite satellite : pb.satellites){
			planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		}
		System.out.println("Acquisition planning done");
	}
	
}
