package nba;

import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class BestOffensiveRebounds {
	
	private ArrayList[] _ptsAfterOffRebounds;
	private boolean _finishedParsing;
	private PrintWriter _writer;
	
	JAMES COHAN
	
	// variables for testing that all games are visited
	private int _playByPlayCounter;
	private int _boxScoreCounter;
		
	public BestOffensiveRebounds() {
		try {
			_writer = new PrintWriter("Data.txt", "UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		// number of feet from basket shot is taken from is used as index in array. 
		// So each index holds the number of rebounds resulting from misses from that distance
		int arraySize = 150;
		_ptsAfterOffRebounds = new ArrayList[arraySize];
		for (int i = 0; i < arraySize; i++) {
			_ptsAfterOffRebounds[i] = new ArrayList<Integer>();
		}
		
		_finishedParsing = false;
		
		/*
		 * Counters are for testing purposes to make sure all games are visited.
		 * After testing it appears 5 box scores of the 1230 regular season games are missing play-by-plays.
		 * There are 4 extra box score links from two games postponed (each team name is a link).
		 * Unclear why final box score count is 1233 instead of 1234. Possible that one box score is missing hyperlink.
		 */
		_playByPlayCounter = 0;
		_boxScoreCounter = 0;
		
		// espn.com NBA schedule is shown one week at a time. On each iteration new date is pasted to end of hyperlink to advance to next page
		MutableDateTime date = new MutableDateTime();
		date.setDate(2013, 10, 29);
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMdd");
		String week = fmt.print(date);
		
		
		// iterates through every page of NBA schedule and explores every game on each page
		while (_finishedParsing == false) {
			// check that all weeks are visited
			_writer.println("\nDate: "+ week + "\n");
			
			// get a page of the schedule
			Document schedulePage = this.findSchedule(week);

			// explores every game on the page of the schedule it is passed
			this.exploreGames(schedulePage);
			
			date.addWeeks(1);
			week = fmt.print(date);
		}
		
		// when done parsing schedule, print number of offensive and defensive rebounds resulting from misses from each distance
		this.interpretResults();
	}
	
	
	// returns the schedule webpage for the week it is passed
	public Document findSchedule(String week) {
		
		/*
		 * To test on just one page of schedule, uncomment out the url one line below and comment out the current url.
		 */ 
		 String url = "http://espn.go.com/nba/schedule/_/date/20140413";
		 
		 
		
		// String url = "http://espn.go.com/nba/schedule/_/date/"+week;
		
		Document schedulePage = null;
		try {
			schedulePage = Jsoup.connect(url).timeout(20000).get();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return schedulePage;
	}
	

	
	/*
	 * this method explores each game on the page of the schedule it is passed
	 * first it gets the links for all of the box scores on the page,
	 * then it goes to each box score page and extracts the link for the play-by-play page within the box score
	 * then it explores the play-by-plays to get the data on offensive vs. defensive rebounds off misses from different shot locations
	 */
	public void exploreGames(Document schedulePage) {
		// get a list of all the box score links on the page
		List<String> boxScoreLinks = new ArrayList<String>();
		boxScoreLinks = this.findBoxScoreLinks(schedulePage);
		
		// get a list of all the play-by-play links within the box score links
		List<String> playByPlayLinks = new ArrayList<String>();
		playByPlayLinks = this.findPlayByPlayLinks(boxScoreLinks);
		
		// go through the play-by-plays and for every missed shot record the shot distance and whether it led to an offensive or defensive rebound
		this.explorePlayByPlays(playByPlayLinks);
		
	}
	
	
	// this method extracts and returns the links for the box score of each game on the page of the schedule it is passed
	public List<String> findBoxScoreLinks(Document schedulePage){
		// extracts the table data cells with the scores of games and adds them to scores
		Elements table = schedulePage.getElementsByClass("tablehead");
		Elements rows = table.select("tr:not(tr.colhead)");
		Elements scores = new Elements();
		for (Element row: rows) {
			// don't include all star game (Eastern Conf vs. Western Conf)
			Elements cells = row.select("td:eq(0)").not("td:contains(Eastern Conf)");
			scores.addAll(cells);
			
			// if a cell contains this date, regular season is over and parsing is finished
			Elements endRegSeason = row.select("td:eq(0):contains(Thursday, April 17)");
			if (!endRegSeason.isEmpty()) {
				_finishedParsing = true;
				break;
			}
		}
		
		// extracts the box score links from the list of cells with the scores of each game
		List<String> boxScoreLinks = new ArrayList<String>();
		Elements links = scores.select("a");
		for (Element link: links) {
			// Testing - print text of each box score link and count number of links to make sure all pages are visited
			String text = link.text();
			_writer.println(text + "\n");
			_boxScoreCounter++;
			
			String absUrl = link.absUrl("href");
			boxScoreLinks.add(absUrl);
		}
		return boxScoreLinks;
	}
	
	
	// finds the play-by-links within the box score links it is passed
	public List<String> findPlayByPlayLinks(List<String> boxScoreLinks) {
		
		List<String> playByPlayLinks = new ArrayList<String>();
		
		for (String url: boxScoreLinks) {
			Document boxScore = null;
			try {
				boxScore = Jsoup.connect(url).timeout(20000).get();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Elements playByPlayCell = boxScore.select("[href*=playbyplay]");
			for (Element pbpc: playByPlayCell) {
				String playByPlayUrl = pbpc.absUrl("href");
				playByPlayUrl = playByPlayUrl + "&period=0";
				playByPlayLinks.add(playByPlayUrl);
			}
			
			// Test to find the box scores that are missing play-by-plays
			if (playByPlayCell.isEmpty()) {
				_writer.println("NO PLAY-BY-PLAY: " + url + "\n\n\n");
			}
		
		}
		return playByPlayLinks;
	}
	
	/*
	 * explores the list of play-by-play links it is passed
	 * Finds every missed shot and records the distance it was taken from and whether it led to an offensive or defensive rebound
	 */
	public void explorePlayByPlays(List<String> playByPlayLinks) {
		for (String url: playByPlayLinks) {
			Document playByPlayPage = null;
			try {
				playByPlayPage = Jsoup.connect(url).timeout(20000).get();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Test to make sure play-by-play urls are visited
			_writer.println("Visited URL: "+ url + "\n");
			_playByPlayCounter++;
			
			int offensiveReboundNum = 1;
			
			// iterate through play-by-play table recording data
			Elements table = playByPlayPage.getElementsByClass("mod-data");
			Elements rows = table.select("tr");
			for (Element row: rows) {
				// add cells that contain the word misses or blocks, don't include missed free throws that aren't last free throw taken since no rebound
				Elements miss = row.select("td:contains(misses), td:contains(blocks)").not("td:contains(1 of 2), td:contains(1 of 3), td:contains(2 of 3)");
				if (!miss.isEmpty()) {
					Element reboundRow = row.nextElementSibling();
					
					// rebound row will be null if miss is last row in quarter summary (no rebound since quarter is over)
					if (reboundRow != null) {
						// make sure row doesn't have 0:00 on the clock (occasionally espn's play-by-play will credit rebound at end of quarter where there is none)
						Elements endOfQuarterCell = reboundRow.select("td:contains(0:00)");
						if (!endOfQuarterCell.isEmpty()) {
							continue;
						}
						
						Elements reboundCell = reboundRow.select("td:contains(rebound)");
						if (!reboundCell.isEmpty()) {
							// there has been a miss and a rebound. classify the type of miss. missIndex is equal to number of feet shot is taken from
							int missIndex = this.classifyShot(miss);
							
							// if there is an offensive rebound, record how many points it results in
							String text = reboundCell.text();
							if (text.contains("offensive")) {
								/*
								 * ERROR CHECK
								 */
								_writer.println("Offensive Rebound Number: " + offensiveReboundNum + "\n");
								_writer.println(text);
								offensiveReboundNum++;
								
								int pointsScored = this.pointsOffRebound(reboundRow);
								_ptsAfterOffRebounds[missIndex].add(pointsScored);				
							}
						}
					}
				}
			}
		}
	}
	
	/*
	 * classifies the type of miss it is passed and returns the index of where it should go in the rebound arrays
	 * index is either a constant for layups, tip shots, dunks, free throws, and threes or the number of feet shot was taken from
	 */
	public int classifyShot(Elements shot) {
		String shotText = shot.text();

		if (shotText.contains("layup")){
			return CONSTANTS.LAYUP;
		}
		else if (shotText.contains("three") || shotText.contains("3-pt") || shotText.contains("3-point")) {
			return CONSTANTS.THREE;
		}
		else if (shotText.contains("tip")) {
			return CONSTANTS.TIPSHOT;
		}
		else if (shotText.contains("dunk")) {
			return CONSTANTS.DUNK;
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
				// Test to make sure that no shot is longer than 94 feet (length of NBA Court)
				if (location > 94) {
					_writer.println("LOCATION ERROR: " + shotText + "\n\n\n");
					return CONSTANTS.ERROR;
				}
				
				/*
				 * a few misses in play-by-play on espn.com are missing text saying three-pointer. 
				 * three point line is anywhere from 22-feet long on corners to 23 feet 9 inches at top
				 * so anything 24 feet and above is guaranteed to be a three
				 */
				if (location >= 24) {
					return CONSTANTS.THREE;
				}
				
				return location;
			}
	    }
		
		// some shots in espn.com's play-by-play are missing shot distance or any useful classifications
		return CONSTANTS.UNCLASSIFIED;
	}
	
	
	// returns how many points are scored off an offensive rebound
	public int pointsOffRebound(Element reboundRow) {
		// determine who got rebound
		Possession reboundPossession = this.getPossession(reboundRow);
		Possession currentPossession = null;
		Element currentRow = reboundRow;
		
		while (true) {
				currentRow = currentRow.nextElementSibling();
				currentPossession = this.getPossession(currentRow);
				String rowText = currentRow.text();
				
				_writer.println("Current Possession: " + currentPossession + "\n");
				_writer.println("Rebound Possession: " + reboundPossession + "\n");
				
				// if quarter over, return 0 points scored
				if (rowText.contains("end of quarter")) return 0;
				
				// if in next row the team that got the offensive rebound does something
				if (currentPossession == reboundPossession) {
					// if a team makes the shot, return how many points they got
					if (rowText.contains("makes")) {
						int shot = 0;
						int points = 0;
						
						if (currentPossession == Possession.HOME) shot = this.classifyShot(currentRow.select("td:nth-child(4)"));
						else if (currentPossession == Possession.AWAY) shot = this.classifyShot(currentRow.select("td:nth-child(2)"));
						/*
						 * ERROR CHECK
						 */
						else _writer.println("Error determining shot: " + rowText + "\n");
					
						// if he makes an and one on the basket, increment points by one
						boolean makesAndOne = this.makesAndOne(currentRow);
						if (makesAndOne) points++;
						// increment points by amount scored on basket
						if (shot == CONSTANTS.DUNK || shot == CONSTANTS.TIPSHOT || shot == CONSTANTS.LAYUP  || shot <= 23) points +=2;
						else if (shot > 23 || shot == CONSTANTS.THREE) points +=3;
						// all made baskets that aren't threes are twos
						else if (shot == CONSTANTS.UNCLASSIFIED) points +=2;
						
						/*
						 * ERROR CHECK
						 */
						else {
							_writer.println("Made Basket Error: " + rowText + "\n");
						}
						
						return points;
					}
					
					// if a team does not score on their next possession in any of the following ways, return 0
					if (rowText.contains("misses") || rowText.contains("blocks")) return 0;
					if (rowText.contains("turnover")) return 0;
					if (rowText.contains("offensive foul")) return 0;
					if (rowText.contains("traveling")) return 0;	
				}
				
				// if the non offensive rebounding team is what appears in the play-by-play row
				else if (currentPossession != reboundPossession) {
					if (rowText.contains("shooting foul")) return this.freeThrowsMade(currentRow);
			
					// if defensive team commits personal foul, continue to next iteration since offense still has possession
					else if (rowText.contains("personal foul")) {
						_writer.println("Personal Foul \n");
						continue;
					}
				}
				/*
				 * ERROR CHECK
				 */
				else {
					_writer.println("Unexpected play-by-play text: " + rowText + "\n");
					break;
				}
		}
		return 0;
	}
	
	// checks to see if someone gets an And One on a basket (fouled in addition to making basket)
	public boolean makesAndOne(Element basketRow) {
		// first, check to see if the opposing commits a shooting foul. If not, return false right away
		Possession basketPossession = this.getPossession(basketRow);
		Element nextRow = basketRow.nextElementSibling();
		Possession nextRowPossession = this.getPossession(nextRow);
		boolean isFoulOnBasket = false;
		if (basketPossession != nextRowPossession) {
			if (nextRow.text().contains("shooting foul")) {
				isFoulOnBasket = true;
			}
		}
		if (isFoulOnBasket == false) {
			return false;
		}
		
		if (this.freeThrowsMade(nextRow) == 1) {
			return true;
		}
		
		return false;
	}
	
	// count the number of made free throws
	public int freeThrowsMade(Element freeThrowRow) {
		
		String rowText = freeThrowRow.text();
		// find first shot free throw (sometimes things like timeout are in the next row after the foul)
		boolean shotFreeThrow = false;
		while (shotFreeThrow == false) { 
			if (rowText.contains("of 1") || rowText.contains("of 2") || rowText.contains("of 3")) {
				shotFreeThrow = true;
			}
			else {
				freeThrowRow = freeThrowRow.nextElementSibling();
				rowText = freeThrowRow.text();
			}
		}
		// find out number of free throws are being taken
		int numFreeThrows = 0;
		if (rowText.contains("of 1")) numFreeThrows = 1;
		else if (rowText.contains("of 2")) numFreeThrows = 2;
		else if (rowText.contains("of 3")) numFreeThrows = 3;
		/*
		 * ERROR CHECK
		 */
		else _writer.println("freeThrows of 1 of 2 of 3 counting error: " + rowText + "\n");
		
		// count number of free throws made
		int pointsScored = 0;
		int freeThrowsCounted = 0;
		// only increment freeThrowsCounted on make or miss (not on a substitution or something between shots)
		while (freeThrowsCounted != numFreeThrows) {
			if (rowText.contains("makes")) {
				pointsScored++;
				freeThrowsCounted++;
			}
			else if (rowText.contains("misses")) freeThrowsCounted++;
			
			freeThrowRow = freeThrowRow.nextElementSibling();
			rowText = freeThrowRow.text();
		}
		
		return pointsScored;
	}
	
	
	// returns whichever side has possession
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
		
		/*
		 * ERROR CHECK
		 */
		_writer.println("No possession determined" + playByPlayRow.text() + "\n");
		return null;
		
	}
	
	/*
	 *  This method takes in the offensive and defensive rebounding arrays and interprets and prints out their results
	 *  Since array indexes equal the distance from the basket shot is taken from, all it has to do is print out the number
	 *  of offensive or defensive rebounds that index is holding.
	 */
	public void interpretResults() {
		for (int i = 0; i < _ptsAfterOffRebounds.length; i++) {
			if (_ptsAfterOffRebounds[i].isEmpty() == false) {
				
				_writer.println("\n");
				
				if (i == CONSTANTS.UNCLASSIFIED) {
					_writer.println("Offensive rebounds off unclassified shots: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.THREE) {
					_writer.println("Offensive rebounds off threes: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.FREETHROW) {
					_writer.println("Offensive rebounds off free throws: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.DUNK) {
					_writer.println("Offensive rebounds off dunks: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.LAYUP) {
					_writer.println("Offensive rebounds off layups: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.TIPSHOT) {
					_writer.println("Offensive rebounds off tip shots: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == CONSTANTS.ERROR) {
					_writer.println("Offensive rebound error total: " + _ptsAfterOffRebounds[i] + "\n");
				}
				else if (i == 1) {
					_writer.println("Offensive rebounds off shots from 1 foot: " + _ptsAfterOffRebounds[1] + "\n");
				}
				else {
					_writer.println("Offensive rebounds off shots from "+i+" feet: " + _ptsAfterOffRebounds[i] + "\n");
				}	
			}
		}
		_writer.close();
	}
}
