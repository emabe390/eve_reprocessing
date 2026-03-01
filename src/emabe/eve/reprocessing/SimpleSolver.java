package emabe.eve.reprocessing;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleSolver {
    private static List<String> formatResult(Map<Double, String> results) {
        List<String> res = new ArrayList<>();
        // if (res.isEmpty()) return res;

        res.add("+ Reprocess " + LocalDate.now() + "\n");
        Object[] resultArray = results.keySet().stream().sorted().toArray();
        int len = (int) Math.floor(Math.log((Double) resultArray[resultArray.length - 1])) / 2;
        boolean first = true;
        int lastBlock = 0;
        int blockSize = 10000;
        for (Object objectKey : resultArray) {
            Double key = (Double) objectKey;
            if ((int) (key / blockSize) > lastBlock) {
                lastBlock = (int) (key / blockSize);
                res.add(String.format("++ %0" + len + "d-%0" + len + "d%n", lastBlock * blockSize, ((lastBlock + 1) * blockSize - 1)));
            }
            if (lastBlock == 0 && first) {
                first = false;
                res.add(String.format("++ %0" + len + "d-%0" + len + "d%n", 0, blockSize - 1));
            }

            res.add("-- " + results.get(key) + "\n");
        }
        return res;
    }

    private List<Integer> preSolve(List<Integer> resources, int system, float reprocessing, float costPerM3, PriceCalculator priceCalculator) throws IOException {
        boolean calcAll = resources.isEmpty();
        Map<Integer, Double> resourceToPrice = new HashMap<>();
        int index = 0;
        for (int resource : resources) {
            Double[] priceList = new Double[resources.size()];
            double price = priceCalculator.calculate(Cache.ESImarketValue(resource, system)) + costPerM3 * Cache.idToVolume(resource);
            priceList[index] = price;
            resourceToPrice.put(resource, priceList[index]);

            index++;
        }

        List<Integer> results = new LinkedList<>();

        int numberOfMats = Cache.getInvTypeMaterials().size();
        int i = 0;
        for (Map.Entry<Integer, List<Pair<Integer, Double>>> entry : Cache.getInvTypeMaterials().entrySet()) {
            System.out.println(++i + " / " + numberOfMats);
            Integer itemId = entry.getKey();
            List<Pair<Integer, Double>> val = entry.getValue();

            double volumeReprocessed = 0;
            double sumReprocessed = 0;
            for (Pair<Integer, Double> reprocessedResult : val) {
                if (!resourceToPrice.containsKey(reprocessedResult.first()) & !calcAll) continue;
                double reprocessedValue = priceCalculator.calculate(Cache.ESImarketValue(reprocessedResult.first(), system)) * reprocessedResult.second() * reprocessing;
                Debug.print(priceCalculator.calculate(Cache.ESImarketValue(reprocessedResult.first(), system)) + " * " + reprocessedResult.second() + " * " + reprocessing);
                Debug.print(Cache.getItemName(reprocessedResult.first()) + " : " + reprocessedValue);
                volumeReprocessed += Cache.idToVolume(reprocessedResult.first()) * reprocessedResult.second();
                sumReprocessed += reprocessedValue;
            }

            if (sumReprocessed == 0) continue; // doesn't reproduce to

            double itemPrice = priceCalculator.calculate(Cache.ESImarketValue(itemId, system));
            if (itemPrice == 0) continue;

            Debug.print(itemId);
            Debug.print("Only item: " + itemPrice);
            Debug.print("Pre hauling: " + sumReprocessed + " . " + Cache.idToVolume(itemId) + " * " + costPerM3 + " = " + (Cache.idToVolume(itemId) * costPerM3));
            sumReprocessed = sumReprocessed - costPerM3 * Cache.idToVolume(itemId) + costPerM3 * volumeReprocessed;
            Debug.print("Post hauling: " + sumReprocessed);

            if (sumReprocessed > itemPrice) {
                results.add(itemId);
                //System.out.println(emabe.eve.reprocessing.Cache.getItemName(itemId) + "," + (sumReprocessed - itemPrice));
            }
        }
        return results;
    }

    public List<String> solve(List<Integer> resources, int system, float reprocessing, float costPerM3, PriceCalculator priceCalculator) throws Exception {
        boolean calcAll = resources.isEmpty();
        Map<Integer, Double> resourceToPrice = new HashMap<>();
        int index = 0;
        for (int resource : resources) {
            Double[] priceList = new Double[resources.size()];
            double price = priceCalculator.calculate(Cache.ESImarketValue(resource, system)) + costPerM3 * Cache.idToVolume(resource);
            priceList[index] = price;
            resourceToPrice.put(resource, priceList[index]);
            index++;
        }

        Map<Double, String> results = new ConcurrentHashMap<>();

        List<Integer> preFilteredList = preSolve(resources, system, reprocessing, costPerM3, priceCalculator);

        int threadCount = 20;
        Thread[] pool = new Thread[threadCount];

        int totalCount = preFilteredList.size();
        int baseSize = totalCount / threadCount;
        int remainder = totalCount % threadCount;

        int start = 0;

        for (int i = 0; i < threadCount; i++) {
            int size = baseSize + (i < remainder ? 1 : 0);
            int end = start + size - 1;

            int finalStart = start;
            pool[i] = new Thread(() -> {
                for (int i1 = finalStart; i1 <= end; i1++) {
                    try {
                        System.out.println(i1 + "/" + totalCount);
                        calculateItemId(system, reprocessing, costPerM3, priceCalculator, preFilteredList.get(i1), resourceToPrice, calcAll, results);
                    } catch (IOException ignore) {
                    }
                }
            });
            pool[i].setDaemon(true);
            pool[i].start();
            start = end + 1;
        }
        for (int i = 0; i < threadCount; i++) {
            pool[i].join();
        }

        return formatResult(results);
    }

    private static void calculateItemId(int system, float reprocessing, float costPerM3, PriceCalculator priceCalculator, Integer itemId, Map<Integer, Double> resourceToPrice, boolean calcAll, Map<Double, String> results) throws IOException {
        List<Pair<Integer, Double>> val = Cache.getInvTypeMaterials().get(itemId);

        double volumeReprocessed = 0;
        double sumReprocessed = 0;
        for (Pair<Integer, Double> reprocessedResult : val) {
            if (!resourceToPrice.containsKey(reprocessedResult.first()) & !calcAll) continue;
            double reprocessedValue = priceCalculator.calculate(Cache.tycoonMarketValue(reprocessedResult.first(), system)) * reprocessedResult.second() * reprocessing;
            volumeReprocessed += Cache.idToVolume(reprocessedResult.first()) * reprocessedResult.second();
            sumReprocessed += reprocessedValue;
        }

        if (sumReprocessed == 0) return;

        double itemPrice = priceCalculator.calculate(Cache.tycoonMarketValue(itemId, system));
        if (itemPrice == 0) return;

        Debug.print(itemId);
        Debug.print("Only item: " + itemPrice);
        Debug.print("Pre hauling: " + sumReprocessed + " . " + Cache.idToVolume(itemId) + " * " + costPerM3 + " = " + (Cache.idToVolume(itemId) * costPerM3));
        sumReprocessed = sumReprocessed - costPerM3 * Cache.idToVolume(itemId) + costPerM3 * volumeReprocessed;
        Debug.print("Post hauling: " + sumReprocessed);

        if (sumReprocessed > itemPrice) {
            results.put(sumReprocessed - itemPrice, Cache.getItemName(itemId));
        }
    }

    public interface PriceCalculator {
        static PriceCalculator BUY() {
            return new SimplePriceCalculator() {
                @Override
                public double calculate(Cache.APIResponse response) {
                    if (response.buyOrders == 0) return 0;
                    if (response.sellOrders == 0) return 0;
                    if (response.buyVolume < 100) return 0;
                    if (response.sellVolume < 100) return 0;
                    return response.buyAvgFivePercent;
                }
            };
        }

        static PriceCalculator MAX_BUY() {
            return new SimplePriceCalculator() {
                @Override
                public double calculate(Cache.APIResponse response) {
                    if (response.buyOrders == 0) return 0;
                    if (response.sellOrders == 0) return 0;
                    if (response.buyVolume < 100) return 0;
                    if (response.sellVolume < 100) return 0;
                    return response.maxBuy;
                }
            };
        }

        static PriceCalculator SELL() {
            return new SimplePriceCalculator() {
                @Override
                public double calculate(Cache.APIResponse response) {
                    if (response.buyOrders == 0) return 0;
                    if (response.sellOrders == 0) return 0;
                    if (response.buyVolume < 100) return 0;
                    if (response.sellVolume < 100) return 0;
                    return response.sellAvgFivePercent;
                }
            };
        }

        static PriceCalculator MIN_SELL() {
            return new SimplePriceCalculator() {
                @Override
                public double calculate(Cache.APIResponse response) {
                    if (response.buyOrders == 0) return 0;
                    if (response.sellOrders == 0) return 0;
                    if (response.buyVolume < 100) return 0;
                    if (response.sellVolume < 100) return 0;
                    return response.minSell;
                }
            };
        }

        double calculate(Cache.APIResponse response);

        double calculate(Cache.ESIPriceData response);
    }

    private abstract static class SimplePriceCalculator implements PriceCalculator {
        @Override
        public double calculate(Cache.ESIPriceData response) {
            if (response == null) {
                return 0;
            }
            return response.getAverage_price();
        }
    }
}
