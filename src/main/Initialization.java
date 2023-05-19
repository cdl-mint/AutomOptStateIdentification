package main;

import java.util.List;

import design.AxisStream;
import evaluation.Evaluation;
import evaluation.TestCase;
import evaluation.Evaluation.MatchingMode;
import timeSeries.TimeSeriesDatabase;

public class Initialization {

	/** Do not modify below! **/
	public static String MODEL_FILE_PATH = null;

	public static void setupCase(TestCase testCase, MatchingMode mode, List<AxisStream> axesUsed, int nrTrainTraces,
			int nrTestTraces) {
		String sensorStreamsFile = null;
		String groundTruthStatesFile = null;
		List<String> firstStates = null;
		List<String> lastStates = null;
		switch (testCase) {
		case UC1_2States:
			MODEL_FILE_PATH = "model/System03.xmi";
			sensorStreamsFile = "./lib/uc1_2states_sensordata.csv";
			groundTruthStatesFile = "./lib/uc1_2states_reference.csv";
			firstStates = List.of("DriveDown");
			lastStates = List.of("PickUp");
			break;
		case UC1_14States:
			MODEL_FILE_PATH = "model/System4.xmi";
			sensorStreamsFile = "./lib/uc1_14states_sensordata.csv";
			groundTruthStatesFile = "./lib/uc1_14states_reference.csv";
			firstStates = List.of("Idle");
			lastStates = List.of("ReleaseRed", "ReleaseGreen");
			break;
		case UC2_8States:
			MODEL_FILE_PATH = "model/System5.xmi";
			sensorStreamsFile = "./lib/uc2_8states_sensordata.csv";
			groundTruthStatesFile = "./lib/uc2_8states_reference.csv";
			firstStates = List.of("Idle");
			lastStates = List.of("Release");
			break;
		}

		if (nrTrainTraces == 0) {
			// use all traces for training/finding best offsets with Harmony Search
			Evaluation.init(axesUsed, mode, groundTruthStatesFile);
			TimeSeriesDatabase.init(sensorStreamsFile, false, 0);

		} else {
			// use X traces (randomly selected) for training/finding best offsets with
			// Harmony Search and
			// others for evaluation
			Evaluation.init(axesUsed, mode, groundTruthStatesFile, nrTrainTraces, nrTestTraces, firstStates,
					lastStates);
			TimeSeriesDatabase.init(sensorStreamsFile, Evaluation.getInstance().getTrainingTraces(),
					Evaluation.getInstance().getTestTraces());
		}

	}
}
