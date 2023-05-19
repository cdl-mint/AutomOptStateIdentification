package main;

import static io.jenetics.internal.util.Hashes.hash;
import static java.lang.String.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.*;

import design.AxisStream;
import design.StateModel;
import evaluation.Evaluation;
import evaluation.EvaluationResult;
import evaluation.Solution;
import evaluation.TestCase;
import evaluation.Evaluation.EvaluationMode;
import evaluation.Evaluation.MatchingMode;
import harmony.PropertyBoundaries;
import harmony.HarmonyParameters.AdditionalOptimizationMethod;
import io.jenetics.Chromosome;
import io.jenetics.Crossover;
import io.jenetics.DoubleChromosome;
import io.jenetics.DoubleGene;
import io.jenetics.GaussianMutator;
import io.jenetics.Gene;
import io.jenetics.Genotype;
import io.jenetics.MultiPointCrossover;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.SinglePointCrossover;
import io.jenetics.TournamentSelector;
import io.jenetics.UniformCrossover;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.ext.moea.ElementComparator;
import io.jenetics.ext.moea.ElementDistance;
import io.jenetics.ext.moea.MOEA;
import io.jenetics.ext.moea.Vec;
import io.jenetics.ext.moea.VecFactory;
import io.jenetics.stat.DoubleMomentStatistics;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;
import io.jenetics.IntermediateCrossover;

import java.util.stream.Stream;

import timeSeries.TimeSeriesDatabase;

import utils.Printer;

public class MainGA {

	/** Settable variables for testing setup **/
	private static final TestCase[] testCase = { TestCase.UC1_14States, TestCase.UC2_8States };
	private static final int[] USE_X_TRACES_FOR_TRAINING = { 10 };
	private static final int[] USE_X_TRACES_FOR_TESTING = { 50 };

	private static final MatchingMode matchingMode = MatchingMode.PLAIN_RETRIEVAL_MATCHING;

	private static final List<AxisStream> axesUsed = Arrays.asList(AxisStream.GP, AxisStream.MAP, AxisStream.BP,
			AxisStream.SAP, AxisStream.WP);
	private static final List<String> STATES_TO_NOT_EVALUATE = new ArrayList<String>();

	private static final String OUTPUT_DIRECTORY = "./results/GA_varypopulation_5000i_fixed";
	private static final boolean OUTPUT_TO_FILE = true;

	/** **/

	/** GA Parameters **/
	private static final int RUNS = 2;
	private static final List<Integer> POPULATION_SIZES = List.of(10, 25, 50, 100, 150, 200);

	private static final List<Double> MUTATION_PROBABILITIES = new ArrayList<Double>(Arrays.asList(.1));
	private static final List<Crossover> CROSSOVER_OPERATORS = new ArrayList<Crossover>(
			Arrays.asList(new IntermediateCrossover(0.8)));
	private static final double OFFSPRING_FRACTION = .6;
	private static final double LOWER_OFFSET_BORDER = 0.0;
	private static final double UPPER_OFFSET_BORDER = 0.7;
	private static final AdditionalOptimizationMethod[] ADDITIONAL_OPTIMIZATION = { AdditionalOptimizationMethod.NONE }; // 2nd
																															// objective
	private static final AtomicLong evaluations = new AtomicLong(0);
	private static AdditionalOptimizationMethod fitnessMethodSurrogate;

	static VecFactory<double[]> factory = null;

	private static List<Double> getAlleles(Genotype<DoubleGene> genotype) {
		List<Double> alleles = new ArrayList<Double>(genotype.geneCount());

		for (Chromosome<DoubleGene> chromosome : genotype) {
			for (DoubleGene gene : chromosome) {
				alleles.add(gene.doubleValue());
			}
		}
		return alleles;
	}

	// The fitness function.
	private static FitnessResult fitness(final Genotype<DoubleGene> genotype) {
		evaluations.incrementAndGet();
		List<Double> alleles = getAlleles(genotype);

		Map<String, PropertyBoundaries> propertyMap = getSensorOffsetMap(alleles);

		List<EvaluationResult> resultList = performStateDetection(propertyMap);
		double p = resultList.stream().mapToDouble(res -> res.getPrecision()).average().getAsDouble();
		double r = resultList.stream().mapToDouble(res -> res.getRecall()).average().getAsDouble();

		double fMeasure = resultList.stream().mapToDouble(res -> res.getfMeasure()).average().getAsDouble();

		Vec<double[]> fitness = fitnessMethodSurrogate == AdditionalOptimizationMethod.NONE
				? factory.newVec(new double[] { fMeasure })
				: factory.newVec(new double[] { fMeasure, alleles.stream().mapToDouble(Double::doubleValue).sum() });

		return FitnessResult.of(fitness, Map.of("precision", p, "recall", r));
	}

