package harmony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import design.AxisStream;
import evaluation.Evaluation;
import evaluation.Evaluation.EvaluationMode;
import evaluation.EvaluationResult;
import evaluation.IdentifiedState;
import evaluation.Solution;
import harmony.HarmonyParameters.AdditionalOptimizationMethod;

import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.apache.commons.math3.random.SobolSequenceGenerator;

import utils.Columns;
import utils.Printer;

public class HarmonyMemory {

	// Parameters with which harmony search is run
	private HarmonyParameters hParams;

	// Solution maps, bUpper and bLower in PropertyBoundaries per sensor
	private List<Map<String, PropertyBoundaries>> solutions;

	// f score per solution
	private List<EvaluationResult>[] fitness;

	// offset sum per solution
	private double[] overallOffsets;

	// List of axis used by this Harmony Search run
	private List<AxisStream> axisStream;

	/**
	 * Initialize memory with initial memory solutions, f1 scores and used sensors
	 * 
	 * @param params .. initialized memory pre search phase
	 */
	public HarmonyMemory(HarmonyParameters params) {
		hParams = params;
		axisStream = params.getAxisStreams();
		solutions = this.generateDefaultMemory(params.isUseSobolInit());
		Evaluation eval = Evaluation.instance;
		overallOffsets = new double[params.getMemorySize()];
		fitness = new List[params.getMemorySize()];
		int count = 0;
		for (Map<String, PropertyBoundaries> solution : solutions) {
			fitness[count] = eval.evaluate(EvaluationMode.TRAIN_DATA, solution, false, params.getStatesToNotEvaluate());
			double curOverallOffset = 0;
			for (Entry<String, PropertyBoundaries> pair : solution.entrySet()) {
				curOverallOffset += pair.getValue().getLower() + pair.getValue().getUpper();
			}
			overallOffsets[count] = curOverallOffset;
			count++;
		}
	}

	public Map<String, PropertyBoundaries> getMinSolutionForPropertyAndDim(String property, int dim) {
		Map<String, PropertyBoundaries> minSolution = solutions.parallelStream()
				.min(Comparator.comparingDouble(s -> s.get(property).getAsArray()[dim])).get();
		return minSolution;
	}

	public Map<String, PropertyBoundaries> getMaxSolutionForPropertyAndDim(String property, int dim) {
		Map<String, PropertyBoundaries> maxSolution = solutions.parallelStream()
				.max(Comparator.comparingDouble(s -> s.get(property).getAsArray()[dim])).get();
		return maxSolution;
	}

	public List<Map<String, PropertyBoundaries>> getSolutions() {
		return this.solutions;
	}

	public List<EvaluationResult>[] getFitness() {
		return this.fitness;
	}

	public double[] getOverallOffsets() {
		return this.overallOffsets;
	}

	List<Map<String, PropertyBoundaries>> getMemory() {
		return solutions;
	}

	void setMemory(List<Map<String, PropertyBoundaries>> newMemory) {
		solutions = newMemory;
	}

	/**
	 * Evaluates whether new solution is better than worst solution in memory. In
	 * case new solution is better than worst solution, replace worst with new
	 * solution.
	 * 
	 * Evaluation happens according to means (of states) of precision and recall
	 * 
	 * @param newSolution .. the new solution map
	 * @return boolean .. true when optimal solution (precision and recall both =
	 *         1.0) found
	 */
	public boolean evalSolution(Map<String, PropertyBoundaries> newSolution) {

		Evaluation eval = Evaluation.instance;

		// get offset and result of worst solution in memory
		int worstResultIdx = findWorstEvalResult();
		List<EvaluationResult> worstResult = this.fitness[worstResultIdx];
		double worstSolutionOffset = this.overallOffsets[worstResultIdx];

		// evaluate new solution
		List<EvaluationResult> newResult = eval.evaluate(EvaluationMode.TRAIN_DATA, newSolution, false,
				hParams.getStatesToNotEvaluate());

		// calculate absolute offset of new solution
		double newSolutionOffset = 0;
		for (Entry<String, PropertyBoundaries> pair : newSolution.entrySet()) {
			newSolutionOffset += pair.getValue().getLower() + pair.getValue().getUpper();
		}

		// compare new solution result to worst solution result
		if (cmpListEvalResults(newResult, newSolutionOffset, worstResult, worstSolutionOffset, false) > 0) {
			// new solution is better
			if (hParams.getPrintMemorySwaps())
				printSwaps(worstResult, worstResultIdx, newResult, newSolution);

			// add new solution in place of worst solution
			solutions.set(worstResultIdx, newSolution);
			fitness[worstResultIdx] = newResult;
			overallOffsets[worstResultIdx] = newSolutionOffset;

			// check if optimum was found
			if (newResult.stream().mapToDouble(s -> s.getfMeasure()).average().getAsDouble() == 1.0)
				return true;

		}
		return false;
	}

