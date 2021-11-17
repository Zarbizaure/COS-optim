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
import solver.EvalPerf;
import plot.Plot;
import java.awt.Color;
import java.util.stream.IntStream;


/**
 * Acquisition planner which solves the acquisition problem based on a greedy algorithm
 * which tries to plan at each step one additional acquisition, while there are candidate acquisitions left.
 * @author cpralet
 *
 */
public class AcquisitionPlannerACOGlobal {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private Map<Satellite,SatellitePlan> satellitePlans;
	/**Data for the weights */
	private Map<AcquisitionWindow,Double> pheronomes;

	private List<AcquisitionWindow> selectedWindows;

	private final int searchDepth;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerACOGlobal(PlanningProblem planningProblem, int searchDepth){
		this.planningProblem = planningProblem;
		this.searchDepth = searchDepth;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
		// Initialize weights
		initializeWeightMap(planningProblem.acquisitionWindows);
	}

	/** Reset but keep weigths */
	public void reset(){
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
	}

	public void initializeWeightMap(List<AcquisitionWindow> acqWindowList){
		pheronomes = new HashMap<AcquisitionWindow,Double>();
		for (AcquisitionWindow aw : acqWindowList){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			Double weight = getInitialWeigth(aw);
			pheronomes.put(aw, weight);
		}
	}

	public double getInitialWeigth(AcquisitionWindow aw){
		return 1 - aw.cloudProba;
	}

	public void decayPheromones(double decayRate){
		for (AcquisitionWindow aw : pheronomes.keySet()){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			double currentPheromone = pheronomes.get(aw);
			double  newPheromone = Math.max((1-decayRate)*currentPheromone, getInitialWeigth(aw));
			pheronomes.put(aw, newPheromone);
		}
	}

