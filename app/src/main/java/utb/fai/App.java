package utb.fai;

import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class App {

	private static final int max_depth = 2;
	private static final int top_slova = 20;

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Chybi parametr: <url> [maxDepth=2] [debugLevel=0]");
			System.exit(1);
			return;
		}

		int maxDepth = max_depth;
		int debugLevel = 0;
		try {
			if (args.length > 1)
				maxDepth = Integer.parseInt(args[1]);
			if (args.length > 2)
				debugLevel = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			System.err.println("maxDepth a debugLevel musi byt cisla (int)");
			System.exit(1);
			return;
		}
		if (maxDepth < 0)
			maxDepth = 0;

		String startUrl = args[0].trim();
		if (!startUrl.matches("(?i)^https?://.*"))
			startUrl = "http://" + startUrl;

		URI start = Crawler.normalize(startUrl);
		if (start == null) {
			System.err.println("Invalidni URL: " + args[0]);
			System.exit(1);
			return;
		}

		Crawler crawler = new Crawler(maxDepth, debugLevel);
		Map<String, Integer> counts;
		try {
			counts = crawler.crawl(start);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println("Interrupted");
			counts = crawler.getWordCounts();
		}

		PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
		List<Map.Entry<String, Integer>> top = Crawler.topWords(counts, top_slova);
		for (Map.Entry<String, Integer> e : top)
			out.println(e.getKey() + ";" + e.getValue());
		out.flush();
	}
}