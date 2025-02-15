   ```html
<center><h2> 震荡机枪池白皮书</h2></center>
   ```


   ## 1、概述

灵魂三问：机枪池是什么？它能做什么？它是怎么工作的？

> 机枪池是一类理财产品的总称、其最大特点为通过**始终**将子弹(资金)打向理论收益率最高的靶子(投资标的)来达到提高整体收益率的效果。
>
> 机枪池概念最早诞生于算力挖矿、繁荣于Defi流动性挖矿

为了完成**始终**将子弹打向理论收益率最高的靶子的这个目标、机枪池需要完成两个事情

1. 不断的去计算市面上所有投资标的在未来一段时间内的理论收益
2. 当某个投资标的在未来一段时间内的理论收益超过当前投资标的并且能够覆盖固定切换成本时、切换打靶方向

> 其中核心的是第一步的算法、需要不断汇集各类数据、进行分析聚合、通过调参拟合和回测等手段使你的算法成熟、但往往一个算法也仅适用于当下一个领域、当一个成熟的机枪池项目开源后、也往往会被分叉出各种机枪池子项目。

震荡机枪池是什么？

> 震荡机枪池是为网格量化策略提供投资标的选择的辅助产品、通过算法、我们能量化出市场上所有投资标的的震荡状态、震荡状态越强、越适合作为网格策略的投资标的、且理论收益将会是最高的、

下面将大致介绍下

1. 传统网格策略的套利原理和收益分析
2. 震荡机枪池算法的量化思路

   ## 2、网格策略

   ### 2.1、套利过程

传统的网格策略即是在震荡行情中、划定一段价格区间、在区间内不断的执行低买高卖的行为过程。一图以蔽之   ![image-20210618221132318](https://img-blog.csdnimg.cn/img_convert/6a1c7856c2c413208eefa60b22f5f7a9.png)

   ### 2.2、收益分析

在量化策略优劣分析中、网格策略仅适合于在震荡行情下进行套利操作、在单边上涨行情时、你甚至跑不过基准收益。既然仅适合于震荡行情、那么我们还是有必要使用当前处于震荡行情的投资标进行网格套利、且越震荡我越快乐。这似乎是一个很实用的结论。

但好像你还是不知道怎么选。下面我们先看下列这些例子

如果当下行情有两支投资标的走势如下图、那么你会如何选择？

   #### 例1

图左是 y1 = sin(2x) 函数曲线、图右是 y2 = sin(x)函数曲线、

众所周知、我们能得到：

1. y1的振幅 = y2的振幅
2. y1的周期 = 二分之一倍 y2的周期

如果将两条曲线比作两个不同的投资标的走势的话、很明显、左图的震荡状态指标优于右图

   ![image-20210618223451788](https://img-blog.csdnimg.cn/img_convert/2ff9b0f3d7d0105ccd27605485f3a362.png)

   #### 例2

图左是 y1 = sin(2x) 函数曲线、图右是 y2 = 2sin(x)函数曲线、

众所周知、我们能得到：

1. y1的振幅 =二分之一倍 y2的振幅
2. y1的周期 = 二分之一倍 y2的周期

如果将两条曲线比作两个不同的投资标的走势的话、你也不太好选择、左图虽然周期更小、但是振幅也较小、右图虽然周期大、但是振幅也大、那么如何选择呢？

   ![image-20210618223836798](https://img-blog.csdnimg.cn/img_convert/3b63081f0f5ed508bb7389a7551bcc2e.png)

我们将两条曲线置于同一个坐标系后发现、y1和y2具备公共周期6.28、在6.28周期内、他们的 sum(abs(△y))是相等的！即两个条曲线在公共周期内、对y轴扫过的距离是相等的。

而应用于行情走势中、我们可以定义：

> 当两个投资标的在一段周期内对价格轴(y轴)扫描的距离相等时、那么两者的震荡状态指标的值相等。

   ![image-20210618225055141](https://img-blog.csdnimg.cn/img_convert/f5f59724ed67ec43932eed63cab11877.png)

那么我们是否可以得到结论： **某投资标的应用于网格策略的理论收益正相关于一定周期内、k线对价格轴的扫过距离。扫过的距离越大、表示波动越强、震荡状态越优、理论收益将会越大。**

这是肯定的、但是、市场上、不会有投资标的走势完美契合三角函数、也不会有任意两支投资标的走势具有公共周期、那么我们如何在走势多样的实盘中进行投资标的的震荡分析呢。

   ## 3、震荡机枪池

   ### 3.1、分析-y轴映射法

前文提到、某投资标的应用于网格策略的理论收益正相关于一定周期内、k线对价格轴的扫过距离

那么如何计算其扫过的距离成为了算法实现核心所在

我们先来看一段行情走势图

图左是某投资标的的最近四根1h级别k线

```yaml
8am: 2$ to 6$
9am: 6$ to 4$
10am: 4$ to 8$
11am: 8$ to 0~(跑路了属于是)
```

图右是将k线向左平移后产生的图谱

![image-20210620230924400](https://img-blog.csdnimg.cn/img_convert/580ccedc10b639d293e49eec87757809.png)

图右将四根1h级别k线向价格轴平移映射后、就形成了简单的对价格轴一次映射堆叠图谱。到了这里我们似乎还不能直观的得到任何有效信息、那么我们先将右图简单的颠倒一下、看看像什么。

![image-20210620233254090](https://img-blog.csdnimg.cn/img_convert/69183093dcd5522d2d88a6acad5da27a.png)

先看图左、是个柱形图、只不过x轴变成了价格轴、假设 x = 7时、y = 2、那么x = 5时、y = 4

而图右呢、好像柱形图的均线、这条均线的四个点位为

```html
(7,2);(5,4);(3,2);(1,1)
```

但我更建议你用正态分布的角度去分析这条均线。我们假设上述仅有9个点位、我们将得到

1. 该分布曲线的峰在 x =5、总点位数为4个、占据百分比为44.4%
2. 在 x in (7,5,3) 时的点位和为8个、占据百分比为88.8%

**那么我们是否可以根据正态分布的特征、确定一个占据百分比的下限、并在左右端点各定义一个指针、指针向中间移动、当双指针围成的区间内的点数和比上总点数接近于这个下线时、把左右指针所在价格围成的价格区间定义为该投资标的的震荡区间、通过计算震荡区间内的点位总数来得到一个可量化的指标**。

举个栗子:

> 下限设为80%、则投资标在震荡区间内点位数占比必须大于等于80%
> 那么上述行情则得到、该投资标的震荡区间为[2,8]、区间内点位和为8、因为其在该区间内的点位和占比为88.8%、大于80%。

   ### 3.2、实现

> [联系我](https://t.me/ychen5325)


