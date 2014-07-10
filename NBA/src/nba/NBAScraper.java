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

public class NBAScraper {
	
	private int[] _offensiveRebounds;
	private int[] _defensiveRebounds;
	private boolean _finishedParsing;
	private PrintWriter _writer;
	
	// variables for testing that all games are visited
	private int _playByPlayCounter;
	private int _boxScoreCounter;
		
	public NBAScraper() {
		try {
			_writer = new PrintWriter("Data.txt", "UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		// number of feet from basket shot is taken from is used as index in array. 
		// So each index holds the number of rebounds resulting from misses from that distance
		_offensiveRebounds = new int[150];
		_defensiveRebounds = new int[150];
		
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
		 * String url = "http://espn.go.com/nba/schedule/_/date/20140413";
		 */
		
		String url = "http://espn.go.com/nba/schedule/_/date/"+week;
		
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
						
						Elements reboundCells = reboundRow.select("td:contains(rebound)");
						if (!reboundCells.isEmpty()) {
							// there has been a miss and a rebound. classify the type of miss. missIndex is equal to number of feet shot is taken from
							int missIndex = this.classifyMiss(miss);
	
							String text = reboundCells.text();
							if (text.contains("offensive")) {
								_offensiveRebounds[missIndex]++;
							}
							else if (text.contains("defensive")) {
								_defensiveRebounds[missIndex]++;
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
	public int classifyMiss(Elements miss) {
		String missText = miss.text();

		if (missText.contains("layup")){
			return CONSTANTS.LAYUP;
		}
		else if (missText.contains("three") || missText.contains("3-pt") || missText.contains("3-point")) {
			return CONSTANTS.THREE;
		}
		else if (missText.contains("tip")) {
			return CONSTANTS.TIPSHOT;
		}
		else if (missText.contains("dunk")) {
			return CONSTANTS.DUNK;
		}
		else if (missText.contains("free")) {
			return CONSTANTS.FREETHROW;
		}
		// if it is not one of the above shots, return how many feet away from basket the shot was taken
		else {
			// strip out these numbers so that they are not included in the shot location
			missText = missText.replace("2-pt", "");
			missText = missText.replace("3-pt", "");
			missText = missText.replace("2-point", "");
			missText = missText.replace("3-point", "");
			
			// find and return the distance the shot was taken from
			StringBuffer feetBuffer = new StringBuffer();
			for(char c : missText.toCharArray()){
				if(Character.isDigit(c)){
					feetBuffer.append(c);
				}                
	        }
			if (feetBuffer != null && feetBuffer.length() != 0) {
				String feet = feetBuffer.toString();
				int location = Integer.parseInt(feet);
				// Test to make sure that no shot is longer than 94 feet (length of NBA Court)
				if (location > 94) {
					_writer.println("LOCATION ERROR: " + missText + "\n\n\n");
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
	
	/*
	 *  This method takes in the offensive and defensive rebounding arrays and interprets and prints out their results
	 *  Since array indexes equal the distance from the basket shot is taken from, all it has to do is print out the number
	 *  of offensive or defensive rebounds that index is holding.
	 */
	public void interpretResults() {
		int offensiveSum = 0;
		int defensiveSum = 0;
		int total = 0;
		for (int i = 0; i < _offensiveRebounds.length; i++) {
			if (_offensiveRebounds[i] !=0 || _defensiveRebounds[i] != 0) {
				offensiveSum += _offensiveRebounds[i];
				defensiveSum += _defensiveRebounds[i];
				
				_writer.println("\n");
				
				if (i == CONSTANTS.UNCLASSIFIED) {
					_writer.println("Offensive rebounds off unclassified shots: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off unclassified shots: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.THREE) {
					_writer.println("Offensive rebounds off threes: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off threes: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.FREETHROW) {
					_writer.println("Offensive rebounds off free throws: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off free throws: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.DUNK) {
					_writer.println("Offensive rebounds off dunks: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off dunks: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.LAYUP) {
					_writer.println("Offensive rebounds off layups: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off layups: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.TIPSHOT) {
					_writer.println("Offensive rebounds off tip shots: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off tip shots: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == CONSTANTS.ERROR) {
					_writer.println("Offensive rebound error total: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebound error total: " + _defensiveRebounds[i] + "\n\n");
				}
				else if (i == 1) {
					_writer.println("Offensive rebounds off shots from 1 foot: " + _offensiveRebounds[1] + "\n");
					_writer.println("Defensive rebounds off shots from 1 foot: " + _defensiveRebounds[1] + "\n\n");
				}
				else {
					_writer.println("Offensive rebounds off shots from "+i+" feet: " + _offensiveRebounds[i] + "\n");
					_writer.println("Defensive rebounds off shots from "+i+" feet: " + _defensiveRebounds[i] + "\n\n");
				}	
			}
		}
		total = offensiveSum + defensiveSum;
		_writer.println("\n" + "Offensive Sum: " + offensiveSum + "\n");
		_writer.println("Defensive Sum: " + defensiveSum + "\n");
		_writer.println("Total Sum: " + total + "\n\n");
		_writer.println("Number of play-by-plays visited: " + _playByPlayCounter);
		_writer.println("Number of box scores: " + _boxScoreCounter);
		_writer.close();
	}
}
