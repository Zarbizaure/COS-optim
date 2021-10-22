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
public class AcquisitionPlannerGreedy {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private final Map<Satellite,SatellitePlan> satellitePlans;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerGreedy(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
	}

	/**
	 * Planning function which uses a greedy algorithm. The latter tries to plan at each step 
	 * one additional acquisition (randomly chosen), while there are candidate acquisitions left.
	 */
	public void planAcquisitions(){

		class Task {
			AcquisitionWindow acqWindow;
			double time;
			double score;

			public Task(AcquisitionWindow aw, double t) {
				acqWindow = aw;
				time = t;
				score = 0;
			}
		}
		final Comparator<Task> theComparator = new Comparator<Task>(){
			@Override
			public int compare(Task task0, Task task1) {
				return Double.compare(task0.time, task1.time);
			}		
		};

		List<CandidateAcquisition> candidateAcquisitions = new ArrayList<CandidateAcquisition>(planningProblem.candidateAcquisitions);
		int nCandidates = candidateAcquisitions.size();
		int nPlanned = 0;


		List<Task> taskSorted = new ArrayList<Task>();

		for (CandidateAcquisition acq:candidateAcquisitions) {
			for (AcquisitionWindow aw:acq.acquisitionWindows) {
				double cur_time = aw.earliestStart;
				while (cur_time<aw.latestStart){
					taskSorted.add(new Task(aw, cur_time));
					cur_time += 10*aw.duration;
				}
			}
		}		

		Collections.sort(taskSorted,theComparator);

		Map<Satellite,List<Task>> concerned = new HashMap<Satellite,List<Task>>();
		Map<Satellite,Map<Task,Double>> tabOptim = new HashMap<Satellite,Map<Task,Double>>();
		Map<Satellite,Map<Task,Task>> previous = new HashMap<Satellite,Map<Task,Task>>();
		Map<Satellite,Map<Task,Double>> bestEndTime = new HashMap<Satellite,Map<Task,Double>>();

		for (Satellite sat : planningProblem.satellites) {
			concerned.put(sat,new ArrayList<Task>());
			tabOptim.put(sat, new HashMap<Task,Double>());
			previous.put(sat, new HashMap<Task,Task>());
			bestEndTime.put(sat, new HashMap<Task,Double>());
		}

		while(!taskSorted.isEmpty()){
			// We select the earliest acquisitionWindow and do related computations
			Task task = taskSorted.remove(0);
			AcquisitionWindow acqWindow = task.acqWindow;
			Satellite sat = acqWindow.satellite;

			List<Task> listTask = concerned.get(sat);
			Map<Task,Double> tabOptimHere = tabOptim.get(sat);
			Map<Task,Task> previousMap = previous.get(sat);
			Map<Task,Double> bestEndTimeMap = bestEndTime.get(sat);

			double bestCompatible = 0;
			double bestCompatibleStartTime = acqWindow.earliestStart;
			Task bestTaskFound = task;	/* Not to be used without update */

			/* Find the best previous acquisition */
			for(Task prevTask : listTask){
				double prevAcqEndTime = bestEndTimeMap.get(prevTask);
				double rollAngleTransitionTime = planningProblem.getTransitionTime(prevTask.acqWindow, acqWindow);
				double startTime = Math.max(prevAcqEndTime+rollAngleTransitionTime,acqWindow.earliestStart);

				if (tabOptimHere.get(prevTask) > bestCompatible){
					if (startTime < acqWindow.latestStart) {
						bestTaskFound = prevTask;
						bestCompatible = tabOptimHere.get(prevTask);
						bestCompatibleStartTime = startTime;
					}
				else if(tabOptimHere.get(prevTask) == bestCompatible){
					if (startTime < bestCompatibleStartTime) {
						bestTaskFound = prevTask;
						bestCompatibleStartTime = startTime;
					}
				}
				}
			}
			/* Update the fields of the map concerning the current acquisition window */
			listTask.add(task);
			/* 10000*(1-task.acqWindow.candidateAcquisition.priority)+(1-task.acqWindow.cloudProba) */

			if (bestCompatible == 0) {
				task.score=1;
				tabOptimHere.put(task,task.score); /*Poids de l'acquisition*/
				bestEndTimeMap.put(task,bestCompatibleStartTime+task.acqWindow.duration);
			}
			else{
				if (bestTaskFound.acqWindow != acqWindow) {
					task.score = 1;
				}
				tabOptimHere.put(task,bestCompatible+task.score); /*Poids de l'acquisition*/
				bestEndTimeMap.put(task,bestCompatibleStartTime+task.acqWindow.duration);
				previousMap.put(task,bestTaskFound);
			}
		}

		/* Making the real planification */
		for (Satellite sat : planningProblem.satellites) {
			SatellitePlan satellitePlan = satellitePlans.get(sat);
			List<Task> listTask = concerned.get(sat);
			Map<Task,Double> tabOptimHere = tabOptim.get(sat);
			Map<Task,Task> previousMap = previous.get(sat);

			/* Find max in tabOptimHere */
			double bestResult = 0;
			Task taskOpti = listTask.get(0); /* Not to be used without modification */
			for (Task task : listTask) {
				if (tabOptimHere.get(task) > bestResult){
					bestResult = tabOptimHere.get(task);
					taskOpti = task ;
				}
			}
			System.out.println("bestResult = "+bestResult);

			System.out.println("Result of first "+tabOptimHere.get(taskOpti));

			/* Get up in previous to find the corresponding tasks and update the plan */
			while (bestResult>0.01){
				bestResult=bestResult-taskOpti.score;
				if (taskOpti.acqWindow.candidateAcquisition.selectedAcquisitionWindow==null) {
					satellitePlan.add(taskOpti.acqWindow);
					taskOpti.acqWindow.candidateAcquisition.selectedAcquisitionWindow=taskOpti.acqWindow;
					nPlanned++;
				}
				taskOpti = previousMap.get(taskOpti);
			}
			System.out.println("Result of last "+tabOptimHere.get(taskOpti));
			boolean feasible = satellitePlan.isFeasible();
			System.out.println(sat.name+" feasibility "+feasible);
		}

		System.out.println("nPlanned: " + nPlanned + "/" + nCandidates);
	}



