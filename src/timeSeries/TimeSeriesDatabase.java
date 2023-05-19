package timeSeries;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import design.Property;
import evaluation.IdentifiedState;
import evaluation.Trace;
import harmony.HarmonyParameters;
import harmony.PropertyBoundaries;

public class TimeSeriesDatabase {

	public enum TSDBMode {
		TRAIN, TEST
	}
	public static TimeSeriesDatabase instance = null;

	private String dbNameTrain = "state_identification";
	private String dbNameTest = "state_identification_test";

	private InfluxDB influxDBTrainData;
	private InfluxDB influxDBTestData;


	private static final DateTimeFormatter ISO8601_FORMATTER = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd'T'HH:mm:ss").appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
			.appendPattern("X").toFormatter();

	private TimeSeriesDatabase(boolean useTrainAndTestDB) {
		this.influxDBTrainData = InfluxDBFactory.connect("http://localhost:8086", "root", "root");
		this.influxDBTrainData.deleteDatabase(dbNameTrain);
		this.influxDBTrainData.createDatabase(dbNameTrain);
		this.influxDBTrainData.setDatabase(dbNameTrain);
//		this.tsdbMode = TSDBMode.SINGLE_DB;

		if (useTrainAndTestDB) {	
//			this.tsdbMode = TSDBMode.TRAIN_TEST_DB;
			this.influxDBTestData = InfluxDBFactory.connect("http://localhost:8086", "root", "root");
			this.influxDBTestData.deleteDatabase(dbNameTest);
			this.influxDBTestData.createDatabase(dbNameTest);
			this.influxDBTestData.setDatabase(dbNameTest);
		} 
		
	}
	
	  public TimeSeriesDatabase getInstance() {
		  	if (instance == null) throw new RuntimeException("TSDB not initialized; Call one of the init functions of this class first!");
	        return instance;
	    }
	
	public static void init(String filename, boolean longRun, int timespan) {
	    instance = new TimeSeriesDatabase(false);
	    instance.setUpData(filename, longRun, timespan);
	}
	
	public static void init(String filename, List<Trace> trainingTraces, List<Trace> testTraces) {
	     instance = new TimeSeriesDatabase(true);
		 instance.setUpData(filename, trainingTraces, testTraces);
	}