	private static PrintStream outputFile = null;
	private static PrintStream resultFile = null;

	private static void initFitnessFunction(AdditionalOptimizationMethod m) {
		switch (m) {
		case NONE:
			factory = VecFactory.ofDoubleVec(Optimize.MAXIMUM);
			break;
		case MAXIMIZE:
			factory = VecFactory.ofDoubleVec(Optimize.MAXIMUM, Optimize.MAXIMUM);
			break;

		case MINIMIZE:
			factory = VecFactory.ofDoubleVec(Optimize.MAXIMUM, Optimize.MINIMUM);
			break;
		}

	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws FileNotFoundException {

		// Setup reference set, read sensor stream in TSDB

		new File(OUTPUT_DIRECTORY).mkdirs();

		for (TestCase tCase : testCase) {

			for (int pSize : POPULATION_SIZES) {
				for (AdditionalOptimizationMethod method : ADDITIONAL_OPTIMIZATION) {
					initFitnessFunction(method);
					fitnessMethodSurrogate = method;
					for (double mutProp : MUTATION_PROBABILITIES) {
						for (Crossover crossoverMethod : CROSSOVER_OPERATORS) {
							int caseRun = 0;
							for (int xTrain : USE_X_TRACES_FOR_TRAINING) {

								// Save statistics per repetition

								List<EvolutionStatistics<FitnessResult, ?>> statisticsList = new ArrayList<>();

								// Exec algorithm multiple times for test
								PrintStream averagedStatsFile = null;
								String folderName = Paths.get(OUTPUT_DIRECTORY,
										String.format("%s_%s_%s_%dtr%dte_m%.3f_c%.3f_%s_p%d", tCase, matchingMode,
												method, xTrain, USE_X_TRACES_FOR_TESTING[caseRun], mutProp,
												crossoverMethod.probability(),
												crossoverMethod.getClass().getSimpleName(), pSize))
										.toString();
								new File(folderName).mkdirs();

								averagedStatsFile = new PrintStream(new FileOutputStream(
										Paths.get(OUTPUT_DIRECTORY,
												String.format("%s_%s_%s_%dtr%dte_m%.3f_c%.3f_%s_p%d_main.txt", tCase,
														matchingMode, method, xTrain, USE_X_TRACES_FOR_TESTING[caseRun],
														mutProp, crossoverMethod.probability(),
														crossoverMethod.getClass().getSimpleName(), pSize))
												.toString(),
										true), true);

								long startTime = System.nanoTime();

								List<Double> testRunFMeasures = new ArrayList<>();
								List<Double> testRunPrecisions = new ArrayList<>();
								List<Double> testRunRecalls = new ArrayList<>();

								List<Double> trainRunFMeasures = new ArrayList<>();
								List<Double> trainRunPrecisions = new ArrayList<>();
								List<Double> trainRunRecalls = new ArrayList<>();

								Comparator<? super Phenotype<DoubleGene, FitnessResult>> c = Comparator
										.comparing(Phenotype<DoubleGene, FitnessResult>::fitness, (f1, f2) -> {
											int c1 = Double.compare(f1.fitness.data()[0], f2.fitness.data()[0]);
											return c1 != 0 ? c1
													: method != AdditionalOptimizationMethod.NONE
															? (method == AdditionalOptimizationMethod.MAXIMIZE
																	? Double.compare(f1.fitness.data()[1],
																			f2.fitness.data()[1])
																	: Double.compare(f2.fitness.data()[1],
																			f1.fitness.data()[1]))
															: 0;
										});

								final Engine<DoubleGene, FitnessResult> engine = Engine
										.builder(MainGA::fitness,
												Genotype.of(DoubleChromosome.of(LOWER_OFFSET_BORDER,
														UPPER_OFFSET_BORDER, 2 * axesUsed.size())))
										.offspringFraction(OFFSPRING_FRACTION)
										.survivorsSelector(new TournamentSelector<>(c, 3))
										.offspringSelector(new TournamentSelector<>(c, 3)).populationSize(pSize)
										.alterers(new GaussianMutator<>(mutProp), crossoverMethod).build();

								for (int run = 0; run < RUNS; run++) {
									evaluations.set(0);

									Initialization.setupCase(tCase, matchingMode, axesUsed, xTrain,
											USE_X_TRACES_FOR_TESTING[caseRun]);

									// Create output directory
									if (OUTPUT_TO_FILE)
										outputToFile(tCase, mutProp, crossoverMethod, pSize, run, xTrain,
												USE_X_TRACES_FOR_TESTING[caseRun], method);

									// Create evolution statistics consumer.
									final EvolutionStatistics<FitnessResult, ?> statistics = EvolutionStatistics
											.ofComparable();

									StringBuilder headerStr = new StringBuilder();
									headerStr.append("generation;iteration;");
									for (int i = 0; i < axesUsed.size(); i++) {
										headerStr.append(axesUsed.get(i).getAxisName() + " ");
										headerStr.append(" lower;");
										headerStr.append(axesUsed.get(i).getAxisName() + " ");
										headerStr.append(" upper;");
									}

									headerStr.append("fMeasure;precision;recall");
									if (method != AdditionalOptimizationMethod.NONE)
										headerStr.append(";OffsetSum");

									System.out.println(headerStr);

									final ISeq<Phenotype<DoubleGene, FitnessResult>> pSet = engine.stream()
											.limit(byFixedFunctionEvaluations(500, evaluations)) // Update the //
																									// evaluation //
																									// statistics after
											// each generation
											.peek(statistics)
											// print generation log
											.peek((evolutionResult) -> {
												List<Phenotype<DoubleGene, FitnessResult>> li = new ArrayList<>(
														evolutionResult.population().asList());
												double[] bestFScoreVec = null;
												Map<String, Double> bestVecAdditionalValues = null;

												switch (method) {
												case NONE:
													Collections.sort(li,
															Comparator.comparingDouble(
																	x -> ((Phenotype<DoubleGene, FitnessResult>) x)
																			.fitness().data()[0])
																	.reversed());
													bestFScoreVec = li.get(0).fitness().data();
													bestVecAdditionalValues = li.get(0).fitness().getAdditionalValues();
													break;
												case MAXIMIZE:
													Collections.sort(li, Comparator
															.comparingDouble(
																	x -> ((Phenotype<DoubleGene, FitnessResult>) x)
																			.fitness().data()[0])
															.thenComparingDouble(
																	x -> ((Phenotype<DoubleGene, FitnessResult>) x)
																			.fitness().data()[1]));
													bestFScoreVec = li.get(li.size() - 1).fitness().data();
													bestVecAdditionalValues = li.get(li.size() - 1).fitness()
															.getAdditionalValues();

													break;
												case MINIMIZE:
													Collections.sort(li, Comparator
															.comparingDouble(
																	x -> ((Phenotype<DoubleGene, Vec<double[]>>) x)
																			.fitness().data()[0])
															.reversed().thenComparingDouble(
																	x -> ((Phenotype<DoubleGene, FitnessResult>) x)
																			.fitness().data()[1]));
													bestFScoreVec = li.get(0).fitness().data();
													bestVecAdditionalValues = li.get(0).fitness().getAdditionalValues();

													break;
												}

												Genotype<DoubleGene> genum = li.get(0).genotype();
												StringBuilder str = new StringBuilder();
												str.append(String.format("%d;%d;", evolutionResult.generation(),
														evaluations.get()));
												for (Chromosome<DoubleGene> chromosome : genum) {
													for (DoubleGene gene : chromosome) {
														str.append(gene.doubleValue());
														str.append(";");
													}
												}
												str.append(String.format("%.5f;%.5f;%.5f", bestFScoreVec[0],
														bestVecAdditionalValues.get("precision"),
														bestVecAdditionalValues.get("recall")));
												if (method != AdditionalOptimizationMethod.NONE)
													str.append(";" + bestFScoreVec[1]);

												System.out.println(str);
											})
											// Collect (reduce) the evolution stream to
											// its best phenotype.
											.collect(MOEA.toParetoSet());

									if (OUTPUT_TO_FILE) {
										System.setOut(resultFile);
									}

									System.out.println(statistics);
									System.out.println(String.format("=> populationSize: %d  (offspringFraction: %.1f)",
											pSize, OFFSPRING_FRACTION));
									System.out.println("=> mutationProbability: " + mutProp);
									System.out.println(String.format("=> crossoverProbability: \t(%s)",
											crossoverMethod.probability(), crossoverMethod.getClass().getSimpleName()));

									statisticsList.add(statistics);

									List<Phenotype<DoubleGene, FitnessResult>> pList = new ArrayList<>(pSet.asList());

									Collections.sort(pList,
											Comparator.comparingDouble(
													x -> ((Phenotype<DoubleGene, FitnessResult>) x).fitness().data()[0])
													.reversed());
									trainRunFMeasures.add(pList.get(0).fitness().data()[0]);

									if (xTrain > 0) {

										Map<String, PropertyBoundaries> bestSolution = getSensorOffsetMap(
												getAlleles(pList.get(0).genotype()));

										List<EvaluationResult> testResults = Evaluation.getInstance().evaluate(
												EvaluationMode.TEST_DATA, bestSolution, false, STATES_TO_NOT_EVALUATE);

										Printer.printSmallHeader("Evaluations: " + evaluations.get(), System.out);

										Printer.printHeader("Results on test set (" + USE_X_TRACES_FOR_TESTING[caseRun]
												+ " traces)", System.out);
										for (EvaluationResult solEvalResult : testResults) {
											System.out.printf("\n=> %s: Precision: %.5f  Recall: %.5f\t\t(Missed: %s)",
													solEvalResult.getState(), solEvalResult.getPrecision(),
													solEvalResult.getRecall(), solEvalResult.getMissedStates());
										}

										double fMeasure = testResults.stream().mapToDouble(s -> s.getfMeasure())
												.average().getAsDouble();
										double p = testResults.stream().mapToDouble(s -> s.getPrecision()).average()
												.getAsDouble();
										double r = testResults.stream().mapToDouble(s -> s.getRecall()).average()
												.getAsDouble();

										System.out.printf(
												"\n\nPrecision (avg): %.5f\nRecall (avg): %.5f\nF-measure (avg): %.5f\n",
												p, r, fMeasure);

										testRunFMeasures.add(fMeasure);
										testRunPrecisions.add(p);
										testRunRecalls.add(r);

									}

									if (outputFile != null) {
										outputFile.close();
									}
									if (resultFile != null) {
										outputFile.close();
									}
								}

								System.setOut(averagedStatsFile);

								System.out.println(averagedEvaluationStatistic(statisticsList));
								System.out.println(
										String.format("Best F-Measure (all runs, mean): %.5f", trainRunFMeasures
												.stream().mapToDouble(Double::doubleValue).average().getAsDouble()));

								if (xTrain > 0) {
									Printer.printSmallHeader("On test set:", System.out);
									System.out.println(String.format("Avg Precision: %.5f", testRunPrecisions.stream()
											.mapToDouble(Double::doubleValue).average().getAsDouble()));
									System.out.println(String.format("Avg Recall: %.5f", testRunRecalls.stream()
											.mapToDouble(Double::doubleValue).average().getAsDouble()));
									System.out.println(String.format("Avg F-measure: %.5f", testRunFMeasures.stream()
											.mapToDouble(Double::doubleValue).average().getAsDouble()));
								}

								System.out.println("\n => Overall execution time in seconds: "
										+ (System.nanoTime() - startTime) / 1000000000);
								if (averagedStatsFile != null) {
									averagedStatsFile.close();
								}
								caseRun++;
							}
						}
					}
				}
			}
		}
	}

	private static void outputToFile(TestCase tCase, double mutProp, Crossover crossoverMethod, int pSize, int run,
			int xTrain, int xTest, AdditionalOptimizationMethod method) {
		String outputDir = Paths.get(OUTPUT_DIRECTORY,
				String.format("%s_%s_%s_%dtr%dte_m%.3f_c%.3f_%s_p%d", tCase, matchingMode, method, xTrain, xTest,
						mutProp, crossoverMethod.probability(), crossoverMethod.getClass().getSimpleName(), pSize))
				.toString();

		new File(outputDir).mkdirs();

		String fileBase = outputDir + "/" + tCase + "_" + matchingMode + "_" + method + "_"
				+ String.format("%dtr%dte", xTrain, xTest);
		try {
			outputFile = new PrintStream(new FileOutputStream(
					String.format("%s_%.3f_%s_%.3f_p%d_%d_logs.csv", fileBase, mutProp,
							crossoverMethod.getClass().getSimpleName(), crossoverMethod.probability(), pSize, run),
					true), true);

			resultFile = new PrintStream(new FileOutputStream(
					String.format("%s_%.3f_%s_%.3f_p%d_%d_result.txt", fileBase, mutProp,
							crossoverMethod.getClass().getSimpleName(), crossoverMethod.probability(), pSize, run),
					true), true);

			System.setOut(outputFile);
		} catch (IOException e) {
			System.err.print(e.getMessage());
			e.printStackTrace();
		}
	}

	private static EvolutionStatistics<FitnessResult, ?> averagedEvaluationStatistic(
			List<EvolutionStatistics<FitnessResult, ?>> statisticsList) {
		System.out.println("+---------------------------------------------------------------------------+");
		System.out.println("|  Time statistics                                                          |");
		System.out.println("+---------------------------------------------------------------------------+");
		EvolutionStatistics<FitnessResult, ?> firstRes = statisticsList.get(0);
		for (int i = 1; i < statisticsList.size(); i++) {
			EvolutionStatistics<FitnessResult, ?> curStat = statisticsList.get(i);

			firstRes.alterDuration().combine(curStat.alterDuration());
			firstRes.selectionDuration().combine(curStat.selectionDuration());
			firstRes.evolveDuration().combine(curStat.evolveDuration());
			firstRes.evaluationDuration().combine(curStat.evaluationDuration());
			firstRes.altered().combine(curStat.altered());
			firstRes.killed().combine(curStat.killed());
			firstRes.invalids().combine(curStat.invalids());
			firstRes.phenotypeAge().combine(curStat.phenotypeAge());
			firstRes.selectionDuration().combine(curStat.selectionDuration());
		}

		return firstRes;
	}

	public static Predicate<Object> byFixedFunctionEvaluations(final long evaluations, final AtomicLong _curEvals) {

		return new Predicate<>() {
			private final AtomicBoolean _functionEvaluationsReached = new AtomicBoolean(false);

			@Override
			public boolean test(final Object o) {
				if (_functionEvaluationsReached.get())
					return false;
				_functionEvaluationsReached.set(_curEvals.get() > evaluations);
				return true;
			}
		};
	}

	private static String d(final DoubleMomentStatistics statistics) {
		return java.lang.String.format("sum=%3.12f s; mean=%3.12f s", statistics.sum(), statistics.mean());
	}

	private static List<EvaluationResult> performStateDetection(Map<String, PropertyBoundaries> sensorOffsetMap) {
		Evaluation eval = Evaluation.instance;
		// evaluate new solution
		List<EvaluationResult> evalResults = eval.evaluate(EvaluationMode.TRAIN_DATA, sensorOffsetMap, false,
				STATES_TO_NOT_EVALUATE);
		return evalResults;
	}

	private static Map<String, PropertyBoundaries> getSensorOffsetMap(List<Double> alleles) {

		Map<String, PropertyBoundaries> sensorOffsetMap = new HashMap<String, PropertyBoundaries>();

		if (axesUsed.contains(AxisStream.BP)) {
			sensorOffsetMap.put(AxisStream.BP.getAxisName(), new PropertyBoundaries(alleles.get(0), alleles.get(1)));
		}

		if (axesUsed.contains(AxisStream.GP)) {
			sensorOffsetMap.put(AxisStream.GP.getAxisName(), new PropertyBoundaries(alleles.get(2), alleles.get(3)));

		}

		if (axesUsed.contains(AxisStream.MAP)) {
			sensorOffsetMap.put(AxisStream.MAP.getAxisName(), new PropertyBoundaries(alleles.get(4), alleles.get(5)));

		}

		if (axesUsed.contains(AxisStream.SAP)) {
			sensorOffsetMap.put(AxisStream.SAP.getAxisName(), new PropertyBoundaries(alleles.get(6), alleles.get(7)));

		}

		if (axesUsed.contains(AxisStream.WP)) {
			sensorOffsetMap.put(AxisStream.WP.getAxisName(), new PropertyBoundaries(alleles.get(8), alleles.get(9)));

		}
		return sensorOffsetMap;
	}

	static class FitnessResult implements Vec<double[]> {

		Vec<double[]> fitness;
		Map<String, Double> additionalValues;

		public FitnessResult(Vec<double[]> v, Map<String, Double> aValues) {
			this.fitness = v;
			this.additionalValues = aValues;
		}

		public static FitnessResult of(Vec<double[]> v, Map<String, Double> aValues) {
			return new FitnessResult(v, aValues);
		}

		public int compareTo(FitnessResult o) {
			return this.fitness.compareTo(o.fitness);
		}

		public Vec<double[]> getFitness() {
			return fitness;
		}

		public void setFitness(Vec<double[]> fitness) {
			this.fitness = fitness;
		}

		public Map<String, Double> getAdditionalValues() {
			return additionalValues;
		}

		public void setAdditionalValues(Map<String, Double> additionalValues) {
			this.additionalValues = additionalValues;
		}

		@Override
		public double[] data() {
			return fitness.data();
		}

		@Override
		public int length() {
			return fitness.length();
		}

		@Override
		public ElementComparator<double[]> comparator() {
			return fitness.comparator();
		}

		@Override
		public ElementDistance<double[]> distance() {
			return fitness.distance();
		}

		@Override
		public Comparator<double[]> dominance() {
			return fitness.dominance();
		}

	}
}