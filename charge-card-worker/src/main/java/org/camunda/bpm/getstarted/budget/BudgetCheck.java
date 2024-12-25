package org.camunda.bpm.getstarted.budget;

public class BudgetCheck {
    private Double amount;
    private String decision;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}