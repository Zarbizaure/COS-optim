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

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.Acquisition;
import problem.AcquisitionWindow;
import problem.CandidateAcquisition;
import problem.PlanningProblem;
import problem.ProblemParserXML;
import problem.Satellite;
import plot.Plot;
import java.awt.Color;


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
	private Map<Satellite,SatellitePlan> satellitePlans;
	/** Data for the pheromones weights */
	private Map<AcquisitionWindow,Double> pheronomes;
	/** Hashmap to keep track of the available AcquisitionWindow */
	private Map<AcquisitionWindow, Boolean> availableWindows;
	/** List of selected windows */
	private List<AcquisitionWindow> selectedWindows;
	/** List of selected windows which have been randomly drawn */
	private List<AcquisitionWindow> drawnWindows;
	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerACO(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		this.reset();
		this.initializePheromones(planningProblem.acquisitionWindows);
	}

	/** Reset but keep pheromones weigths */
	public void reset(){
		// Create new satellite plans
		this.satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
		// Create lists
		this.selectedWindows = new ArrayList<AcquisitionWindow>();
		this.drawnWindows = new ArrayList<AcquisitionWindow>();
		// Initialize weights
		this.setAllWindowsAvailable();
	}

	/** Set windows availability to true for all AW */
	public void setAllWindowsAvailable(){
		this.availableWindows = new HashMap<AcquisitionWindow,Boolean>();
		for (AcquisitionWindow aw : planningProblem.acquisitionWindows){
			this.availableWindows.put(aw,true);
		}
	}

	/** Set all given windows to false */
	public void setWindowsUnavailable(List<AcquisitionWindow> acqWindows){
		for (AcquisitionWindow aw : acqWindows){
			availableWindows.put(aw,false);
		}
	}

	/** Get first available window among a list of AW, null otherwise */
	public AcquisitionWindow findFirstAvailableWindowLeft(List<AcquisitionWindow> acqWindows){
		for (AcquisitionWindow aw : acqWindows){
			if (availableWindows.get(aw)){
				return aw;
			}
		}
		return null;
	}

	/** Return true if there remains at least one available window */
	public Boolean areAnyAvailableWindowLeft(List<AcquisitionWindow> acqWindows){
		return Objects.nonNull(findFirstAvailableWindowLeft(acqWindows));
	}

	public void initializePheromones(List<AcquisitionWindow> acqWindowList){
		pheronomes = new HashMap<AcquisitionWindow,Double>();
		for (AcquisitionWindow aw : acqWindowList){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			Double weight = getInitialWeigth(aw);
			pheronomes.put(aw, weight);
		}
	}

	public double getInitialWeigth(AcquisitionWindow aw){
		return 1;
	}

	public double getBaseWeigth(AcquisitionWindow aw){
		Acquisition acq = aw.candidateAcquisition;
		if (acq.priority == 0) {
			return (1 - aw.cloudProba) * 10;
		}else{
			return (1 - aw.cloudProba) * 1;
		}
	}

	public void decayPheromones(double decayRate){
		for (AcquisitionWindow aw : pheronomes.keySet()){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			double currentPheromone = pheronomes.get(aw);
			double newPheromone = Math.max((1-decayRate)*currentPheromone, getInitialWeigth(aw));
			pheronomes.put(aw, newPheromone);
		}
	}

	public void multPheromones(double mult){
		for (AcquisitionWindow aw : this.drawnWindows){
			double currentPheromone = pheronomes.get(aw);
			double newPheromone = Math.max(currentPheromone * mult,0);
			pheronomes.put(aw, newPheromone);
		}
	}

	public void addPheromones(double add){
		for (AcquisitionWindow aw : this.drawnWindows){
			double currentPheromone = pheronomes.get(aw);
			double newPheromone = Math.max(currentPheromone + add,0);
			pheronomes.put(aw, newPheromone);
		}
	}

	/**
	 * Planning function which uses a greedy algorithm. The latter tries to plan at each step 
	 * one additional acquisition (randomly chosen), while there are candidate acquisitions left.
	 */
	public void planAcquisitions(){

		List<CandidateAcquisition> candidateAcquisitions = new ArrayList<CandidateAcquisition>(planningProblem.candidateAcquisitions);
		List<AcquisitionWindow> acqWindowsSorted = new ArrayList<AcquisitionWindow>();

		for (CandidateAcquisition acq:candidateAcquisitions) {
			acqWindowsSorted.addAll(acq.acquisitionWindows);
		}

		Collections.sort(acqWindowsSorted,startTimeComparator);
		
		while(areAnyAvailableWindowLeft(acqWindowsSorted)){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			selectNextWindow(acqWindowsSorted);
		}
	}

	private List<AcquisitionWindow> getConccurentWindows(List<AcquisitionWindow> acqWindows, AcquisitionWindow aw){

		List<AcquisitionWindow> acqWindowsConccurent = new ArrayList<AcquisitionWindow>();

		for (AcquisitionWindow awConc:acqWindows){
			// Not already unavailable
			if (availableWindows.get(awConc)) {
				CandidateAcquisition acqConccurent = awConc.candidateAcquisition;
				Satellite satelliteConcurrent = awConc.satellite;
				SatellitePlan satellitePlan = satellitePlans.get(satelliteConcurrent);
				
				// In case of conflict add to the list
				
				if ((acqConccurent == aw.candidateAcquisition) || (satellitePlan.findStartTime(awConc) < 0.0)) {
					acqWindowsConccurent.add(awConc);
				} 
			}
		}

		return acqWindowsConccurent;
	}

	private void addAcqWindowToPlan(AcquisitionWindow aw){
		Satellite satelliteCandidate = aw.satellite;
		SatellitePlan satellitePlan = satellitePlans.get(satelliteCandidate);
		double startTime = satellitePlan.findStartTime(aw);
		assert startTime > 0.0 : "Error :  Invalid startTime";
		satellitePlan.add(aw, startTime);
	}

	private void removeAcqWindowFromPlan(AcquisitionWindow aw){
		Satellite satelliteCandidate = aw.satellite;
		SatellitePlan satellitePlan = satellitePlans.get(satelliteCandidate);
		satellitePlan.remove(aw);
	}

	public void selectNextWindow(List<AcquisitionWindow> acqWindowsSorted) {
		// First potential acquitisition window
		AcquisitionWindow acqWindow = findFirstAvailableWindowLeft(acqWindowsSorted);
		// Test add to the plan
		addAcqWindowToPlan(acqWindow);

		// System.out.print("Candidate " + acqWindow.candidateAcquisition.name + " Priority " + acqWindow.candidateAcquisition.priority);

		// List of conccurent acquisitions windows
		List<AcquisitionWindow> acqWindowsConccurent = getConccurentWindows(acqWindowsSorted, acqWindow);

		// System.out.print(" ---- " + acqWindowsConccurent.size() +  " conccurent windows");
		
		if (acqWindowsConccurent.size() > 1){ // DRAW
			removeAcqWindowFromPlan(acqWindow); // Remove window
			// Select a random acquisition based on the computed weigths and add it to the corresponding satellitePlan
			Random rand = new Random(System.nanoTime());
			acqWindow = selectWeightedAcquisitionWindow(rand, acqWindowsConccurent, pheronomes);
			drawnWindows.add(acqWindow);

			// Add to plan and to the selected windows
			addAcqWindowToPlan(acqWindow);
			// Set these windows to the unavailable state
			setWindowsUnavailable(getConccurentWindows(acqWindowsSorted, acqWindow));
		}else{ // DETERMINISTIC
			// Set these windows to the unavailable state
			setWindowsUnavailable(acqWindowsConccurent);
		} 

		selectedWindows.add(acqWindow);

		// System.out.println(" ---- " + "Selected " + acqWindow.candidateAcquisition.name + " Priority " + acqWindow.candidateAcquisition.priority);
	}


    public double getProbability(double base, double pheromone){
        return Math.pow(base,1)*Math.pow(pheromone,1);
    }

    public AcquisitionWindow selectWeightedAcquisitionWindow(Random rand, List<AcquisitionWindow> acqWindows, Map<AcquisitionWindow,Double> pheronomes) {
        double probaSum = 0.0;
        AcquisitionWindow selectedAcqWindow = null;
        
        // Sum total weight
        for (AcquisitionWindow aw : acqWindows){
            probaSum += getProbability(getBaseWeigth(aw), pheronomes.get(aw));
        }
        
        // Select
        double randomValue = rand.nextDouble()*probaSum;
        double currentSum = 0.0;
        for (AcquisitionWindow aw : acqWindows){
            double proba = getProbability(getBaseWeigth(aw), pheronomes.get(aw));
            currentSum+= proba;
            if (randomValue < currentSum){
                return aw;
            }
            selectedAcqWindow = aw;
        }
        return selectedAcqWindow;
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

/* 		public double getEnd(AcquisitionWindow aw){
			return endTimes.get(aw);

		} */

		public List<AcquisitionWindow> getAcqWindows(){
			return acqWindows;
		}

		public void add(AcquisitionWindow aw, double startTime){
			acqWindows.add(aw);
			addTimes(aw, startTime);
		}

		public void remove(AcquisitionWindow aw){
			acqWindows.remove(aw);
			startTimes.remove(aw);
			endTimes.remove(aw);
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
		 * @return startTime if the list of acquisition windows is evaluated as being feasible from a temporal point of view
		 */
		public double findStartTime(AcquisitionWindow acqWindow){

			// sort acquisition windows by increasing start times
			startTimes.put(acqWindow, acqWindow.earliestStart);
			acqWindows.add(acqWindow);
			Collections.sort(acqWindows,chosenStartTimeComparator);
			int i_start = Math.max(Collections.binarySearch(acqWindows, acqWindow, chosenStartTimeComparator),1)-1;
			acqWindows.remove(acqWindow);
													// sortByValues();

			// First acquisition to be added
			if (acqWindows.isEmpty()){
				double startTime = Math.max(planningProblem.horizonStart,acqWindow.earliestStart);
				return startTime;
			}else{
				// System.out.println("Index " + i_start + " earliestStart " + acqWindow.earliestStart + " firstStartTime " + startTimes.get(acqWindows.get(i_start)));

				// Else try to insert it between existing acqWindows

				for (int i=i_start;i<acqWindows.size();i++){
					AcquisitionWindow acqWindowPrev = acqWindows.get(i);
					double rollAngleTransitionTimePrev = planningProblem.getTransitionTime(acqWindowPrev, acqWindow);
					double startCandidate = endTimes.get(acqWindowPrev) + rollAngleTransitionTimePrev;

					// break loop if already too far
					if (startCandidate > acqWindow.latestStart){
						return -1;
					}

					if (i < acqWindows.size()-1){

						AcquisitionWindow acqWindowNext = acqWindows.get(i+1);

						double rollAngleTransitionTimeNext = planningProblem.getTransitionTime(acqWindowNext, acqWindow);
						double nextWindowStart = startTimes.get(acqWindowNext);
						double startTime = Math.max(startCandidate, acqWindow.earliestStart);
						double endCandidate = startTime + acqWindow.duration + rollAngleTransitionTimeNext;
						if ((acqWindow.latestStart > startCandidate) && (endCandidate < nextWindowStart)) {
							// Feasible
							return startTime;
						}
					}
					else{
						if (acqWindow.latestStart > startCandidate) {
							// Add to the end
							double startTime = Math.max(startCandidate, acqWindow.earliestStart);
							return startTime;
						}
					}

				}
				// Not feasible
				return -1;
			}

		}

		public boolean isFeasible(){

			// sort acquisition windows by increasing start times
			Collections.sort(acqWindows,chosenStartTimeComparator);

			// initialize the forward traversal of the acquisition windows by considering the first one 
			AcquisitionWindow prevAcqWindow = acqWindows.get(0);
			if(planningProblem.horizonStart > prevAcqWindow.latestStart)
				return false;		
			double startTime = Math.max(planningProblem.horizonStart,prevAcqWindow.earliestStart);
			startTimes.put(prevAcqWindow,startTime);
			double prevEndTime = startTime + prevAcqWindow.duration;

			// traverse all acquisition windows and check that each acquisition can be realized (taking into account roll angle transitions) 
			for(int i=1;i<acqWindows.size();i++){
				AcquisitionWindow acqWindow = acqWindows.get(i);
				double rollAngleTransitionTime = planningProblem.getTransitionTime(prevAcqWindow, acqWindow);
				startTime = Math.max(prevEndTime+rollAngleTransitionTime,acqWindow.earliestStart);
				if(startTime > acqWindow.latestStart) // sequence of acquisition windows not feasible
					return false;
				startTimes.put(acqWindow,startTime);
				prevEndTime = startTime + acqWindow.duration;
				prevAcqWindow = acqWindow;
			}		
			return true;
		}
	}

	public double computeReferenceFitness(){
		List<AcquisitionWindow> awList = selectBestWindows();
		return computeFitness(awList, 1.0);
	}

	public List<AcquisitionWindow> selectBestWindows() { // select best window by coverage
		List<AcquisitionWindow> awList = new ArrayList<AcquisitionWindow>();
		for (CandidateAcquisition acquisition : planningProblem.candidateAcquisitions) {
			try{
				awList.add(Collections.max(acquisition.acquisitionWindows, cloudComparator));
			}catch(java.util.NoSuchElementException e){}
		}
		return awList;
	}

	public double computeFitness(List<AcquisitionWindow> awList, double referenceFitness) {
		// Reset count
		double cntFitness = 0.0;

		// Count
		for (AcquisitionWindow aw : awList) {
			Acquisition acq = aw.candidateAcquisition;
			if (acq.priority == 0) {
				cntFitness += (1-aw.cloudProba) * 10;
			}
			if (acq.priority == 1) {
				cntFitness += (1-aw.cloudProba) * 1;
			}
		}
		double fitness = cntFitness / referenceFitness;
		return fitness;
	}

	public Integer[] computeAmount(){
		Integer[] cntByPriority = {0, 0};
		// int cntTotal = 0;
		// Count
		for (AcquisitionWindow aw : selectedWindows) {
			Acquisition acq = aw.candidateAcquisition;
			cntByPriority[acq.priority] ++;
			// cntTotal ++; 
		}
		return cntByPriority;
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

/* 	private final Comparator<AcquisitionWindow> priorityComparator = new Comparator<AcquisitionWindow>(){
		@Override
		public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
			return Double.compare(w0.candidateAcquisition.priority, w1.candidateAcquisition.priority);
		}		
	}; */

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
		/* Parameters **/
		int nRuns = 500;
		// double progressionRewardExp = 50; // reward exponent when the fitness increases
		// double regressionRewardExp = 45; // penalty exponent when the fitness decreases
		double progressionRewardMult = 200; // reward mult when the fitness increases
		double regressionRewardMult = 50; // penalty mult when the fitness decreases
		double decayRate = 0.025; // decay rate
		/***************/

		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerACO planner = new AcquisitionPlannerACO(pb);

		// Reference fitness
		double referenceFitness = planner.computeReferenceFitness();
		System.out.println("Reference Fitness " + String.format("% .2f", referenceFitness));
		double previousFitness = 0.0;
		double updateScore = 1.0;

		// Statistics
		double maxFitness = 0.0;
		double minFitness = 100000.0;
		int idxMaxFitness = 0;
		double[] fitnessArray = new double[nRuns];
		double[] iterationArray = new double[nRuns];
		double[] prio0Array = new double[nRuns];
		double[] prio1Array = new double[nRuns];
		double[] totalArray = new double[nRuns];
		double[] timeArray = new double[nRuns];

		/** MAIN LOOP */ 
		for (int i=0; i<nRuns; i++){
			long startFuncTime = System.nanoTime();

			planner.reset();
			planner.planAcquisitions();	
			// Evaluate fitness
			double fitness = planner.computeFitness(planner.selectedWindows, referenceFitness);
			Integer[] count = planner.computeAmount();

			double val0 = planner.pheronomes.get(pb.acquisitionWindows.get(10));
			double val1 = planner.pheronomes.get(pb.acquisitionWindows.get(423));

			if (fitness > maxFitness) {
				maxFitness = fitness;
				idxMaxFitness = i;
				// Save the plan
				for(Satellite satellite : pb.satellites){
					planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
				}
			}else if (fitness < minFitness){
				minFitness = fitness;
			}

			if (i>0) {
				// double relativeFitness = fitness/previousFitness;
				double diffFitness = fitness - previousFitness;
				if (diffFitness > 0) {
					updateScore = diffFitness * progressionRewardMult;
					// updateScore = Math.pow(relativeFitness,  progressionRewardExp);
				}else{
					updateScore = diffFitness * regressionRewardMult;
				}
				
			}

			long endFuncTime = System.nanoTime();
			System.out.print(String.format("% .2f",(endFuncTime - startFuncTime)/1000000000.0) + " s | ");
			System.out.println("Gen " + (i+1) + " | Tot " + (count[0] + count[1]) + " | P0 " + count[0] + " | P1 " + count[1] + " | Fitness " + String.format("% .2f", fitness) + " | UpScore " + String.format("% .2f", updateScore) + " | Ndrawns : " + planner.drawnWindows.size() + " | Val0 " + String.format("% .2f", val0) + " | Val " + String.format("% .2f", val1));

			// planner.multPheromones(updateScore);
			planner.addPheromones(updateScore);
			planner.decayPheromones(decayRate);

			// Count
			fitnessArray[i] = fitness;
			iterationArray[i] = (double) i;
			prio0Array[i] += count[0];
			prio1Array[i] += count[1];
			totalArray[i] += count[0] + count[1];
			timeArray[i] = ((endFuncTime - startFuncTime)/1000000.0);

			previousFitness = fitness;
		}
		/** END MAIN LOOP */ 

		/** CHECK FEASABILITY */
		for (Satellite satellite : planner.planningProblem.satellites){
			SatellitePlan plan = planner.satellitePlans.get(satellite);
			boolean planFeasible = plan.isFeasible();
			if (planFeasible==false){
				System.out.println("ERROR ! Plan not feasible");
			}
		}
		
		System.out.println("Max Fitness of " + String.format("% .2f", maxFitness) + " at generation " + idxMaxFitness);

		double minY = minFitness - (maxFitness - minFitness) * 0.1;
		double maxY = maxFitness + (maxFitness - minFitness) * 0.1;
		Plot scorePlot = Plot.plot(Plot.plotOpts().
			title("Fitness Evolution").
			legend(Plot.LegendFormat.NONE)).
			xAxis("Run", Plot.axisOpts().
				range(0, nRuns)).
			yAxis("Fitness", Plot.axisOpts().
				range(minY, maxY)). 
			series("Fitness", Plot.data().
				xy(iterationArray, fitnessArray),
			Plot.seriesOpts().
				marker(Plot.Marker.NONE).
				color(Color.BLUE));

		Plot countPlot = Plot.plot(Plot.plotOpts().
				title("Number of windows evolution").
				legend(Plot.LegendFormat.BOTTOM)).
				xAxis("Run", Plot.axisOpts().
					range(0, nRuns)).
				yAxis("Count", Plot.axisOpts().
					range(0, planner.planningProblem.candidateAcquisitions.size())).
				series("Prio0", Plot.data().
					xy(iterationArray, prio0Array),
				Plot.seriesOpts().
					marker(Plot.Marker.NONE).
					color(Color.RED)).
				series("Prio1", Plot.data().
					xy(iterationArray, prio1Array),
				Plot.seriesOpts().
					marker(Plot.Marker.NONE).
					color(Color.BLUE)).
				series("Total", Plot.data().
					xy(iterationArray, totalArray),
				Plot.seriesOpts().
					marker(Plot.Marker.NONE).
					color(Color.BLACK));;;

		countPlot.save("plot_count", "png");
		scorePlot.save("plot_score", "png");

		String name_csv = Params.constellation + "_" + Params.horizon + "_ACO_n" + nRuns + ".csv";
		BufferedWriter br = new BufferedWriter(new FileWriter("results/" + name_csv));
		StringBuilder sb = new StringBuilder();

		// Header
		sb.append("Iteration");
		sb.append(";");
		sb.append("Time (ms)");
		sb.append(";");
		sb.append("Fitness");
		sb.append(";");
		sb.append("Prio0");
		sb.append(";");
		sb.append("Prio1");
		sb.append(";");
		sb.append("Total");
		sb.append("\n");

		// Append strings from array
		for (int i=0; i<fitnessArray.length; i++) {
			sb.append((iterationArray[i]+1));
			sb.append(";");
			sb.append(timeArray[i]);
			sb.append(";");
			sb.append(fitnessArray[i]);
			sb.append(";");
			sb.append(prio0Array[i]);
			sb.append(";");
			sb.append(prio1Array[i]);
			sb.append(";");
			sb.append(totalArray[i]);
			sb.append("\n");
		}

		br.write(sb.toString());
		br.close();

		System.out.println("Acquisition planning done");
	}
	
}
