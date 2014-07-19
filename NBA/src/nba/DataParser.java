package nba;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * This class models a data parser.
 * 
 * It has methods for parsing rebound data on the espn.com NBA schedule, but it leaves the decision for what data to
 * be recorded to its subclasses, OffensiveReboudingPercentageParser and PointsPerOffensiveReboundParser.
 */
public abstract class DataParser {
	
	private boolean _finishedParsing;
	
	public DataParser() {}
	
	// parse all the desired data in the NBA schedule
	public void parseData() {
		// Create a schedule and parse it until finished
		Schedule _schedule = new Schedule(2013, 10, 29);
		_finishedParsing = false;
		
		// iterate through every week of NBA schedule and explore every game on each page
		while (_finishedParsing == false) {
			this.parsePlayByPlays(_schedule.getPlayByPlayLinks());
			
			_schedule.advanceToNextWeek();
			
			if (_schedule.isEndOfRegularSeason()) {
				_finishedParsing = true;
			}
		}
		
		// when done parsing schedule, interpret results
		this.interpretResults();
	}
	
	/*
	 * Parses the list of play-by-play links it is passed.
	 * Finds if there is a miss and a rebound, and then records the desired data off off those rebounds
	 */
	public void parsePlayByPlays(List<String> playByPlayLinks) {
		for (String url: playByPlayLinks) {
			Document playByPlayPage = null;
			try {
				playByPlayPage = Jsoup.connect(url).timeout(20000).get();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// iterate through play-by-play table recording data
			Elements table = playByPlayPage.getElementsByClass("mod-data");
			Elements rows = table.select("tr");
			int arraySize = rows.size();
			Object[] rowArray = rows.toArray();
			
			for (int i = 0; i < arraySize; i++){
				Element row = (Element) rowArray[i];
				// add cells that contain the word misses or blocks, don't include missed free throws that aren't last free throw taken or technical free throws since no rebound (espn.com has bug where it credits rebounds anyways)
				Elements miss = row.select("td:contains(misses), td:contains(blocks)").not("td:contains(1 of 2), td:contains(1 of 3), td:contains(2 of 3), td:contains(technical");
				if (!miss.isEmpty()) {
					Element reboundRow = row.nextElementSibling();
					// rebound row will be null if miss is last row in quarter summary (no rebound since quarter is over)
					if (reboundRow != null) {
						// make sure row doesn't have 0:00 on the clock (occasionally espn's play-by-play will credit rebound at end of quarter where there is none)
						Elements endOfQuarterCell = reboundRow.select("td:contains(0:00)");
						if (!endOfQuarterCell.isEmpty()) {
							continue;
						}

						// if there's a putback shot after a miss, sometimes it credits shot first then the rebound. 
						// if that is the case, switch the html in those rows to fix bug
						if (this.isEspnPutBackBug(reboundRow)){
							Element nextRow = reboundRow.nextElementSibling();
							
							String tempText = reboundRow.html();
							reboundRow.html(nextRow.html());
							nextRow.html(tempText);
							
							rowArray[i+1] = reboundRow;
							rowArray[i+2] = nextRow;
						}
						
						// there is a miss and a rebound, so record the desired data from the miss and rebound
						this.recordReboundData(miss, reboundRow);
					}
				}
			}
		}
	}
	
	// abstract method, its subclasses decide what data they want recorded off the rebound
	abstract void recordReboundData(Elements miss, Element reboundRow);
	
	// abstract method, its subclasses decide how they want to interpret the data
	abstract void interpretResults();
	
	// classifies the type of miss it is passed and returns the index of where it should go
	public int classifyShot(Elements shot) {
		String shotText = shot.text();

		if (shotText.contains("layup") || shotText.contains("tip") || shotText.contains("dunk")){
			return CONSTANTS.ATRIM;
		}
		else if (shotText.contains("three") || shotText.contains("3-pt") || shotText.contains("3-point")) {
			return CONSTANTS.THREEPOINTER;
		}
		else if (shotText.contains("free")) {
			return CONSTANTS.FREETHROW;
		}
		// if it is not one of the above shots, return how many feet away from basket the shot was taken
		else {
			// strip out these numbers so that they are not included in the shot location
			shotText = shotText.replace("2-pt", "");
			shotText = shotText.replace("3-pt", "");
			shotText = shotText.replace("2-point", "");
			shotText = shotText.replace("3-point", "");

			// find and return the distance the shot was taken from
			StringBuffer feetBuffer = new StringBuffer();
			for(char c : shotText.toCharArray()){
				if(Character.isDigit(c)){
					feetBuffer.append(c);
				}                
			}
			if (feetBuffer != null && feetBuffer.length() != 0) {
				String feet = feetBuffer.toString();
				int location = Integer.parseInt(feet);

				if (location <= 2) {
					return CONSTANTS.ATRIM;
				}
				else if (location >= 3 && location <=9) {
					return CONSTANTS.THREETONINE;
				}
				else if (location >= 10 && location <=15) {
					return CONSTANTS.TENTOFIFTEEN;
				}
				else if (location >= 16 && location <=23) {
					return CONSTANTS.SIXTEENTOTWENTYTHREE;
				}
				/*
				 * a few misses in play-by-play on espn.com are missing text saying three-pointer. 
				 * three point line is anywhere from 22-feet long on corners to 23 feet 9 inches at top
				 * so anything 24 feet and above is guaranteed to be a three
				 */
				else if (location >= 24) {
					return CONSTANTS.THREEPOINTER;
				}
			}
		}
		// some shots in espn.com's play-by-play are known only as "jumper," "bank shot," "two point shot" or other shots worth two points
		return CONSTANTS.MISC;
	}
	
	// returns whichever side has possession in the row of the play-play-play it is passed
	public Possession getPossession(Element playByPlayRow) {
		Elements awayCell = playByPlayRow.select("td:nth-child(2)");
		Elements homeCell = playByPlayRow.select("td:nth-child(4)");
		
		// if away cell isn't empty, then away team has possession
		for (Element cell: awayCell) {
			String cellText = cell.text();
			cellText.replace(" ", "");
			
			if (cellText != "" && cellText.length() > 1) {
				return Possession.AWAY;
			}
		}
		
		// if home cell isn't empty, then home team has possession
		for (Element cell: homeCell) {
			String cellText = cell.text();
			cellText.replace(" ", "");
			if (cellText != "" && cellText.length() > 1) {
				return Possession.HOME;
			}
		}
		return null;
	}
	
	/*
	 * There is a bug in ESPN's play-by-plays, where sometimes when there is a putback shot, it credits
	 * the shot first, and then the offensive rebound that led to the shot second. 
	 * Even though these things are virtually one motion, the rebound should come first, and then the shot. 
	 * If this bug appears, switch the text in the rows to correct the bug.
	 */
	public boolean isEspnPutBackBug(Element putBackRow) {
		Element reboundRow = putBackRow.nextElementSibling();
		if (reboundRow == null) {
			return false;
		}
		
		boolean isPutBackShot = false;
		if (putBackRow.text().contains("misses") || putBackRow.text().contains("blocks") || putBackRow.text().contains("makes")) {
			isPutBackShot = true;
		}
		
		boolean isRebound = false;
		if (reboundRow.text().contains("rebound")) {
			isRebound = true;
		}
		
		boolean isSameTime = false;
		String gameClockPutBack = putBackRow.select("td:nth-child(1)").text();
		String gameClockRebound = reboundRow.select("td:nth-child(1)").text();
		if (gameClockPutBack.equals(gameClockRebound)) {
			isSameTime = true;
		}
		
		// if there is a putback shot and a rebound, and they occur at the same time, switch text in rows so that rebound comes before shot
		if (isPutBackShot && isRebound && isSameTime) {
			return true;
		}
		
		return false;
	}
	
	
	// get PrintWriter to write data onto .txt file
	public PrintWriter getWriter() {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter("Data.txt", "UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return writer;
	}
	

}