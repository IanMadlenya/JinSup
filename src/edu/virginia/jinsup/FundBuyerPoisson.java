package edu.virginia.jinsup;

public class FundBuyerPoisson extends PoissonAgent {

  public FundBuyerPoisson(MatchingEngine matchEng, int lambdaOrder,
    int lambdaCancel) {
    super(matchEng, lambdaOrder, lambdaCancel);
  }

  public void makeOrder() {
    // calculate the probability
    double probability = Math.random();
    // Market order 14% of the time
    if (probability < 0.14) {
      // Issue market order, assuming only one market order is
      createMarketOrder(1, true);
    }
    // 1 tick of last trade price 27% of the time
    else if (probability < 0.41) {
      createNewOrder(getLastTradePrice(), 1, true);
    }
    // 2 tick of last trade price 11% of the time
    else if (probability < 0.52) {
      createNewOrder(getLastTradePrice(), 2, true);
    }
    // 3 tick of last trade price 9% of the time
    else if (probability < 0.61) {
      createNewOrder(getLastTradePrice(), 3, true);
    }
    // 4 tick of last trade price 7% of the time
    else if (probability < 0.68) {
      createNewOrder(getLastTradePrice(), 4, true);
    }
    // 5 tick of last trade price 7% of the time
    else if (probability < 0.75) {
      createNewOrder(getLastTradePrice(), 5, true);
    }
    // 6 tick of last trade price 7% of the time
    else if (probability < 0.82) {
      createNewOrder(getLastTradePrice(), 6, true);
    }
    // 7 tick of last trade price 5% of the time
    else if (probability < 0.87) {
      createNewOrder(getLastTradePrice(), 7, true);
    }
    // 8 tick of last trade price 5% of the time
    else if (probability < 0.92) {
      createNewOrder(getLastTradePrice(), 8, true);
    }
    // 9 tick of last trade price 5% of the time
    else if (probability < 0.97) {
      createNewOrder(getLastTradePrice(), 9, true);
    }
    // 10 tick of last trade price 3% of the time
    else {
      createNewOrder(getLastTradePrice(), 10, true);
    }
  }
}
