package top.ychen5325.smartPool.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import top.ychen5325.smartPool.common.IntervalEnum;
import top.ychen5325.smartPool.common.UrlConfig;
import top.ychen5325.smartPool.model.KlineForBa;
import top.ychen5325.smartPool.model.SymbolShock;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yyy
 * @wx ychen5325
 * @email yangyouyuhd@163.com
 */
@Slf4j
@Component
public class SmartPoolService {

    @Resource
    private RestTemplate restTemplate;

    /**
     * Get the list of currencies that support contract transactions
     *
     * @return
     */
    public List<String> listContractSymbol() {
        JSONObject resp = restTemplate.getForObject(UrlConfig.listSymbolsUrl, JSONObject.class);
        List<String> symbols = resp.getJSONArray("symbols")
                .stream()
                .map(e -> ((Map<String, String>) e).get("symbol"))
                .collect(Collectors.toList());
        return symbols;
    }

    /**
     * Obtain specified currency and k-line data
     *
     * @return
     */
    private List<KlineForBa> listKline(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        String reqUrl = String.format(UrlConfig.kLinesUrl, symbol, interval);
        if (startTime != null) {
            reqUrl = reqUrl.concat("&startTime=" + startTime);
        }
        if (endTime != null) {
            reqUrl = reqUrl.concat("&endTime=" + endTime);
        }
        if (limit != null) {
            reqUrl = reqUrl.concat("&limit=" + limit);
        }
        // log.info("list kline url is {}", reqUrl);

        String resp = restTemplate.getForObject(reqUrl, String.class);
        List<List> klines = JSON.parseArray(resp, List.class);


        List<KlineForBa> result = new ArrayList<>(klines.size() * 2);

        for (List cur : klines) {
            List kline = cur;
            Long openTime = (long) kline.get(0);
            Long closeTime = (long) kline.get(6);
            String openPrice = kline.get(1).toString();
            String maxPrice = kline.get(2).toString();
            String minPrice = kline.get(3).toString();
            String closePrice = kline.get(4).toString();
            // Total transaction
            String txAmount = kline.get(7).toString();
            result.add(KlineForBa.builder()
                    .openTime(openTime)
                    .closeTime(closeTime)
                    .openPrice(new BigDecimal(openPrice))
                    .maxPrice(new BigDecimal(maxPrice))
                    .minPrice(new BigDecimal(minPrice))
                    .closePrice(new BigDecimal(closePrice))
                    .txAmount(new BigDecimal(txAmount))
                    .build());
        }
        return result;
    }


