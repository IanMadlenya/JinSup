package edu.virginia.jinsup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Class that handles order creation, modification, and cancellation. Also deals
 * with trades and provides agents with appropriate trade data from the last
 * millisecond of trading.
 */

public class MatchingEngine {

  /**
   * Maximum size the log buffer will reach before writing
   */
  private static final int LOG_BUFFER_SIZE = 524288;

  /**
   * ID to be assigned to orders that trade.
   */
  private long tradeMatchID;

  /**
   * All the orders in the simulation, grouped by agent ID.
   */
  private final HashMap<Long, ArrayList<Order>> orderMap;

  /**
   * All the agents in the simulation, indexed by agent ID.
   */
  private final HashMap<Long, Agent> agentMap;

  /**
   * All the buy orders in the simulation, highest first.
   */
  private final TreeSet<Order> buyOrders;

  /**
   * All the sell orders in the simulation, highest first.
   */
  private final TreeSet<Order> sellOrders;

  /**
   * The price (CENTS) that the share was last traded at. This should be plotted
   * every time it is updated (i.e. whenever a trade occurs).
   */
  private int lastTradePrice;

  /**
   * Remains true while the simulator is still in the starting period. This
   * means that all orders that may result in a trade will be cancelled.
   */
  private boolean startingPeriod;

  /**
   * A temporary buffer in memory used to keep logging information so that the
   * program does not have write to the log file every time an order is made,
   * traded, etc. Writes are made to the log file either when the simulator is
   * done or when the buffer is full.
   */
  private final ArrayList<String> logBuffer;

  /**
   * Time in milliseconds that an action occurs. This is kept in sync with the
   * controller's time. Used as a time-stamp for the log.
   */
  private long time;

  /**
   * Creates a matching engine with empty fields. Everything is initialized to
   * zero. Also initializes the log file.
   * 
   */
  public MatchingEngine() {
    orderMap = new HashMap<Long, ArrayList<Order>>();
    buyOrders = new TreeSet<Order>(Order.highestFirstComparator);
    sellOrders = new TreeSet<Order>(Order.highestFirstComparator);
    agentMap = new HashMap<Long, Agent>();
    lastTradePrice = Parameters.buyPrice;
    startingPeriod = true;
    tradeMatchID = 0;

    // 2^19 lines before writing to file
    logBuffer = new ArrayList<String>(LOG_BUFFER_SIZE);
    if (!Parameters.testing) {
      // create the CSV file
      File logFile = new File(Settings.getDestTradeFile());
      try {
        FileWriter writer = new FileWriter(logFile.getAbsoluteFile());
        writer
          .append("Time, Best Bid Price, Best Ask Price, Agent ID, Agent Type,"
            + " Message, Buy/Sell, Order ID, "
            + "Original Quantity, Price, Type, Leaves Quantity, Trade Price, "
            + "Quantity Filled, Aggressor, Trade Match ID\n");
        writer.flush();
        writer.close();
      } catch (IOException e) {
        System.err.println("Error: Failed to create log file.");
        e.printStackTrace();
        System.exit(1);
      }
    }
  }

  /**
   * Deletes an order from the simulation (orderMap and allOrders).
   * 
   * @param order
   *          The order to be removed.
   * @param agentID
   *          ID of the agent whose order is to be removed.
   */
  public void cancelOrder(Order order) {
    buyOrders.remove(order);
    sellOrders.remove(order);
    orderMap.get(order.getCreatorID()).remove(order);
    logOrder(order, 3, false, -order.getCurrentQuant(), 0);
  }

  /**
   * Deletes all orders of an agent for a certain price from the simulation.
   * 
   * @param price
   *          The price of the order to remove.
   */
  public void cancelOrder(long agentID, int price) {
    ArrayList<Order> cancelList = new ArrayList<Order>();
    for (Order o : orderMap.get(agentID)) {
      if (o.getPrice() == price) {
        // Cannot remove order while iterating.
        cancelList.add(o);
      }
    }
    for (Order o : cancelList) {
      cancelOrder(o);
    }
  }

