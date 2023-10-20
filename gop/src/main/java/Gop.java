import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import model.Measure;
import model.Config;
import model.Data;
import model.ResultCommon;
import service.Database;
import service.ReadLog;
import service.ReadOs;
import service.Rest;
import service.CommandLineParser;
import service.CRetention;

/*
 * This Java source file was generated by the Gradle 'init' task.
 */

public class Gop {
	static boolean gColumn = true;
	static File rFile = null;

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";

	public static void main(String[] args) throws Exception {
		new Gop().startApp(args);
	}

	public void startApp(String[] args) throws Exception {
		CommandLineParser clp = new CommandLineParser(args);

		boolean help = clp.getFlag("help");
		if (help) {
			System.out.println(" ---");
			System.out.println(" -config <config file path> [ -demon | -client -log <log file path> <option> ]");
			System.out.println(" ");
			System.out.println(" client option:");
			System.out.println(
					"   -log <log file path> [ -time 'yyyy-mm-dd hh24:mi:ss.fff' 'yyyy-mm-dd hh24:mi:ss.fff' | -name <column name> | -tag <tag name> ]");
			System.out.println("			[ -head | -tail <print count> ]  ");
			System.out.println(" ");
			System.out.println(" ---");
			System.out.println(" sample use");
			System.out.println(" java -Xmx100M -jar gop.jar -config resource/config.json -demon  ");
			System.out.println(
					" java -jar gop.jar -config resource/config.json -client -log resource/log_20221201.json -time '2022-12-01 03:14:40.000' '2022-12-01 03:15:00.000'");
			System.out.println(
					" java -jar gop.jar -config resource/config.json -client -log resource/log_20221201.json -name execute -tail 10");
			System.out.println(
					" java -jar gop.jar -config resource/config.json -client -log resource/log_20221201.json -tag tag1 -head 10");
			System.out
					.println(" java -jar gop.jar -config resource/config.json -client -log resource/log_20221201.json");
			System.out.println(" ");

			System.exit(0);
		}
		if (args.length < 2) {
			System.out.println("invalid argument args : " + args.length);
			System.exit(0);
		}
		boolean client = clp.getFlag("client");
		boolean demon = clp.getFlag("demon");
		String configFile = clp.getArgumentValue("config")[0];
		String log = clp.getArgumentValue("log")[0];
		int head = clp.getArgumentValueInt("head");
		int tail = clp.getArgumentValueInt("tail");
		String time1 = clp.getArgumentValue("time")[0];
		String time2 = clp.getArgumentValue("time")[1];
		String tagArg = clp.getArgumentValue("tag")[0];
		String nameArg = clp.getArgumentValue("name")[0];
		

		rFile = new File(configFile);
		Gson gson = new GsonBuilder().setLenient().create();

		Config config = readAndConvConf(rFile, Config.class, gson);
		
		if (demon) {
			printInfo(config);
			CRetention cr = new CRetention(config.setting.fileLog.logPath);
			cr.go(config.setting.retention);
			gStampLog(config, gson);
			
		} else if (client) {
			ReadLog rl = new ReadLog(new File(log), gson, config);

			if (time1 != null && time2 != null) {
				LocalDateTime stTs = stringToDate(time1);
				LocalDateTime edTs = stringToDate(time2);
				rl.setRangeTimeMap(stTs, edTs);
				printTableMap(rl.rangeTimeMap, head, tail);
			} else if (nameArg != null) {
				String name = nameArg;
				rl.setNameMap(name);
				printTableMap(rl.nameMap, head, tail);
			} else if (tagArg != null) {
				String tag = tagArg;
				rl.setTagMap(tag);
				printTableMap(rl.tagMap, head, tail);
			} else {
				printTableMap(rl.timeMap, head, tail);
			}
		} else {
			System.out.println("invalid argument");
		}
	}