	private void printSwaps(List<EvaluationResult> worstResult, int worstResultIdx, List<EvaluationResult> newResult,
			Map<String, PropertyBoundaries> newSolution) {
		// print swap to console
		Printer.printHeader("SWAP: Solution " + (worstResultIdx) + " (worst) -> New solution", System.out);
		Map<String, PropertyBoundaries> worstSolution = solutions.get(worstResultIdx);
		// Get all properties in map
		List<String> propertyNameList = new ArrayList<>(newSolution.keySet());
		for (int i = 0; i < propertyNameList.size(); i++) {
			String curProperty = propertyNameList.get(i);
			new Columns()
					.addLine(
							curProperty + ": " + worstSolution.get(curProperty) + " -> " + newSolution.get(curProperty))
					.print();
		}
		double worstFMeasure = 0;
		double worstPrec = 0;
		double worstRec = 0;
		for (EvaluationResult res : worstResult) {
			worstFMeasure += res.getfMeasure() / worstResult.size();
			worstPrec += res.getPrecision() / worstResult.size();
			worstRec += res.getRecall() / worstResult.size();
		}
		double newFMeasure = 0;
		double newPrec = 0;
		double newRec = 0;
		for (EvaluationResult res : newResult) {
			newFMeasure += res.getfMeasure() / newResult.size();
			newPrec += res.getPrecision() / newResult.size();
			newRec += res.getRecall() / newResult.size();
		}
		System.out.println("--------MEMORY STATE--------");
		for (int a = 0; a < this.fitness.length; a++) {
			double fScore = 0.0;
			for (EvaluationResult evalResult : this.fitness[a]) {
				fScore += evalResult.getfMeasure() / this.fitness[a].size();
			}
			System.out.println(a + ": " + fScore);
		}
		System.out.println("//--------MEMORY STATE--------");
		System.out.print("\n" + worstResult + "\n==> Fscore:" + worstFMeasure + ", Precision:" + worstPrec + ", Recall:"
				+ worstRec + "\n->\n" + newResult + "\n==> Fscore:" + newFMeasure + ", Precision:" + newPrec
				+ ", Recall:" + newRec);
	}

	/**
	 * Tests if new solution is the currently best solution in whole memory.
	 * 
	 * @param testSolution .. solution
	 * @return true if solution is best in memory
	 */
	public boolean isSolutionBest(Map<String, PropertyBoundaries> testSolution) {
		int bestIndex = this.findBestEvalResult();
		Map<String, PropertyBoundaries> bestSolution = this.solutions.get(bestIndex);
		return bestSolution.equals(testSolution);
	}

	/**
	 * Get the memory index of best result
	 * 
	 * @return int Index
	 */
	public int getBestIndex() {
		return this.findBestEvalResult();
	}

	/**
	 * Get the average FMeasure of the best result in memory
	 * 
	 * @return Avg Fmeasure
	 */
	public double getBestAvgFMeasure() {
		int bestIndex = this.findBestEvalResult();
		List<EvaluationResult> best = this.fitness[bestIndex];

		return best.stream().mapToDouble(s -> s.getfMeasure()).average().getAsDouble();
	}

	/**
	 * Get average Precision of the best result in memory
	 * 
	 * @return Avg Precision
	 */
	public double getBestAvgPrecision() {
		int bestIndex = this.findBestEvalResult();
		List<EvaluationResult> best = this.fitness[bestIndex];

		return best.stream().mapToDouble(s -> s.getPrecision()).average().getAsDouble();
	}

	/**
	 * Get average Recall of the best result in memory
	 * 
	 * @return Avg Recall
	 */
	public double getBestAvgRecall() {
		int bestIndex = this.findBestEvalResult();
		List<EvaluationResult> best = this.fitness[bestIndex];

		return best.stream().mapToDouble(s -> s.getRecall()).average().getAsDouble();
	}

	/**
	 * Get the summed up offset of all sensors of best result in memory
	 * 
	 * @return double Sum of offsets
	 */
	public double getBestAbsOffset() {
		int bestIndex = this.findBestEvalResult();
		return this.overallOffsets[bestIndex];
	}

	/**
	 * Determines worst result in list of solution maps. Worst solution is
	 * determined by worst precision/recall
	 * 
	 * @return index of worst result
	 */
	private int findWorstEvalResult() {
		int worstIndex = 0;
		int index = 0;
		List<EvaluationResult> worstEvalResult = this.fitness[0];
		double worstSolOffset = this.overallOffsets[0];
		for (List<EvaluationResult> evalResultList : this.fitness) {
			if (cmpListEvalResults(worstEvalResult, worstSolOffset, evalResultList, this.overallOffsets[index],
					false) > 0) {
				worstEvalResult = evalResultList;
				worstSolOffset = this.overallOffsets[index];
				worstIndex = index;
			}
			index++;
		}
		return worstIndex;
	}