    public Double[] shockAnalyze(String symbol, IntervalEnum period) {
        try {
            Long curTime = System.currentTimeMillis() / 1000 * 1000;
            Long time = period.time;
            List<KlineForBa> klines = new ArrayList<>();
            do {
                // Get 12 hours of 1m k-line data each time
                klines.addAll(listKline(symbol, "1m", curTime - time, null, 720));
                time -= 43200000;
            } while (time > 0);

            double maxPrice = klines.stream().max(Comparator.comparing(KlineForBa::getMaxPrice)).get().getMaxPrice().doubleValue();
            double minPrice = klines.stream().min(Comparator.comparing(KlineForBa::getMinPrice)).get().getMinPrice().doubleValue();
            double avgPrice = klines.stream().map(e -> e.getOpenPrice().add(e.getClosePrice()).doubleValue()).collect(Collectors.averagingDouble(e -> e)) / 2;

            /**
             * The minimum price percentage, that is, 1/1000 of avgPrice
             */
            double scalePrice = avgPrice / 1000;

            /**
             * Array length definition,
             * [avgP-x,avgP-x+1,avgP-1,avgP,avgP+1,avgP+x-1,avgP+x]
             */
            int len = (int) ((maxPrice - minPrice) / scalePrice) + 1;
            int[] priceCountArr = new int[len];
            for (KlineForBa kline : klines) {
                double openPrice = kline.getOpenPrice().doubleValue();
                double closePrice = kline.getClosePrice().doubleValue();
                double lowPrice = Math.min(openPrice, closePrice);
                double highPrice = Math.max(openPrice, closePrice);

                if (lowPrice >= avgPrice * (1 + ((maxPrice - avgPrice) / avgPrice) / 2) || highPrice <= avgPrice * (1 - ((avgPrice - minPrice) / avgPrice) / 2)) {
                    continue;
                }

                for (int i = 0; i < priceCountArr.length; i++) {
                    double curPrice = minPrice + (i * scalePrice);
                    if (lowPrice <= curPrice && highPrice >= curPrice) {
                        priceCountArr[i]++;
                    }
                }
            }

            int ShockVal = Arrays.stream(priceCountArr).sum();
            /**
             * Sparse limit
             *    In the final normal distribution curve, we remove the points at both ends and the interval that does not exceed 10% of the total,
             *    and the rest is the oscillation interval
             */
            float sparseLimit = 0.1F;
            int sparseCount = (int) (ShockVal * sparseLimit);
            int left = 0, right = priceCountArr.length - 1;
            int tmpCount = 0;
            while (left < right) {
                if (priceCountArr[left] < priceCountArr[right]) {
                    tmpCount += priceCountArr[left++];
                } else {
                    tmpCount += priceCountArr[right--];
                }
                if (tmpCount > sparseCount) {
                    break;
                }
            }

            Double[] res = {1D * ShockVal, (minPrice + scalePrice * left), (minPrice + scalePrice * right), 0.0};
            /**
             *  The following three types of situations are not suitable for grids
             *  1、Unilateral rise
             *  2、Unilateral decline
             *  3、Mountain Peaks and Basin Quotes
             */
            double maxP = res[2];
            double minP = res[1];
            int size = klines.size();
            /**
             * Unilateral market analysis
             * Get its average market, and shock amplitude,
             *  Compare the difference between the first and the last, if it is close to the amplitude, it means that it may be one-sided market,
             *  If the end is close, check whether there are peaks and valley bottoms, if there are, it represents the peak market
             *
             *  Get the overall mean,
             *  When obtaining the local mean, such as the difference ratio between the 5% interval and the overall mean,
             *      (distinguish between positive and negative), (the difference ratio will be [0%, 100%], 0 means equal to the mean, 100 means a serious deviation from the mean)
             *  Finally, if it is a unilateral increase in the market, you will get an interval similar to [-30%, -20%, -10%, 5%, 15%, 50%]
             *  If it is a volatile market, you will get an interval similar to [4%, 1%, -2%, 0%, -1%, 2%]
             */
            // Overall average price
            double overallAvgPrice = klines.stream()
                    .collect(Collectors.averagingDouble(e -> e.getOpenPrice().add(e.getClosePrice()).doubleValue())) / 2;
            // 假设以5%为最小区间分割、则将得到区间为
            List<Double> avgList = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int from = size * (i * 5) / 100;
                int to = size * (i * 5) / 100 + size * 5 / 100;
                double avgP = 0;
                while (from < to) {
                    KlineForBa kline = klines.get(from);
                    avgP += kline.getOpenPrice().add(kline.getClosePrice()).doubleValue() / 2;
                    from++;
                }
                avgP /= (size * 5 / 100);
                avgList.add(avgP);
            }

            /**
             * Mean variance, reflecting the overall shock
             */
            Double avgVariance = avgList.stream().map(avgP ->
                    BigDecimal.valueOf(Math.pow(((avgP - overallAvgPrice) / overallAvgPrice * 100), 2))
                            .setScale(2, RoundingMode.DOWN).doubleValue()
            ).collect(Collectors.summingDouble(e -> e));
            // Mean variance
            res[3] = avgVariance;
            return res;
        } catch (Exception e) {
            log.info(e.getMessage());
            return null;
        }
    }

    /**
     * Entry function
     */
    public List<SymbolShock> shockAnalyzeHandler(List<String> symbols, IntervalEnum period) {
        List<SymbolShock> shockModels = new ArrayList<>(symbols.size() * 2);
        for (String symbol : symbols) {
            /**
             * res[0] shakeVal
             * res[1] Lower limit price of shock range
             * res[2] high limit price of shock range
             */
            Double[] res = this.shockAnalyze(symbol, period);
            if (Objects.isNull(res)) {
                continue;
            }
            // The result is ${incRate}%
            BigDecimal incRate = BigDecimal.valueOf((res[2] - res[1]) * 100 / res[1]).setScale(3, RoundingMode.DOWN);
            double meanVariance = res[3];
            shockModels.add(SymbolShock.builder()
                    .symbol(symbol)
                    .ShockVal(res[0].longValue())
                    .incRate(incRate)
                    .maxPrice(BigDecimal.valueOf(res[2]))
                    .minPrice(BigDecimal.valueOf(res[1]))
                    .meanVariance(meanVariance)
                    .build());
        }

        /**
         *  shockVal and incRate Basically inversely proportional, requires weighted summation,
         *  shock Calculate the percentage of the median and filter out the positive value
         *  incRate Calculate the percentage of the median and filter out the positive value
         */
        // Get the median of shockVal and incRate
        shockModels.sort((a, b) -> (int) (a.getShockVal() - b.getShockVal()));
        double shockValMedian = shockModels.size() % 2 == 0
                ? (shockModels.get(shockModels.size() / 2).getShockVal() + shockModels.get((shockModels.size() - 1) / 2).getShockVal()) / 2
                : shockModels.get(shockModels.size() / 2).getShockVal();

        shockModels.sort(Comparator.comparing(SymbolShock::getIncRate));
        BigDecimal incRateMedian = shockModels.size() % 2 == 0
                ? (shockModels.get(shockModels.size() / 2).getIncRate().add(shockModels.get((shockModels.size() - 1) / 2).getIncRate())).divide(BigDecimal.valueOf(2), 6, RoundingMode.DOWN)
                : shockModels.get(shockModels.size() / 2).getIncRate();

        List<SymbolShock> result = shockModels.stream()
                // The mean variance is less than
                //      125 * (assuming that the upper limit of the amplitude is 10% and the local interval is 5%, the optimal variance is 2.5^2 * 20=125)
                .filter(e -> e.getMeanVariance() < 125 * period.time / 1440 / 1000 / 60)
                .sorted((e1, e2) -> {
                    double e1ShockRate = e1.getShockVal() / shockValMedian - 1;
                    double e2ShockRate = e2.getShockVal() / shockValMedian - 1;
                    double e1IncRate = e1.getIncRate().divide(incRateMedian, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE).doubleValue();
                    double e2IncRate = e2.getIncRate().divide(incRateMedian, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE).doubleValue();
                    return e2ShockRate > e1ShockRate ? 1 : -1;
                })
//                .limit(20)
                .collect(Collectors.toList());
        return result;
    }
}