	/**
	 * Setup data streams in TSDB.
	 * 
	 * @param filename .. file with sensor data streams
	 * @param longRun .. use long time format
	 * @param timespan .. 
	 * @param firstXTraces
	 */
	public void setUpData(String filename, boolean longRun, int timespan) {
		String csvFile = filename;
		String line = "";
		String cvsSplitBy = ";";
		BufferedReader br = null;
		Date parsedDate = new Date();
		try {

			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {

				String[] information = line.split(cvsSplitBy);
				String timestamp = information[0] + " " + information[1];

				double bp = Double.parseDouble(information[2]);
				double map = Double.parseDouble(information[3]);
				double sap = Double.parseDouble(information[4]);
				double wp = Double.parseDouble(information[5]);
				double gp = Double.parseDouble(information[6]);

				SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");

				try {
					parsedDate = df.parse(timestamp);
					savePoint(influxDBTrainData, dbNameTrain, parsedDate.getTime(), bp, map, sap, wp, gp);
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (longRun) {
			setUpDataLong(filename, parsedDate, timespan);
		}

	}

	private void setUpDataLong(String filename, Date lastDate, int timespan) {
		String csvFile = filename;
		String line = "";
		String cvsSplitBy = ";";
		BufferedReader br = null;
		Date currentDate = lastDate;
		for (int i = 0; i < timespan; i++) {
			try {

				br = new BufferedReader(new FileReader(csvFile));
				while ((line = br.readLine()) != null) {

					String[] information = line.split(cvsSplitBy);

					double bp = Double.parseDouble(information[2]);
					double map = Double.parseDouble(information[3]);
					double sap = Double.parseDouble(information[4]);
					double wp = Double.parseDouble(information[5]);
					double gp = Double.parseDouble(information[6]);

					Calendar now = Calendar.getInstance();
					now.setTime(currentDate);
					now.add(Calendar.SECOND, 1);
					currentDate = now.getTime();
					savePoint(influxDBTrainData, dbNameTrain, currentDate.getTime(), bp, map, sap, wp, gp);
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Setup data streams in TSDB.
	 * 
	 * @param filename .. file with sensor data streams
	 * @param longRun .. use long time format
	 * @param timespan .. 
	 * @param firstXTraces
	 */
	public void setUpData(String filename, List<Trace> trainingTraces, List<Trace> testTraces) {
		String csvFile = filename;
		String line = "";
		String cvsSplitBy = ";";
		BufferedReader br = null;
		Date parsedDate = new Date();		
		TreeMap<Date, List<Double>> dateToProperties = new TreeMap<>();
		SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");

		try {
			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {

				String[] information = line.split(cvsSplitBy);
				String timestamp = information[0] + " " + information[1];

				String[] split = information[0].split("\\.");
//				curLastStateMillis = Instant.from(ISO8601_FORMATTER.parse(split[2] + "-" + split[1] + "-" + split[0] + "T" + information[1] + "Z")).toEpochMilli();
				
				double bp = Double.parseDouble(information[2]);
				double map = Double.parseDouble(information[3]);
				double sap = Double.parseDouble(information[4]);
				double wp = Double.parseDouble(information[5]);
				double gp = Double.parseDouble(information[6]);

				
				try {
					parsedDate = df.parse(timestamp);
					dateToProperties.put(parsedDate, List.of(bp, map, sap, wp, gp));

//					savePoint((curLastStateMillis <= lastStateMillisGT ? influxDBTrainData : influxDBTestData), (curLastStateMillis <= lastStateMillisGT ? dbNameTrain : dbNameTest), parsedDate.getTime(), bp, map, sap, wp, gp);

				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		for (Trace t : trainingTraces) {
			for(Entry<Date, List<Double>> e :  new TreeMap<Date, List<Double>>(dateToProperties).subMap(t.getStartDate(), true, t.getEndDate(), true).entrySet()) {
				List<Double> props = e.getValue();
				savePoint(influxDBTrainData, dbNameTrain, e.getKey().getTime(), props.get(0), props.get(1), props.get(2), props.get(3), props.get(4));
				dateToProperties.remove(e.getKey());
			}
		}
		
		for (Trace t : testTraces) {
			for(Entry<Date, List<Double>> e :  new TreeMap<Date, List<Double>>(dateToProperties).subMap(t.getStartDate(), true, t.getEndDate(), true).entrySet()) {
				List<Double> props = e.getValue();
				savePoint(influxDBTestData, dbNameTest, e.getKey().getTime(), props.get(0), props.get(1), props.get(2), props.get(3), props.get(4));
				dateToProperties.remove(e.getKey());
			}
		}
//		for(Entry<Date, List<Double>> e :  dateToProperties.entrySet()) {
//			List<Double> props = e.getValue();
//			savePoint(influxDBTestData, dbNameTest, e.getKey().getTime(), props.get(0), props.get(1), props.get(2), props.get(3), props.get(4));
//		}
	}


	private void savePoint(InfluxDB db, String dbName, long timestamp, double bp, double map, double sap, double wp, double gp) {
		BatchPoints batchPoints = BatchPoints.database(dbName).tag("async", "true").retentionPolicy("autogen")
				.consistency(InfluxDB.ConsistencyLevel.ALL).tag("BatchTag", "BatchTagValue").build();
		Point point1 = Point.measurement("roboticArm").time(timestamp, TimeUnit.MILLISECONDS).addField("bp", bp)
				.addField("map", map).addField("sap", sap).addField("wp", wp).addField("gp", gp).build();
		batchPoints.point(point1);

		// Write them to InfluxDB
		db.write(batchPoints);

	}

//	 public List<IdentifiedState> recognizeStateHarmonized(String statename, List<Property> propertyValues, 
//			 Map<String, Boundaries<Double, Double>> harmonics, HarmonyParameters harmonyParams) {
//			Query query = new Query(StateQuery.createQuery(statename), dbName);
//			//final long startTime = System.nanoTime();
//			QueryResult queryResult = influxDB.query(query);
//			//final long duration = System.nanoTime() - startTime;
//			//System.out.println("Time: " + duration);
//
//			List<IdentifiedState> stateList = new ArrayList<IdentifiedState>();
//
//			for (QueryResult.Result result : queryResult.getResults()) {
//				if (result.getSeries() != null) {
//					for (QueryResult.Series series : result.getSeries()) {
//
//						for (List<Object> val : series.getValues()) {
//							IdentifiedState s = new IdentifiedState();
//							List<Property> properties = new ArrayList<Property>();
//							s.setName(statename);
//							for (int i = 0; i < val.size(); i++) {
//								if (i == 0) {
//									Instant instant = Instant.from(ISO8601_FORMATTER.parse(String.valueOf(val.get(i))));
//									LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
//									s.setTimestamp(ldt.toString());
//								} else {
//									Property p = new Property();
//									p.setName(series.getColumns().get(i));
//									p.setValue((double) val.get(i));
//									properties.add(p);
//								}
//							}
//							s.setProperties(properties);
//							stateList.add(s);
//
//						}
//					}
//				} else {
//					IdentifiedState s = new IdentifiedState();
//					List<Property> properties = propertyValues;
//					s.setName(statename);
//					s.setTimestamp("");
//					s.setProperties(properties);
//					stateList.add(s);
//
//				}
//			}
//			return stateList;
//		}

	/**
	 * Checks g
	 * 
	 * @param statename
	 * @param propertyValues
	 * @param propertyMap
	 * @return
	 */
	public List<IdentifiedState> recognizeState(TSDBMode mode, String statename, List<Property> propertyValues,
			Map<String, PropertyBoundaries> propertyMap) {
		Query query = new Query(StateQuery.createQuery(statename, propertyValues, propertyMap), mode == TSDBMode.TRAIN ? this.dbNameTrain :  this.dbNameTest);
		// final long startTime = System.nanoTime();
		
		QueryResult queryResult = mode == TSDBMode.TRAIN ? this.influxDBTrainData.query(query) :  this.influxDBTestData.query(query);
		// final long duration = System.nanoTime() - startTime;
		// System.out.println("Time: " + duration);

		List<IdentifiedState> stateList = new ArrayList<IdentifiedState>();

		for (QueryResult.Result result : queryResult.getResults()) {
			if (result.getSeries() != null) {
				for (QueryResult.Series series : result.getSeries()) {

					for (List<Object> val : series.getValues()) {
						IdentifiedState s = new IdentifiedState();
						List<Property> properties = new ArrayList<Property>();
						s.setName(statename);
						for (int i = 0; i < val.size(); i++) {
							if (i == 0) {
								Instant instant = Instant.from(ISO8601_FORMATTER.parse(String.valueOf(val.get(i))));
								LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
								s.setTimestamp(ldt.toString());
							} else {
								Property p = new Property();
								p.setName(series.getColumns().get(i));
								p.setValue((double) val.get(i));
								properties.add(p);
							}
						}
						s.setProperties(properties);
						stateList.add(s);

					}
				}
			} else {
//				IdentifiedState s = new IdentifiedState();
//				List<Property> properties = propertyValues;
//				s.setName(statename);
//				s.setTimestamp("1990-01-01T00:00:00");
//				s.setProperties(properties);
//				stateList.add(s);

			}
		}
		return stateList;
	}

}