	public void updatePheromones(double score){
		for (AcquisitionWindow aw : this.selectedWindows){
			double currentWeigth = pheronomes.get(aw);
			double  newWeigth = currentWeigth + score;
			pheronomes.put(aw, newWeigth);
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

		Collections.sort(acqWindowP0Sorted,startTimeComparator);
		Collections.sort(acqWindowP1Sorted,startTimeComparator);
		
		while(!acqWindowP0Sorted.isEmpty()){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			AcquisitionSelectorContainer selectorContainer = selectRandom(acqP0Selected,acqWindowP0Sorted, searchDepth);
			acqWindowP0Sorted = selectorContainer.acquisitionList;
			AcquisitionWindow acqWindow = selectorContainer.acquisition;
			if (Objects.nonNull(acqWindow)){
				Acquisition acq = acqWindow.candidateAcquisition;
				acqP0Selected.add(acq);
				nPlanned++;
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
			}
		}
		// System.out.println("nPlanned: " + nPlanned + "/" + nCandidates);
	}

	private AcquisitionSelectorContainer selectRandom(List<Acquisition> acqSelected, List<AcquisitionWindow> acqWindowSorted, int searchDepth) {
		int maxLength = Math.min(searchDepth, acqWindowSorted.size());
		List<AcquisitionWindow> acqWindowFirsts = new ArrayList<>(acqWindowSorted.subList(0, maxLength));
		List<AcquisitionWindow> acqWindowFirstsValid = new ArrayList<AcquisitionWindow>();

		for (AcquisitionWindow acqWindow : acqWindowFirsts) {
			CandidateAcquisition acq = acqWindow.candidateAcquisition;
			Satellite satellite = acqWindow.satellite;
			SatellitePlan satellitePlan = satellitePlans.get(satellite);
	
			// Check that the acquisition window is not already realized and feasible
			if (!acqSelected.contains(acq) && satellitePlan.isFeasible(acqWindow)){
				acqWindowFirstsValid.add(acqWindow);
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
		Random rand = new Random(System.nanoTime());
		AcquisitionWindow acqWindow = selectWeightedAcquisitionWindow(rand, acqWindowFirstsValid, pheronomes);
		Satellite satellite = acqWindow.satellite;
		SatellitePlan satellitePlan = satellitePlans.get(satellite);
		// CandidateAcquisition acq = acqWindow.candidateAcquisition;
		// acq.selectedAcquisitionWindow = acqWindow;
		// acqSelected.add(acq);
		satellitePlan.add(acqWindow);
		acqWindowSorted.remove(acqWindow);

		return new AcquisitionSelectorContainer(acqWindowSorted, acqWindow);
	}


    public double getProbability(AcquisitionWindow aw, double pheromone){
        return Math.pow(getInitialWeigth(aw),1)*Math.pow(pheromone,1);
    }

    public AcquisitionWindow selectWeightedAcquisitionWindow(Random rand, List<AcquisitionWindow> acqWindows, Map<AcquisitionWindow,Double> weightsMap) {
        double probaSum = 0.0;
        AcquisitionWindow selectedAcqWindow = null;
        
        // Sum total weight
        for (AcquisitionWindow aw : acqWindows){
            probaSum += getProbability(aw, weightsMap.get(aw));
        }
        
        // Select
        double randomValue = rand.nextDouble()*probaSum;
        double currentSum = 0.0;
        for (AcquisitionWindow aw : acqWindows){
            double proba = getProbability(aw, weightsMap.get(aw));
            currentSum+= proba;
            if (randomValue < currentSum){
                return aw;
            }
            selectedAcqWindow = aw;
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

	public void getSelectedWindows() {
		// Reset count
		selectedWindows = new ArrayList<AcquisitionWindow>();
		// Count
		for (Satellite satellite: planningProblem.satellites) {
			selectedWindows.addAll(satellitePlans.get(satellite).acqWindows);
		}
	}

	public double computeReferenceFitness(){
		List<AcquisitionWindow> awList = selectBestWindows();
		return computeFitness(awList, 1.0);
	}

	public List<AcquisitionWindow> selectBestWindows() { // select best window by coverage
		List<AcquisitionWindow> awList = new ArrayList<AcquisitionWindow>();
		for (CandidateAcquisition acquisition : planningProblem.candidateAcquisitions) {
			awList.add(Collections.max(acquisition.acquisitionWindows, cloudComparator));
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
				cntFitness += (1-aw.cloudProba) * 5;
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
		int cntTotal = 0;
		// Count
		for (AcquisitionWindow aw : selectedWindows) {
			Acquisition acq = aw.candidateAcquisition;
			cntByPriority[acq.priority] += 1;
			cntTotal += 1; 
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

	private final Comparator<AcquisitionWindow> priorityComparator = new Comparator<AcquisitionWindow>(){
		@Override
		public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
			return Double.compare(w0.candidateAcquisition.priority, w1.candidateAcquisition.priority);
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
		int nRuns = 200;
		int searchDepth = 5;
		double progressionRewardMult = 300; // reward mult when the fitness increases
		double regressionRewardMult = 50; // penalty mult when the fitness decreases
		double decayRate = 0.025; // decay rate

		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerACOGlobal planner = new AcquisitionPlannerACOGlobal(pb, searchDepth);

		// Reference fitness
		double referenceFitness = planner.computeReferenceFitness();
		double previousFitness = 0.0;
		System.out.println("Reference Fitness " + String.format("% .2f", referenceFitness));
		double updateScore = 0.0;

		// Statistics
		double maxFitness = 0.0;
		double minFitness = 100000.0;
		int idxMaxFitness = 0;
		double[] fitnessArray = new double[nRuns];
		double[] iterationArray = new double[nRuns];
		double[] prio0Array = new double[nRuns];
		double[] prio1Array = new double[nRuns];
		double[] totalArray = new double[nRuns];

		for (int i=0; i<nRuns; i++){
			long startFuncTime = System.nanoTime();

			planner.reset();
			planner.planAcquisitions();	
			// Evaluate fitness
			planner.getSelectedWindows();
			double fitness = planner.computeFitness(planner.selectedWindows, referenceFitness);
			Integer[] count = planner.computeAmount();

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

			double val0 = planner.pheronomes.get(pb.acquisitionWindows.get(367));
			double val1 = planner.pheronomes.get(pb.acquisitionWindows.get(423));

			long endFuncTime = System.nanoTime();
			System.out.print(String.format("% .2f",(endFuncTime - startFuncTime)/1000000000.0) + " s | ");
			System.out.println("Gen " + (i+1) + " | P0 " + count[0] + " | P1 " + count[1] + " | Fitness " + String.format("% .2f", fitness) + " | UpScore " + String.format("% .2f", updateScore) + " | Value0 " + String.format("% .2f", val0) + " | Value1 " + String.format("% .2f", val1));

			// Count
			fitnessArray[i] = fitness;
			iterationArray[i] = (double) i;
			prio0Array[i] += count[0];
			prio1Array[i] += count[1];
			totalArray[i] += count[0] + count[1];

			previousFitness = fitness;
		}

		Plot plot = Plot.plot(Plot.plotOpts().
			title("Fitness Evolution").
			legend(Plot.LegendFormat.NONE)).
			xAxis("Run", Plot.axisOpts().
				range(0, nRuns)).
			yAxis("Score", Plot.axisOpts().
				range(0, referenceFitness*0.2)).
			series("Fitness", Plot.data().
				xy(iterationArray, fitnessArray),
			Plot.seriesOpts().
				marker(Plot.Marker.NONE).
				markerColor(Color.BLACK).
				color(Color.BLUE));

		plot.save("score", "png");

		for(Satellite satellite : pb.satellites){
			planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
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
	}
	
}