  /**
   * Inserts the agent into the MatchingEngine's agentMap so that it can keep
   * track of it. This is called every time a new agent is constructed so it
   * should not have to be explicitly called.
   * 
   * @param id
   *          ID of the agent.
   * @param agent
   *          Agent object to be added.
   */
  public void addNewAgent(long id, Agent agent) {
    agentMap.put(id, agent);
  }

  /**
   * Takes a newly created order and stores it in the MatchingEngine's allOrders
   * and orderMap so that it can keep track of it. Also checks if any trade
   * occurs from this newly created order.
   * 
   * @param order
   *          New order to be inserted into the MatchingEngine.
   * @param agentID
   *          ID of the agent that initiated the order.
   */
  public boolean createOrder(Order order) {

    if (order.isBuyOrder()) {
      buyOrders.add(order);
    } else {
      sellOrders.add(order);
    }
    if (orderMap.containsKey(order.getCreatorID())) {
      orderMap.get(order.getCreatorID()).add(order);
    } else {
      ArrayList<Order> orderList = new ArrayList<Order>();
      orderList.add(order);
      orderMap.put(order.getCreatorID(), orderList);
    }
    // log the action.
    // must then check if a trade can occur

    logOrder(order, 1, order.isMarketOrder(), 0, 0);
    return trade(order, willTrade(order));
  }

  /**
   * Trades market orders only. If there are not orders to satisfy the market
   * order, then the simulation exits.
   * 
   * @param order
   *          The market order to be traded.
   */
  public void
    tradeMarketOrder(long agentID, int initialQuant, boolean buyOrder) {
    if (startingPeriod) {
      return;
    }
    int quantToRid = initialQuant;

    TreeSet<Order> interestedOrders;
    if (buyOrder) {
      // The top ten sell orders sorted by price (lowest) and time of order
      // creation (most recent).
      interestedOrders = new TreeSet<Order>(Order.lowestFirstComparator);
      for (Order o : sellOrders) {
        if (interestedOrders.size() < 10) {
          interestedOrders.add(o);
        } else if (Order.lowestFirstComparator.compare(o,
          interestedOrders.last()) < 0) {
          interestedOrders.pollLast();
          interestedOrders.add(o);
        }
      }
    } else {
      // The top ten pending buy orders sorted by price (highest) and time of
      // order creation (most recent).
      interestedOrders = new TreeSet<Order>(Order.highestFirstComparator);
      for (Order o : buyOrders) {
        if (interestedOrders.size() < 10) {
          interestedOrders.add(o);
        } else if (Order.highestFirstComparator.compare(o,
          interestedOrders.last()) < 0) {
          interestedOrders.pollLast();
          interestedOrders.add(o);
        }
      }
    }

    if (interestedOrders.isEmpty()) {
      System.err.println("Error: Sell / Buy orders depleted.");
      System.exit(1);
    }

    // TODO Perhaps use a SortedMap instead, but must ensure that prices are
    // ordered correctly depending on buy/sell.
    ArrayList<Integer> prices = new ArrayList<Integer>();
    ArrayList<Integer> quantities = new ArrayList<Integer>();
    int priceIndex = -1;
    int currPrice = 0;

    while (quantToRid > 0 && !interestedOrders.isEmpty()) {
      if (currPrice == interestedOrders.first().getPrice()) {
        if (quantToRid > interestedOrders.first().getCurrentQuant()) {
          quantities.set(priceIndex, quantities.get(priceIndex)
            + interestedOrders.first().getCurrentQuant());
        } else {
          quantities.set(priceIndex, quantities.get(priceIndex) + quantToRid);
          quantToRid = 0;
          break;
        }
      } else {
        priceIndex++;
        currPrice = interestedOrders.first().getPrice();
        prices.add(currPrice);
        if (quantToRid > interestedOrders.first().getCurrentQuant()) {
          quantities.add(interestedOrders.first().getCurrentQuant());
        } else {
          quantities.add(quantToRid);
          quantToRid = 0;
          break;
        }

      }
      quantToRid -= interestedOrders.first().getCurrentQuant();
      interestedOrders.pollFirst();
    }

    if (quantToRid > 0) {
      System.err.println("Error: Not enough orders to satisfy market order.");
      System.exit(1);
    }

    int i = 0;
    while (i <= priceIndex) {
      createOrder(new Order(agentID, prices.get(i), quantities.get(i),
        buyOrder, true));
      ++i;
    }

    // Last trade price logging is done in the trade(order, arrlist) method.
  }

