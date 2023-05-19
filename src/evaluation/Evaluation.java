package evaluation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Sets;

import design.AxisStream;
import design.Block;
import design.Property;
import design.State;
import design.StateModel;
import harmony.PropertyBoundaries;
import timeSeries.TimeSeriesDatabase;
import timeSeries.TimeSeriesDatabase.TSDBMode;

/**
 * Utilities for evaluation; Carries the actually realized states (read from
 * csv)
 *
 */
public class Evaluation {

	public enum MatchingMode {

		PLAIN_RETRIEVAL_MATCHING("simple_matching"), FILTER_SUCCEEDING_DUPLICATES("duplicates_filtered");

		// Member to hold the name
		private String string;

		// constructor to set the string
		MatchingMode(String name) {
			string = name;
		}

		// the toString just returns the given name
		@Override
		public String toString() {
			return string;
		}
	}

	public enum EvaluationMode {
		TRAIN_DATA, TEST_DATA
	}

	public static Evaluation instance = null;
	private static Random rand = new Random(23);

	private static int counter = 0;
	HashMap<String, List<IdentifiedState>> gtStateToRealizationsTrain = null;
	HashMap<String, List<IdentifiedState>> gtStateToRealizationsTest = null;
	private Block roboticArm = null;
	private List<Integer> trainTracePositions;
	private List<Integer> testTracePositions;

	private final MatchingMode matchingMode;
	private List<Trace> trainingTraces;
	private List<Trace> testTraces;

	private String realDataFilename;
	private static final DateTimeFormatter ISO8601_FORMATTER = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd'T'HH:mm:ss").appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
			.appendPattern("X").toFormatter();

	private Evaluation(List<AxisStream> axisStreams, MatchingMode matchingMode) {
		this.roboticArm = StateModel.setUpDataStream(axisStreams);
		this.matchingMode = matchingMode;
	}

	private Evaluation(List<AxisStream> axesUsed, MatchingMode matchingMode, String realDataFileName) {
		this(axesUsed, matchingMode);
		this.realDataFilename = realDataFileName;
		this.setUpRealDataStream(axesUsed);
	}

	private Evaluation(List<AxisStream> axesUsed, MatchingMode matchingMode, String realDataFileName, int trainTraces,
			int testTraces, List<String> firstStates, List<String> lastStates) {
		this(axesUsed, matchingMode);
		this.realDataFilename = realDataFileName;
		this.trainingTraces = new ArrayList<>();
		this.testTraces = new ArrayList<>();

		this.setUpRealDataStream(axesUsed, trainTraces, testTraces, firstStates, lastStates);
	}

	public static void init(List<AxisStream> axesUsed, MatchingMode matchingMode, String realDataFileName) {
		instance = new Evaluation(axesUsed, matchingMode, realDataFileName);
	}

	public static void init(List<AxisStream> axesUsed, MatchingMode matchingMode, String realDataFileName,
			int trainTraces, int testTraces, List<String> firstStates, List<String> lastStates) {
		instance = new Evaluation(axesUsed, matchingMode, realDataFileName, trainTraces, testTraces, firstStates,
				lastStates);
	}

	public static Evaluation getInstance() {
		if (instance == null)
			throw new RuntimeException("Object not initialized; Call one of the init functions of this class first!");
		return instance;
	}

