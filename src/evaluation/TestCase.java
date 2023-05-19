package evaluation;

public enum TestCase {
	UC1_2States("uc1_2states"), UC2_8States("uc2_8states"), UC1_14States("uc1_14states");

	// Member to hold the name
	private String string;

	// constructor to set the string
	TestCase(String name) {
		string = name;
	}

	// the toString just returns the given name
	@Override
	public String toString() {
		return string;
	}
}
