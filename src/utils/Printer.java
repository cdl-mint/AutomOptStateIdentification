package utils;

import java.io.PrintStream;

public class Printer {
	public static String div = "------------------------------------------------------------------------------------------";
	private static PrintStream p;

	public static void printHeader(String s, PrintStream p) {
		Printer.printDivider(2, p);
		p.println(s);
		Printer.printDivider(1, p);
	}

	public static void printSmallHeader(String s, PrintStream p) {
		Printer.printDivider(1, p);
		p.println(s);
		Printer.printDivider(1, p);
	}

	public static void printDivider(int dividers, PrintStream p) {
		for (int i = 0; i < dividers; i++)
			p.println(div);
	}
}
