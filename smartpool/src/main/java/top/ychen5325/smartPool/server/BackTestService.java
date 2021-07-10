package top.ychen5325.smartPool.server;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import top.ychen5325.smartPool.common.CallResult;
import top.ychen5325.smartPool.common.IntervalEnum;
import top.ychen5325.smartPool.common.UrlConfig;
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
 * @apiNote Historical backtesting of machine gun pool recommendations
 */
@Slf4j
@Component
public class BackTestService {

    @Resource
    public RestTemplate restTemplate;

    /**
     * Price per unit and disposal funds
     */
    BigDecimal gridPrice;
    BigDecimal gridBalance;
    BigDecimal usdtBalance = BigDecimal.valueOf(100);
    BigDecimal nextPayPrice;
    BigDecimal nextSellPrice;

    BigDecimal monthRate;

    List<Double> prices = new ArrayList<>();
    int index = 0;
    /**
     * LoverId&buyQty of all unsold buy orders
     */
    Stack<String> qtyStack = new Stack<>();


    private void clear() {
        index = 0;
        prices.clear();
        monthRate = BigDecimal.ZERO;
        nextSellPrice = null;
        nextPayPrice = null;
        usdtBalance = BigDecimal.valueOf(100);
        gridBalance = null;
        gridPrice = null;
        qtyStack.clear();
    }

    /**
     * Conduct historical market backtests on the currency within a specified period, and finally
     * return to its theoretical monthly rate of backtesting
     *
     * @return Theoretical monthly rate
     */
    public BigDecimal startRobot(String symbol, BigDecimal highP, BigDecimal lowP, BigDecimal incRate, Long startTime, Long endTime) {
        // 1、Get kline first
        if (!initKlines(symbol, startTime, endTime)) {
            return null;
        }
        // 2、Initialize the robot
        initRobot(highP, lowP, incRate);
        // 3、Simulation running
        patrol();
        // 4、Return monthly
        return monthRate;
    }

    private void initRobot(BigDecimal highP, BigDecimal lowP, BigDecimal incRate) {
        highP = highP.multiply(
                incRate.add(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.DOWN)
        );
        lowP = lowP.multiply(
                BigDecimal.valueOf(100).subtract(incRate)
                        .divide(BigDecimal.valueOf(100)));

        BigDecimal gridCount = incRate.multiply(BigDecimal.valueOf(9));

        gridPrice = (highP.subtract(lowP).divide(gridCount, 8, RoundingMode.DOWN));
        gridBalance = usdtBalance.divide(gridCount, 8, RoundingMode.DOWN);
        BigDecimal curP = BigDecimal.valueOf(prices.get(index));
        nextPayPrice = curP.subtract(gridPrice);
        nextSellPrice = curP.add(gridPrice);

        int unFilledCount = (highP.subtract(curP).divide(gridPrice, 8, BigDecimal.ROUND_DOWN)).intValue();
        // Available funds, place an order, obtain the actual purchase quantity, actual spent funds and handling fees
        double useFunds = gridBalance.doubleValue() * unFilledCount;
        double planQty = useFunds / curP.doubleValue();

        usdtBalance = usdtBalance.subtract(BigDecimal.valueOf(useFunds));

        for (int i = 0; i < unFilledCount; i++) {
            qtyStack.push(RandomUtil.randomLong(0, Long.MAX_VALUE) + "".concat("&").concat(String.valueOf(planQty / unFilledCount)));
        }
    }

    /**
     * Obtain the bar list for the backtest period,
     */
    private boolean initKlines(String symbol, Long startTime, Long endTime) {
        int count = 0;
        do {
            try {
                clear();
                monthRate = BigDecimal.ZERO;
                CallResult result = restTemplate.getForObject(
                        String.format(UrlConfig.klineListUrl, symbol, startTime, endTime), CallResult.class);
                prices = (List<Double>) result.getData();
                return true;
            } catch (Exception e) {
                log.info("{}k line acquisition failed:{}", symbol, e.getMessage());
            }
        } while (count++ < 3);
        return false;
    }

    /**
     * Simulation running
     */
    private void patrol() {
        while (true) {
            if (index == prices.size()) {
                return;
            }
            BigDecimal realPrice = BigDecimal.valueOf(prices.get(index++));
            if (realPrice.compareTo(nextPayPrice) < 1) {
                if (usdtBalance.compareTo(gridBalance) > -1) {
                    // Execute buy
                    usdtBalance = usdtBalance.subtract(gridBalance);

                    nextPayPrice = realPrice.subtract(gridPrice);
                    nextSellPrice = realPrice.add(gridPrice);

                    qtyStack.push(RandomUtil.randomLong(0, Long.MAX_VALUE) + "".concat("&").concat(String.valueOf(gridBalance.doubleValue() / realPrice.doubleValue())));
                }
            } else if (realPrice.compareTo(nextSellPrice) > -1) {
                if (!qtyStack.isEmpty()) {
                    // Get the top node of the stack
                    String curNode = qtyStack.pop();
                    String[] split = curNode.split("&");
                    String exeQty = split[1];

                    BigDecimal income = new BigDecimal(exeQty).multiply(realPrice);

                    monthRate = monthRate.add(income.subtract(gridBalance));

                    usdtBalance = usdtBalance.add(income);

                    nextPayPrice = realPrice.subtract(gridPrice);
                    nextSellPrice = realPrice.add(gridPrice);
                }
            }
        }
    }

    /**
     * Entry function
     * Backtest the currency shock data calculated by the machine gun pool within a specified period, and return to its theoretical monthly,
     *
     * @return ["symbol \t monthRate"]
     */
    public List<String> backTestHandler(IntervalEnum intervalEnum, List<SymbolShock> symbolShocks) {
        try {
            List<String> backTestPool = new ArrayList<>();
            Long time = intervalEnum.time;
            for (SymbolShock symbolShock : symbolShocks) {
                try {
                    BigDecimal monthRate = this.startRobot(
                            symbolShock.getSymbol(),
                            symbolShock.getMaxPrice(),
                            symbolShock.getMinPrice(),
                            symbolShock.getIncRate(),
                            System.currentTimeMillis() - time,
                            System.currentTimeMillis()
                    );
                    if (Objects.isNull(monthRate)) {
                        continue;
                    }
                    backTestPool.add(
                            symbolShock.getSymbol()
                                    .concat("\t" + monthRate.multiply(BigDecimal.valueOf(30))
                                            .setScale(2, RoundingMode.DOWN)
                                            .toPlainString()));
                } catch (Exception e) {
                    log.error("{},msg:{}", symbolShock.getSymbol(), e.getMessage());
                    e.printStackTrace();
                }
            }
            // Descending order, interception 20
            return backTestPool.stream().sorted((a, b) -> {
                Double a1 = Double.valueOf(a.split("\t")[1]);
                Double b1 = Double.valueOf(b.split("\t")[1]);
                return b1.compareTo(a1);
            }).limit(20).collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