  /**
   * Modifies the order in the MatchingEngine's allOrders and orderMap. Also
   * checks if any trade occurs from this modification.
   * 
   * @param order
   *          Order being modified.
   * @param newPrice
   *          The new price the order should have.
   * @param newQuant
   *          The new quantity the order should have.
   */
  public boolean modifyOrder(Order order, int newPrice, int newQuant) {
    order.setPrice(newPrice);
    order.setQuant(newQuant);
    // log the action
    logOrder(order, 2, false, newQuant - order.getCurrentQuant(), newPrice
      - order.getPrice());
    // must then check if a trade can occur
    return trade(order, willTrade(order));
  }

  /**
   * @return The last price that a share was traded for. Used primarily as data
   *         for the bar chart.
   */
  public int getLastTradePrice() {
    return lastTradePrice;
  }

  /**
   * @return The sum of quantities of all orders at the best bid price.
   */
  public int getBestBidQuantity() {
    if (buyOrders.isEmpty()) {
      return 0;
    }
    int quantity = 0;
    int bestBidPrice = getBestBid().getPrice();
    for (Order o : buyOrders) {
      if (o.getPrice() == bestBidPrice) {
        quantity += o.getCurrentQuant();
      }
    }
    return quantity;
  }

  /**
   * @return The sum of quantities of all orders at the best ask price.
   */
  public int getBestAskQuantity() {
    if (sellOrders.isEmpty()) {
      return 0;
    }
    int quantity = 0;
    int bestAskPrice = getBestAsk().getPrice();
    for (Order o : sellOrders) {
      if (o.getPrice() == bestAskPrice) {
        quantity += o.getCurrentQuant();
      }
    }
    return quantity;
  }

  /**
   * Checks if an order will cause a trade.
   * 
   * @param order
   *          The order to check for a trade
   * @return Orders that have the same price, if a trade can be made. Otherwise,
   *         null. Returns only enough orders to satisfy a trade. The list is
   *         sorted in descending order of priority.
   */
  public ArrayList<Order> willTrade(Order order) {
    ArrayList<Order> samePrice = new ArrayList<Order>();
    ArrayList<Order> toTrade = new ArrayList<Order>();
    int quantToRid = order.getCurrentQuant();
    TreeSet<Order> interestedOrders =
      order.isBuyOrder() ? sellOrders : buyOrders;
    for (Order o : interestedOrders) {
      if (o.getPrice() == order.getPrice()) {
        samePrice.add(o);
      }
    }

    // Does not matter which comparator; prices are the same. Just want orders
    // that have been made first to get the highest trading priority.
    Collections.sort(samePrice, Order.highestFirstComparator);

    for (Order o : samePrice) {
      toTrade.add(o);
      quantToRid -= o.getCurrentQuant();
      if (quantToRid <= 0) {
        break;
      }
    }

    return (toTrade.isEmpty()) ? null : samePrice;
  }

  /**
   * Initializes trading for limit orders. If samePricedOrders is null, i.e. the
   * order will not make a trade, then the function simply exits and no logging
   * is done.
   * 
   * @param order
   *          The order to be traded.
   * @param samePricedOrders
   *          Orders having the same price as the order to be traded.
   * 
   * @return True if the trade was made.
   */
  public boolean trade(Order order, ArrayList<Order> samePricedOrders) {
    if (samePricedOrders == null) {
      return false;
    }

    // save price for logging below
    int price = order.getPrice();

    if (startingPeriod) {
      cancelOrder(order);
      return false;
    }

    lastTradePrice = samePricedOrders.get(0).getPrice();
    int quantToRid = order.getCurrentQuant();
    int orderIndex = 0;
    long currentID = getAndUpdateTradeMatchID();
    while (quantToRid > 0 && orderIndex < samePricedOrders.size()) {
      quantToRid -= trade(order, samePricedOrders.get(orderIndex), currentID);
      orderIndex++;
    }

    // Update trading graph
    if (Parameters.showGui) {
      Controller.graphFrame.addTrade(Controller.time * 0.001, price);
    }
    return true;
  }