	private void printInfo(Config config) {
		try {
			String currentPath = new java.io.File(".").getCanonicalPath();
			System.out.println("Current dir : " + currentPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Source jdbc url : " + config.setting.jdbcSource.url);
		System.out.println("Write file : " + config.setting.fileLog.enable);
		System.out.println("Write file path : " + config.setting.fileLog.logPath);
		System.out.println("Write stacker : " + config.setting.stacker.enable);
		System.out.println("Write stacker : " + config.setting.stacker.baseUrl);
		System.out.println("Write stacker db name: " + config.setting.stacker.dbName);
	}

	private static String getTime(String string) {
		SimpleDateFormat sdf = new SimpleDateFormat(string);

		Calendar c1 = Calendar.getInstance();

		return sdf.format(c1.getTime());
	}

	private static void printTableMap(LinkedHashMap<LocalDateTime, ResultCommon[]> rangeTimeMap, int head, int tail) {
		if (rangeTimeMap.isEmpty()) {
			System.out.println("no data!");
			System.exit(0);
		}
		Set<LocalDateTime> timeKeys = rangeTimeMap.keySet();
		int i = 0;

		Data sumDt = null;
		for (LocalDateTime key : timeKeys) {
			// System.out.println(key);

			if (head > 0) {
				if (i < head) {
					Data data = new Data(timestampToString(key), rangeTimeMap.get(key));
					printTable(data);
					sumDt=sumData(sumDt,data);
					// System.out.println("head: " +i);
				}
			} else if (tail > 0) {
				if (timeKeys.size() - tail <= i) {
					Data data = new Data(timestampToString(key), rangeTimeMap.get(key));
					printTable(data);
					sumDt=sumData(sumDt,data);
					// System.out.println("tail: " +i);
				}
			} else {
				Data data = new Data(timestampToString(key), rangeTimeMap.get(key));
				printTable(data);
				sumDt=sumData(sumDt,data);
			}
			i++;
		}
		printTable(sumDt);
		printTable(avgData(sumDt,timeKeys.size()));
	}

	private static Data avgData(Data sumDt, int size) {
		sumDt.time = "AVG:                     ";
		
		for (ResultCommon rc : sumDt.rc) {
			rc.value = rc.value/size;
		}

		return sumDt;
	}

	private static Data sumData(Data sumDt, Data data) {
		if(sumDt == null){
			sumDt = data;
			sumDt.time = "SUM:                     ";
		}else{
		   for (int i = 0; i < data.rc.length; i++) {
			sumDt.rc[i].value += data.rc[i].value;
		   }
		}
		return sumDt;
	}

	private static LocalDateTime stringToDate(String startSearchKey) {

		try {
			DateTimeFormatter formatDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
			LocalDateTime localDateTime = LocalDateTime.from(formatDateTime.parse(startSearchKey));
			return localDateTime;
		} catch (java.time.format.DateTimeParseException e) {
			// TODO: handle exceptionA
			System.out.println("invalid time : " + startSearchKey);
			System.exit(0);
		}
		return null;
	}

	private static void gStampLog(Config config, Gson gson)
			throws Exception {

		// db
		Database db = new Database(config);

		int printRow = 0;
		Data beforeData = new Data(null, null);
		Data calData = new Data(null, null);

		PreparedStatement[] arrPstmt = null;

		arrPstmt = db.createConAndPstmt(db);
		boolean firstSkip = true;

		while (true) {
			Data data = null;
			int i = 0;
			do {
				data = db.getCommonQuery(arrPstmt);
				if (data != null) {
					continue;
				}
				arrPstmt = db.createConAndPstmt(db);

				System.out.println("getCommonQuery error retry cnt : " + i);
				i++;
				Thread.sleep(1000);
			} while (data == null);

			// ResultCommon[] rc2 = db.getOsQuery();

			// write output file (json)
			if (beforeData.rc == null) {
				calData = data.newInstance(data);
				beforeData = data.newInstance(data);
			} else {
				calData = diffDataCal(data, beforeData, config);
				beforeData = data.newInstance(data);
			}

			if (!firstSkip){
				if (config.setting.fileLog.enable) {
					writeJson(calData, gson, config);
				}
				if (config.setting.stacker.enable) {
					postJson(calData, gson, config);
				}

				// print console (table)
				if (config.setting.consolePrint) {
					printTable(calData);
					printRow++;
					if (printRow % config.setting.pageSize == 0) {
						gColumn = true;
					}
				}
			}else{
				firstSkip=false;
			}
			data = null;
			// rc2 = null;
			Thread.sleep(config.setting.timeInterval);
		}
	}

	private static Data diffDataCal(Data data, Data beforeData, Config config) {
		// Data tempData = new Data(data.time, data.rc);
		// ResultCommon[] rc = new ResultCommon[data.rc.length];
		// Data tempData = new Data(data.time, rc);
		Data cal = data.newInstance(data);
		for (int i = 0; i < data.rc.length; i++) {
			if (config.measure[i].diff) {
				cal.rc[i].value = data.rc[i].value - beforeData.rc[i].value;
			}
		}
		return cal;
	}

	private static String timestampToString(LocalDateTime timestamp) {
		// TODO Auto-generated method stub
		String tempTime = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").create()
				.toJson(Timestamp.valueOf(timestamp));

		return tempTime;
	}

	private static Config readAndConvConf(File rFile, Class<Config> class1, Gson gson) throws FileNotFoundException {
		FileInputStream fis = new FileInputStream(rFile);
		InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
		BufferedReader reader = new BufferedReader(isr);

		return gson.fromJson(reader, Config.class);
	}

	private static void postJson(Data data, Gson gson, Config config) {

		// for (ResultCommon rc:data.rc) {
		// 	String measureName=rc.name;
		// }
		// for rest
		String gRcs = gson.toJson(data.rc);


		// post stacker
		String gRcUnit = "{ \"point\" : " + gRcs + "}";
		String postBody = gRcUnit.replaceAll("false", "0").replaceAll("true", "1");

		// System.out.println("xxX:"+tmp1);
		// System.out.println("xxX:"+tmp1.length());
		Rest rest = new Rest();

		String postUrl = config.setting.stacker.baseUrl + config.setting.stacker.dbName;
		rest.sendPOST(postUrl, postBody);

		// Thread.sleep(1000);
		// rest.sendGET("http://192.168.0.120:5108/dbs/gop/test7");
	}

	private static void writeJson(Data data, Gson gson, Config config) throws IOException {

		String basePath = config.setting.fileLog.logPath;

		File logFile = new File(basePath + "log_" + getTime("YYYYMMdd") + ".json");
		File alertFile = new File(basePath + "alert_" + getTime("YYYYMM") + ".json");

		if (!logFile.getName().equals(basePath + "log_" + getTime("YYYYMMdd") + ".json")) {
			logFile = new File(basePath + "log_" + getTime("YYYYMMdd") + ".json");
		}
		if (!alertFile.getName().equals(basePath + "alert_" + getTime("YYYYMM") + ".json")) {
			alertFile = new File(basePath + "alert_" + getTime("YYYYMM") + ".json");
		}
		FileWriter fw = new FileWriter(logFile, true);
		BufferedWriter bw = new BufferedWriter(fw);

		FileWriter alertFw = new FileWriter(alertFile, true);
		BufferedWriter alertBw = new BufferedWriter(alertFw);
		String gRc = gson.toJson(data);
		bw.write(gRc);
		// }

		Measure[] ms = config.measure;

		for (int i = 0; i < data.rc.length; i++) {
			if (data.rc[i].alert && ms[i].alertScript != null) {
				alertBw.newLine();
				alertBw.write("alert time :" + data.time);
				alertBw.newLine();
				alertBw.newLine();
				if (ms[i].alertScriptIsOs) {
					alertBw.write(ReadOs.executeS(ms[i].alertScript));
				} else {
					String tmp = "echo \'" + ms[i].alertScript + ";\' |gsqlnet sys gliese --no-prompt";
					alertBw.write(ReadOs.executeS(tmp));
				}
				alertBw.newLine();
			}
		}
		// bw.append(",");
		bw.newLine();
		bw.close();
		alertBw.close();
	}

	private static void printTable(Data data) {
		// TODO Auto-generated method stub
		String[] column = new String[data.rc.length];
		String[] row = new String[data.rc.length];

		for (int i = 0; i < data.rc.length; i++) {
			if (data.rc[i] != null) {
				column[i] = data.rc[i].measure;
				row[i] = alertFormat(data.rc[i].value, data.rc[i].alert);
				// System.out.println(rc[i].toString());
			}
		}

		int i = 0;
		if (gColumn == true) {
			// System.out.println("** instance name :" + gName);

			System.out.println("");
			System.out.format("%34s", ANSI_GREEN + "time" + ANSI_RESET);
			for (i = 0; i < row.length; i++) {
				if (column[i] != null) {
					System.out.format("%23s", ANSI_GREEN + column[i] + ANSI_RESET);
				}
			}
			System.out.format("%n");
			gColumn = false;
		}

		for (i = 0; i < data.rc.length; i++) {
			if (data.rc[i] != null) {
				// System.out.println(rc[i].toString());
				System.out.format("%22s", ANSI_GREEN + data.time + ANSI_RESET);
				break;
			}
		}

		for (i = 0; i < row.length; i++) {
			if (row[i] != null) {
				System.out.format("%23s", row[i]);
			}
		}
		System.out.format("%n");
	}

	private static String alertFormat(long value, boolean alert) {
		String temp = null;
		if (alert) {
			temp = ANSI_RED + String.valueOf(value) + ANSI_RESET;
		} else {
			temp = ANSI_WHITE + String.valueOf(value) + ANSI_RESET;
		}
		return temp;
	}

	public Object startApp(String string, String string2) {
		return null;
	}
}
