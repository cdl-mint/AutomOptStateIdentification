package evaluation;

import java.util.ArrayList;
import java.util.List;

public class EvaluationResult {
	String state;
	double precision;
	double recall;
	double fMeasure;
	List<IdentifiedState> missedStates;

	public EvaluationResult() {
		state = "";
		precision = 0;
		recall = 0;
		fMeasure = 0;
		missedStates = new ArrayList<>();
	}

	public EvaluationResult(String state, double precision, double recall, double fMeasure,
			List<IdentifiedState> missedStates) {
		this.state = state;
		this.precision = precision;
		this.recall = recall;
		this.fMeasure = fMeasure;
		this.missedStates = missedStates;
	}

	public List<IdentifiedState> getMissedStates() {
		return missedStates;
	}

	public void setMissedStates(List<IdentifiedState> missedStates) {
		this.missedStates = missedStates;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public double getPrecision() {
		return precision;
	}

	public void setPrecision(double precision) {
		this.precision = precision;
	}

	public double getRecall() {
		return recall;
	}

	public void setRecall(double recall) {
		this.recall = recall;
	}

	public double getfMeasure() {
		return fMeasure;
	}

	public void setfMeasure(double fMeasure) {
		this.fMeasure = fMeasure;
	}

	public String toString() {
		return state + " precision:" + precision + " recall:" + recall + " F-Measure:" + fMeasure;
	}
}
