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

public class Test {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/**Data for the weights */
	private Map<AcquisitionWindow,Double> weightsMap;
    
    public Test(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		// Initialize weights
		initializeWeightMap(this.planningProblem.acquisitionWindows);
    }
    
    public void initializeWeightMap(List<AcquisitionWindow> acqWindowList){
		weightsMap = new HashMap<AcquisitionWindow,Double>();
		for (AcquisitionWindow aw : acqWindowList){
			// Double weight = acquWindow.cloudProba*(1-0.5*acquWindow.candidateAcquisition.priority);
			Double weight = getInitialWeigth(aw);
			weightsMap.put(aw, weight);
		}
    }
    
    public double getInitialWeigth(AcquisitionWindow aw){
		return 1 - aw.cloudProba;
    }
    
    public double getProbability(AcquisitionWindow aw, double pheromone){
        return Math.pow(getInitialWeigth(aw),2)*Math.pow(pheromone,1);
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


    public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
        int nRuns = 100000;
        int maxLength = 5;

        ProblemParserXML parser = new ProblemParserXML(); 
        PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);

        pb.printStatistics();
        Test test = new Test(pb);

        // test random
        List<AcquisitionWindow> selectedWindows = new ArrayList<AcquisitionWindow>();
        List<AcquisitionWindow> acqWindowFirsts = new ArrayList<>(pb.acquisitionWindows.subList(0, maxLength));
        List<AcquisitionWindow> testWindows = acqWindowFirsts;
        Map<AcquisitionWindow,Double> selectedWindowsAmount = new HashMap<AcquisitionWindow,Double>();

        double probaSum = 0.0;
        for (AcquisitionWindow aw : testWindows){
            selectedWindowsAmount.put(aw, 0.0);
            probaSum += test.getProbability(aw, test.weightsMap.get(aw));
        }

        Random rand = new Random(System.nanoTime());

        for (int i=0; i<nRuns; i++){
            AcquisitionWindow selectedWindow = test.selectWeightedAcquisitionWindow(rand,testWindows, test.weightsMap);
            selectedWindows.add(selectedWindow);
            double amount = selectedWindowsAmount.get(selectedWindow);
            selectedWindowsAmount.put(selectedWindow,amount+1.0);
        }

        for (AcquisitionWindow aw:acqWindowFirsts){
            double expProba = 100*selectedWindowsAmount.get(aw) / nRuns;
            double thProba = 100*test.getProbability(aw, test.weightsMap.get(aw)) / probaSum;
            System.out.println("Weight | " + String.format("% .2f", test.weightsMap.get(aw)) +  " | ThProba " +  String.format("% .2f", thProba) + " | ExpProba " + String.format("% .2f", expProba));
        }

        System.out.println("Test done");
    }

}