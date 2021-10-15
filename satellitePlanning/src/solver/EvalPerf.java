package solver;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;

import javax.swing.JFrame;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.Acquisition;
import problem.AcquisitionWindow;
import problem.CandidateAcquisition;
import problem.DownloadWindow;
import problem.PlanningProblem;
import problem.ProblemParserXML;
import problem.Satellite;
import problem.Station;
import utils.Trajectory2d;
import utils.TrajectoryView;


/**
 * Class useful for defining the GanttChart representation of the plan
 * @author cpralet
 *
 */
public class EvalPerf {

	/** Satellite associated with this plan view */
	SolutionPlan plan;

	/** 
	 * @param planner Planner whose solution is depicted by the viewer
	 * @throws ParseException 
	 */
	public EvalPerf(SolutionPlan plan) throws ParseException {
		this.plan = plan;
	}

	public class ResultsPerf {

		private List<CandidateAcquisition> candAcqList;
		public Integer[] cntByPriority = {0, 0};
		public Integer cntTotal = 0;
		public Double cntByCoverage = 0.0;

		public ResultsPerf(List<CandidateAcquisition> candAcqList){
			this.candAcqList = candAcqList;
		}

		public void selectBestWindow() { // select best window by coverage
			for (CandidateAcquisition acquisition : this.candAcqList) {
				acquisition.selectedAcquisitionWindow = Collections.max(acquisition.acquisitionWindows, cloudProbaComparator);
			}
		}

		public void count() {
			// Reset count
			cntByPriority[0] = 0;
			cntByPriority[1] = 0;
			cntTotal = 0;
			cntByCoverage = 0.0;
			// Count
			for (CandidateAcquisition acquisition : this.candAcqList) {
				this.cntByPriority[acquisition.priority] += 1;
 				this.cntTotal += 1; 
				this.cntByCoverage += (1-acquisition.selectedAcquisitionWindow.cloudProba);
			}
		}

		/** Comparator used to choose an acquisitionWindow based on best cloud coverage */
		private final Comparator<AcquisitionWindow> cloudProbaComparator = new Comparator<AcquisitionWindow>(){
			@Override
			public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
				return Double.compare(w1.cloudProba, w0.cloudProba);
			}		
		};
	}

	public void evaluate() {
		ResultsPerf resPlan = new ResultsPerf(plan.plannedAcquisitions);
		ResultsPerf resCand= new ResultsPerf(plan.pb.candidateAcquisitions);

		// Select the best windows for the candidates
		resCand.selectBestWindow();

		// Perform count on both acquisition list
		resPlan.count();
		resCand.count();

		// Compute ratio
		double ratioTotal = (double) resPlan.cntTotal / resCand.cntTotal;
		double[] ratioByPriority = {0.0,0.0};
		ratioByPriority[0] = (double) resPlan.cntByPriority[0] / resCand.cntByPriority[0];
		ratioByPriority[1] = (double) resPlan.cntByPriority[1] / resCand.cntByPriority[1];
		double ratioByCoverage = resPlan.cntByCoverage / resCand.cntByCoverage;

		// Print
		System.out.println("Acquisition Total (plan/cand): " + String.format("%.2f",ratioTotal) + " (" + resPlan.cntTotal + "/" + resCand.cntTotal + ")");

		System.out.println("Acquisition Prio0 (plan/cand): " + String.format("%.2f",ratioByPriority[0]) + " (" + resPlan.cntByPriority[0] + "/" + resCand.cntByPriority[0] + ")");
		System.out.println("Acquisition Prio1 (plan/cand): " + String.format("%.2f",ratioByPriority[1]) + " (" + resPlan.cntByPriority[1] + "/" + resCand.cntByPriority[1] + ")");

		System.out.println("Acquisition Cloud (plan/cand): " + String.format("%.2f",ratioByCoverage) + " (" + String.format("%.2f",resPlan.cntByCoverage) + "/" +  String.format("%.2f",resCand.cntByCoverage) + ")");

	}

	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException, ParseException{
		
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		SolutionPlan plan = new SolutionPlan(pb);
		int nSatellites = pb.satellites.size();
		for(int i=1;i<=nSatellites;i++)
			plan.readAcquisitionPlan("output/solutionAcqPlan_SAT"+i+".txt");	
		plan.readDownloadPlan("output/downloadPlan.txt");
		
		EvalPerf evalPerf = new EvalPerf(plan);
		evalPerf.evaluate();
	}

}