	private class SatellitePlan {

		/** Acquisitions to be realized by the satellite */
		private List<AcquisitionWindow> AcquisitionWindows;
		/** Map defining the start time of each acquisition in the solution schedule */
		private Map<AcquisitionWindow,Double> startTimes;
		/** Map defining the end time of each acquisition in the solution schedule */
		private Map<AcquisitionWindow, Double> endTimes;


		public SatellitePlan(){
			AcquisitionWindows = new ArrayList<AcquisitionWindow>();
			startTimes = new HashMap<AcquisitionWindow,Double>();
			endTimes = new HashMap<AcquisitionWindow,Double>();
		}

		public double getStart(AcquisitionWindow aw){
			return startTimes.get(aw);
		}
		private final Comparator<AcquisitionWindow> cloudComparator = new Comparator<AcquisitionWindow>(){
			@Override
			public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
				return Double.compare(w0.cloudProba, w1.cloudProba);
			}		
		};
		public double getEnd(AcquisitionWindow aw){
			return endTimes.get(aw);

		}

		public List<AcquisitionWindow> getAcquisitionWindows(){
			return AcquisitionWindows;
		}

		public void add(AcquisitionWindow aw){
			AcquisitionWindows.add(aw);
		}

		public void remove(AcquisitionWindow aw){
			AcquisitionWindows.remove(aw);
			startTimes.remove(aw);
		}

		/**
		 * 
		 * @return true if the list of acquisition windows is evaluated as being feasible from a temporal point of view
		 */
		public boolean isFeasible(){

			// sort acquisition windows by increasing start times
			//Collections.sort(AcquisitionWindows,startTimeComparator);
			Collections.reverse(AcquisitionWindows);
			// initialize the forward traversal of the acquisition windows by considering the first one 
			AcquisitionWindow prevAcq = AcquisitionWindows.get(0);
			if(planningProblem.horizonStart > prevAcq.latestStart) {
				return false;
			}
			double startTime = Math.max(planningProblem.horizonStart,prevAcq.earliestStart);
			startTimes.put(prevAcq,startTime);
			double prevEndTime = startTime + prevAcq.duration;

			// traverse all acquisition windows and check that each acquisition can be realized (taking into account roll angle transitions) 
			for(int i=1;i<AcquisitionWindows.size();i++){
				AcquisitionWindow aw = AcquisitionWindows.get(i);
				double rollAngleTransitionTime = planningProblem.getTransitionTime(prevAcq, aw);
				startTime = Math.max(prevEndTime+rollAngleTransitionTime,aw.earliestStart);
				if(startTime > aw.latestStart) // sequence of acquisition windows not feasible
					return false;
				startTimes.put(aw,startTime);
				prevEndTime = startTime + aw.duration;
				prevAcq = aw;
			}		
			return true;
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
		for(AcquisitionWindow aw : plan.getAcquisitionWindows()){
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
		AcquisitionPlannerGreedy planner = new AcquisitionPlannerGreedy(pb);
		planner.planAcquisitions();	
		for(Satellite satellite : pb.satellites){
			planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		}
		System.out.println("Acquisition planning done");
	}
	
}
