package evaluation;

import java.util.Map;

import harmony.PropertyBoundaries;

public class Solution {
	double fMeasure;
	double absOffsetSum;
	Map<String, PropertyBoundaries> propertyOffsets;

	public Solution(double f, double absOffset, Map<String, PropertyBoundaries> props) {
		this.fMeasure = f;
		this.absOffsetSum = absOffset;
		this.propertyOffsets = props;
	}

	public Double getFMeasure() {
		return fMeasure;
	}

	public void setfMeasure(double fMeasure) {
		this.fMeasure = fMeasure;
	}

	public Double getAbsOffsetSum() {
		return absOffsetSum;
	}

	public void setAbsOffsetSum(double absOffsetSum) {
		this.absOffsetSum = absOffsetSum;
	}

	public Map<String, PropertyBoundaries> getPropertyOffsets() {
		return propertyOffsets;
	}

	public void setPropertyOffsets(Map<String, PropertyBoundaries> propertyOffsets) {
		this.propertyOffsets = propertyOffsets;
	}

	@Override
	public String toString() {
		return String.format("F1: %.3f\tOffsetSum: %.3f", fMeasure, absOffsetSum);
	}

}