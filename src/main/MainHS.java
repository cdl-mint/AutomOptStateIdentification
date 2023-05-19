package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import design.AxisStream;
import evaluation.Evaluation;
import evaluation.Evaluation.EvaluationMode;
import evaluation.Evaluation.MatchingMode;
import evaluation.EvaluationResult;
import evaluation.TestCase;
import evaluation.Trace;

//import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

import harmony.HarmonyMemory;
import harmony.HarmonyParameters;
import harmony.HarmonyParameters.AdditionalOptimizationMethod;
import harmony.HarmonyResult;
import harmony.HarmonySearch;
import harmony.PropertyBoundaries;

import java.util.HashMap;

import timeSeries.TimeSeriesDatabase;
import utils.Printer;

public class MainHS {

	/** Settable variables for testing setup **/
	private static final TestCase testCase = TestCase.UC1_2States;
	private static final int[] USE_X_TRACES_FOR_TRAINING = { 10 };
	private static final int[] USE_X_TRACES_FOR_TESTING = { 50 };

	private static final String OUTPUT_DIRECTORY = "./results/Random_final";

	private static final MatchingMode matchingMode = MatchingMode.PLAIN_RETRIEVAL_MATCHING;
	private static final List<AxisStream> axesUsed = Arrays.asList(AxisStream.GP, AxisStream.MAP, AxisStream.BP,
			AxisStream.SAP, AxisStream.WP);
	private static final List<String> STATES_TO_NOT_EVALUATE = new ArrayList<String>();
	/** **/

	/** Harmony Search Parameters **/
	private static final int RUNS = 30;
	private static final int ITERATIONS = 5000;
	private static final double LOWER_OFFSET_BORDER = 0.0;
	private static final double UPPER_OFFSET_BORDER = 0.7;
	private static final boolean USE_SOBOL_MEMORY_INIT = false;

	private static final AdditionalOptimizationMethod[] ADDITIONAL_OPTIMIZATION = { AdditionalOptimizationMethod.NONE,
			AdditionalOptimizationMethod.MINIMIZE, AdditionalOptimizationMethod.MAXIMIZE }; // 2nd objective
	private static final boolean ABORT_RUN_IF_OPTIMUM_FOUND = true;

//	// Parameter combinations
	private static final List<Double> P_ACCEPTANCE_LIST = new ArrayList<Double>(Arrays.asList(.0));
	private static final List<Double> P_ADJUSTMENT_LIST = new ArrayList<Double>(Arrays.asList(.0));
	private static final List<Integer> MEMORY_SIZE_LIST = new ArrayList<Integer>(Arrays.asList(1));
	private static final List<Double> BANDWIDTH_LIST = new ArrayList<Double>(Arrays.asList(0.007));

	/** **/

