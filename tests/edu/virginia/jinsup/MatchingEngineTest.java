package edu.virginia.jinsup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

public class MatchingEngineTest {

  private static final int TRADE_PRICE = 127000;
  private static final int TICK_SIZE = 25;
  private static final int SOME_AGENT_ID = -1;
  private MatchingEngine matchingEngine;
  private FundBuyer fundBuyer;
  private FundSeller fundSeller;

  @Before
  public void setUp() throws Exception {
    matchingEngine = new MatchingEngine(TRADE_PRICE, 0);
    fundBuyer = new FundBuyer(matchingEngine);
    fundSeller = new FundSeller(matchingEngine);
    matchingEngine.setStartingPeriod(false);
  }

  @Test
  public void willTradeTest_takeAll() {
    int orderedQuantities[] = new int[] {1, 2, 1};
    for (int i : orderedQuantities) {
      fundSeller.createNewOrder(TRADE_PRICE + TICK_SIZE, i, false);
    }
    Order buyOrder =
      new Order(SOME_AGENT_ID, TRADE_PRICE + TICK_SIZE, 4, true, false);
    ArrayList<Order> toTrade = matchingEngine.willTrade(buyOrder);
    ArrayList<Integer> resultList = new ArrayList<Integer>();

    for (Order order : toTrade) {
      resultList.add(order.getCurrentQuant());
      System.out.println(order.getId());
    }
    assertArrayEquals(orderedQuantities, arrayListToArray(resultList));
  }

  @Test
  public void willTradeTest_takeLess() {
    int orderedQuantities[] = new int[] {2, 2, 1,};
    int actualQuants[] = new int[] {2, 2};
    for (int i : orderedQuantities) {
      fundSeller.createNewOrder(TRADE_PRICE + TICK_SIZE, i, false);
    }
    Order buyOrder =
      new Order(SOME_AGENT_ID, TRADE_PRICE + TICK_SIZE, 4, true, false);
    ArrayList<Order> toTrade = matchingEngine.willTrade(buyOrder);
    ArrayList<Integer> resultList = new ArrayList<Integer>();

    for (Order order : toTrade) {
      resultList.add(order.getCurrentQuant());
      System.out.println(order.getId());
    }
    assertArrayEquals(actualQuants, arrayListToArray(resultList));
  }

  @Test
  public void willTradeTest_notEnough() {
    int orderedQuantities[] = new int[] {1, 2};
    for (int i : orderedQuantities) {
      fundSeller.createNewOrder(TRADE_PRICE + TICK_SIZE, i, false);
    }
    Order buyOrder =
      new Order(SOME_AGENT_ID, TRADE_PRICE + TICK_SIZE, 4, true, false);
    ArrayList<Order> toTrade = matchingEngine.willTrade(buyOrder);
    ArrayList<Integer> resultList = new ArrayList<Integer>();

    for (Order order : toTrade) {
      resultList.add(order.getCurrentQuant());
      System.out.println(order.getId());
    }
    assertArrayEquals(orderedQuantities, arrayListToArray(resultList));
  }

  @Test
  public void tradeLimitOrdersTest_leavesOrder() {
    tradeLimitOrderSetup();
    assertEquals(1, matchingEngine.getSellOrdersAsArrayList().size());
  }

  @Test
  public void tradeLimitOrderTest_leavesQuant() {
    tradeLimitOrderSetup();
    assertEquals(1, matchingEngine.getSellOrdersAsArrayList().get(0)
      .getCurrentQuant());
  }

