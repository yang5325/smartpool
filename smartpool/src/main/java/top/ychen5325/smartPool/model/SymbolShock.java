package top.ychen5325.smartPool.model;

/**
 * @author yyy
 * @wx ychen5325
 * @email yangyouyuhd@163.com
 */

import lombok.*;

import java.math.BigDecimal;

/**
 * 记载某个币种的震荡属性
 *
 * @author yyy
 * @wx ychen5325
 * @email yangyouyuhd@163.com
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SymbolShock {

    String symbol;
    /**
     * Frequency values and key quantitative indicators calculated according to the algorithm
     */
    double ShockVal;


    BigDecimal incRate;


    BigDecimal maxPrice;
    BigDecimal minPrice;


    double meanVariance;
}

