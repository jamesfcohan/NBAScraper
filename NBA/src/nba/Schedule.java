package nba;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
/*
 * This class represents an NBA schedule.
 * 
 * It is constructed by passing in a start date. 
 * 
 * Since the NBA schedule is loaded on expn.com one week at a time, it keeps track of the schedule page for the current week
 */
public class Schedule {
	
	private MutableDateTime _date;
	private Document _currWeekSchedulePage;
	
	private boolean _isEndOfRegularSeason;
	
	public Schedule(int year, int month, int day){
		_date = new MutableDateTime();
		_date.setDate(year, month, day);
		String week = this.formatWeek(_date);
		
		_isEndOfRegularSeason = false;
		if (Integer.parseInt(week) >= 20140417) {
			_isEndOfRegularSeason = true;
		}
		
		_currWeekSchedulePage = this.findSchedule(week);
	}
	
	

	/*
	 * This method takes in an exact date/time and returns a String in "week" format, 
	 * which can be added to end of espn.com's schedule url to get a page with a week of the schedule
	 */
	public String formatWeek(MutableDateTime date) {
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMdd");
		String week = fmt.print(date);
		return week;
	}
	
	/*
	 * Retrieves and returns the schedule webpage for the week it is passed
	 * Gets the url by adding the week of the schedule to the end of espn.com's schedule url
	 */
	public Document findSchedule(String week) {
		String url = "http://espn.go.com/nba/schedule/_/date/"+week;
		
		Document schedulePage = null;
		try {
			schedulePage = Jsoup.connect(url).timeout(20000).get();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return schedulePage;
	}
	
	// advances to the next week of the schedule
	public void advanceToNextWeek(){
		_date.addWeeks(1);
		String week = this.formatWeek(_date);
		_currWeekSchedulePage = this.findSchedule(week);
	}
	
	/*
	 * Gets all the play by play links for the current week of the schedule.
	 * Does this by going inside each box score link on current page of schedule and getting play-by-play link within box score
	 */
	public List<String> getPlayByPlayLinks() {
		List<String> playByPlayLinks = new ArrayList<String>();
		
		List<String> boxScoreLinks = this.getBoxScoreLinks();
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
				// append &period=0 to url to get link for play-by-play with all four quarters, instead of just first quarter
				playByPlayUrl = playByPlayUrl + "&period=0";
				playByPlayLinks.add(playByPlayUrl);
			}		
		}
		return playByPlayLinks;
	}
	
	
	// helper method for getPlayByPlayLinks. Returns a list of all the box scores on the current page of the schedule
	public List<String> getBoxScoreLinks() {
		// extracts the table data cells on schedule page with the scores of games and adds them to scores
		Elements table = _currWeekSchedulePage.getElementsByClass("tablehead");
		Elements rows = table.select("tr:not(tr.colhead)");
		Elements scores = new Elements();
		for (Element row: rows) {
			// include all rows but all-star game (Eastern Conf vs. Western Conf)
			Elements cells = row.select("td:eq(0)").not("td:contains(Eastern Conf)");
			scores.addAll(cells);
			// if a cell contains this date, regular season is over and parsing is finished
			Elements endRegSeasonDate = row.select("td:eq(0):contains(Thursday, April 17)");
			if (!endRegSeasonDate.isEmpty()) {
				_isEndOfRegularSeason = true;
				break;
			}
		}
		
		// extracts the box score links from the list of cells with the scores of each game
		List<String> boxScoreLinks = new ArrayList<String>();
		Elements links = scores.select("a");
		for (Element link: links) {
			String absUrl = link.absUrl("href");
			boxScoreLinks.add(absUrl);
		}
		return boxScoreLinks;
	}
	
	// returns true if schedule has reached the end of the regular season
	public boolean isEndOfRegularSeason(){
		return _isEndOfRegularSeason;
	}
	
}
