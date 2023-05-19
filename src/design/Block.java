package design;

import java.util.ArrayList;
import java.util.List;

public class Block {
	private String name;
	private List<State> assignedState;

	public Block() {
		this.name = "";
		this.assignedState = new ArrayList<State>();
	}

	public Block(String name, List<State> assignedState) {
		this.name = name;
		this.assignedState = new ArrayList<State>();
		this.assignedState = assignedState;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<State> getAssignedState() {
		return assignedState;
	}

	public void setAssignedState(List<State> assignedState) {
		this.assignedState = assignedState;
	}

}