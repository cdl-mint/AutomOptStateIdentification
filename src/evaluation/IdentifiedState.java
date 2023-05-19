package evaluation;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import design.Property;

public class IdentifiedState implements Comparable<IdentifiedState> {
	private String name;
	private String timestamp;
	private List<Property> properties;
	private String time;
	private Timestamp ts;

	public IdentifiedState() {
		this("", "", new ArrayList<Property>());
	}

	public IdentifiedState(String name, List<Property> properties) {
		this(name, "", properties);
	}

	public IdentifiedState(String name, String timestamp, List<Property> properties) {
		this.name = name;
		this.timestamp = timestamp;
		this.properties = new ArrayList<Property>();
		this.properties = properties;
		if (!timestamp.equals("")) {
			changeTime();
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Timestamp getTs() {
		return ts;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
		changeTime();
	}

	public List<Property> getProperties() {
		return properties;
	}

	public void setProperties(List<Property> properties) {
		this.properties = properties;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((properties == null) ? 0 : properties.hashCode());
		result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IdentifiedState other = (IdentifiedState) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (properties == null) {
			if (other.properties != null)
				return false;
		} else if (!properties.equals(other.properties))
			return false;
		if (timestamp == null) {
			if (other.timestamp != null)
				return false;
		} else if (!timestamp.equals(other.timestamp))
			return false;
		return true;
	}

	public String toString() {
		return name + ";" + timestamp;
	}

	private void changeTime() {
		time = timestamp.replace('T', ' ');
		String[] parts = time.split(":");
		if (parts.length == 2) {
			time = time + ":00";
		}
		ts = Timestamp.valueOf(time);
	}

	@Override
	public int compareTo(IdentifiedState u) {
		if (getTs() == null || u.getTs() == null) {
			return 0;
		}
		return getTs().compareTo(u.getTs());
	}

}
