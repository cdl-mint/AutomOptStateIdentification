package design;

public enum AxisStream {
	BP, MAP, SAP, WP, GP;

	public int getSplitNr() {
		switch (this) {
		case BP:
			return 2;
		case MAP:
			return 3;
		case SAP:
			return 4;
		case WP:
			return 5;
		case GP:
			return 6;
		default:
			return 0;
		}
	}

	public String getAxisName() {
		switch (this) {
		case BP:
			return "bp";
		case MAP:
			return "map";
		case SAP:
			return "sap";
		case WP:
			return "wp";
		case GP:
			return "gp";
		default:
			return "";
		}
	}
}
