package utb.fai;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Crawler {
	private final int maxDepth;
	private final int debugLevel;


	private static final int timeout_ms = 20000;
	private static final String USER_AGENT = "Mozilla/5.0";
	private static final Pattern slovo = Pattern.compile("\\p{L}+");
	private final Set<URI> visitedURIs = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();

	private final ExecutorService executor;
	private final AtomicInteger pending = new AtomicInteger();
	private final CountDownLatch hotovo = new CountDownLatch(1);

	public Crawler(int maxDepth, int debugLevel) {
		this.maxDepth = maxDepth;
		this.debugLevel = debugLevel;
		int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
		this.executor = Executors.newFixedThreadPool(threads);
	}

	public Map<String, Integer> getWordCounts() {
		return wordCounts;
	}

	public Map<String, Integer> crawl(URI start) throws InterruptedException {
		try {
			if (visitedURIs.add(start))
				odeslat(new URIinfo(start, 0));
			else
				hotovo.countDown();
			hotovo.await();
		} finally {
			executor.shutdown();
			if (!executor.awaitTermination(30, TimeUnit.SECONDS))
				executor.shutdownNow();
		}
		return wordCounts;
	}

	private void odeslat(URIinfo info) {
		pending.incrementAndGet();
		try {
			executor.execute(() -> {
				try {
					process(info);
				} catch (Throwable t) {
					System.err.println("Neocekavany error " + info.uri + ": " + t);
				} finally {
					taskFinished();
				}
			});
		} catch (RejectedExecutionException e) {
			taskFinished();
		}
	}

	private void taskFinished() {
		if (pending.decrementAndGet() == 0)
			hotovo.countDown();
	}

	private void process(URIinfo info) {
		URI uri = info.uri;
		int depth = info.depth;
		if (debugLevel > 0)
			System.err.println("Analyzuji [" + depth + "] " + uri);

		Document doc;
		try {
			Connection.Response res = Jsoup.connect(uri.toString())
					.userAgent(USER_AGENT)
					.timeout(timeout_ms)
					.followRedirects(true)
					.ignoreContentType(true)
					.execute();

			String kontent = res.contentType();
			if (kontent != null) {
				String lower = kontent.toLowerCase(Locale.ROOT);
				if (!lower.startsWith("text/html") && !lower.startsWith("application/xhtml")) {
					if (debugLevel > 0)
						System.err.println("Preskocen neHTML kontent (" + kontent + "): " + uri);
					return;
				}
			}
			doc = res.parse();
		} catch (Exception e) {
			System.err.println("Error " + uri + " : " + e.getMessage());
			return;
		}

		int slova = sectiSlova(doc);
		if (debugLevel > 0)
			System.err.println("Nalezeno " + slova + " slov na teto strance: " + uri);

		if (depth >= maxDepth)
			return;

		for (Element el : doc.select("a[href], frame[src], iframe[src]")) {
			String abs = el.hasAttr("href") ? el.absUrl("href") : el.absUrl("src");
			if (abs.isEmpty())
				continue;
			URI link = normalize(abs);
			if (link != null && visitedURIs.add(link)) {
				if (debugLevel > 1)
					System.err.println("Adding URI: " + link);
				odeslat(new URIinfo(link, depth + 1));
			}
		}
	}

	private int sectiSlova(Document doc) {
		if (doc.body() == null)
			return 0;
		String text = doc.body().text();
		Map<String, Integer> local = new HashMap<>();
		int celkem = 0;
		Matcher match = slovo.matcher(text);
		while (match.find()) {
			local.merge(match.group().toLowerCase(Locale.ROOT), 1, Integer::sum);
			celkem++;
		}
		local.forEach((w, c) -> wordCounts.merge(w, c, Integer::sum));
		return celkem;
	}

	static URI normalize(String url) { //normalizace uri
		try {
			URI u = new URI(url.trim());
			String scheme = u.getScheme();
			if (scheme == null)
				return null;
			scheme = scheme.toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https"))
				return null;
			if (u.getRawAuthority() == null)
				return null;
			String path = u.getRawPath();
			if (path == null || path.isEmpty())
				path = "/";
			String query = u.getRawQuery() != null ? "?" + u.getRawQuery() : "";
			return new URI(scheme + "://" + u.getRawAuthority().toLowerCase(Locale.ROOT) + path + query);
		} catch (Exception e) {
			return null;
		}
	}

	//seradit podle abecedy
	static List<Map.Entry<String, Integer>> topWords(Map<String, Integer> counts, int n) {
		List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
		list.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
				.thenComparing(Map.Entry.comparingByKey()));
		return list.subList(0, Math.min(n, list.size()));
	}
}