	public static void main(String[] args) {

		// Define parameter combinations for test

		DecimalFormat df = new DecimalFormat("#.###");
		df.setRoundingMode(RoundingMode.CEILING);

		// Run Harmony Search Algorithm with all Parameter combinations
		for (AdditionalOptimizationMethod method : ADDITIONAL_OPTIMIZATION) {

			for (double acc : P_ACCEPTANCE_LIST) {
				for (double adj : P_ADJUSTMENT_LIST) {
					for (int size : MEMORY_SIZE_LIST) {
						for (double bandwidth : BANDWIDTH_LIST) {
							int caseRun = 0;
							for (int xTrain : USE_X_TRACES_FOR_TRAINING) {

								// Create output directory
								String outputDir = OUTPUT_DIRECTORY + "/" + testCase + "_"
										+ String.format("%.2f-%.2f-%.3f-%d", acc, adj, bandwidth, size) + "_"
										+ matchingMode + "_" + method
										+ String.format("_%dtr%dte", xTrain, USE_X_TRACES_FOR_TESTING[caseRun]);
								new File(outputDir).mkdirs();

								String fileBase = outputDir + "/" + testCase + matchingMode + "_" + method
										+ String.format("_%dtr%dte", xTrain, USE_X_TRACES_FOR_TESTING[caseRun]) + "_"
										+ acc + "_" + adj + "_" + size + "_" + bandwidth;
								PrintStream mainFile = setOutStreamToMainFile(fileBase + "_main" + ".txt");

								// states NOT to use for evaluation
								List<HarmonyResult> resultList = new ArrayList<HarmonyResult>();

								// configure Harmony Search
								HarmonyParameters hpa = new HarmonyParameters(adj, bandwidth, acc, size, axesUsed,
										USE_SOBOL_MEMORY_INIT);
								hpa.setPrintNewSolutions(false);
								hpa.setPrintMemorySwaps(false);
								hpa.setNrOfIterations(ITERATIONS);
								hpa.setStopOnOptimum(ABORT_RUN_IF_OPTIMUM_FOUND);
								hpa.setStatesToNotEvaluate(STATES_TO_NOT_EVALUATE);
								hpa.setAdditionalOptimizationMethod(method);
								hpa.setLowerSearchBorder(LOWER_OFFSET_BORDER);
								hpa.setUpperSearchBorder(UPPER_OFFSET_BORDER);

								// Print Harmony Parameters to main file
								Printer.printHeader("Harmony parameters:", System.out);
								System.out.println(hpa);

								// Print Headers for output csv
								System.out.println(
										"Iteration;AvgFMeasure;AvgPrecision;AvgRecall;IterationBestFMeasure;IterationMinimization");

								// number of iterations for average calculation
								for (int i = 0; i < RUNS; i++) {
									// Setup reference set, read sensor stream in TSDB
									Initialization.setupCase(testCase, matchingMode, axesUsed, xTrain,
											USE_X_TRACES_FOR_TESTING[caseRun]);
									// Set output stream to file for this run
									PrintStream outTxt = setOutStreamToCaseFile(fileBase + "_" + i + ".txt");
									PrintStream outCsv = setOutStreamToCaseFile(fileBase + "_" + i + ".csv");

									// Run Harmony Search Algorithm
									HarmonyResult result = runHarmonySearch(hpa, outTxt, outCsv,
											USE_X_TRACES_FOR_TESTING[caseRun]);

									outTxt.close();
									outCsv.close();

									// Print averages to main file
									System.setOut(mainFile);
									System.out.print(i + ";" + result.getAvgBestFMeasure() + ";"
											+ result.getAvgBestPrecision() + ";" + result.getAvgBestRecall() + ";"
											+ result.getNrOfIterationsForBestFMeasure() + ";"
											+ result.getNrOfIterationsForBestMinimizedRange() + "\n");
									resultList.add(result);
								}

								System.setOut(mainFile);

								printAveragedResultsToMainFile(resultList);

								if (mainFile != null) {
									mainFile.close();
								}
								caseRun++;
							}
						}
					}
				}
			}
		}
	}

	private static PrintStream setOutStreamToCaseFile(String filepath) {
		PrintStream out = null;
		try {
			out = new PrintStream(new FileOutputStream(filepath, true), true);
		} catch (IOException e) {
			System.err.print(e.getMessage());
			e.printStackTrace();
		}
		return out;
	}

	private static void printAveragedResultsToMainFile(List<HarmonyResult> resultList) {

		List<Integer> iterationsFMeasureList = new ArrayList<Integer>();
		List<Integer> iterationsMinRangeList = new ArrayList<Integer>();
		List<Double> absOffsetPercDiff = new ArrayList<Double>();
		List<Double> timeTilOptList = new ArrayList<Double>();
		List<Double> avgPrecisionList = new ArrayList<Double>();
		List<Double> avgRecallList = new ArrayList<Double>();
		List<Double> avgFMeasureList = new ArrayList<Double>();
		List<Double> avgTestPrecisionList = new ArrayList<Double>();
		List<Double> avgTestRecallList = new ArrayList<Double>();
		List<Double> avgTestFMeasureList = new ArrayList<Double>();
		List<Double> timeOverallList = new ArrayList<Double>();
		int repetition = 0;
		for (HarmonyResult res : resultList) {
			res.getAvgBestFMeasure();
			System.out.println(String.format("Iterations til best f-measure = %.5f (Exec. %d): %d (%.1fs)",
					res.getAvgBestFMeasure(), repetition, res.getNrOfIterationsForBestFMeasure(),
					res.getRuntimeTilOptimumFound()));

			iterationsFMeasureList.add(res.getNrOfIterationsForBestFMeasure());
			iterationsMinRangeList.add(res.getNrOfIterationsForBestMinimizedRange());
			timeTilOptList.add(res.getRuntimeTilOptimumFound());
			avgPrecisionList.add(res.getAvgBestPrecision());
			avgRecallList.add(res.getAvgBestRecall());
			avgFMeasureList.add(res.getAvgBestFMeasure());
			timeOverallList.add(res.getRuntimeIterations());

			avgTestFMeasureList.add(res.getAvgBestTestFMeasure());
			avgTestPrecisionList.add(res.getAvgBestTestPrecision());
			avgTestRecallList.add(res.getAvgBestTestRecall());

			repetition++;
		}

		// Calculate averages over all runs
		double avgIterationsFMeasure = iterationsFMeasureList.stream().mapToInt(x -> x).average().orElse(-1);
		double avgIterationsMinRange = iterationsMinRangeList.stream().mapToInt(x -> x).average().orElse(-1);
		double avgAbsOffsetPercDiff = absOffsetPercDiff.stream().mapToDouble(x -> x).average().orElse(-1);

		double avgTimeTilOpt = timeTilOptList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgTimeOverall = timeOverallList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgPrec = avgPrecisionList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgRec = avgRecallList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgFMeasure = avgFMeasureList.stream().mapToDouble(x -> x).average().orElse(-1);

		double avgTestPrec = avgTestPrecisionList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgTestRec = avgTestRecallList.stream().mapToDouble(x -> x).average().orElse(-1);
		double avgTestFMeasure = avgTestFMeasureList.stream().mapToDouble(x -> x).average().orElse(-1);
		// Print averages to main file
		System.out.println(Printer.div);
		Printer.printHeader("After all runs:", System.out);
		System.out.println(String.format("Iterations til best f-measure (avg): %.3f", avgIterationsFMeasure));
		System.out.println("Iterations til best minimized range (avg): " + avgIterationsMinRange);
		System.out.println("Abs Offset Difference (between best f-measure and offset optimized best f-measure), (avg): "
				+ ((1.0 - avgAbsOffsetPercDiff) * 100.0) + "%");
		System.out.println(String.format("Execution time til Best found (avg): %.1fs", avgTimeTilOpt));
		System.out.println(String.format("Total execution time (avg): %.1fs", avgTimeOverall));
		System.out.println(String.format("Avg Precision: %.5f", avgPrec));
		System.out.println(String.format("Avg Recall: %.5f", avgRec));
		System.out.println(String.format("Avg F-measure: %.5f", avgFMeasure));

		Printer.printSmallHeader("On test set:", System.out);
		System.out.println(String.format("Avg Precision: %.5f", avgTestPrec));
		System.out.println(String.format("Avg Recall: %.5f", avgTestRec));
		System.out.println(String.format("Avg F-measure: %.5f", avgTestFMeasure));

	}

