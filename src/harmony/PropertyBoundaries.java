package harmony;

public class PropertyBoundaries {

	private double lower;
	private double upper;

	public PropertyBoundaries(double lower, double upper) {
		this.lower = lower;
		this.upper = upper;
	}

	public double getUpper() {
		return upper;
	}

	public double getLower() {
		return lower;
	}

	public void setLower(double lower) {
		this.lower = lower;
	}

	public void setUpper(double upper) {
		this.upper = upper;
	}

	@Override
	public String toString() {
		return String.format("%.6f, %.6f", this.lower, this.upper);

	}

	public double[] getAsArray() {
		double[] arr = { lower, upper };
		return arr;
	}

	@Override
	public boolean equals(Object other) {
		PropertyBoundaries otherBounds = (PropertyBoundaries) other; // downcasting from object to Person
		if (this.lower != otherBounds.lower || this.upper != otherBounds.upper) {
			return false;
		}
		return true;
	}
}