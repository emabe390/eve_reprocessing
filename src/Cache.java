import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import net.jcip.annotations.GuardedBy;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    private static final Gson gson = new Gson();
    private static final Path cacheDir = Path.of("cache");
    private static final String INV_TYPES = "https://www.fuzzwork.co.uk/dump/latest/invTypes.xls";
    private static final String INV_TYPES_MATERIALS = "https://www.fuzzwork.co.uk/dump/latest/invTypeMaterials.csv";
    private static final String EVE_TYCOON = "https://evetycoon.com/api";


    private static Map<String, CachedResponse<APIResponse>> buySellApiCache;

    private static final String[] HARD_CACHE = new String[]{INV_TYPES, INV_TYPES_MATERIALS};

    @GuardedBy("this")
    private static Map<Integer, String> idToName;
    @GuardedBy("this")
    private static Map<String, Integer> nameToId;
    @GuardedBy("this")
    private static HashMap<Integer, Double> idToVolume;
    @GuardedBy("this")
    private static HashMap<Integer, Double> idToRequiredForReprocess;
    @GuardedBy("this")
    private static Map<Integer, List<Pair<Integer, Double>>> idToReprocessed;
    private static final Map<Integer, Map<Integer, ESIPriceData>> marketPriceResp = new HashMap<>();

    public static void initialize() throws Exception {
        populate();
        loadCacheEntries();
    }

    @SuppressWarnings("unchecked")
    private static void loadCacheEntries() {
        File file = new File("cache/buySellApiCache.bin");

        if (!file.exists()) {
            buySellApiCache = new ConcurrentHashMap<>();  // empty cache on first run
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {

            buySellApiCache = (Map<String, CachedResponse<APIResponse>>) in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Failed to load api cache");
            buySellApiCache = new ConcurrentHashMap<>();   // fallback on error
        }
    }

    public static void save() throws Exception {
        storeCacheEntries();
    }

    private static void storeCacheEntries() throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("cache/buySellApiCache.bin"));
        out.writeObject(buySellApiCache);
        out.close();
    }

    @GuardedBy("this")
    public static String getItemName(int id) {
        return idToName.get(id);
    }

    @GuardedBy("this")
    public static Integer getItemId(String name) {
        return nameToId.get(name);
    }

    private static void populate() throws IOException, BiffException {
        if (!Files.isDirectory(cacheDir)) {
            Files.createDirectory(cacheDir);
        }
        for (String url : HARD_CACHE) {
            populate(url);
        }

        parseInvTypes();
    }

    public synchronized static Map<Integer, List<Pair<Integer, Double>>> getInvTypeMaterials() throws IOException {
        if (idToReprocessed != null) return idToReprocessed;
        Debug.print("Loading invTypeMaterials");
        idToReprocessed = new HashMap<>();
        boolean firstLine = true;
        try (BufferedReader br = new BufferedReader(new FileReader(cacheDir.resolve("invTypeMaterials.csv").toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                Integer[] values = Arrays.stream(line.split(",")).map(Integer::parseInt).toArray(Integer[]::new);

                Debug.print("items per conv" + values[2]);
                Debug.print("req for conv: " + idToRequiredForReprocess.get(values[1]));
                idToReprocessed.computeIfAbsent(values[0], k -> new ArrayList<>()).add(new Pair<>(values[1], values[2] / idToRequiredForReprocess.get(values[0])));

            }
        }
        Debug.print("Loading invTypeMaterials - complete");
        return idToReprocessed;
    }

    private static synchronized void parseInvTypes() throws IOException, BiffException {
        if (idToName != null) return;
        Workbook invTypes = Workbook.getWorkbook(cacheDir.resolve("invTypes.xls").toFile());

        idToName = new HashMap<>();
        nameToId = new HashMap<>();
        idToVolume = new HashMap<>();
        idToRequiredForReprocess = new HashMap<>();
        Sheet sheet = invTypes.getSheet(0);
        int rows = sheet.getRows();

        for (int i = 1; i < rows; i++) {
            Cell[] row = sheet.getRow(i);
            int itemId = Integer.parseInt(row[0].getContents());
            String name = row[2].getContents();
            idToName.put(itemId, name);
            nameToId.put(name, itemId);
            idToVolume.put(itemId, Double.parseDouble(row[5].getContents()));
            idToRequiredForReprocess.put(itemId, Double.parseDouble(row[7].getContents()));
        }
    }

    public static double idToVolume(int itemId) {
        return idToVolume.get(itemId);
    }

    public static ESIPriceData ESImarketValue(int typeId, int regionId) throws IOException {
        if (!marketPriceResp.containsKey(regionId)) {
            URL url = new URL("https://esi.evetech.net/markets/prices");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            APIResponse apiResponse = null;
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                // Read the response
                try (InputStream in = conn.getInputStream()) {
                    String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();

                    Map<Integer, ESIPriceData> data = new HashMap<>();
                    for (ESIPriceData priceData : mapper.readValue(body, ESIPriceData[].class)) {
                        data.put(priceData.type_id, priceData);
                    }
                    marketPriceResp.put(regionId, data);
                    Debug.print(body);
                }
            }
        }
        System.out.println(typeId + " " + regionId + " : " + marketPriceResp.get(regionId).get(typeId));
        return marketPriceResp.get(regionId).get(typeId);
    }

    public static APIResponse tycoonMarketValue(int typeId, int regionId) throws IOException {
        String urlString = EVE_TYCOON + "/v1/market/stats/%d/%d".formatted(regionId, typeId);

        CachedResponse<APIResponse> c = buySellApiCache.get(urlString);
        if (c != null && c.expiresAt != null && c.expiresAt.isAfter(Instant.now())) {
            Debug.print("API Cache Hit");
            return c.body;
        }
        Debug.print("API Cache miss");
        APIResponse apiResponse = null;
        Instant expiresAt = null;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {

            // Read Expires header
            String expiresHeader = conn.getHeaderField("Expires");
            if (expiresHeader != null) {
                expiresAt = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(expiresHeader));
                expiresAt = expiresAt.plus(1, ChronoUnit.DAYS);
                Debug.print("Expires at: " + expiresAt);
            } else {
                expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
            }

            // Read the response
            try (InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                apiResponse = gson.fromJson(body, APIResponse.class);
                Debug.print(body);
            }
        }
        buySellApiCache.put(urlString, new CachedResponse<>(apiResponse, expiresAt));

        return apiResponse;
    }

    private static void populate(String url) {
        String[] split = url.split("/");
        Path fileName = cacheDir.resolve(split[split.length - 1]);
        if (Files.isRegularFile(fileName)) {
            Debug.print("Cache hit " + fileName);
            return;
        }

        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream())) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(fileName.toFile())) {
                Debug.print("Downloading " + fileName);
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            // handle exception
        }
    }

    public static class ESIPriceData {

        private double adjusted_price;
        private double average_price;
        private int type_id;

        // Default constructor required
        public ESIPriceData() {
        }

        @Override
        public String toString() {
            return type_id + " : " + average_price + " , " + adjusted_price;
        }

        // Getters and setters
        public double getAdjusted_price() {
            return adjusted_price;
        }

        public void setAdjusted_price(double adjusted_price) {
            this.adjusted_price = adjusted_price;
        }

        public double getAverage_price() {
            return average_price;
        }

        public void setAverage_price(double average_price) {
            this.average_price = average_price;
        }

        public int getType_id() {
            return type_id;
        }

        public void setType_id(int type_id) {
            this.type_id = type_id;
        }
    }

    public static class APIResponse implements Serializable {
        long buyVolume;
        long sellVolume;
        long buyOrders;
        long sellOrders;
        long buyOutliers;
        long sellOutliers;
        double buyThreshold;
        double sellThreshold;
        double buyAvgFivePercent;
        double sellAvgFivePercent;
        double maxBuy;
        double minSell;
    }

    private static class CachedResponse<T> implements Serializable {
        T body;
        Instant expiresAt;

        public CachedResponse(T _body, Instant _expiresAt) {
            body = _body;
            expiresAt = _expiresAt;
        }
    }
}
