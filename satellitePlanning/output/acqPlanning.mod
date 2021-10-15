
/** Number of potential acquisition windows for the satellite */
int NacquisitionWindowsForSat = ...;
/** Acquisition range */
range AcquisitionWindowsForSat = 1..NacquisitionWindowsForSat;
range AcquisitionWindowsForSatPlusZero = 0..NacquisitionWindowsForSat;

/** Index of the acquisition window in the list of acquisition windows of the full problem */
int AcquisitionWindowIdx[AcquisitionWindowsForSat] = ...;
/** Index of the corresponding acquisition in the list of candidate acquisitions of the full problem */
int CandidateAcquisitionIdx[AcquisitionWindowsForSat] = ...;

/** Earliest start time associated with each acquisition window */
float EarliestStartTime[AcquisitionWindowsForSat] = ...;
/** Latest start time associated with each acquisition window */
float LatestStartTime[AcquisitionWindowsForSat] = ...;
/** Acquisition duration associated with each acquisition window */
float Duration[AcquisitionWindowsForSat] = ...;

/** Required transition time between each pair of successive acquisitions windows */
float TransitionTimes[AcquisitionWindowsForSat][AcquisitionWindowsForSat] = ...;

/** File in which the result will be written */
string OutputFile = ...;

/** Boolean variable indicating whether an acquisition window is selected */
dvar int selectAcq[AcquisitionWindowsForSatPlusZero] in 0..1;
/** next[a1][a2] = 1 when a1 is the selected acquisition window that precedes a2 */
dvar int next[AcquisitionWindowsForSatPlusZero][AcquisitionWindowsForSatPlusZero] in 0..1;
/** Acquisition start time in each acquisition window */
dvar float+ startTime[a in AcquisitionWindowsForSat] in EarliestStartTime[a]..LatestStartTime[a];

execute{
	cplex.tilim = 60; // 60 seconds
}

// maximize the number of acquisition windows selected
maximize sum(a in AcquisitionWindowsForSat) selectAcq[a];

constraints {
	
	// default selection of the dummy acquisition window numbered by 0
	selectAcq[0] == 1;
	// an acquisition window is selected if and only if it has a (unique) precedessor and a (unique) successor in the plan
	forall(a1 in AcquisitionWindowsForSatPlusZero){
		sum(a2 in AcquisitionWindowsForSatPlusZero : a2 != a1) next[a1][a2] == selectAcq[a1];
		sum(a2 in AcquisitionWindowsForSatPlusZero : a2 != a1) next[a2][a1] == selectAcq[a1];
		next[a1][a1] == 0;
	}

	// restriction of possible successive selected acquisition windows by using earliest and latest acquisition times
	forall(a1,a2 in AcquisitionWindowsForSat : a1 != a2 && EarliestStartTime[a1] + Duration[a1] + TransitionTimes[a1][a2] >= LatestStartTime[a2]){
		next[a1][a2] == 0;
	}

	// temporal separation constraints between successive acquisition windows (big-M formulation)
	forall(a1,a2 in AcquisitionWindowsForSat : a1 != a2 && EarliestStartTime[a1] + Duration[a1] + TransitionTimes[a1][a2] < LatestStartTime[a2]){
		startTime[a1] + Duration[a1] + TransitionTimes[a1][a2]  <= startTime[a2] 
                + (1-next[a1][a2])*(LatestStartTime[a1]+Duration[a1]+TransitionTimes[a1][a2]-EarliestStartTime[a2]);
	}

	// arbitrary start time for non selected acquisition windows
	forall(a in AcquisitionWindowsForSat){
		startTime[a] <= selectAcq[a]*(LatestStartTime[a] - EarliestStartTime[a]) + EarliestStartTime[a];
	}
	
	// a simple additional cut
	forall(ordered a1,a2 in AcquisitionWindowsForSat : 
		   EarliestStartTime[a1] + Duration[a1] + TransitionTimes[a1][a2] < LatestStartTime[a2]
		&& EarliestStartTime[a2] + Duration[a2] + TransitionTimes[a2][a1] < LatestStartTime[a1]){
		next[a1][a2] + next[a2][a1] <= 1;
	}
		
}

execute {
	var ofile = new IloOplOutputFile(OutputFile);
	for(var i=1; i <= NacquisitionWindowsForSat; i++) { 
		if(selectAcq[i] == 1){
			ofile.writeln(CandidateAcquisitionIdx[i] + " " + AcquisitionWindowIdx[i] + " " + startTime[i] + " " + (startTime[i]+Duration[i]));
		}
	}	
}
