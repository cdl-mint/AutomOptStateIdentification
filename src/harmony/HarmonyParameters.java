package harmony;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import design.AxisStream;

public class HarmonyParameters {

	private double r_pa; // pitch adjustment rate
	private double band; // bandwidth
	private double r_accept; // acceptance rate
	private int memorySize; // Size of Harmony Memory

	private List<AxisStream> axisStream; // List of Sensors from Data stream

	private boolean printNewSolutions; // Print new Solutions to log file
	private boolean printMemorySwaps; // Print memory swaps to log file

	private int nrOfIterations; // Number of iterations in Harmony Search
	private boolean stopOnOptimum; // Stop Harmony Search Algorithm when f-measure is 1
	private List<String> statesToNotEvaluate; // List of States that should be ignored
	private AdditionalOptimizationMethod additionalOptimizationMethod; // for use of add. optimization Function to
																		// prioritize larger/smaller tolerance ranges
	private boolean useSobolInit; // using low-discrepancy sequences for memory initialization

	public enum AdditionalOptimizationMethod {
		MINIMIZE("min"), MAXIMIZE("max"), NONE("OnlyFMeasure");

		// Member to hold the name
		private String string;

		// constructor to set the string
		AdditionalOptimizationMethod(String name) {
			string = name;
		}

		// the toString just returns the given name
		@Override
		public String toString() {
			return string;
		}
	}

	private double lowerSearchBorder; // Lower Border of Search area
	private double upperSearchBorder; // Upper Border of Search area

	/**
	 * Initializes harmony parameters
	 *
	 * @param r_pa        .. pitch adjustment rate
	 * @param band        .. bandwidth
	 * @param r_accept    .. acceptance rate
	 * @param memorySize  .. size of memory
	 * @param streamCount .. number of stream (single, three, five)
	 */
	public HarmonyParameters(double r_pa, double band, double r_accept, int memorySize, List<AxisStream> axisStream,
			boolean useSobolInit) {
		this.r_pa = r_pa;
		this.band = band;
		this.r_accept = r_accept;
		this.memorySize = memorySize;
		this.axisStream = axisStream;
		this.useSobolInit = useSobolInit;
	}

	public boolean isUseSobolInit() {
		return useSobolInit;
	}

	public void setUseSobolInit(boolean useSobolInit) {
		this.useSobolInit = useSobolInit;
	}

	public double getR_pa() {
		return r_pa;
	}

	public void setR_pa(double r_pa) {
		this.r_pa = r_pa;
	}

	public double getBand() {
		return band;
	}

	public void setBand(double band) {
		this.band = band;
	}

	public double getR_accept() {
		return r_accept;
	}

	public void setR_accept(double r_accept) {
		this.r_accept = r_accept;
	}

	public int getMemorySize() {
		return memorySize;
	}

	public List<AxisStream> getAxisStreams() {
		return this.axisStream;
	}

	public void setAxisStreams(List<AxisStream> axisStream) {
		this.axisStream = axisStream;
	}

	public boolean getPrintNewSolutions() {
		return printNewSolutions;
	}

	public void setPrintNewSolutions(boolean printNewSolutions) {
		this.printNewSolutions = printNewSolutions;
	}

	public boolean getPrintMemorySwaps() {
		return printMemorySwaps;
	}

	public void setPrintMemorySwaps(boolean printMemorySwaps) {
		this.printMemorySwaps = printMemorySwaps;
	}

	public int getNrOfIterations() {
		return nrOfIterations;
	}

	public void setNrOfIterations(int nrOfIterations) {
		this.nrOfIterations = nrOfIterations;
	}

	public boolean getStopOnOptimum() {
		return stopOnOptimum;
	}

	public void setStopOnOptimum(boolean stopOnOptimum) {
		this.stopOnOptimum = stopOnOptimum;
	}

	public List<String> getStatesToNotEvaluate() {
		return statesToNotEvaluate;
	}

	public void setStatesToNotEvaluate(List<String> statesToNotEvaluate) {
		this.statesToNotEvaluate = statesToNotEvaluate;
	}

	public AdditionalOptimizationMethod getAdditionalOptimizationMethod() {
		return this.additionalOptimizationMethod;
	}

	public void setAdditionalOptimizationMethod(AdditionalOptimizationMethod method) {
		this.additionalOptimizationMethod = method;
	}

	public double getLowerSearchBorder() {
		return lowerSearchBorder;
	}

	public void setLowerSearchBorder(double lowerSearchBorder) {
		this.lowerSearchBorder = lowerSearchBorder;
	}

	public double getUpperSearchBorder() {
		return upperSearchBorder;
	}

	public void setUpperSearchBorder(double upperSearchBorder) {
		this.upperSearchBorder = upperSearchBorder;
	}

	public String toString() {
		return String.format("Acceptance rate (r_accept): %.2f\nParameter Adjustment Rate (r_pa): %.2f\n"
				+ "bandwith (band) = %.4f\n"
				+ "Memory size (solutions) = %d\nIterations: %d\nMinimization of Result: %s\nLower Search Border: %.2f\nUpper Search Border: %.2f\n"
				+ "Low-discrepancy sequence initialization used: " + this.useSobolInit, this.r_accept, this.r_pa,
				this.band, memorySize, nrOfIterations, additionalOptimizationMethod.toString(), lowerSearchBorder,
				upperSearchBorder);
	}

}
