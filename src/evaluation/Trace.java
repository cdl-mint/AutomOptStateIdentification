package evaluation;

import java.util.Date;

public class Trace {
	private Date startDate;
	private Date endDate;
	private int position;

	public Trace(int position, Date startDate, Date endDate) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.position = position;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

}