	/**
	 * Determines best result in list of solution maps. Best solution is determined
	 * by highest f-measure
	 * 
	 * @return Index of best result
	 */
	public int findBestEvalResult() {
		int bestIndex = 0;
		int index = 0;
		List<EvaluationResult> bestEvalResult = this.fitness[0];
		double bestSolOffset = this.overallOffsets[0];
		for (List<EvaluationResult> evalResultList : this.fitness) {
			if (cmpListEvalResults(evalResultList, this.overallOffsets[index], bestEvalResult, bestSolOffset,
					false) > 0) {
				bestEvalResult = evalResultList;
				bestSolOffset = this.overallOffsets[index];
				bestIndex = index;
			}
			index++;
		}
		return bestIndex;
	}

	/**
	 * Determines best result in list of solution maps. Best solution is determined
	 * by highest f-measure
	 * 
	 * @param additionalOptimizationMethod
	 * 
	 * @return Index of best result
	 */

	public Map<String, PropertyBoundaries> getBestSolution(AdditionalOptimizationMethod additionalOptimizationMethod) {

		List<Solution> solutionList = new ArrayList<>();

		int idx = 0;
		for (List<EvaluationResult> evalResultList : this.fitness) {
			solutionList.add(new Solution(
					evalResultList.stream().mapToDouble(r -> r.getfMeasure()).sum() / evalResultList.size(),
					this.overallOffsets[idx], this.solutions.get(idx)));
			idx++;

		}

		switch (additionalOptimizationMethod) {
		case MAXIMIZE:
			Collections.sort(solutionList,
					Comparator.comparingDouble(Solution::getFMeasure).thenComparingDouble(Solution::getAbsOffsetSum));

			return solutionList.get(solutionList.size() - 1).getPropertyOffsets();

		case MINIMIZE:
			Collections.sort(solutionList, Comparator.comparingDouble(Solution::getFMeasure).reversed()
					.thenComparingDouble(Solution::getAbsOffsetSum));
			return solutionList.get(0).getPropertyOffsets();

		default:
			Collections.sort(solutionList, Comparator.comparingDouble(Solution::getFMeasure).reversed());
			return solutionList.get(0).getPropertyOffsets();
		}

	}

	/**
	 * 
	 * @param evalList1 .. first solution map
	 * @param evalList2 .. second solution map
	 * @return 1 if evalList1 carries better results, -1 if evalList2 carries better
	 *         results, 0 if results (precision and recall) are equal in both lists
	 */

	/**
	 * Compares two EvaluationResult objects and determines result from worse
	 * solution.
	 * 
	 * Worse solution is the one with worse mean (of states) precision/recall
	 * whereas precision has higher priority.
	 * 
	 * @param evalList1  .. first solution map
	 * @param solOffset1 .. avg offset of first solution map
	 * @param evalList2  .. second solution map
	 * @param solOffset2 .. avg offset of second solution map
	 * @param print      .. flag for verbose printing
	 * @return
	 */
	private int cmpListEvalResults(List<EvaluationResult> evalList1, double solOffset1,
			List<EvaluationResult> evalList2, double solOffset2, boolean print) {

		double avgFMeasureList1 = evalList1.stream().mapToDouble(r -> r.getfMeasure()).sum() / evalList1.size();
		double avgFMeasureList2 = evalList2.stream().mapToDouble(r -> r.getfMeasure()).sum() / evalList2.size();

		if (avgFMeasureList1 > avgFMeasureList2) {
			if (print)
				System.out.format("Swapped for better f-measure! (%.4f > %.4f)\n", avgFMeasureList1, avgFMeasureList2);

			return 1;
		} else if (avgFMeasureList1 == avgFMeasureList2) {
			if (hParams.getAdditionalOptimizationMethod() == AdditionalOptimizationMethod.NONE
					|| solOffset1 == solOffset2)
				return 0;
			switch (hParams.getAdditionalOptimizationMethod()) {
			case MINIMIZE:
				if (solOffset1 < solOffset2) {
					if (print)
						System.out.format("Swapped for narrower range! (%.4f < %.4f)\n", solOffset1, solOffset2);
					return 1;
				} else if (solOffset1 > solOffset2) {
					return -1;
				}
				return 0;

			case MAXIMIZE:
				if (solOffset1 > solOffset2) {
					if (print)
						System.out.format("Swapped for wider range! (%.4f < %.4f)\n", solOffset1, solOffset2);
					return 1;
				} else if (solOffset1 < solOffset2) {
					return -1;
				}
				return 0;

			default:
				return 0;
			}
		} else {
			return -1;
		}
	}