  /**
   * @return The order with the highest bid price.
   */
  public Order getBestBid() {
    if (buyOrders.isEmpty()) {
      return null;
    } else {
      return buyOrders.first();
    }
  }

  /**
   * @return The order with the lowest ask price.
   */
  public Order getBestAsk() {
    if (sellOrders.isEmpty()) {
      return null;
    } else {
      return sellOrders.last();
    }
  }

  /**
   * Sets the matching to allow or disallow trades to occur based on whether or
   * not the simulation is still running during the startup period.
   * 
   * @param isStartingPeriod
   *          If true, then the simulation will remain under startup mode and
   *          trades will not be allowed. Otherwise, trades will be enabled.
   */
  public void setStartingPeriod(boolean isStartingPeriod) {
    startingPeriod = isStartingPeriod;
  }

  /**
   * @return True if the simulator is in the start-up period.
   */
  public boolean isStartingPeriod() {
    return startingPeriod;
  }

  /**
   * Logs all the required information into the order book and updates graph
   * when an order is created, modified, or deleted. The CSV to be logged will
   * have the following fields: Agent ID, Message Type (1 = new order, 2 =
   * modification , 3 = cancel, 105 = trade), Buy/Sell (1/2), Order ID, Original
   * Order Quantity, Price, Order Type (limit/market), Leaves Quantity.
   * 
   * @param order
   *          Order to log
   * @param messageType
   *          Message type
   * @param market
   *          True if logging a market order. False if logging a limit order
   */
  public void logOrder(Order order, int messageType, boolean market,
    int quantChanged, int priceChanged) {
    if (Parameters.showGui) {
      switch (messageType) {
        case 1:
          Controller.graphFrame.addOrder(order.isBuyOrder(),
            order.getCurrentQuant(), order.getPrice());
          break;
        case 2:
          if (priceChanged != 0) {
            // delete all orders from the old price point
            Controller.graphFrame.addOrder(order.isBuyOrder(),
              -order.getCurrentQuant(), order.getPrice() - priceChanged);
            // re-add the orders to the new price point
          } else {
            Controller.graphFrame.addOrder(order.isBuyOrder(), quantChanged,
              order.getPrice());
          }
          break;
        case 3:
          Controller.graphFrame.addOrder(order.isBuyOrder(), quantChanged,
            order.getPrice());
          break;
        default:
          System.err.println("Message type " + messageType + " is invalid.");
          System.exit(1);
          break;
      }
    }
    if (!Parameters.testing) {
      if (logBuffer.size() == LOG_BUFFER_SIZE) {
        // write the stuff to the file.
        writeToLog();
      }

      logBuffer.add(time + "," + getBestBidOrAskPriceString(true) + ","
        + getBestBidOrAskPriceString(false) + "," + order.getCreatorID() + ","
        + agentMap.get(order.getCreatorID()).getType() + "," + messageType
        + "," + (order.isBuyOrder() ? "1" : "2") + "," + order.getID() + ","
        + order.getOriginalQuant() + "," + order.getPrice() / 100.0 + ","
        + (market ? "Market" : "Limit") + "," + order.getCurrentQuant() + "\n");
    }
  }

  /**
   * A logging method that is called to log orders when they trade.
   * 
   * @param order
   *          The order to be logged.
   * @param market
   *          True if the order is a market order.
   * @param tradePrice
   *          The price that the trade occurred at.
   * @param volume
   *          The volume that was traded on this order.
   */
  public void logTrade(Order order, boolean market, int tradePrice, int volume,
    boolean aggressor, long matchID) {
    if (Parameters.showGui) {
      Controller.graphFrame.addOrder(order.isBuyOrder(), -volume, tradePrice);
    }
    if (!Parameters.testing) {
      if (logBuffer.size() == LOG_BUFFER_SIZE) {
        // write the stuff to the file.
        // logging for the passive order
        writeToLog();
      }
      logBuffer.add(time + "," + getBestBidOrAskPriceString(true) + ","
        + getBestBidOrAskPriceString(false) + "," + order.getCreatorID() + ","
        + agentMap.get(order.getCreatorID()).getType() + ",105,"
        + (order.isBuyOrder() ? "1" : "2") + "," + order.getID() + ","
        + order.getOriginalQuant() + "," + order.getPrice() * 0.01 + ","
        + (market ? "Market," : "Limit,") + order.getCurrentQuant() + ","
        + tradePrice * 0.01 + "," + volume + (aggressor ? ",Y" : ",N") + ", "
        + matchID + "\n");
    }
  }

