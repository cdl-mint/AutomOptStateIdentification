package design;

import java.util.ArrayList;
import java.util.List;

public class State {
	private String name;
	private List<Property> assignedProperties;

	public State() {
		name = "";
		this.assignedProperties = new ArrayList<Property>();

	}

	public State(String name, List<Property> assignedProperties) {
		this.name = name;
		this.assignedProperties = new ArrayList<Property>();
		this.assignedProperties = assignedProperties;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Property> getAssignedProperties() {
		return assignedProperties;
	}

	public void setAssignedProperties(List<Property> assignedProperties) {
		this.assignedProperties = assignedProperties;
	}

}