	/**
	 * Prints all the solutions (= total deviances per sensor) with resulting
	 * measures
	 */
	public void print() {
		for (int i = 0; i < solutions.size(); i++) {
			Map<String, PropertyBoundaries> curMap = solutions.get(i);
			System.out.println(Printer.div + "\nSolution " + (i + 1) + " in memory\n" + Printer.div);
			curMap.forEach((propertyName, boundaries) -> System.out.printf("%s: %.7f, %.7f\n", propertyName,
					boundaries.getLower(), boundaries.getUpper()));

			List<EvaluationResult> solutionEvalResults = fitness[i];
			List<Double> fmeasureList = new ArrayList<Double>();

			for (EvaluationResult solEvalResult : solutionEvalResults) {
				System.out.printf("\n=> %s: Precision: %.2f  Recall: %.2f", solEvalResult.getState(),
						solEvalResult.getPrecision(), solEvalResult.getRecall());
				fmeasureList.add(solEvalResult.getfMeasure());

			}
			System.out.printf("\n\nf-measure: %.3f\n\n",
					fmeasureList.stream().mapToDouble(x -> x).average().orElse(-1));
		}
	}

	/**
	 * Prints a single solution (= total deviances per sensor) with resulting
	 * measures
	 * 
	 * @param idx .. index of solution
	 */
	public String solutionToString(int idx) {
		StringBuilder sb = new StringBuilder();
		Map<String, PropertyBoundaries> curMap = solutions.get(idx);
		sb.append(Printer.div + "\nSolution " + (idx + 1) + " in memory\n" + Printer.div);
		curMap.forEach((propertyName, boundaries) -> sb
				.append(String.format("%s: %.4f, %.4f\n", propertyName, boundaries.getLower(), boundaries.getUpper())));

		List<EvaluationResult> solutionEvalResults = fitness[idx];
		sb.append(this.printResultSummary(solutionEvalResults));

		return sb.toString();

	}

	public static String printResultSummary(List<EvaluationResult> evalResults) {
		StringBuilder sb = new StringBuilder();
		for (EvaluationResult solEvalResult : evalResults) {
			sb.append(String.format("\n=> %s: Precision: %.2f  Recall: %.2f\t\t(Missed: %s)", solEvalResult.getState(),
					solEvalResult.getPrecision(), solEvalResult.getRecall(),
					solEvalResult.getMissedStates().toString()));

		}

		sb.append(String.format("\n\nPrecision (avg): %.3f\nRecall (avg): %.3f\nF-measure (avg): %.3f\n",
				evalResults.stream().mapToDouble(s -> s.getPrecision()).average().getAsDouble(),
				evalResults.stream().mapToDouble(s -> s.getRecall()).average().getAsDouble(),
				evalResults.stream().mapToDouble(s -> s.getfMeasure()).average().getAsDouble()));
		return sb.toString();
	}

	/**
	 * Initializes the Memory with random Values
	 * 
	 * @return Initialized Memory
	 */
	private List<Map<String, PropertyBoundaries>> generateDefaultMemory(boolean usingSobolSequences) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		SobolSequenceGenerator sobolGen = usingSobolSequences
				? new SobolSequenceGenerator(hParams.getAxisStreams().size() * 2)
				: null;

		List<Map<String, PropertyBoundaries>> initialMemory = new ArrayList<Map<String, PropertyBoundaries>>();

		// Initialize solution map with expected abs. sensor deviations
		for (int memorySolutions = 0; memorySolutions < hParams.getMemorySize(); memorySolutions++) {
			Map<String, PropertyBoundaries> defaultSolutions = new HashMap<String, PropertyBoundaries>();
			if (usingSobolSequences) {
				List<Double> initVec = DoubleStream.of(sobolGen.nextVector())
						.map(d -> Math.max(hParams.getLowerSearchBorder(), Math.min(hParams.getUpperSearchBorder(), d)))
						.boxed().collect(Collectors.toList());
				int axisPropIdx = 0;
				for (AxisStream stream : AxisStream.values()) {
					if (hParams.getAxisStreams().contains(stream)) {
						defaultSolutions.put(stream.getAxisName(),
								new PropertyBoundaries(initVec.get(axisPropIdx++), initVec.get(axisPropIdx++)));
					}
				}
			} else {
				for (AxisStream stream : AxisStream.values()) {
					if (hParams.getAxisStreams().contains(stream)) {
						defaultSolutions.put(stream.getAxisName(),
								new PropertyBoundaries(
										r.nextDouble(hParams.getLowerSearchBorder(), hParams.getUpperSearchBorder()),
										r.nextDouble(hParams.getLowerSearchBorder(), hParams.getUpperSearchBorder())));
					}
				}
			}

			initialMemory.add(defaultSolutions);
		}
		return initialMemory;
	}

}