	private void printIdentifiedStates(ArrayList<IdentifiedState> identifiedStates) {
		PrintWriter pw;
		String dir = "modelTest/";
		String filename = "result" + counter + ".csv";
		String path = dir + filename;
		try {
			File f = new File(dir, filename);
			f.createNewFile();
			pw = new PrintWriter(new File(path));
			String row = "";
			Collections.sort(identifiedStates);
			int countProcess = 0;
			for (IdentifiedState identifiedState : identifiedStates) {
				if (identifiedState.getName().equals("DriveDown")) {
					countProcess++;
				}
				row = countProcess + ";" + identifiedState.getName() + ";" + identifiedState.getTs().toString() + "\n";
				pw.write(row);
			}
			pw.close();
			counter++;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Evaluates the given solution map
	 * 
	 * @param roboticArm     .. Model with list of expected States
	 * @param propertyMap    .. Solution Map
	 * @param verbose        .. flag for verbose printing
	 * @param statesToIgnore .. states that should not be evaluated
	 * @return List<EvaluationResult> .. Results from Evaluation in List mapped per
	 *         State
	 */
	public List<EvaluationResult> evaluate(EvaluationMode evalMode, Map<String, PropertyBoundaries> propertyMap,
			boolean verbose, List<String> statesToIgnore) {
		ArrayList<IdentifiedState> recStates = this.testRecognition(evalMode, propertyMap, statesToIgnore);
		List<EvaluationResult> allResults = new ArrayList<EvaluationResult>();
		if (verbose)
			printIdentifiedStates(recStates);

		// Collections.sort(recStates);
		// Calculate Precision and Recall
		allResults.addAll(this.evaluateAgainstGroudTruth(evalMode, recStates));

		return allResults;
	}

	public void setUpRealDataStream(String filename, List<AxisStream> axisList) {
		this.realDataFilename = filename;
		this.setUpRealDataStream(axisList);
	}

	/**
	 * Finds all states in database that fit the currently evaluating solution
	 * 
	 * @param b..            Model of Block with states
	 * @param propertyMap    .. currently evaluating solution map
	 * @param statesToIgnore .. states that are not evaluated
	 * @return ArrayList<IdentifiedState> .. States that have been identified in the
	 *         database
	 */
	public ArrayList<IdentifiedState> testRecognition(EvaluationMode evalMode,
			Map<String, PropertyBoundaries> propertyMap, List<String> statesToIgnore) {
		TimeSeriesDatabase db = TimeSeriesDatabase.instance;
		if (db == null)
			throw new RuntimeException("No timeseries db connceted / db == null");
		ArrayList<IdentifiedState> result = new ArrayList<IdentifiedState>();
		for (State s : roboticArm.getAssignedState()) {
			if (statesToIgnore.size() == 0 || !statesToIgnore.contains(s.getName())) {
				result.addAll(db.recognizeState(evalMode == EvaluationMode.TRAIN_DATA ? TSDBMode.TRAIN : TSDBMode.TEST,
						s.getName(), s.getAssignedProperties(), propertyMap));
			}
		}
		return result;
	}

	public Block getBlock() {
		return this.roboticArm;
	}

	/**
	 * Sets the state duplicates in the passed list of states to NULL. A duplicate
	 * is characterized by following the same state (e.g., A -> B -> B -> C) in the
	 * chronologically ordered list of detected states. Given 2 or more consecutive
	 * instances of the same state, the 1st one will be kept, and the following ones
	 * filtered out.
	 * 
	 * The algorithm starts at an index in the list known to match with the ground
	 * truth, and incrementally checks states to the right whether one is a
	 * duplicate.
	 * 
	 * @param stateList     .. list of states (ordered chronologically)
	 * @param sName         .. state for which to set duplicates NULL
	 * @param validStateIds .. indices in stateList of correctly identified states
	 *                      for sName
	 */
	private void setStateDuplicatesNull(List<IdentifiedState> stateList) {

		for (int i = 0; i < stateList.size(); i++) {
			IdentifiedState s = stateList.get(i);
			String sName = s.getName();
			while (i < stateList.size() - 1 && stateList.get(i + 1).getName().equals(sName)) {
				stateList.set(i + 1, null);
				i++;
			}

		}
	}

	/**
	 * Calculates the Evaluation Result for a single State
	 * 
	 * @param statename             .. State to evaluate
	 * @param recognizedStates      .. List of states that have been recognized
	 * @param gtStateToRealizations .. List of states that should have been
	 *                              recognized
	 * @return
	 */
	private List<EvaluationResult> calculatePrecisionRecall(List<IdentifiedState> recognizedStates,
			Map<String, List<IdentifiedState>> gtStateToRealizations) {

		Map<String, List<IdentifiedState>> stateToMissedRealizations = gtStateToRealizations.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
		Map<String, List<Integer>> validDetectionRecIds = gtStateToRealizations.keySet().stream()
				.collect(Collectors.toMap(s -> s, s -> new ArrayList<Integer>()));
		Collections.sort(recognizedStates); // sort stream recordings by time

		// get overall prediction count per state for precision computation
		switch (matchingMode) {
		case FILTER_SUCCEEDING_DUPLICATES:

			setStateDuplicatesNull(recognizedStates);
			recognizedStates = recognizedStates.parallelStream().filter(Objects::nonNull).collect(Collectors.toList());
			break;
		case PLAIN_RETRIEVAL_MATCHING:
			break;
		default:
			break;
		}

		// Store ids of correctly recognized states
		Map<String, Long> predictionsPerState = recognizedStates.stream().map(s -> s.getName())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		for (int i = 0; i < recognizedStates.size(); i++) {
			IdentifiedState s = recognizedStates.get(i);

			if (gtStateToRealizations.containsKey(s.getName()) && gtStateToRealizations.get(s.getName()).contains(s)) {
				validDetectionRecIds.get(s.getName()).add(i);
				stateToMissedRealizations.get(s.getName()).remove(s);
			}
		}

		List<EvaluationResult> evalResults = new ArrayList<>();

		// per state, compute precision and recall
		for (String sName : gtStateToRealizations.keySet()) {
			int gtRecognizedIntersections = validDetectionRecIds.get(sName).size();
			Long statePredictions = predictionsPerState.containsKey(sName) ? predictionsPerState.get(sName) : 0;

			double precision = calculatePrecision(gtRecognizedIntersections, statePredictions);
			double recall = calculateRecall(gtRecognizedIntersections, gtStateToRealizations.get(sName).size());
			double fMeasure = calculateFMeasure(precision, recall);
			evalResults.add(
					new EvaluationResult(sName, precision, recall, fMeasure, stateToMissedRealizations.get(sName)));
		}

		return evalResults;
	}

	/**
	 * Calculate Evaluation Results for all States
	 * 
	 * @param recognizedStates .. list of recognized states in Database
	 * @return
	 */
	public List<EvaluationResult> evaluateAgainstGroudTruth(EvaluationMode evalMode,
			List<IdentifiedState> recognizedStates) {

		HashMap<String, List<IdentifiedState>> recStateToRealizations = new HashMap<String, List<IdentifiedState>>();
		for (IdentifiedState s : recognizedStates) {
			if (!recStateToRealizations.containsKey(s.getName()))
				recStateToRealizations.put(s.getName(), new ArrayList<IdentifiedState>());
			recStateToRealizations.get(s.getName()).add(s);
		}

		return calculatePrecisionRecall(recognizedStates,
				evalMode == EvaluationMode.TRAIN_DATA ? gtStateToRealizationsTrain : gtStateToRealizationsTest);

	}

	public Map<String, List<IdentifiedState>> getGtStateToRealizationsTrain() {
		return this.gtStateToRealizationsTrain;
	}

	/**
	 * Reads actually realized states from csv file and writes it into the database
	 * 
	 * @param axisList
	 */
	public void setUpRealDataStream(List<AxisStream> axisList) {
		String line = "";
		String cvsSplitBy = ";";
		BufferedReader br = null;
		gtStateToRealizationsTrain = new HashMap<String, List<IdentifiedState>>();

		try {

			br = new BufferedReader(new FileReader(this.realDataFilename));
			while ((line = br.readLine()) != null) {

				String[] information = line.split(cvsSplitBy);
				String[] split = information[0].split("\\.");
				String timestamp = split[2] + "-" + split[1] + "-" + split[0] + "T" + information[1] + "Z";

				List<Property> properties = new ArrayList<Property>();
				if (axisList.contains(AxisStream.BP)) {
					properties.add(new Property(AxisStream.BP.getAxisName(),
							Double.parseDouble(information[AxisStream.BP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.MAP)) {
					properties.add(new Property(AxisStream.MAP.getAxisName(),
							Double.parseDouble(information[AxisStream.MAP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.GP)) {
					properties.add(new Property(AxisStream.GP.getAxisName(),
							Double.parseDouble(information[AxisStream.GP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.SAP)) {
					properties.add(new Property(AxisStream.SAP.getAxisName(),
							Double.parseDouble(information[AxisStream.SAP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.WP)) {
					properties.add(new Property(AxisStream.WP.getAxisName(),
							Double.parseDouble(information[AxisStream.WP.getSplitNr()])));
				}

				String statename = information[7];

				Instant instant = Instant.from(ISO8601_FORMATTER.parse(timestamp));
				LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.from(ISO8601_FORMATTER.parse(timestamp)));

				IdentifiedState s = new IdentifiedState(statename, ldt.toString(), properties);

				if (!gtStateToRealizationsTrain.containsKey(statename))
					gtStateToRealizationsTrain.put(statename, new ArrayList<IdentifiedState>());
				gtStateToRealizationsTrain.get(statename).add(s);

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Reads actually realized states from csv file and writes it into the database
	 * 
	 * @param axisList
	 * @param trainTraces .. Reads only first X traces
	 */
	public void setUpRealDataStream(List<AxisStream> axisList, int nrTrainTraces, int nrTestTraces,
			List<String> firstStates, List<String> lastStates) {
		String line = "";
		String cvsSplitBy = ";";
		BufferedReader br = null;
		int passedTraces = 0;

		// int iter = 0;
		gtStateToRealizationsTrain = new HashMap<String, List<IdentifiedState>>();
		gtStateToRealizationsTest = new HashMap<String, List<IdentifiedState>>();

		trainTracePositions = rand.ints(0, 60).distinct().limit(nrTrainTraces).boxed().collect(Collectors.toList());
		testTracePositions = rand.ints(0, 60).distinct().filter(x -> !trainTracePositions.contains(x))
				.limit(nrTestTraces).boxed().collect(Collectors.toList());
		Date traceStart = null;
		SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");

		boolean passedFirst = false;
		try {

			br = new BufferedReader(new FileReader(this.realDataFilename));
			while ((line = br.readLine()) != null) {

				String[] information = line.split(cvsSplitBy);
				Date parsedDate = df.parse(information[0] + " " + information[1]);

				String[] split = information[0].split("\\.");
				String timestamp = split[2] + "-" + split[1] + "-" + split[0] + "T" + information[1] + "Z";

				List<Property> properties = new ArrayList<Property>();

				if (axisList.contains(AxisStream.BP)) {
					properties.add(new Property(AxisStream.BP.getAxisName(),
							Double.parseDouble(information[AxisStream.BP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.MAP)) {
					properties.add(new Property(AxisStream.MAP.getAxisName(),
							Double.parseDouble(information[AxisStream.MAP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.GP)) {
					properties.add(new Property(AxisStream.GP.getAxisName(),
							Double.parseDouble(information[AxisStream.GP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.SAP)) {
					properties.add(new Property(AxisStream.SAP.getAxisName(),
							Double.parseDouble(information[AxisStream.SAP.getSplitNr()])));
				}
				if (axisList.contains(AxisStream.WP)) {
					properties.add(new Property(AxisStream.WP.getAxisName(),
							Double.parseDouble(information[AxisStream.WP.getSplitNr()])));
				}

				String stateName = information[7];

				Instant instant = Instant.from(ISO8601_FORMATTER.parse(timestamp));
				LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.from(ISO8601_FORMATTER.parse(timestamp)));

				IdentifiedState s = new IdentifiedState(stateName, ldt.toString(), properties);

				if (trainTracePositions.contains(passedTraces)) {
					gtStateToRealizationsTrain.putIfAbsent(stateName, new ArrayList<IdentifiedState>());
					gtStateToRealizationsTrain.get(stateName).add(s);
				} else if (testTracePositions.contains(passedTraces)) {
					gtStateToRealizationsTest.putIfAbsent(stateName, new ArrayList<IdentifiedState>());
					gtStateToRealizationsTest.get(stateName).add(s);
				}

				if (passedFirst && lastStates.contains(stateName)) {
					passedFirst = false;
					if (trainTracePositions.contains(passedTraces)) {
						trainingTraces.add(new Trace(passedTraces, traceStart, parsedDate));
					} else if (testTracePositions.contains(passedTraces)) {
						testTraces.add(new Trace(passedTraces, traceStart, parsedDate));
					}
					passedTraces++;

					continue;
				}

				if (!passedFirst && firstStates.contains(stateName)) {
					passedFirst = true;
					traceStart = parsedDate;
				}

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private double calculatePrecision(double intersection, double retrieved) {
		return retrieved == 0 ? 0 : intersection / (double) retrieved;
	}

	private double calculateRecall(double intersection, double relevant) {
		return relevant == 0 ? 0 : intersection / (double) relevant;
	}

	private double calculateFMeasure(double precision, double recall) {
		if (precision == 0.0 && recall == 0.0)
			return 0.0;
		return 2 * (precision * recall) / (precision + recall);
	}

	public List<Trace> getTestTraces() {
		return this.testTraces;

	}

	public List<Trace> getTrainingTraces() {
		return this.trainingTraces;

	}
}