	/**
	 * Execute harmony search with given parameters and prints some information
	 * (influenced by constants in main).
	 * 
	 * @param hpa    .. Configuration Object of Harmony Search
	 * @param outCsv
	 * @param outTxt
	 * 
	 * @return HarmonyResult
	 */
	static HarmonyResult runHarmonySearch(HarmonyParameters hpa, PrintStream outTxt, PrintStream outCsv,
			int testTraces) {
		// Initialize HarmonyMemory with HarmonyParameters
		HarmonyMemory memory = new HarmonyMemory(hpa);

		// Initialize Harmony Search with Harmony Memory and Parameters
		HarmonySearch search = new HarmonySearch(memory, hpa, outTxt, outCsv);

		HarmonyResult hs = search.execHarmonySearch();

		// Print results of this Harmony Search run
		Printer.printHeader("HARMONY RESULTS", outTxt);
		outTxt.println(hs);
		Printer.printHeader("BEST", outTxt);
		outTxt.print(memory.solutionToString(memory.findBestEvalResult()));

		List<EvaluationResult> testResults = Evaluation.getInstance().evaluate(EvaluationMode.TEST_DATA,
				hs.getBestSolution(), false, STATES_TO_NOT_EVALUATE);

		Printer.printHeader("Results on test set (" + testTraces + " traces)", outTxt);
		String trainTracePositions = Evaluation.getInstance().getTrainingTraces().stream()
				.map(t -> String.valueOf(t.getPosition())).collect(Collectors.joining(", "));
		Printer.printSmallHeader(String.format("Trace positions used for tuning tolerance: %s", trainTracePositions),
				outTxt);
		for (EvaluationResult solEvalResult : testResults) {
			outTxt.printf("\n=> %s: Precision: %.5f  Recall: %.5f\t\t(Missed: %s)", solEvalResult.getState(),
					solEvalResult.getPrecision(), solEvalResult.getRecall(), solEvalResult.getMissedStates());
		}

		double fMeasure = testResults.stream().mapToDouble(s -> s.getfMeasure()).average().getAsDouble();
		double p = testResults.stream().mapToDouble(s -> s.getPrecision()).average().getAsDouble();
		double r = testResults.stream().mapToDouble(s -> s.getRecall()).average().getAsDouble();

		outTxt.printf("\n\nPrecision (avg): %.5f\nRecall (avg): %.5f\nF-measure (avg): %.5f\n", p, r, fMeasure);

		hs.setAvgBestTestFMeasure(fMeasure);
		hs.setAvgBestTestPrecision(p);
		hs.setAvgBestTestRecall(r);

//		}

		return hs;
	}

	private static PrintStream setOutStreamToMainFile(String caseName) {
		PrintStream mainFile = null;
		try {
			mainFile = new PrintStream(new FileOutputStream(caseName, true), true);
			System.setOut(mainFile);
		} catch (IOException e) {
			System.err.print(e.getMessage());
			e.printStackTrace();
		}
		return mainFile;
	}

}