  /**
   * Writes the logBuffer contents to the file and then clears the log buffer.
   * This is called when the buffer is full or when the simulation has ended.
   */
  public void writeToLog() {
    try {
      FileWriter writer = new FileWriter(Settings.getDestTradeFile(), true);
      for (int i = 0; i < logBuffer.size(); i++) {
        writer.append(logBuffer.get(i));
      }
      writer.flush();
      writer.close();

    } catch (IOException e) {
      System.err.println("Error: Failed to update log.");
      e.printStackTrace();
      System.exit(1);
    }

    logBuffer.clear();
  }

  /**
   * @param agentID
   *          Agent to select orders from
   * @return A random order that the agent made that has not been traded yet
   */
  public Order getRandomOrder(long agentID) {
    // if agent does not have anything to trade
    if (orderMap.get(agentID) == null || orderMap.get(agentID).size() < 1) {
      return null;
    }
    return orderMap.get(agentID).get(
      JinSup.rand.nextInt(orderMap.get(agentID).size()));
  }

  /**
   * Returns the oldest order in an agent's orderbook.
   * 
   * @param agentID
   *          The agent that needs the oldest order.
   * @return The oldest order in the agent's orderbook.
   */
  public Order getOldestOrder(long agentID) {
    ArrayList<Order> orders = orderMap.get(agentID);
    Order oldestOrder = null;
    long oldest = Order.getNextOrderID();
    for (Order order : orders) {
      if (order.getID() < oldest) {
        oldestOrder = order;
        oldest = order.getID();
      }
    }
    return oldestOrder;
  }

  /**
   * Check if an agent has orders.
   * 
   * @param agentID
   *          The agent to check.
   * @return True if agent has orders; false otherwise
   */
  public boolean agentHasOrders(long agentID) {
    return !(orderMap.get(agentID) == null || orderMap.get(agentID).size() == 0);
  }

  /**
   * @param agentID
   *          Agent that wants to cancel all outstanding sell orders
   */
  public void cancelAllSellOrders(long agentID) {
    int currIndex = 0;
    while (currIndex < orderMap.get(agentID).size()) {
      // If a removal is needed, then cannot increment the index since it is
      // possible that the next element (now placed at the current index after
      // removal of current element) is
      // also a sell order
      if (!orderMap.get(agentID).get(currIndex).isBuyOrder()) {
        cancelOrder(orderMap.get(agentID).get(currIndex));
      } else {
        currIndex++;
      }
    }
  }

  /**
   * @param agentID
   *          Agent that wants to cancel all outstanding buy orders
   */
  public void cancelAllBuyOrders(long agentID) {
    int currIndex = 0;
    while (currIndex < orderMap.get(agentID).size()) {
      // Same logic from cancelAllSellOrders applies
      if (orderMap.get(agentID).get(currIndex).isBuyOrder()) {
        cancelOrder(orderMap.get(agentID).get(currIndex));
      } else {
        currIndex++;
      }
    }
  }

  /**
   * Increments time; called from controller.
   */
  public void incrementTime() {
    time++;
  }

  /**
   * @return Sell orders as an ArrayList.
   */
  public ArrayList<Order> getSellOrdersAsArrayList() {
    ArrayList<Order> sellArrayList = new ArrayList<Order>();
    sellArrayList.addAll(sellOrders);
    return sellArrayList;
  }

  /**
   * @return Buy orders as an ArrayList.
   */
  public ArrayList<Order> getBuyOrdersAsArrayList() {
    ArrayList<Order> buyArrayList = new ArrayList<Order>();
    buyArrayList.addAll(buyOrders);
    return buyArrayList;
  }

  public long getAndUpdateTradeMatchID() {
    return tradeMatchID++;
  }