  @Test
  public void tradeLimitOrderTest_mutualDepletion() {
    tradeLimitOrderSetup();
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 1, true);
    assertEquals(0, matchingEngine.getSellOrdersAsArrayList().size());
  }

  @Test
  public void tradeLimitOrderTest_shortageSellSide() {
    tradeLimitOrderSetup();
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 2, true);
    assertEquals(0, matchingEngine.getSellOrdersAsArrayList().size());
  }

  @Test
  public void tradeLimitOrderTest_shortageBuySide() {
    tradeLimitOrderSetup();
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 2, true);
    assertEquals(1, matchingEngine.getBuyOrdersAsArrayList().size());
  }

  @Test
  public void tradeLimitOrderTest_shortageBuySideLeavesQuant() {
    tradeLimitOrderSetup();
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 2, true);
    assertEquals(1, matchingEngine.getBuyOrdersAsArrayList().get(0)
      .getCurrentQuant());
  }

  @Test
  public void tradeLimitOrderTest_inventoryBuySide() {
    tradeLimitOrderSetup();
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 1, true);
    assertEquals(3, fundBuyer.getInventory());
  }

  @Test
  public void tradeLimitOrderTest_inventorySellSide() {
    tradeLimitOrderSetup();
    assertEquals(-2, fundSeller.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_simpleOneVsOne() {
    fundBuyer.createNewOrder(TRADE_PRICE, 1, true);
    fundSeller.createMarketOrder(1, false);
    assertEquals(0, matchingEngine.getBestAskQuantity());
  }

  @Test
  public void tradeMarketOrderTest_simpleOneVsOneBuySideInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 1, true);
    fundSeller.createMarketOrder(1, false);
    assertEquals(1, fundBuyer.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_simpleOneVsOneSellSideInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 1, true);
    fundSeller.createMarketOrder(1, false);
    assertEquals(-1, fundSeller.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_quantityDepletionBuySide() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(1, matchingEngine.getBestBidQuantity());
  }

  @Test
  public void tradeMarketOrderTest_quantityDepletionSellSide() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(0, matchingEngine.getBestAskQuantity());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionBuySideLeavesQuant() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(1, matchingEngine.getBestBidQuantity());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionBuySideLeavesPrice() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(TRADE_PRICE - TICK_SIZE, matchingEngine.getBestBid()
      .getPrice());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionSellSideLeavesQuant() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(0, matchingEngine.getBestAskQuantity());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionBuySideInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(4, fundBuyer.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionSellSideInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(4, false);
    assertEquals(-4, fundSeller.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionManyToFewInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 5, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(9, false);
    assertEquals(-9, fundSeller.getInventory());
  }

  @Test
  public void tradeMarketOrderTest_singlePriceDepletionManyToFewQuantity() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 5, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundSeller.createMarketOrder(9, false);
    assertEquals(0, matchingEngine.getBestAskQuantity());
  }

  @Test
  public void tradeMarketOrderTest_multiplePriceDepletionFewToMany() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 5, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 4, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE * 2, 10, true);
    fundSeller.createMarketOrder(20, false);
    assertEquals(4, matchingEngine.getBestBidQuantity());
  }

  @Test
  public void tradeMarketOrderTest_multiplePriceDepletionFewToManyPrice() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 5, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 4, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE * 2, 10, true);
    fundSeller.createMarketOrder(20, false);
    assertEquals(TRADE_PRICE - TICK_SIZE * 2, matchingEngine.getBestBid()
      .getPrice());
  }

  @Test
  public void tradeMarketOrderTest_multiplePriceDepletionFewToManyInventory() {
    fundBuyer.createNewOrder(TRADE_PRICE, 3, true);
    fundBuyer.createNewOrder(TRADE_PRICE, 5, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 2, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE, 4, true);
    fundBuyer.createNewOrder(TRADE_PRICE - TICK_SIZE * 2, 10, true);
    fundSeller.createMarketOrder(20, false);
    assertEquals(-20, fundSeller.getInventory());
  }

  public void tradeLimitOrderSetup() {
    int orderedQuantities[] = new int[] {1, 2};
    for (int i : orderedQuantities) {
      fundSeller.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, i, false);
    }
    fundBuyer.createNewOrder(TRADE_PRICE + TICK_SIZE * 2, 2, true);
  }

  public int[] arrayListToArray(ArrayList<Integer> arrayList) {
    int result[] = new int[arrayList.size()];
    for (int i = 0; i < arrayList.size(); ++i) {
      result[i] = arrayList.get(i);
    }
    return result;
  }
}
