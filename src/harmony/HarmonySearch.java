package harmony;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import utils.Printer;

public class HarmonySearch {

	private HarmonyMemory harmonyMemory;
	private HarmonyParameters harmonyParameters;
	private PrintStream outTextStream;
	private PrintStream outCsvStream;

	public HarmonySearch(HarmonyMemory memory, HarmonyParameters params, PrintStream outTxt, PrintStream outCsv) {
		harmonyMemory = memory;
		harmonyParameters = params;
		outTextStream = outTxt;
		outCsvStream = outCsv;
	}

	/**
	 * Executes harmony search on given harmony parameters.
	 * 
	 * Base idea of this algorithm is that from given initial solutions ("harmonies"
	 * in the harmony memory) it tries to find better solutions by using random
	 * values (according to set bandwith, acceptance rate and adjustment rate) and
	 * on finding a solution that is better than the worst in our memory, replaces
	 * the worst in our memory with the new solution.
	 * 
	 * By chance, new solutions are generated: (1) by using an existing partial
	 * solution randomly from the memory (if rand < acceptance rate) (2) adjusting
	 * the new partial solution randomly within the bandwidth (additionally to (1),
	 * if rand < param. adjustment rate) (3) completely random within the bandwith
	 * (rand >= acceptance rate)
	 * 
	 * @return HarmonyResult Result Object containing Information about this Harmony
	 *         Search run
	 */
	public HarmonyResult execHarmonySearch() {
		HarmonyResult hs = new HarmonyResult();
		outTextStream.println("Iteration;Best mean f-measure;Best mean precision; Best mean recall;Best abs. offset");
		outCsvStream.println("Iteration;Best mean f-measure;Best mean precision; Best mean recall;Best abs. offset");
		double lowerSBorder = harmonyParameters.getLowerSearchBorder();
		double upperSBorder = harmonyParameters.getUpperSearchBorder();
		double accRate = harmonyParameters.getR_accept();
		double pAdj = harmonyParameters.getR_pa();
		double pBW = harmonyParameters.getBand();

		List<Map<String, PropertyBoundaries>> mem = harmonyMemory.getMemory();

		if (harmonyParameters.getStopOnOptimum() && harmonyMemory.getBestAvgFMeasure() == 1.0) {
			hs.setAvgBestFMeasure(1.0);
			hs.setAvgBestPrecision(1.0);
			hs.setAvgBestRecall(1.0);
			hs.setRuntimeTilOptimumFound(0);
			hs.setSolutionsInMemory(harmonyMemory.getSolutions());
			hs.setBestSolution(harmonyMemory.getBestSolution(harmonyParameters.getAdditionalOptimizationMethod()));

			return hs;
		}

		if (harmonyParameters.getPrintNewSolutions() || harmonyParameters.getPrintMemorySwaps())
			Printer.printHeader("ALGORITHM START", this.outTextStream);
		int iterations = harmonyParameters.getNrOfIterations() - mem.size();
		long startIterTime = System.currentTimeMillis();
		for (int i = 0; i < iterations; i++) {
			// Init new solution map
			Map<String, PropertyBoundaries> newSolution = mem.get(0).keySet().parallelStream()
					.collect(Collectors.toMap(String::valueOf, x -> new PropertyBoundaries(0, 0)));

			newSolution.entrySet().parallelStream().forEach(v -> {
				ThreadLocalRandom r1 = ThreadLocalRandom.current();
				double randLVal = r1.nextDouble();
				double randUVal = r1.nextDouble();

				double lowerVal = randLVal >= accRate ? r1.nextDouble(lowerSBorder, upperSBorder)
						: randLVal < pAdj
								? Math.max(lowerSBorder,
										Math.min(upperSBorder,
												mem.get(r1.nextInt(mem.size())).get(v.getKey()).getLower()
														+ r1.nextDouble(-pBW, pBW)))
								: mem.get(r1.nextInt(mem.size())).get(v.getKey()).getLower();
				//
				double upperVal = randUVal >= accRate ? r1.nextDouble(lowerSBorder, upperSBorder)
						: randUVal < pAdj
								? Math.max(lowerSBorder,
										Math.min(upperSBorder,
												mem.get(r1.nextInt(mem.size())).get(v.getKey()).getUpper()
														+ r1.nextDouble(-pBW, pBW)))
								: mem.get(r1.nextInt(mem.size())).get(v.getKey()).getUpper();
				v.getValue().setLower(lowerVal);
				v.getValue().setUpper(upperVal);
			});

			if (harmonyParameters.getPrintNewSolutions()) {
				Printer.printHeader("New solution (" + (i + 1) + ". iteration)", this.outTextStream);
				newSolution
						.forEach((propertyName, boundaries) -> outTextStream.println(propertyName + ": " + boundaries));

			}
			// cache preswap values for comparison
			double bestAvgFMeasure = harmonyMemory.getBestAvgFMeasure();

			// Hand new solution over to memory. If is better than the worst solution in
			// memory it will be stored
			boolean foundOptimum = harmonyMemory.evalSolution(newSolution);
			boolean isBest = harmonyMemory.isSolutionBest(newSolution);

			// after swap
			double bestAvgFMeasureAfterSwap = harmonyMemory.getBestAvgFMeasure();

			// store results in HarmonyResult object
			if (foundOptimum || isBest) {
				hs.setRuntimeTilOptimumFound((System.currentTimeMillis() - startIterTime) / 1000.0);

				// check if new solution is new best solution in memory
				if (bestAvgFMeasureAfterSwap > bestAvgFMeasure) {
					hs.setNrOfIterationsForBestFMeasure(i + 1);
					hs.setAbsOffsetBestInitial(harmonyMemory.getBestAbsOffset());
					hs.setAbsOffsetBestMinimized(harmonyMemory.getBestAbsOffset());

				} else {
					hs.setAbsOffsetBestMinimized(harmonyMemory.getBestAbsOffset());
				}
				hs.setOptimumFound(foundOptimum);
				hs.setAvgBestPrecision(harmonyMemory.getBestAvgPrecision());
				hs.setAvgBestRecall(harmonyMemory.getBestAvgRecall());
				hs.setAvgBestFMeasure(harmonyMemory.getBestAvgFMeasure());
				if (harmonyParameters.getStopOnOptimum() && foundOptimum) {

					String resLine = String.format("%d;%.5f;%.5f;%.5f;%.5f", i + 1, harmonyMemory.getBestAvgFMeasure(),
							harmonyMemory.getBestAvgPrecision(), harmonyMemory.getBestAvgRecall(),
							harmonyMemory.getBestAbsOffset());
					outTextStream.println(resLine.replaceAll(";", "\t"));
					outCsvStream.println(resLine);
					break;
				}
			}
			String resLine = String.format("%d;%.5f;%.5f;%.5f;%.5f", i + 1, harmonyMemory.getBestAvgFMeasure(),
					harmonyMemory.getBestAvgPrecision(), harmonyMemory.getBestAvgRecall(),
					harmonyMemory.getBestAbsOffset());
			outCsvStream.println(resLine);
			outTextStream.println(resLine.replaceAll(";", "\t"));
		}

		// Set runtime in HarmonyResult object
		hs.setRuntimeIterations((System.currentTimeMillis() - startIterTime) / 1000.0);
		hs.setSolutionsInMemory(harmonyMemory.getSolutions());
		hs.setBestSolution(harmonyMemory.getBestSolution(harmonyParameters.getAdditionalOptimizationMethod()));

		return hs;
	}
}
