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


/**
 * Acquisition planner which solves the acquisition problem based on a greedy algorithm
 * which tries to plan at each step one additional acquisition, while there are candidate acquisitions left.
 * @author cpralet
 *
 */
public class AcquisitionPlannerRandom {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private final Map<Satellite,SatellitePlan> satellitePlans;
	/** Store selected AcquisitionWindow List */
	public List<AcquisitionWindow> selectedWindows;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerRandom(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
		selectedWindows = new ArrayList<AcquisitionWindow>();
	}

	/** Reset algorithm */
	public void reset(){
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
		selectedWindows = new ArrayList<AcquisitionWindow>();

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
		List<AcquisitionWindow> acquWindowP0Sorted = new ArrayList<AcquisitionWindow>();
		List<AcquisitionWindow> acquWindowP1Sorted = new ArrayList<AcquisitionWindow>();

		for (CandidateAcquisition Acq:candidateAcquisitions) {
			if (Acq.priority == 0) {
				candidateAcquisitionsP0.add(Acq);
				acquWindowP0Sorted.addAll(Acq.acquisitionWindows) ;
			}
			else {
				candidateAcquisitionsP1.add(Acq);
				acquWindowP1Sorted.addAll(Acq.acquisitionWindows) ;
			}

		}

		Random rand = new Random(System.nanoTime());

		Collections.shuffle(acquWindowP0Sorted, rand);
		Collections.shuffle(acquWindowP1Sorted, rand);
		
		while(!acquWindowP0Sorted.isEmpty()){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			AcquisitionWindow acqWindow = acquWindowP0Sorted.remove(0);
			// try to plan one acquisition window for this acquisition (and stop once a feasible acquisition window is found
			Satellite satellite = acqWindow.satellite;
			CandidateAcquisition acq = acqWindow.candidateAcquisition;
			SatellitePlan satellitePlan = satellitePlans.get(satellite);
			if (candidateAcquisitionsP0.contains(acq)) {
				satellitePlan.add(acqWindow);
				if(satellitePlan.isFeasible()){
					nPlanned++;
					acq.selectedAcquisitionWindow = acqWindow;
					candidateAcquisitionsP0.remove(acq);
					selectedWindows.add(acqWindow);
				}
				else
					satellitePlan.remove(acqWindow);
			}
		}
		while(!acquWindowP1Sorted.isEmpty()){
			// We select the less cloud-disturbed acquisitionWindow and do related acquisitions
			AcquisitionWindow acqWindow = acquWindowP1Sorted.remove(0);
			// try to plan one acquisition window for this acquisition (and stop once a feasible acquisition window is found
			Satellite satellite = acqWindow.satellite;
			CandidateAcquisition acq = acqWindow.candidateAcquisition;
			SatellitePlan satellitePlan = satellitePlans.get(satellite);
			if (candidateAcquisitionsP1.contains(acq)) {
				satellitePlan.add(acqWindow);
				if(satellitePlan.isFeasible()){
					nPlanned++;
					acq.selectedAcquisitionWindow = acqWindow;
					candidateAcquisitionsP1.remove(acq);
					selectedWindows.add(acqWindow);
				}
				else
					satellitePlan.remove(acqWindow);
			}
		}
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

		/**
		 * 
		 * @return true if the list of acquisition windows is evaluated as being feasible from a temporal point of view
		 */
		public boolean isFeasible(){

			// sort acquisition windows by increasing start times
			Collections.sort(acqWindows,startTimeComparator);

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

	public Integer[] computeAmount(){
		Integer[] cntByPriority = {0, 0};
		int cntTotal = 0;
		// Count
		for (AcquisitionWindow aw : selectedWindows) {
			Acquisition acq = aw.candidateAcquisition;
			cntByPriority[acq.priority] ++;
			cntTotal ++; 
		}
		return cntByPriority;
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

	private final Comparator<AcquisitionWindow> randomComparator = new Comparator<AcquisitionWindow>(){
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
		int nRuns = 10000;

		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerRandom planner = new AcquisitionPlannerRandom(pb);

		double referenceFitness = planner.computeReferenceFitness();
		// Statistics
		double maxFitness = 0.0;
		int idxMaxFitness = 0;
		int cntMax = 0;
		int idxMaxCnt = 0;
		double[] fitnessArray = new double[nRuns];
		double[] iterationArray = new double[nRuns];
		double[] prio0Array = new double[nRuns];
		double[] prio1Array = new double[nRuns];
		double[] totalArray = new double[nRuns];
		double[] timeArray = new double[nRuns];

		for (int i=0; i<nRuns; i++){
			long startFuncTime = System.nanoTime();

			planner.reset();
			planner.planAcquisitions();	
			// Count
			Integer[] count = planner.computeAmount();
			int cntTotal = count[0] + count[1];

			// Fitness for comparison with ACO
			double fitness = planner.computeFitness(planner.selectedWindows, referenceFitness);

			long endFuncTime = System.nanoTime();
			System.out.print(String.format("% .2f",(endFuncTime - startFuncTime)/1000000000.0) + " s | ");

			iterationArray[i] = (double) i + 1;
			timeArray[i] = ((endFuncTime - startFuncTime)/1000000.0);
			if (fitness > maxFitness) {
				maxFitness = fitness;
				idxMaxFitness = i;
				// Save the plan
				for(Satellite satellite : pb.satellites){
					planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
				}
				fitnessArray[i] = fitness;
				prio0Array[i] += count[0];
				prio1Array[i] += count[1];
				totalArray[i] += count[0] + count[1];

			}else{
				// Count
				fitnessArray[i] = fitnessArray[i-1];
				prio0Array[i] = prio0Array[i-1];
				prio1Array[i] = prio1Array[i-1];
				totalArray[i] = totalArray[i-1];
			}

			System.out.println("Generation " + (i+1) + " | Tot " + (count[0] + count[1]) + " | P0 " + count[0] + " | P1 " + count[1]);

		}

		String name_csv = Params.constellation + "_" + Params.horizon + "_RNG_n" + nRuns + ".csv";
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
		for (int i=0; i<iterationArray.length; i++) {
			sb.append(iterationArray[i]);
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

		System.out.println("Max fitness of " + maxFitness + " at generation " + (idxMaxFitness+1));
		System.out.println("Acquisition planning done");
	}
	
}
