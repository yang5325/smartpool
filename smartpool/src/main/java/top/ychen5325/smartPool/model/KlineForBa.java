package top.ychen5325.smartPool.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * @author yyy
 * @wx ychen5325
 * @email yangyouyuhd@163.com
 */
@Data
@Builder
@ToString
public class KlineForBa {
    Long id;
    Long openTime;
    Long closeTime;
    BigDecimal openPrice;
    BigDecimal closePrice ;
    BigDecimal maxPrice ;
    BigDecimal minPrice ;
    BigDecimal txAmount ;
}