  public void setTradePrice(int newTradePrice) {
    lastTradePrice = newTradePrice;
  }

  /**
   * Method that performs the actual trading for the tradeMarketOrder and
   * trade(Order, ArrayList) method. Takes care of filling the correct
   * quantities during a trade.
   * 
   * @param agOrder
   *          Order of the aggressive agent.
   * @param passOrder
   *          Order of the passive agent.
   * @return The volume that was traded between the two orders.
   */
  private int trade(Order agOrder, Order passOrder, long matchID) {
    // save price for logging at the end.
    int price = passOrder.getPrice();
    lastTradePrice = price;

    int volumeTraded;
    if (agOrder.getCurrentQuant() == passOrder.getCurrentQuant()) {
      volumeTraded = agOrder.getCurrentQuant();
      if (agOrder.isBuyOrder()) {
        buyOrders.remove(agOrder);
      } else {
        sellOrders.remove(agOrder);
      }
      if (passOrder.isBuyOrder()) {
        buyOrders.remove(passOrder);
      } else {
        sellOrders.remove(passOrder);
      }
      // Setting quantities to new values is necessary for correct logging of
      // leaves quantity.
      agOrder.setQuant(0);
      passOrder.setQuant(0);
      orderMap.get(agOrder.getCreatorID()).remove(agOrder);
      orderMap.get(passOrder.getCreatorID()).remove(passOrder);
    } else if (agOrder.getCurrentQuant() > passOrder.getCurrentQuant()) {
      volumeTraded = passOrder.getCurrentQuant();
      agOrder.setQuant(agOrder.getCurrentQuant() - passOrder.getCurrentQuant());
      if (passOrder.isBuyOrder()) {
        buyOrders.remove(passOrder);
      } else {
        sellOrders.remove(passOrder);
      }
      passOrder.setQuant(0);
      orderMap.get(passOrder.getCreatorID()).remove(passOrder);
    } else {
      volumeTraded = agOrder.getCurrentQuant();
      passOrder.setQuant(passOrder.getCurrentQuant()
        - agOrder.getCurrentQuant());
      if (agOrder.isBuyOrder()) {
        buyOrders.remove(agOrder);
      } else {
        sellOrders.remove(agOrder);
      }
      agOrder.setQuant(0);
      orderMap.get(agOrder.getCreatorID()).remove(agOrder);
    }

    // Update order-book visualization
    logTrade(agOrder, agOrder.isMarketOrder(), price, volumeTraded, true,
      matchID);
    logTrade(passOrder, false, price, volumeTraded, false, matchID);

    // Update inventories
    int inventoryChange = volumeTraded * (agOrder.isBuyOrder() ? 1 : -1);
    agentMap.get(agOrder.getCreatorID()).setLastOrderTraded(true,
      inventoryChange);
    agentMap.get(passOrder.getCreatorID()).setLastOrderTraded(true,
      -inventoryChange);

    checkIntelligentAgentOrder(agOrder, volumeTraded);
    checkIntelligentAgentOrder(passOrder, volumeTraded);

    return volumeTraded;
  }

  /**
   * Checks if the order belongs to an intelligent agent. If so, then notify the
   * agent that the order has been traded.
   * 
   * @param order
   */
  private void checkIntelligentAgentOrder(Order order, int volumeTraded) {
    if (agentMap.get(order.getCreatorID()) instanceof IntelligentAgent) {
      agentMap.get(order.getCreatorID()).notify(order.getPrice(), time,
        volumeTraded, order.isBuyOrder());
    }
  }

  /**
   * Gets the best bid/ask price as a string for logging purposes.
   * 
   * @param wantBidPrice
   *          True if the requesting the best bid price as a string. Otherwise,
   *          will return the best ask price as a string.
   * 
   * @return The best bid/ask price in CENTS as a string. Returns "None" if
   *         there were no bid/ask orders.
   */
  private String getBestBidOrAskPriceString(boolean wantBidPrice) {
    return (wantBidPrice) ? (getBestBid() == null) ? "None" : Double
      .toString(getBestBid().getPrice() / 100.0) : (getBestAsk() == null)
      ? "None" : Double.toString(getBestAsk().getPrice() / 100.0);
  }
}
